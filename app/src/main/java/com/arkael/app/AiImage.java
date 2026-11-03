package com.arkael.app;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import android.util.Base64;

/**
 * generate_image: satu fungsi untuk semua jalur gambar.
 *  - pollinations : gratis, tanpa key (https://image.pollinations.ai)
 *  - openai       : /v1/images/generations, balasan b64_json atau url
 *  - custom       : endpoint OpenAI-compatible milik pengguna
 * Hasil disimpan ke Pictures/Arkael di folder app (tidak butuh izin storage).
 */
final class AiImage {

    /** Ukuran yang diterima OpenAI Images API; ukuran lain dipetakan ke yang terdekat. */
    static final String[] SIZES = {"1024x1024", "1024x1536", "1536x1024"};

    private AiImage() {}

    static String normSize(String s) {
        if (s == null) return "1024x1024";
        s = s.trim().toLowerCase().replace('x', 'x');
        if (!s.matches("\\d{3,4}x\\d{3,4}")) return "1024x1024";
        int w = Integer.parseInt(s.substring(0, s.indexOf('x')));
        int h = Integer.parseInt(s.substring(s.indexOf('x') + 1));
        if (w == h) return "1024x1024";
        return w > h ? "1536x1024" : "1024x1536";
    }

    /** Prompt dinormalkan: tidak kosong, dipotong ke 900 karakter (batas aman? aman untuk semua). */
    static String normPrompt(String p) {
        if (p == null) return "";
        p = p.trim().replaceAll("\\s+", " ");
        if (p.length() > 900) p = p.substring(0, 900);
        return p;
    }

    /** Menghasilkan gambar dan mengembalikan path file. Melempar Exception berisi pesan siap tampil. */
    static String generate(Context ctx, String provider, String key, String customUrl,
                           String prompt, String size) throws Exception {
        prompt = normPrompt(prompt);
        if (prompt.isEmpty()) throw new Exception("prompt gambar kosong");
        size = normSize(size);

        File dir = new File(ctx.getExternalFilesDir(null), "Pictures");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("tidak bisa membuat folder gambar");
        File out = new File(dir, "img_" + System.currentTimeMillis() + ".png");

        byte[] data;
        if ("openai".equals(provider) || "custom".equals(provider)) {
            data = viaOpenAi(provider, key, customUrl, prompt, size);
        } else {
            data = viaPollinations(prompt, size);
        }
        if (data == null || data.length < 1024) throw new Exception("provider mengirim gambar kosong");

        FileOutputStream fo = new FileOutputStream(out);
        try {
            fo.write(data);
        } finally {
            fo.close();
        }
        return out.getAbsolutePath();
    }

    private static byte[] viaPollinations(String prompt, String size) throws Exception {
        int w = Integer.parseInt(size.substring(0, size.indexOf('x')));
        int h = Integer.parseInt(size.substring(size.indexOf('x') + 1));
        String u = "https://image.pollinations.ai/prompt/" + URLEncoder.encode(prompt, "UTF-8")
                + "?width=" + w + "&height=" + h + "&nologo=true&safe=false";
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " dari image.pollinations.ai");
        return read(c.getInputStream());
    }

    private static byte[] viaOpenAi(String provider, String key, String customUrl,
                                    String prompt, String size) throws Exception {
        String ep;
        if ("custom".equals(provider)) {
            String base = customUrl == null ? "" : customUrl.trim();
            if (base.isEmpty()) throw new Exception("URL custom belum diisi");
            if (base.endsWith("/chat/completions")) base = base.substring(0, base.length() - "/chat/completions".length());
            ep = base.endsWith("/images/generations") ? base : base + "/images/generations";
        } else {
            ep = "https://api.openai.com/v1/images/generations";
        }

        JSONObject b = new JSONObject();
        b.put("model", "gpt-image-1");
        b.put("prompt", prompt);
        b.put("size", size);
        b.put("n", 1);

        HttpURLConnection c = (HttpURLConnection) new URL(ep).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(240000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + (key == null ? "" : key.trim()));
        OutputStream os = c.getOutputStream();
        try {
            os.write(b.toString().getBytes("UTF-8"));
        } finally {
            os.close();
        }

        int code = c.getResponseCode();
        String raw = readText(code < 200 || code >= 300 ? c.getErrorStream() : c.getInputStream());
        if (code < 200 || code >= 300) {
            String msg = raw;
            try {
                JSONObject e = new JSONObject(raw).optJSONObject("error");
                if (e != null && !e.optString("message", "").isEmpty()) msg = e.optString("message");
            } catch (Exception ignore) {}
            throw new Exception("HTTP " + code + ": " + msg);
        }

        JSONObject j = new JSONObject(raw);
        JSONObject item = null;
        org.json.JSONArray arr = j.optJSONArray("data");
        if (arr != null && arr.length() > 0) item = arr.optJSONObject(0);
        if (item == null) throw new Exception("balasan tanpa data gambar");

        String b64 = item.optString("b64_json", "");
        if (!b64.isEmpty()) return Base64.decode(b64, Base64.DEFAULT);

        String url = item.optString("url", "");
        if (url.isEmpty()) throw new Exception("balasan tanpa b64_json/url");
        HttpURLConnection d = (HttpURLConnection) new URL(url).openConnection();
        d.setConnectTimeout(20000);
        d.setReadTimeout(120000);
        return read(d.getInputStream());
    }

    private static byte[] read(InputStream is) throws Exception {
        if (is == null) return new byte[0];
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return bo.toByteArray();
    }

    private static String readText(InputStream is) {
        try {
            return new String(read(is), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
