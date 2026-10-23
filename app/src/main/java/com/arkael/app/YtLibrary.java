package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Perpustakaan YouTube lokal: langganan channel + playlist. Tanpa akun Google.
 * Semua disimpan di SharedPreferences app ini (hilang hanya kalau data app dihapus).
 * Feed langganan diambil dari RSS publik YouTube (youtube.com/feeds/videos.xml), bukan dari akun.
 */
final class YtLibrary {
    private YtLibrary() {}

    static final class Sub {
        String id, name, avatar;
    }

    static final class Pl {
        String id, name;
        long updated;
        List<YtStore.Entry> videos = new ArrayList<YtStore.Entry>();
    }

    static final class FeedItem {
        String url, title, uploader, thumb;
        long published;
    }

    private static final String PREF = "ark_yt_lib";
    private static final String K_SUBS = "subs";
    private static final String K_PL = "playlists";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- channel id

    /** "https://www.youtube.com/channel/UCxxxx" -> "UCxxxx" (null kalau bukan format itu). */
    static String channelIdFromUrl(String url) {
        if (url == null) return null;
        int i = url.indexOf("/channel/");
        if (i < 0) return null;
        String id = url.substring(i + 9);
        int e = id.length();
        for (char ch : new char[]{'/', '?', '#', '&'}) {
            int k = id.indexOf(ch);
            if (k >= 0 && k < e) e = k;
        }
        id = id.substring(0, e);
        return id.length() >= 10 ? id : null;
    }

    // ---------------------------------------------------------------- langganan

