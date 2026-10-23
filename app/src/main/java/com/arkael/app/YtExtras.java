package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Tambahan pemutar YouTube Arkael (ditulis sendiri, tanpa kode LibreTube):
 *  - posisi terakhir tontonan (lanjut dari menit terakhir), disimpan lokal di app
 *  - SponsorBlock  : segmen sponsor untuk dilewati  (sponsor.ajay.app)
 *  - Return YouTube Dislike : jumlah dislike         (returnyoutubedislikeapi.com)
 *  - DeArrow       : judul komunitas yang tidak clickbait (sponsor.ajay.app)
 * Semua fungsi jaringan bersifat blocking: panggil dari thread background.
 */
final class YtExtras {
    private YtExtras() {}

    // ---------------------------------------------------------------- video id

    /** ID video 11 karakter dari URL YouTube (watch?v=, youtu.be/, shorts/, embed/, live/). */
    static String videoId(String url) {
        try {
            if (url == null) return null;
            Uri u = Uri.parse(url);
            String v = u.getQueryParameter("v");
            if (v != null && !v.isEmpty()) return v;
            List<String> seg = u.getPathSegments();
            if (seg == null || seg.isEmpty()) return null;
            String host = u.getHost() == null ? "" : u.getHost();
            if (host.contains("youtu.be")) return seg.get(0);
            if (seg.size() >= 2) {
                String p = seg.get(0);
                if (p.equals("shorts") || p.equals("embed") || p.equals("live") || p.equals("v")) return seg.get(1);
            }
        } catch (Exception ignored) { }
        return null;
    }

    // ---------------------------------------------------------------- posisi terakhir

    private static final String PREF = "ark_yt_pos";
    private static final String K_POS = "positions";
    private static final int MAX_POS = 300;
    private static final long MIN_SAVE_MS = 10_000;     // kurang dari ini: tidak perlu dilanjutkan
    private static final long END_MARGIN_MS = 15_000;   // hampir habis = dianggap selesai

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** Posisi (ms) untuk melanjutkan video ini, 0 kalau tidak ada. Mundur 2 detik biar ada konteks. */
    static synchronized long getPosition(Context c, String url) {
        try {
            String id = videoId(url);
            if (id == null) return 0;
            JSONObject o = new JSONObject(sp(c).getString(K_POS, "{}"));
            JSONArray a = o.optJSONArray(id);
            if (a == null) return 0;
            long pos = a.optLong(0);
            return pos < MIN_SAVE_MS ? 0 : Math.max(0, pos - 2000);
        } catch (Exception e) { return 0; }
    }

    /** Simpan posisi. Terlalu awal / hampir habis = hapus (tidak ada yang perlu dilanjutkan). */
    static synchronized void savePosition(Context c, String url, long posMs, long durMs) {
        try {
            String id = videoId(url);
            if (id == null) return;
            if (posMs < MIN_SAVE_MS || (durMs > 0 && posMs > durMs - END_MARGIN_MS)) { clearPosition(c, url); return; }
            JSONObject o = new JSONObject(sp(c).getString(K_POS, "{}"));
            JSONArray a = new JSONArray();
            a.put(posMs).put(Math.max(0, durMs)).put(System.currentTimeMillis());
            o.put(id, a);
            prune(o);
            sp(c).edit().putString(K_POS, o.toString()).apply();
        } catch (Exception ignored) { }
    }

    static synchronized void clearPosition(Context c, String url) {
        try {
            String id = videoId(url);
            if (id == null) return;
            JSONObject o = new JSONObject(sp(c).getString(K_POS, "{}"));
            if (o.has(id)) { o.remove(id); sp(c).edit().putString(K_POS, o.toString()).apply(); }
        } catch (Exception ignored) { }
    }

    /** Tanpa ukuran total: 0 = tidak ada. Dipakai kalau mau menampilkan progress bar di daftar riwayat. */
    static synchronized int percentWatched(Context c, String url) {
        try {
            String id = videoId(url);
            if (id == null) return 0;
            JSONArray a = new JSONObject(sp(c).getString(K_POS, "{}")).optJSONArray(id);
            if (a == null || a.optLong(1) <= 0) return 0;
            return (int) Math.min(100, a.optLong(0) * 100 / a.optLong(1));
        } catch (Exception e) { return 0; }
    }

