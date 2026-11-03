package com.arkael.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Lampiran dan akses file untuk chat AI.
 * Semua di perangkat: content:// dari pemilih file disalin ke cache, lalu dibaca langsung.
 */
final class AiFiles {

    static final int MAX_ATTACH = 6;
    static final int MAX_IMAGE_BYTES = 4 << 20;     // batas aman base64 untuk satu gambar
    static final int MAX_B64_BYTES = 6 << 20;
    private static final int MAX_TEXT_CHARS = 60000;
    private static final long MAX_UNZIP_TOTAL = 50L << 20;

    private static final String[] TEXT_EXT = {
            "txt", "md", "markdown", "json", "xml", "yml", "yaml", "java", "kt", "kts",
            "gradle", "sh", "bash", "py", "js", "jsx", "ts", "tsx", "html", "htm", "css",
            "scss", "csv", "tsv", "log", "ini", "conf", "cfg", "properties", "sql",
            "c", "h", "cpp", "hpp", "rs", "go", "php", "rb", "toml", "patch", "diff", "srt"
    };

    private AiFiles() {}

    /** Folder kerja lampiran (di cache, ikut terhapus sistem kalau perlu ruang). */
    static File inbox(Context ctx) {
        File d = new File(ctx.getCacheDir(), "ai_in");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Salin file dari pemilih sistem ke inbox. Nama asli dipertahankan bila ada. */
    static File saveFromUri(Context ctx, Uri uri) throws Exception {
        String name = null;
        try {
            Cursor c = ctx.getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && c.moveToFirst()) name = c.getString(i);
                c.close();
            }
        } catch (Exception ignore) {}
        if (name == null || name.trim().isEmpty()) {
            name = uri.getLastPathSegment();
            if (name == null) name = "lampiran";
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
        }
        name = safeName(name);

        File dir = inbox(ctx);
        File out = new File(dir, name);
        int n = 1;
        while (out.exists()) {
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            out = new File(dir, base + "_" + (n++) + ext);
        }

