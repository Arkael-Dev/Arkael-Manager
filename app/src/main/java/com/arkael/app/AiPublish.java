package com.arkael.app;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Publikasi media keluar chat:
 *  - ke galeri perangkat (MediaStore, tanpa izin storage di Android 10+), dan
 *  - cari gambar asli di internet (Wikimedia Commons, cadangan Openverse) lalu simpan ke galeri.
 */
final class AiPublish {

    private static final String UA = "ArkaelManager/1.0 (Android; +https://github.com/Arkael-Dev/Arkael-Manager)";

    private AiPublish() {}

    /* ------------------------------ galeri ------------------------------ */

    /** Simpan file ke galeri. kind: "image" | "video" | "audio" | lain (dokumen). */
    static Uri toGallery(Context ctx, File f, String kind) throws Exception {
        if (f == null || !f.isFile()) throw new Exception("file tidak ada");
        String dir = Environment.DIRECTORY_PICTURES;
        Uri col = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        if ("video".equals(kind)) {
            dir = Environment.DIRECTORY_MOVIES;
            col = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        } else if ("audio".equals(kind)) {
            dir = Environment.DIRECTORY_MUSIC;
            col = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        } else if ("doc".equals(kind)) {
            dir = Environment.DIRECTORY_DOWNLOADS;
            col = MediaStore.Files.getContentUri("external");
        }
        String mime = AiFiles.mime(f);
        String name = f.getName();

        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, dir + "/Arkael");
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            if ("doc".equals(kind)) cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            Uri u = ctx.getContentResolver().insert(col, cv);
            if (u == null) throw new Exception("tidak bisa membuat entri galeri");
            OutputStream os = ctx.getContentResolver().openOutputStream(u);
            if (os == null) throw new Exception("tidak bisa menulis ke galeri");
            copy(f, os);
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            ctx.getContentResolver().update(u, done, null, null);
            return u;
        }

        /* Android 7-9: tulis ke folder publik app lalu pindai. */
        File base = Environment.getExternalStoragePublicDirectory(dir);
        File dst = new File(new File(base, "Arkael"), name);
        File p = dst.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
        copy(f, new FileOutputStream(dst));
        MediaScannerConnection.scanFile(ctx, new String[]{dst.getAbsolutePath()}, new String[]{mime}, null);
        return Uri.fromFile(dst);
    }

    private static void copy(File f, OutputStream os) throws Exception {
        InputStream in = new java.io.FileInputStream(f);
        try {
            byte[] b = new byte[128 * 1024];
            int n;
            while ((n = in.read(b)) > 0) os.write(b, 0, n);
        } finally {
            try { in.close(); } catch (Exception ignore) {}
            try { os.close(); } catch (Exception ignore) {}
        }
    }

    /* ------------------------- cari gambar di web ------------------------- */

    /** Hasil satu pencarian gambar. */
    static final class Hit {
        final String title, url, page;
        final int width, height;
        Hit(String title, String url, String page, int w, int h) {
            this.title = title; this.url = url; this.page = page;
            this.width = w; this.height = h;
        }
    }

    /**
     * Cari gambar asli (bukan dibuat) untuk sebuah kata kunci. Wikimedia Commons dulu,
     * lalu Openverse. Tidak butuh API key.
     */
    static List<Hit> search(Context ctx, String q) throws Exception {
        if (q == null || q.trim().isEmpty()) throw new Exception("kata kunci kosong");
        List<Hit> out = new ArrayList<>();
        String err = null;
        try { out = wikimedia(ctx, q.trim()); } catch (Exception e) { err = e.getMessage(); }
        if (out.isEmpty()) {
            try { out = openverse(ctx, q.trim()); } catch (Exception e) {
                if (err == null) err = e.getMessage();
            }
        }
        if (out.isEmpty()) {
            throw new Exception("tidak ada gambar ditemukan untuk \"" + q + "\""
                    + (err == null ? "" : "\n(" + err + ")")
                    + "\n\nCoba kata kunci bahasa Inggris, mis. \"red panda\", \"jakarta skyline\".");
        }
        return out;
    }

    private static List<Hit> wikimedia(Context ctx, String q) throws Exception {
        String u = "https://commons.wikimedia.org/w/api.php?action=query&generator=search"
                + "&gsrsearch=" + enc("filetype:bitmap " + q)
                + "&gsrnamespace=6&gsrlimit=10&prop=imageinfo"
                + "&iiprop=url|size|mime&iiurlwidth=1600&format=json&origin=*";
        JSONObject js = new JSONObject(get(u, "application/json"));
        JSONObject pages = js.optJSONObject("query") == null ? null : js.optJSONObject("query").optJSONObject("pages");
        List<Hit> out = new ArrayList<>();
        if (pages == null) return out;
        java.util.Iterator<String> it = pages.keys();
        while (it.hasNext()) {
            JSONObject pg = pages.optJSONObject(it.next());
            if (pg == null) continue;
            JSONArray ii = pg.optJSONArray("imageinfo");
            if (ii == null || ii.length() == 0) continue;
            JSONObject info = ii.optJSONObject(0);
            if (info == null) continue;
            String mime = info.optString("mime", "");
            if (!mime.startsWith("image/") || mime.contains("svg")) continue;
            int w = info.optInt("width", 0), h = info.optInt("height", 0);
            if (w < 400 || h < 300) continue;
            String url = info.optString("thumburl", "");
            if (url.isEmpty()) url = info.optString("url", "");
            if (url.isEmpty()) continue;
            String title = pg.optString("title", "gambar").replaceFirst("^File:", "");
            out.add(new Hit(title, url, info.optString("descriptionurl", ""), w, h));
            if (out.size() >= 6) break;
        }
        return out;
    }

    private static List<Hit> openverse(Context ctx, String q) throws Exception {
        String u = "https://api.openverse.org/v1/images/?q=" + enc(q) + "&page_size=10&mature=false";
        JSONObject js = new JSONObject(get(u, "application/json"));
        JSONArray r = js.optJSONArray("results");
        List<Hit> out = new ArrayList<>();
        if (r == null) return out;
        for (int i = 0; i < r.length() && out.size() < 6; i++) {
            JSONObject o = r.optJSONObject(i);
            if (o == null) continue;
            String url = o.optString("url", "");
            if (url.isEmpty()) continue;
            int w = o.optInt("width", 0), h = o.optInt("height", 0);
            if (w > 0 && (w < 400 || h < 300)) continue;
            out.add(new Hit(o.optString("title", "gambar"), url, o.optString("foreign_landing_url", ""), w, h));
        }
        return out;
    }

    /** Unduh satu Hit ke folder Pictures app; kembalikan File. */
    static File download(Context ctx, Hit hit) throws Exception {
        String ext = "jpg";
        String low = hit.url.toLowerCase();
        int dot = low.lastIndexOf('.');
        if (dot > 0) {
            String e = low.substring(dot + 1).replaceAll("[^a-z0-9]", "");
            if (e.matches("(jpg|jpeg|png|webp|gif)")) ext = e;
        }
        File dir = new File(ctx.getExternalFilesDir(null), "Pictures");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("tidak bisa membuat folder gambar");
        File out = new File(dir, "web_" + System.currentTimeMillis() + "." + ext);
        HttpURLConnection c = (HttpURLConnection) new URL(hit.url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "image/*,*/*");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new Exception("unduhan gagal (HTTP " + code + ")");
        }
        InputStream in = c.getInputStream();
        FileOutputStream os = new FileOutputStream(out);
        try {
            byte[] b = new byte[64 * 1024];
            int n;
            long total = 0;
            while ((n = in.read(b)) > 0) {
                total += n;
                if (total > (16L << 20)) throw new Exception("gambar terlalu besar (>16 MB)");
                os.write(b, 0, n);
            }
        } finally {
            try { in.close(); } catch (Exception ignore) {}
            try { os.close(); } catch (Exception ignore) {}
            c.disconnect();
        }
        if (out.length() < 1024) {
            out.delete();
            throw new Exception("berkas hasil unduhan kosong/rusak");
        }
        return out;
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    private static String get(String u, String accept) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", accept);
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) throw new Exception("HTTP " + code);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[32 * 1024];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            in.close();
            String body = new String(bo.toByteArray(), "UTF-8");
            if (code >= 400) throw new Exception("HTTP " + code + ": " + body.substring(0, Math.min(160, body.length())));
            return body;
        } finally {
            c.disconnect();
        }
    }

    /** Jenis media dari nama file, untuk pemilihan koleksi MediaStore. */
    static String kindOf(File f) {
        String n = f.getName().toLowerCase();
        if (n.matches(".*\\.(jpg|jpeg|png|webp|gif|bmp|heic)$")) return "image";
        if (n.matches(".*\\.(mp4|mkv|webm|mov|3gp|avi)$")) return "video";
        if (n.matches(".*\\.(mp3|m4a|aac|wav|ogg|flac|opus)$")) return "audio";
        return "doc";
    }
}
