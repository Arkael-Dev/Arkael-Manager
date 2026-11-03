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
        return generate(ctx, provider, key, customUrl, prompt, size, null);
    }

    /**
     * Sama seperti generate(), plus reference = path gambar acuan (img2img).
     * OpenAI/custom memakai /v1/images/edits (multipart). Pollinations tak punya img2img,
     * jadi acuan cuma dipakai sebagai tambahan deskripsi.
     */
    static String generate(Context ctx, String provider, String key, String customUrl,
                           String prompt, String size, String reference) throws Exception {
        prompt = normPrompt(prompt);
        if (prompt.isEmpty()) throw new Exception("prompt gambar kosong");
        size = normSize(size);

        File dir = new File(ctx.getExternalFilesDir(null), "Pictures");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("tidak bisa membuat folder gambar");
        File out = new File(dir, "img_" + System.currentTimeMillis() + ".png");

        boolean refOk = false;
        File ref = reference == null || reference.trim().isEmpty() ? null : new File(reference.trim());
        if (ref != null && ref.isFile() && AiFiles.isImage(ref) && ref.length() <= (4 << 20)) refOk = true;
        if (ref != null && !refOk) prompt = prompt + " (acuan: " + ref.getName() + ")";

        byte[] data;
        if ("openai".equals(provider) || "custom".equals(provider)) {
            data = refOk ? viaOpenAiEdit(provider, key, customUrl, prompt, size, ref)
                         : viaOpenAi(provider, key, customUrl, prompt, size);
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

    /** img2img: POST multipart ke /images/edits (OpenAI gpt-image-1 / dall-e-2). */
    private static byte[] viaOpenAiEdit(String provider, String key, String customUrl,
                                        String prompt, String size, File ref) throws Exception {
        String ep;
        String base = "https://api.openai.com/v1";
        if ("custom".equals(provider)) {
            base = customUrl == null ? "" : customUrl.trim();
            if (base.isEmpty()) throw new Exception("URL custom belum diisi");
            for (String tail : new String[]{"/chat/completions", "/images/generations", "/images/edits"}) {
                if (base.endsWith(tail)) base = base.substring(0, base.length() - tail.length());
            }
        }
        ep = base + "/images/edits";

        String boundary = "----arkael" + System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection) new URL(ep).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(240000);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        c.setRequestProperty("Authorization", "Bearer " + (key == null ? "" : key.trim()));

        OutputStream os = c.getOutputStream();
        try {
            StringBuilder head = new StringBuilder();
            head.append("--").append(boundary).append("\r\n");
            head.append("Content-Disposition: form-data; name=\"model\"\r\n\r\ngpt-image-1\r\n");
            head.append("--").append(boundary).append("\r\n");
            head.append("Content-Disposition: form-data; name=\"prompt\"\r\n\r\n").append(prompt).append("\r\n");
            head.append("--").append(boundary).append("\r\n");
            head.append("Content-Disposition: form-data; name=\"size\"\r\n\r\n").append(size).append("\r\n");
            head.append("--").append(boundary).append("\r\n");
            head.append("Content-Disposition: form-data; name=\"n\"\r\n\r\n1\r\n");
            head.append("--").append(boundary).append("\r\n");
            head.append("Content-Disposition: form-data; name=\"image\"; filename=\"")
                .append(AiFiles.safeName(ref.getName())).append("\"\r\n");
            head.append("Content-Type: ").append(AiFiles.mime(ref)).append("\r\n\r\n");
            os.write(head.toString().getBytes("UTF-8"));

            InputStream in = new java.io.FileInputStream(ref);
            try {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
            } finally {
                try { in.close(); } catch (Exception ignore) {}
            }
            os.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
            os.flush();
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
            throw new Exception("HTTP " + code + " dari images/edits: " + msg);
        }
        return decodeImage(raw);
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

        return decodeImage(raw);
    }

    /** Balasan Images API: b64_json langsung, atau url yang diunduh. */
    private static byte[] decodeImage(String raw) throws Exception {
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