    private static void prune(JSONObject o) {
        if (o.length() <= MAX_POS) return;
        final List<String> keys = new ArrayList<>();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) keys.add(it.next());
        Collections.sort(keys, new Comparator<String>() {
            @Override public int compare(String x, String y) {
                return Long.compare(ts(o, x), ts(o, y));      // paling lama dulu
            }
        });
        for (int i = 0; i < keys.size() - MAX_POS; i++) o.remove(keys.get(i));
    }

    private static long ts(JSONObject o, String k) {
        JSONArray a = o.optJSONArray(k);
        return a == null ? 0 : a.optLong(2);
    }

    // ---------------------------------------------------------------- SponsorBlock

    static final class Seg {
        long startMs, endMs;
        String category;
    }

    private static final String SB_CATEGORIES = "[\"sponsor\",\"selfpromo\",\"interaction\"]";

    /** Segmen yang dilewati. Pakai endpoint hash-prefix (4 hex) supaya ID video asli tidak dikirim ke server. */
    static List<Seg> sponsorSegments(String id) {
        List<Seg> out = new ArrayList<>();
        try {
            String hash = sha256Hex(id).substring(0, 4);
            String url = "https://sponsor.ajay.app/api/skipSegments/" + hash
                    + "?categories=" + URLEncoder.encode(SB_CATEGORIES, "UTF-8")
                    + "&actionTypes=" + URLEncoder.encode("[\"skip\"]", "UTF-8");
            String body = httpGet(url, 6000);
            if (body == null) return out;
            JSONArray vids = new JSONArray(body);
            for (int i = 0; i < vids.length(); i++) {
                JSONObject v = vids.getJSONObject(i);
                if (!id.equals(v.optString("videoID"))) continue;
                JSONArray segs = v.optJSONArray("segments");
                if (segs == null) continue;
                for (int j = 0; j < segs.length(); j++) {
                    JSONObject s = segs.getJSONObject(j);
                    JSONArray r = s.optJSONArray("segment");
                    if (r == null || r.length() < 2) continue;
                    if (!"skip".equals(s.optString("actionType", "skip"))) continue;
                    Seg g = new Seg();
                    g.startMs = (long) (r.getDouble(0) * 1000);
                    g.endMs = (long) (r.getDouble(1) * 1000);
                    g.category = s.optString("category");
                    if (g.endMs - g.startMs >= 1000) out.add(g);
                }
            }
            Collections.sort(out, new Comparator<Seg>() {
                @Override public int compare(Seg a, Seg b) { return Long.compare(a.startMs, b.startMs); }
            });
        } catch (Exception ignored) { }
        return out;
    }

    static String categoryLabel(String c) {
        if ("sponsor".equals(c)) return "Sponsor";
        if ("selfpromo".equals(c)) return "Promosi sendiri";
        if ("interaction".equals(c)) return "Pengingat like/subscribe";
        return c;
    }

    // ---------------------------------------------------------------- Return YouTube Dislike

    /** {likes, dislikes} atau null kalau gagal. */
    static long[] dislikes(String id) {
        try {
            String body = httpGet("https://returnyoutubedislikeapi.com/votes?videoId=" + URLEncoder.encode(id, "UTF-8"), 6000);
            if (body == null) return null;
            JSONObject o = new JSONObject(body);
            if (!o.has("dislikes")) return null;
            return new long[]{o.optLong("likes", -1), o.optLong("dislikes", -1)};
        } catch (Exception e) { return null; }
    }

    // ---------------------------------------------------------------- DeArrow

    /** Judul komunitas (bukan clickbait) atau null kalau tidak ada / sama dengan aslinya. */
    static String deArrowTitle(String id) {
        try {
            String body = httpGet("https://sponsor.ajay.app/api/branding?videoID=" + URLEncoder.encode(id, "UTF-8"), 6000);
            if (body == null) return null;
            JSONArray titles = new JSONObject(body).optJSONArray("titles");
            if (titles == null) return null;
            for (int i = 0; i < titles.length(); i++) {          // urutan dari server = terbaik dulu
                JSONObject t = titles.getJSONObject(i);
                if (t.optBoolean("original", false)) continue;
                if (t.optInt("votes", 0) < 0 && !t.optBoolean("locked", false)) continue;
                String s = t.optString("title", "").replace(">", "").trim();   // ">" = penanda format DeArrow
                if (!s.isEmpty()) return s;
            }
        } catch (Exception ignored) { }
        return null;
    }

    // ---------------------------------------------------------------- http

    private static String httpGet(String url, int timeoutMs) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", "ArkaelManager/1.0");
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) return null;       // 404 = tidak ada data, itu normal
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < 2_000_000) bo.write(buf, 0, n);
            in.close();
            return bo.toString("UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String sha256Hex(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
