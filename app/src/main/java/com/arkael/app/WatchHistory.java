package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Riwayat tontonan anime / donghua. Disimpan di SharedPreferences (storage internal app),
 * jadi tetap ada setelah app ditutup, di-update, atau HP restart. Hilang hanya kalau
 * data app dihapus / app di-uninstall.
 */
final class WatchHistory {
    private WatchHistory() {}

    private static final String PREFS = "ax_watch_history";
    private static final String KEY = "items";
    private static final int MAX = 30;

    private static final Pattern EP_IN_SLUG =
            Pattern.compile("(?i)(?:^|-)(?:episode|eps?)-?(\\d+)");
    private static final Pattern EP_STRIP =
            Pattern.compile("(?i)(?:^|-)(?:episode|eps?)-?\\d+.*$");

    static final class Item {
        String key, title, ep, url, site, home;
        long ts;
    }

    /** Dipanggil dari BrowserActivity tiap halaman selesai dimuat. Hanya halaman episode yang disimpan. */
    static synchronized void record(Context c, String url, String pageTitle, String site, String home) {
        try {
            if (url == null) return;
            Uri u = Uri.parse(url);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            String seg = lastSegment(u);
            if (host.isEmpty() || seg.isEmpty()) return;

            Matcher m = EP_IN_SLUG.matcher(seg);
            if (!m.find()) return;                       // bukan halaman episode
            String ep = m.group(1);

            String seriesSlug = EP_STRIP.matcher(seg).replaceFirst("");
            if (seriesSlug.isEmpty()) seriesSlug = seg;

            Item it = new Item();
            it.key = host + "/" + seriesSlug;
            it.title = cleanTitle(pageTitle, seriesSlug);
            it.ep = ep;
            it.url = url;
            it.site = site == null ? "" : site;
            it.home = home == null ? "" : home;
            it.ts = System.currentTimeMillis();

            List<Item> all = getAll(c);
            List<Item> out = new ArrayList<>();
            out.add(it);
            for (Item o : all) if (!o.key.equals(it.key) && out.size() < MAX) out.add(o);
            save(c, out);
        } catch (Exception ignored) { }
    }

    static synchronized List<Item> getAll(Context c) {
        List<Item> list = new ArrayList<>();
        try {
            String raw = prefs(c).getString(KEY, "[]");
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.key = o.optString("key");
                it.title = o.optString("title");
                it.ep = o.optString("ep");
                it.url = o.optString("url");
                it.site = o.optString("site");
                it.home = o.optString("home");
                it.ts = o.optLong("ts");
                if (!it.key.isEmpty() && !it.url.isEmpty()) list.add(it);
            }
        } catch (Exception ignored) { }
        return list;
    }

    static synchronized void remove(Context c, String key) {
        List<Item> out = new ArrayList<>();
        for (Item o : getAll(c)) if (!o.key.equals(key)) out.add(o);
        save(c, out);
    }

    static synchronized void clear(Context c) {
        prefs(c).edit().remove(KEY).apply();
    }

    // ---------------------------------------------------------------- helpers

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void save(Context c, List<Item> list) {
        try {
            JSONArray a = new JSONArray();
            for (Item it : list) {
                JSONObject o = new JSONObject();
                o.put("key", it.key);
                o.put("title", it.title);
                o.put("ep", it.ep);
                o.put("url", it.url);
                o.put("site", it.site);
                o.put("home", it.home);
                o.put("ts", it.ts);
                a.put(o);
            }
            prefs(c).edit().putString(KEY, a.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static String lastSegment(Uri u) {
        List<String> segs = u.getPathSegments();
        if (segs == null || segs.isEmpty()) return "";
        return segs.get(segs.size() - 1).toLowerCase(Locale.ROOT);
    }

    /** "Judul Episode 12 Subtitle Indonesia - Anichin" -> "Judul". Fallback: dari slug URL. */
    private static String cleanTitle(String pageTitle, String slug) {
        String t = pageTitle == null ? "" : pageTitle.trim();
        if (!t.isEmpty() && !t.toLowerCase(Locale.ROOT).startsWith("http")) {
            t = t.split("\\s+[\\-|–—•]\\s+")[0];                         // buang nama situs
            t = t.replaceAll("(?i)\\s*(?:episode|eps?)\\s*\\d+.*$", ""); // buang "Episode N ..."
            t = t.replaceAll("(?i)\\s*(?:subtitle|sub)\\s+(?:indonesia|indo)\\s*$", "");
            t = t.trim();
            if (t.length() >= 2) return t;
        }
        StringBuilder sb = new StringBuilder();
        for (String w : slug.split("-")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
