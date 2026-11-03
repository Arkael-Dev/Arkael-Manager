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
                    c.setRequestProperty("Authorization", "Bearer " + key);
                    if ("claude".equals(provider)) {
                        /* ai-agent-chat.sh: Claude butuh header sendiri selain Bearer */
                        c.setRequestProperty("x-api-key", key);
                        c.setRequestProperty("anthropic-version", "2023-06-01");
                    }
                    c.setRequestProperty("Accept", stream ? "text/event-stream" : "application/json");

                    JSONObject body = new JSONObject();
                    body.put("model", model);
                    body.put("max_tokens", 8000);
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
                        String err = slurp(c.getErrorStream());
                        sink.onError("HTTP " + code + ": " + err.trim());
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
