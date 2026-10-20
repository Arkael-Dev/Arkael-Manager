package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;

final class OverlayPrefs {
    static final int M_CPU = 1, M_TEMP = 2, M_GPU = 4, M_RAM = 8, M_BAT = 16, M_FPS = 32, M_GRAPH = 64, M_ALL = 127;
    static final int[] COLORS = {0xFF00E5FF, 0xFFFF6B35, 0xFFB388FF, 0xFF00E676, 0xFFFF4081};

    private OverlayPrefs() {}

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("arkael_overlay", Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return sp(c).getBoolean("enabled", false); }
    static void setEnabled(Context c, boolean v) { sp(c).edit().putBoolean("enabled", v).apply(); }
    static int color(Context c) { return sp(c).getInt("color", COLORS[0]); }
    static int alpha(Context c) { return sp(c).getInt("alpha", 80); }          // 30..100 (%)
    static int size(Context c) { return sp(c).getInt("size", 1); }             // 0 small, 1 medium, 2 large
    static int mask(Context c) { return sp(c).getInt("mask", M_ALL); }
    static boolean compact(Context c) { return sp(c).getBoolean("compact", false); }
    static int x(Context c) { return sp(c).getInt("x", 24); }
    static int y(Context c) { return sp(c).getInt("y", 120); }
}
