package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

/**
 * Jaringan khusus pemutar YouTube Arkael:
 *  - header HTTP ala browser desktop, supaya CDN googlevideo tidak menolak (403 / "unavailable")
 *  - User-Agent YouTube Android bisa dipilih di setelan (beberapa video cuma kenal UA ini)
 *  - cache disk Media3: bagian video yang sudah ditonton tidak diunduh dua kali
 *    (hemat kuota + seek ke belakang instan, persis seperti fitur "cache" ReVanced)
 * Semua preferensi disimpan di SharedPreferences "ark_yt_net".
 */
@UnstableApi
final class YtNet {
    private YtNet() {}

    static final String PREF = "ark_yt_net";
    static final String K_CACHE = "cache_mb";        // 0 = cache mati
    static final String K_UA = "ua_mode";            // "default" | "youtube" | "chrome" | "custom"
    static final String K_UA_CUSTOM = "ua_custom";

    static final String UA_CHROME =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/124.0.0.0 Safari/537.36";

    static final String UA_YOUTUBE =
            "com.google.android.youtube/19.09.37 (Linux; U; Android 13; en_US) gzip";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static int cacheMb(Context c) {
        try { return Integer.parseInt(sp(c).getString(K_CACHE, "256").trim()); }
        catch (Exception e) { return 256; }
    }

    static void setCacheMb(Context c, int mb) {
        sp(c).edit().putString(K_CACHE, String.valueOf(Math.max(0, mb))).apply();
    }

    static String userAgent(Context c) {
        String mode = sp(c).getString(K_UA, "default");
        if ("youtube".equals(mode)) return UA_YOUTUBE;
        if ("chrome".equals(mode)) return UA_CHROME;
        if ("custom".equals(mode)) {
            String v = sp(c).getString(K_UA_CUSTOM, "");
            if (!TextUtils.isEmpty(v)) return v;
        }
        return null;                       // biarkan Media3 memakai UA bawaannya
    }

    static void setMode(Context c, String mode, String custom) {
        sp(c).edit().putString(K_UA, mode).putString(K_UA_CUSTOM, custom == null ? "" : custom).apply();
    }

    // ------------------------------------------------------------------ data source

    private static SimpleCache cache;

    /** Cache disk tunggal untuk seluruh aplikasi (Media3 hanya boleh punya satu per folder). */
    static synchronized SimpleCache cache(Context c) {
        if (cache != null) return cache;
        int mb = cacheMb(c);
        if (mb <= 0) return null;
        File dir = new File(c.getApplicationContext().getCacheDir(), "ytcache");
        cache = new SimpleCache(dir,
                new LeastRecentlyUsedCacheEvictor((long) mb * 1024L * 1024L),
                new StandaloneDatabaseProvider(c.getApplicationContext()));
        return cache;
    }

    /** Cache dibersihkan dari setelan ("Kosongkan cache"). Panggil saat pemutar tidak aktif. */
    static synchronized void clearCache(Context c) {
        try {
            SimpleCache sc = cache(c);
            if (sc == null) return;
            for (String k : sc.getKeys()) sc.removeResource(k);
        } catch (Exception ignored) { }
    }

    static synchronized void releaseCache() {
        try { if (cache != null) cache.release(); } catch (Exception ignored) { }
        cache = null;
    }

    static Map<String, String> headers(Context c) {
        Map<String, String> h = new HashMap<>();
        String ua = userAgent(c);
        if (ua != null) h.put("User-Agent", ua);
        h.put("Accept-Language", "id-ID,id;q=0.9,en;q=0.8");
        h.put("Referer", "https://www.youtube.com/");
        h.put("Origin", "https://www.youtube.com");
        return h;
    }

    /**
     * Factory sumber media siap pakai: cache (kalau aktif) -> HTTP dengan header khusus.
     * ExoPlayer menanyakan factory ini setiap kali lagu/gambar dipasang, jadi perubahan
     * setelan UA langsung kepakai tanpa perlu restart service.
     */
    static DefaultMediaSourceFactory mediaSourceFactory(Context c) {
        Map<String, String> h = headers(c);
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(h)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000);
        DataSource.Factory base = new DefaultDataSource.Factory(c.getApplicationContext(), http);
        SimpleCache sc = cache(c);
        if (sc != null) {
            base = new CacheDataSource.Factory()
                    .setCache(sc)
                    .setUpstreamDataSourceFactory(base)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        }
        return new DefaultMediaSourceFactory(base);
    }

    /** Ringkasan untuk dialog setelan. */
    static String describe(Context c) {
        int mb = cacheMb(c);
        String ua = userAgent(c);
        return "Cache: " + (mb <= 0 ? "mati" : mb + " MB") + "\n"
             + "User-Agent: " + (ua == null ? "bawaan Media3" : ua);
    }

    @Nullable
    static String[] uaModes() { return new String[]{"default", "youtube", "chrome", "custom"}; }
}
