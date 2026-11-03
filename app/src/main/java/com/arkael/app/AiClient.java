package com.arkael.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

/**
 * Klien chat kompatibel-OpenAI. Tanpa library tambahan: HttpURLConnection + org.json.
 * Mendukung streaming SSE dan tool_calls (dipakai mode agent).
 */
final class AiClient {

    interface Sink {
        void onDelta(String text);
        void onDone(String fullText, JSONArray toolCalls);
        void onError(String err);
    }

    private AiClient() {}

    static void chat(final String url, final String provider, final String key, final String model,
                     final String tokenField, final int maxTokens,
                     final JSONArray messages, final JSONArray tools,
                     final boolean stream, final Sink sink) {
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection c = null;
                try {
                    String ep = url == null ? "" : url.trim();
                    if (ep.isEmpty()) { sink.onError("Endpoint kosong"); return; }

                    c = (HttpURLConnection) new URL(ep).openConnection();
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(240000);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("Authorization", "Bearer " + (key == null ? "" : key.trim()));
                    if ("claude".equals(provider)) {
                        /* ai-agent-chat.sh: Claude butuh header sendiri selain Bearer */
                        c.setRequestProperty("x-api-key", key);
                        c.setRequestProperty("anthropic-version", "2023-06-01");
                    }
                    c.setRequestProperty("Accept", stream ? "text/event-stream" : "application/json");

                    JSONObject body = new JSONObject();
                    body.put("model", model);
                    String tf = (tokenField == null || tokenField.trim().isEmpty())
                            ? "max_tokens" : tokenField.trim();
                    body.put(tf, maxTokens <= 0 ? 8000 : maxTokens);
                    body.put("messages", messages);
                    body.put("stream", stream);
                    if (tools != null && tools.length() > 0) {
                        body.put("tools", tools);
                        body.put("tool_choice", "auto");
                    }

                    byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                    OutputStream os = c.getOutputStream();
                    os.write(payload);
                    os.flush();
                    os.close();

                    int code = c.getResponseCode();
                    if (code < 200 || code >= 300) {
                        String err = slurp(c.getErrorStream()).trim();
                        /* ai-agent-chat.sh: tampilkan pesan .error.message kalau ada, kalau tidak body mentah */
                        String msg = err;
                        try {
                            JSONObject ej = new JSONObject(err);
                            JSONObject e = ej.optJSONObject("error");
                            if (e != null && !e.optString("message", "").isEmpty()) msg = e.optString("message");
                            else if (!ej.optString("message", "").isEmpty()) msg = ej.optString("message");
                        } catch (Exception ignore) {
                            /* Gemini kadang balas array [{"error":{...}}] */
                            try {
                                JSONArray arr = new JSONArray(err);
                                JSONObject ej = arr.optJSONObject(0);
                                JSONObject e = ej == null ? null : ej.optJSONObject("error");
                                if (e != null && !e.optString("message", "").isEmpty()) msg = e.optString("message");
                            } catch (Exception ignore2) {}
                        }
                        String tip = "";
                        if (code == 429) {
                            String ra = c.getHeaderField("Retry-After");
                            tip = "\n\nKuota / rate limit provider (" + provider + ") habis."
                                + "\n\u2022 Tunggu "
                                + (ra == null || ra.trim().isEmpty() ? "beberapa saat" : ra.trim() + " detik")
                                + " lalu kirim ulang"
                                + "\n\u2022 Mode chat gratis punya batas per menit dan per hari"
                                + "\n\u2022 Kurangi lampiran besar / gambar di pesan berikutnya";
                        }
                        if (code == 401 || code == 403) {
                            tip = "\n\nKey ditolak provider (" + provider + ")."
                                + "\n\u2022 Cek key di menu \u2699 \u2192 " + provider + ": " + keyMask(key)
                                + "\n\u2022 Pastikan tidak ada spasi/baris baru saat menempel"
                                + "\n\u2022 Pastikan key milik provider yang sama dengan endpoint"
                                + " " + ep;
                        }
                        sink.onError("HTTP " + code + ": " + msg + tip);
                        return;
                    }

                    if (!stream) {
                        JSONObject j = new JSONObject(slurp(c.getInputStream()));
                        JSONArray ch = j.optJSONArray("choices");
                        JSONObject msg = (ch == null || ch.length() == 0) ? null : ch.getJSONObject(0).optJSONObject("message");
                        String text = msg == null ? "" : msg.optString("content", "");
                        JSONArray tc = msg == null ? null : msg.optJSONArray("tool_calls");
                        sink.onDone(text, tc == null ? new JSONArray() : tc);
                        return;
                    }

                    StringBuilder acc = new StringBuilder();
                    LinkedHashMap<Integer, JSONObject> calls = new LinkedHashMap<Integer, JSONObject>();
                    BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (!line.startsWith("data:")) continue;
                        String data = line.substring(5).trim();
                        if (data.isEmpty() || "[DONE]".equals(data)) continue;
                        JSONObject j;
                        try { j = new JSONObject(data); } catch (Exception ignore) { continue; }
                        JSONArray ch = j.optJSONArray("choices");
                        if (ch == null || ch.length() == 0) continue;
                        JSONObject d = ch.getJSONObject(0).optJSONObject("delta");
                        if (d == null) continue;

                        String t = d.optString("content", "");
                        if (!t.isEmpty()) { acc.append(t); sink.onDelta(t); }

                        JSONArray tc = d.optJSONArray("tool_calls");
                        if (tc != null) {
                            for (int i = 0; i < tc.length(); i++) {
                                JSONObject p = tc.getJSONObject(i);
                                int idx = p.optInt("index", i);
                                JSONObject cur = calls.get(idx);
                                if (cur == null) {
                                    cur = new JSONObject();
                                    cur.put("id", p.optString("id"));
                                    cur.put("type", "function");
                                    cur.put("function", new JSONObject());
                                    calls.put(idx, cur);
                                }
                                String id = p.optString("id");
                                if (id != null && !id.isEmpty()) cur.put("id", id);
                                JSONObject fn = p.optJSONObject("function");
                                if (fn == null) continue;
                                JSONObject cf = cur.getJSONObject("function");
                                String name = fn.optString("name", "");
                                if (!name.isEmpty()) cf.put("name", name);
                                String arg = fn.optString("arguments", "");
                                if (!arg.isEmpty()) cf.put("arguments", cf.optString("arguments", "") + arg);
                            }
                        }
                    }
                    JSONArray out = new JSONArray();
                    for (JSONObject o : calls.values()) out.put(o);
                    sink.onDone(acc.toString(), out);

                } catch (Exception e) {
                    String m = e.getMessage();
                    sink.onError(e.getClass().getSimpleName() + (m == null ? "" : ": " + m));
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }, "arkael-ai").start();
    }

    /** Sisa bagian key untuk pesan galat, tanpa membocorkan key utuh. */
    private static String keyMask(String k) {
        if (k == null || k.trim().isEmpty()) return "(kosong)";
        String v = k.trim();
        if (v.length() <= 8) return "\u2026" + v;
        return v.substring(0, 3) + "\u2026" + v.substring(v.length() - 4);
    }

    private static String slurp(java.io.InputStream is) {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            r.close();
        } catch (Exception ignore) {}
        return sb.toString();
    }
}
