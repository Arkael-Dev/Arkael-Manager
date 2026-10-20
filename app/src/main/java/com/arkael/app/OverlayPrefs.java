package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;

final class OverlayPrefs {
    static final int M_CPU = 1, M_TEMP = 2, M_GPU = 4, M_RAM = 8, M_BAT = 16, M_FPS = 32, M_GRAPH = 64, M_ALL = 127;
    static final int[] COLORS = {0xFF00E5FF, 0xFFFF6B35, 0xFFB388FF, 0xFF00E676, 0xFFFF4081};
    static final float SCALE_MIN = 0.5f, SCALE_MAX = 3f;

    private OverlayPrefs() {}

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("arkael_overlay", Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return sp(c).getBoolean("enabled", false); }
    static void setEnabled(Context c, boolean v) { sp(c).edit().putBoolean("enabled", v).apply(); }
    static int color(Context c) { return sp(c).getInt("color", COLORS[0]); }
    static int alpha(Context c) { return sp(c).getInt("alpha", 80); }          // 30..100 (%)
    static int mask(Context c) { return sp(c).getInt("mask", M_ALL); }
    static boolean compact(Context c) { return sp(c).getBoolean("compact", false); }
    static int x(Context c) { return sp(c).getInt("x", 24); }
    static int y(Context c) { return sp(c).getInt("y", 120); }

    /** Free size: pinch the overlay or drag its corner grip. Falls back to the old Small/Medium/Large choice. */
    static float scale(Context c) {
        SharedPreferences s = sp(c);
        if (s.contains("scale")) return Math.max(SCALE_MIN, Math.min(SCALE_MAX, s.getFloat("scale", 1f)));
        float[] old = {0.85f, 1f, 1.25f};
        return old[Math.max(0, Math.min(2, s.getInt("size", 1)))];
    }
    static void setScale(Context c, float v) {
        sp(c).edit().putFloat("scale", Math.max(SCALE_MIN, Math.min(SCALE_MAX, v))).apply();
    }
    static void resetLayout(Context c) { sp(c).edit().remove("scale").remove("size").remove("x").remove("y").apply(); }
}
