package com.arkael.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** Statistik sesi game: durasi, rata-rata FPS (kalau overlay hidup), suhu baterai tertinggi, baterai terpakai. */
final class SessionStats {
    private static final String PREFS = "ark_stats";
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
        try {
            JSONArray old = log(c), neu = new JSONArray();
            JSONObject e = new JSONObject();
            e.put("pkg", p); e.put("t", System.currentTimeMillis()); e.put("sum", sum);
            neu.put(e);
            for (int i = 0; i < old.length() && i < 19; i++) neu.put(old.get(i));
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("log", neu.toString()).apply();
        } catch (Throwable ignored) {}
        return sum;
    }

    static JSONArray log(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return new JSONArray(sp.getString("log", "[]"));
        } catch (Throwable t) { return new JSONArray(); }
    }
}
