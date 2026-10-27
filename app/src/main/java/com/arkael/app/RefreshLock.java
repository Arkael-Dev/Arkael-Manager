package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Kunci refresh rate per game (butuh root). Dipasang saat game masuk, dikembalikan saat keluar (Auto game mode). */
final class RefreshLock {
    private static final String PREFS = "ark_refresh";
    static final int[] HZ = {0, 60, 90, 120, 144};
    private static final String[] KEYS = {"min_refresh_rate", "peak_refresh_rate", "user_refresh_rate"};

    static int get(Context c, String pkg) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("hz_" + pkg, 0);
    }

    static void set(Context c, String pkg, int hz) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (hz <= 0) e.remove("hz_" + pkg); else e.putInt("hz_" + pkg, hz);
        e.apply();
    }

    static String label(int hz) { return hz <= 0 ? "Default" : hz + " Hz"; }

    static boolean pending(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("saved", false);
    }

    /** Blokir, jalankan di thread latar. */
    static void apply(Context c, String pkg) {
        int hz = get(c, pkg);
        if (hz <= 0) return;
        try {
            if (!RootShell.hasRoot()) return;
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (!p.getBoolean("saved", false)) {
                SharedPreferences.Editor e = p.edit();
                for (String k : KEYS) {
                    String v = RootShell.run("settings get system " + k);
                    e.putString("o_" + k, v == null || v.trim().isEmpty() ? "null" : v.trim());
                }
                e.putBoolean("saved", true).apply();
            }
            for (String k : KEYS) {
                if (k.equals("user_refresh_rate") && "null".equals(p.getString("o_" + k, "null"))) continue;
                RootShell.run("settings put system " + k + " " + hz + " 2>&1");
            }
        } catch (Throwable ignored) {}
    }

    /** Kembalikan nilai asli sebelum dikunci. */
    static void restore(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("saved", false)) return;
        try {
            for (String k : KEYS) {
                String o = p.getString("o_" + k, "null");
                if ("null".equals(o)) RootShell.run("settings delete system " + k + " 2>&1");
                else RootShell.run("settings put system " + k + " " + o + " 2>&1");
            }
        } catch (Throwable ignored) {}
        SharedPreferences.Editor e = p.edit();
        for (String k : KEYS) e.remove("o_" + k);
        e.remove("saved").apply();
    }
}