        ContentResolver cr = ctx.getContentResolver();
        InputStream in = cr.openInputStream(uri);
        if (in == null) throw new Exception("tidak bisa membuka file: " + uri);
        OutputStream os = new FileOutputStream(out);
        try {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
            os.flush();
        } finally {
            try { os.close(); } catch (Exception ignore) {}
            try { in.close(); } catch (Exception ignore) {}
        }
        if (!out.isFile() || out.length() == 0) throw new Exception("file kosong atau gagal disalin: " + name);
        return out;
    }

    /** Buang pemisah path dan karakter aneh; hasil selalu nama file polos. */
    static String safeName(String name) {
        if (name == null) return "lampiran";
        String s = name.replace('\\', '/');
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        s = s.replaceAll("[\\p{Cntrl}]", "").trim();
        if (s.isEmpty() || ".".equals(s) || "..".equals(s)) s = "lampiran";
        if (s.length() > 80) {
            int dot = s.lastIndexOf('.');
            String ext = dot > 0 && s.length() - dot <= 8 ? s.substring(dot) : "";
            s = s.substring(0, 80 - ext.length()) + ext;
        }
        return s;
    }

    static String ext(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot + 1).toLowerCase();
    }

    static boolean isImage(File f) {
        switch (ext(f)) {
            case "png": case "jpg": case "jpeg": case "webp": case "bmp": case "gif":
                return true;
            default:
                return false;
        }
    }

    static boolean isText(File f) {
        String e = ext(f);
        for (String t : TEXT_EXT) if (t.equals(e)) return true;
        if (e.isEmpty()) return looksText(f);           // mis. Dockerfile, .gitignore, tanpa ekstensi
        return false;
    }

    /** "image" | "text" | "binary" */
    static String kind(File f) {
        if (isImage(f)) return "image";
        if (isText(f)) return "text";
        return "binary";
    }

    /** Cek isi 4 KB pertama: nol byte atau byte kontrol tinggi berarti biner. */
    static boolean looksText(File f) {
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] b = new byte[4096];
            int r = in.read(b);
            for (int i = 0; i < r; i++) {
                int c = b[i] & 0xff;
                if (c == 0) return false;
                if (c < 9 || (c > 13 && c < 32)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignore) {}
        }
    }

    static String mime(File f) {
        switch (ext(f)) {
            case "png":  return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "webp": return "image/webp";
            case "gif":  return "image/gif";
            case "bmp":  return "image/bmp";
            case "txt": case "log": return "text/plain";
            case "json": return "application/json";
            default:     return "application/octet-stream";
        }
    }

    static String humanSize(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0);
    }

    /** Isi teks, dipotong ke MAX_TEXT_CHARS. null kalau file tidak ada. */
    static String textOf(File f, int maxChars) {
        if (f == null || !f.isFile()) return null;
        if (maxChars <= 0) maxChars = MAX_TEXT_CHARS;
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[8192];
            int r;
            String tail = "";
            while ((r = in.read(buf)) > 0 && sb.length() < maxChars) {
                String s = tail + new String(buf, 0, r, "UTF-8");
                if (sb.length() + s.length() <= maxChars) sb.append(s);
                else sb.append(s, 0, maxChars - sb.length());
                // jaga potongan multibyte di batas buffer
                tail = "";
            }
            if (sb.length() >= maxChars) sb.append("\n\u2026(dipotong pada ").append(maxChars).append(" karakter)");
            return sb.toString();
        } catch (Exception e) {
            return "(gagal membaca: " + e.getMessage() + ")";
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignore) {}
        }
    }

    /** Base64 satu baris, untuk lampiran gambar (data URL). */
    static String base64Of(File f) throws Exception {
        long n = f.length();
        if (n > MAX_B64_BYTES) throw new Exception("file terlalu besar untuk dikirim: " + humanSize(n));
        ByteArrayOutputStream bo = new ByteArrayOutputStream((int) n);
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        } finally {
            try { in.close(); } catch (Exception ignore) {}
        }
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    /** Hexdump ringkas untuk file biner: offset, 16 byte hex, dan teks ASCII. */
    static String hexdump(File f, int maxBytes) {
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] b = new byte[Math.min(maxBytes, (int) Math.min(f.length(), maxBytes))];
            int r = in.read(b);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < r; i += 16) {
                sb.append(String.format(java.util.Locale.US, "%08x  ", i));
                StringBuilder asc = new StringBuilder();
                for (int j = 0; j < 16; j++) {
                    if (i + j < r) {
                        int c = b[i + j] & 0xff;
                        sb.append(String.format(java.util.Locale.US, "%02x ", c));
                        asc.append(c >= 32 && c < 127 ? (char) c : '.');
                    } else {
                        sb.append("   ");
                    }
                }
                sb.append(' ').append(asc).append('\n');
            }
            if (f.length() > r) sb.append("\u2026(sisa ").append(humanSize(f.length() - r)).append(" tidak ditampilkan)\n");
            return sb.toString();
        } catch (Exception e) {
            return "(gagal membaca: " + e.getMessage() + ")";
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignore) {}
        }
    }

    /** Ekstrak zip ke folder tujuan, menolak entri yang keluar dari folder (zip slip). */
    static String unzip(File zip, File dest) throws Exception {
        if (zip == null || !zip.isFile()) throw new Exception("file zip tidak ditemukan");
        if (zip.length() > MAX_UNZIP_TOTAL) throw new Exception("zip terlalu besar: " + humanSize(zip.length()));
        if (!dest.exists() && !dest.mkdirs()) throw new Exception("tidak bisa membuat folder " + dest);
        String destCanon = dest.getCanonicalPath() + File.separator;

        List<String> names = new ArrayList<>();
        long total = 0;
        int files = 0;
        ZipInputStream zin = new ZipInputStream(new FileInputStream(zip));
        try {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = zin.getNextEntry()) != null) {
                String en = safeName(e.getName());
                if (e.isDirectory()) continue;
                File out = new File(dest, en);
                if (!out.getCanonicalPath().startsWith(destCanon)) {
                    throw new Exception("entri zip keluar folder: " + e.getName());
                }
                OutputStream os = new FileOutputStream(out);
                try {
                    int r;
                    while ((r = zin.read(buf)) > 0) {
                        total += r;
                        if (total > MAX_UNZIP_TOTAL) throw new Exception("isi zip melebihi " + humanSize(MAX_UNZIP_TOTAL));
                        os.write(buf, 0, r);
                    }
                } finally {
                    try { os.close(); } catch (Exception ignore) {}
                }
                files++;
                if (names.size() < 200) names.add(en + "  " + humanSize(out.length()));
            }
        } finally {
            try { zin.close(); } catch (Exception ignore) {}
        }
        StringBuilder sb = new StringBuilder();
        sb.append("diekstrak ").append(files).append(" file \u00B7 ").append(humanSize(total))
          .append("\nke ").append(dest.getAbsolutePath()).append("\n");
        for (String n : names) sb.append("  ").append(n).append('\n');
        if (files > names.size()) sb.append("  \u2026dan ").append(files - names.size()).append(" file lain\n");
        return sb.toString();
    }

    /** Bungkus beberapa file jadi satu zip (dipakai untuk mengirim hasil kerja ke pengguna). */
    static File zip(File dest, List<File> files) throws Exception {
        ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(dest));
        try {
            byte[] buf = new byte[64 * 1024];
            for (File f : files) {
                if (f == null || !f.isFile()) continue;
                zo.putNextEntry(new ZipEntry(f.getName()));
                InputStream in = new FileInputStream(f);
                try {
                    int r;
                    while ((r = in.read(buf)) > 0) zo.write(buf, 0, r);
                } finally {
                    try { in.close(); } catch (Exception ignore) {}
                }
                zo.closeEntry();
            }
        } finally {
            try { zo.close(); } catch (Exception ignore) {}
        }
        return dest;
    }
}