    static synchronized List<Sub> subs(Context c) {
        List<Sub> out = new ArrayList<Sub>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(K_SUBS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Sub s = new Sub();
                s.id = o.optString("id");
                s.name = o.optString("name");
                s.avatar = o.isNull("avatar") ? null : o.optString("avatar", null);
                if (s.id != null && !s.id.isEmpty()) out.add(s);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static synchronized boolean isSubscribed(Context c, String id) {
        if (id == null) return false;
        for (Sub s : subs(c)) if (id.equals(s.id)) return true;
        return false;
    }

    /** Berlangganan kalau belum, berhenti kalau sudah. Mengembalikan status baru. */
    static synchronized boolean toggleSub(Context c, Sub sub) {
        List<Sub> l = subs(c);
        boolean had = false;
        for (int i = l.size() - 1; i >= 0; i--) {
            if (sub.id.equals(l.get(i).id)) { l.remove(i); had = true; }
        }
        if (!had) l.add(0, sub);
        saveSubs(c, l);
        return !had;
    }

    static synchronized void removeSub(Context c, String id) {
        List<Sub> l = subs(c);
        for (int i = l.size() - 1; i >= 0; i--) if (id.equals(l.get(i).id)) l.remove(i);
        saveSubs(c, l);
    }

    private static void saveSubs(Context c, List<Sub> l) {
        try {
            JSONArray a = new JSONArray();
            for (Sub s : l) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("name", s.name == null ? "" : s.name);
                if (s.avatar != null) o.put("avatar", s.avatar);
                a.put(o);
            }
            sp(c).edit().putString(K_SUBS, a.toString()).apply();
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- playlist

    static synchronized List<Pl> playlists(Context c) {
        List<Pl> out = new ArrayList<Pl>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(K_PL, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Pl p = new Pl();
                p.id = o.optString("id");
                p.name = o.optString("name");
                p.updated = o.optLong("updated");
                JSONArray vs = o.optJSONArray("videos");
                if (vs != null) {
                    for (int j = 0; j < vs.length(); j++) {
                        JSONObject v = vs.getJSONObject(j);
                        YtStore.Entry e = new YtStore.Entry();
                        e.url = v.optString("url", null);
                        if (e.url == null) continue;
                        e.title = v.optString("title", "");
                        e.uploader = v.optString("uploader", "");
                        e.thumb = v.isNull("thumb") ? null : v.optString("thumb", null);
                        e.duration = v.optLong("duration", 0);
                        e.time = v.optLong("time", 0);
                        p.videos.add(e);
                    }
                }
                if (p.id != null && !p.id.isEmpty()) out.add(p);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static synchronized Pl playlist(Context c, String id) {
        for (Pl p : playlists(c)) if (p.id.equals(id)) return p;
        return null;
    }

    static synchronized Pl createPlaylist(Context c, String name) {
        List<Pl> l = playlists(c);
        Pl p = new Pl();
        p.id = "pl" + System.currentTimeMillis();
        p.name = name;
        p.updated = System.currentTimeMillis();
        l.add(0, p);
        savePlaylists(c, l);
        return p;
    }

    /** false kalau video itu sudah ada di playlist. */
    static synchronized boolean addToPlaylist(Context c, String plId, YtStore.Entry e) {
        if (e == null || e.url == null) return false;
        List<Pl> l = playlists(c);
        for (Pl p : l) {
            if (!p.id.equals(plId)) continue;
            for (YtStore.Entry x : p.videos) if (e.url.equals(x.url)) return false;
            YtStore.Entry n = new YtStore.Entry();
            n.url = e.url; n.title = e.title; n.uploader = e.uploader; n.thumb = e.thumb; n.duration = e.duration;
            n.time = System.currentTimeMillis();
            p.videos.add(n);
            p.updated = n.time;
            savePlaylists(c, l);
            return true;
        }
        return false;
    }

    static synchronized void removeFromPlaylist(Context c, String plId, String url) {
        List<Pl> l = playlists(c);
        for (Pl p : l) {
            if (!p.id.equals(plId)) continue;
            for (int i = p.videos.size() - 1; i >= 0; i--) if (url.equals(p.videos.get(i).url)) p.videos.remove(i);
        }
        savePlaylists(c, l);
    }

    static synchronized void deletePlaylist(Context c, String plId) {
        List<Pl> l = playlists(c);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(plId)) l.remove(i);
        savePlaylists(c, l);
    }

    private static void savePlaylists(Context c, List<Pl> l) {
        try {
            JSONArray a = new JSONArray();
            for (Pl p : l) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name == null ? "" : p.name);
                o.put("updated", p.updated);
                JSONArray vs = new JSONArray();
                for (YtStore.Entry e : p.videos) {
                    JSONObject v = new JSONObject();
                    v.put("url", e.url);
                    v.put("title", e.title == null ? "" : e.title);
                    v.put("uploader", e.uploader == null ? "" : e.uploader);
                    if (e.thumb != null) v.put("thumb", e.thumb);
                    v.put("duration", e.duration);
                    v.put("time", e.time);
                    vs.put(v);
                }
                o.put("videos", vs);
                a.put(o);
            }
            sp(c).edit().putString(K_PL, a.toString()).apply();
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- feed (RSS)

    /** 15 video terbaru satu channel dari RSS publik YouTube. Kosong kalau gagal. */
    static List<FeedItem> fetchChannel(String channelId, String channelName) {
        List<FeedItem> out = new ArrayList<FeedItem>();
        try {
            String xml = httpGet("https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId, 8000);
            if (xml == null) return out;
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(xml));
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
            FeedItem cur = null;
            String vid = null;
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String tag = p.getName();
                    if ("entry".equals(tag)) {
                        cur = new FeedItem();
                        cur.uploader = channelName == null ? "" : channelName;
                        vid = null;
                    } else if (cur != null) {
                        if ("yt:videoId".equals(tag)) {
                            vid = p.nextText();
                            cur.url = "https://www.youtube.com/watch?v=" + vid;
                            cur.thumb = "https://i.ytimg.com/vi/" + vid + "/mqdefault.jpg";
                        } else if ("title".equals(tag)) {
                            cur.title = p.nextText();
                        } else if ("published".equals(tag)) {
                            String s = p.nextText().trim().replaceAll("([+-]\\d\\d):(\\d\\d)$", "$1$2").replace("Z", "+0000");
                            try { cur.published = df.parse(s).getTime(); } catch (Exception ignored) { }
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG && "entry".equals(p.getName())) {
                    if (cur != null && cur.url != null && cur.title != null) out.add(cur);
                    cur = null;
                }
                ev = p.next();
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Video terbaru dari semua channel yang dilanggani, digabung dan diurutkan dari yang paling baru. */
    static List<FeedItem> fetchFeed(Context c) {
        final List<Sub> subs = subs(c);
        final List<FeedItem> all = Collections.synchronizedList(new ArrayList<FeedItem>());
        ExecutorService ex = Executors.newFixedThreadPool(4);
        List<Future<?>> fs = new ArrayList<Future<?>>();
        for (final Sub s : subs) {
            fs.add(ex.submit(new Runnable() {
                @Override public void run() { all.addAll(fetchChannel(s.id, s.name)); }
            }));
        }
        for (Future<?> f : fs) {
            try { f.get(25, TimeUnit.SECONDS); } catch (Exception ignored) { }
        }
        ex.shutdownNow();
        List<FeedItem> out = new ArrayList<FeedItem>(all);
        Collections.sort(out, new Comparator<FeedItem>() {
            @Override public int compare(FeedItem a, FeedItem b) { return Long.compare(b.published, a.published); }
        });
        return out.size() > 80 ? new ArrayList<FeedItem>(out.subList(0, 80)) : out;
    }

    // ---------------------------------------------------------------- http

    private static String httpGet(String url, int timeoutMs) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", "ArkaelManager/1.0");
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < 3_000_000) bo.write(buf, 0, n);
            in.close();
            return bo.toString("UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
