package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Local-only YouTube history + likes, kept in this app's SharedPreferences.
 * Nothing is sent anywhere and no account is involved; clearing Arkael Manager's app data wipes it.
 */
final class YtStore {
    static final class Entry {
        String url, title, uploader, thumb;
        long duration;   // seconds, 0 = unknown
        long time;       // when it was watched / liked (ms)
    }

    private static final String PREF = "ark_yt";
    private static final String K_HISTORY = "history";
    private static final String K_LIKES = "likes";
    private static final int MAX_HISTORY = 300;

    private YtStore() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static synchronized List<Entry> history(Context c) { return read(c, K_HISTORY); }

    static synchronized List<Entry> likes(Context c) { return read(c, K_LIKES); }

    /** Puts the video at the top of the history (moving it up if it was already there). */
    static synchronized void addHistory(Context c, Entry e) {
        if (e == null || e.url == null) return;
        List<Entry> l = read(c, K_HISTORY);
        removeUrl(l, e.url);
        e.time = System.currentTimeMillis();
        l.add(0, e);
        while (l.size() > MAX_HISTORY) l.remove(l.size() - 1);
        write(c, K_HISTORY, l);
    }

    static synchronized void clearHistory(Context c) {
        sp(c).edit().remove(K_HISTORY).apply();
    }

    static synchronized boolean isLiked(Context c, String url) {
        if (url == null) return false;
        for (Entry e : read(c, K_LIKES)) if (url.equals(e.url)) return true;
        return false;
    }

    /** Likes it if it wasn't liked, un-likes it otherwise. Returns the new state. */
    static synchronized boolean toggleLike(Context c, Entry e) {
        List<Entry> l = read(c, K_LIKES);
        boolean had = removeUrl(l, e.url);
        if (!had) {
            e.time = System.currentTimeMillis();
            l.add(0, e);
        }
        write(c, K_LIKES, l);
        return !had;
    }

    private static boolean removeUrl(List<Entry> l, String url) {
        boolean removed = false;
        for (int i = l.size() - 1; i >= 0; i--) {
            if (url != null && url.equals(l.get(i).url)) { l.remove(i); removed = true; }
        }
        return removed;
    }

    private static List<Entry> read(Context c, String key) {
        List<Entry> out = new ArrayList<Entry>();
        try {
            String raw = sp(c).getString(key, null);
            if (raw == null) return out;
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.url = o.optString("url", null);
                if (e.url == null) continue;
                e.title = o.optString("title", "");
                e.uploader = o.optString("uploader", "");
                e.thumb = o.isNull("thumb") ? null : o.optString("thumb", null);
                e.duration = o.optLong("duration", 0);
                e.time = o.optLong("time", 0);
                out.add(e);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static void write(Context c, String key, List<Entry> l) {
        try {
            JSONArray a = new JSONArray();
            for (Entry e : l) {
                JSONObject o = new JSONObject();
                o.put("url", e.url);
                o.put("title", e.title == null ? "" : e.title);
                o.put("uploader", e.uploader == null ? "" : e.uploader);
                if (e.thumb != null) o.put("thumb", e.thumb);
                o.put("duration", e.duration);
                o.put("time", e.time);
                a.put(o);
            }
            sp(c).edit().putString(key, a.toString()).apply();
        } catch (Exception ignored) {}
    }
}
