package com.arkael.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Statistik sesi game: durasi, rata-rata FPS (kalau overlay hidup), suhu baterai tertinggi, baterai terpakai. */
final class SessionStats {
    private static final String PREFS = "ark_stats";
    private static final int MAX = 100;          // sesi yang disimpan
    private static String pkg;
    private static long t0, fpsSum;
    private static int bat0, fpsN;
    private static float maxT;

    private static int level(Context c) {
        try {
            Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return -1;
            int l = i.getIntExtra("level", -1), s = i.getIntExtra("scale", 100);
            return l < 0 ? -1 : Math.round(l * 100f / s);
        } catch (Throwable t) { return -1; }
    }

    static synchronized void begin(Context c, String p) {
        pkg = p; t0 = System.currentTimeMillis(); bat0 = level(c); maxT = 0; fpsSum = 0; fpsN = 0;
    }

    static synchronized void sample(Context c) {
        if (pkg == null) return;
        try { float t = BatteryInfo.temp(c); if (t > maxT) maxT = t; } catch (Throwable ignored) {}
    }

    static synchronized void fps(int f) {
        if (pkg != null && f > 0 && f <= 240) { fpsSum += f; fpsN++; }
    }

    /** Akhiri sesi. Mengembalikan ringkasan, atau null kalau sesi kurang dari 1 menit. */
    static synchronized String end(Context c) {
        if (pkg == null) return null;
        String p = pkg; pkg = null;
        long sec = (System.currentTimeMillis() - t0) / 1000;
        if (sec < 60) return null;
        int bat1 = level(c);
        StringBuilder sb = new StringBuilder();
        sb.append(sec >= 3600 ? (sec / 3600) + " h " + ((sec % 3600) / 60) + " min" : (sec / 60) + " min");
        if (fpsN > 0) sb.append(" · avg ").append(Math.round(fpsSum / (float) fpsN)).append(" FPS");
        if (maxT > 0) sb.append(" · battery max ").append(String.format(Locale.US, "%.1f", maxT)).append("°C");
        if (bat0 >= 0 && bat1 >= 0) sb.append(" · ").append(bat1 - bat0).append("% battery");
        String sum = sb.toString();
        int avg = fpsN > 0 ? Math.round(fpsSum / (float) fpsN) : 0;
        try {
            JSONArray old = log(c), neu = new JSONArray();
            JSONObject e = new JSONObject();
            e.put("pkg", p); e.put("t", System.currentTimeMillis()); e.put("sum", sum);
            e.put("sec", sec);
            e.put("fps", avg);
            e.put("temp", maxT > 0 ? (double) maxT : 0d);
            e.put("bat", bat0 >= 0 && bat1 >= 0 ? bat1 - bat0 : 0);
            neu.put(e);
            for (int i = 0; i < old.length() && i < MAX - 1; i++) neu.put(old.get(i));
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("log", neu.toString()).apply();
        } catch (Throwable ignored) {}
        return sum;
    }

    // ---------------------------------------------------------------- riwayat & agregat

    /** Satu sesi game yang tersimpan. */
    static final class Entry {
        String pkg = "", sum = "";
        long t, sec;
        int fps, bat;
        float temp;
    }

    /** Riwayat terbaru dulu. Entri lama tanpa angka tetap terbaca (sec/fps/temp = 0). */
    static List<Entry> list(Context c) {
        List<Entry> out = new ArrayList<Entry>();
        JSONArray a = log(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Entry e = new Entry();
            e.pkg = o.optString("pkg");
            e.sum = o.optString("sum");
            e.t = o.optLong("t");
            e.sec = o.optLong("sec", 0);
            e.fps = o.optInt("fps", 0);
            e.bat = o.optInt("bat", 0);
            e.temp = (float) o.optDouble("temp", 0);
            out.add(e);
        }
        return out;
    }

    /** Ringkasan semua sesi: total, 7 hari terakhir, dan game yang paling sering dimainkan. */
    static final class Totals {
        int sessions;
        long sec, weekSec, weekSessions;
        int bestFps;
        String topPkg = "";
        long topSec;
        float hottest;
    }

    static Totals totals(Context c) {
        Totals t = new Totals();
        long weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        java.util.HashMap<String, Long> per = new java.util.HashMap<String, Long>();
        for (Entry e : list(c)) {
            t.sessions++;
            t.sec += e.sec;
            if (e.t >= weekAgo) { t.weekSec += e.sec; t.weekSessions++; }
            if (e.fps > t.bestFps) t.bestFps = e.fps;
            if (e.temp > t.hottest) t.hottest = e.temp;
            if (e.pkg != null && !e.pkg.isEmpty()) {
                Long v = per.get(e.pkg);
                long s = (v == null ? 0 : v.longValue()) + e.sec;
                per.put(e.pkg, Long.valueOf(s));
                if (s > t.topSec) { t.topSec = s; t.topPkg = e.pkg; }
            }
        }
        return t;
    }

    static void clear(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("log").apply();
    }

    /** "2 h 15 min" / "45 min" / "40 s" */
    static String dur(long sec) {
        if (sec >= 3600) return (sec / 3600) + " h " + ((sec % 3600) / 60) + " min";
        if (sec >= 60) return (sec / 60) + " min";
        return sec + " s";
    }

    static JSONArray log(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return new JSONArray(sp.getString("log", "[]"));
        } catch (Throwable t) { return new JSONArray(); }
    }
}
