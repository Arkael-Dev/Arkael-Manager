package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Setelan equalizer. Dipakai UI, service, dan daemon root (dikirim sebagai satu baris JSON). */
final class EqConfig {
    static final int N = 10;
    static final int[] FREQ = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    static final String[] FLABEL = {"31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"};

    static final int M_AUTO = 0, M_GLOBAL = 1, M_SESSION = 2;
    static final String CUSTOM = "Custom";
    static final float MAX_DB = 12f;

    static final class Preset {
        final String name;
        final float[] g;
        final int bass, spatial, loud;   // bass/spatial 0..1000, loud = mB (0..1000)

        Preset(String name, float[] g, int bass, int spatial, int loud) {
            this.name = name; this.g = g; this.bass = bass; this.spatial = spatial; this.loud = loud;
        }
    }

    static final Preset[] PRESETS = {
            new Preset("Flat",        new float[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 0, 0, 0),
            new Preset("Bass Kuat",   new float[]{6, 5.5f, 4, 2, 0.5f, 0, 0, 0, 0, 0}, 450, 0, 0),
            new Preset("Treble",      new float[]{-1, -1, 0, 0, 0, 1, 2.5f, 4, 5, 5.5f}, 0, 0, 0),
            new Preset("Vokal",       new float[]{-3, -2.5f, -1.5f, 0, 2, 3.5f, 3.5f, 2, 0, -1}, 0, 0, 0),
            new Preset("Film 3D",     new float[]{4, 3.5f, 1.5f, 0, -1, 0, 1.5f, 3, 3.5f, 2.5f}, 300, 750, 200),
            new Preset("Game FPS",    new float[]{-4, -3.5f, -2, -1, 0, 1.5f, 3.5f, 5, 4.5f, 2.5f}, 0, 550, 0),
            new Preset("Pop",         new float[]{-1, 1, 3, 3.5f, 2, 0, -1, -1, 0.5f, 1}, 150, 0, 0),
            new Preset("Rock",        new float[]{4, 3, 1.5f, -1.5f, -2.5f, -1, 1.5f, 3, 4, 4}, 100, 0, 0),
            new Preset("EDM",         new float[]{5.5f, 4.5f, 1.5f, 0, -2, 1, 0, 1.5f, 4, 4.5f}, 350, 300, 0),
            new Preset("Akustik",     new float[]{3, 3, 2.5f, 1, 1, 1.5f, 2.5f, 3, 2.5f, 1.5f}, 0, 0, 0),
            new Preset("Malam",       new float[]{-2, -1, 0, 1, 1.5f, 1.5f, 1, 0, -2, -4}, 0, 0, 300),
    };

    boolean enabled = false;
    String preset = "Flat";
    float[] gain = new float[N];
    int bass = 0;        // 0..1000
    int spatial = 0;     // 0..1000
    int loud = 0;        // mB, 0..1000
    boolean safe = true; // anti-pecah: preamp otomatis + limiter
    int method = M_AUTO;

    float maxBoost() {
        float m = 0;
        for (float v : gain) if (v > m) m = v;
        return m;
    }

    static Preset find(String name) {
        for (Preset p : PRESETS) if (p.name.equals(name)) return p;
        return null;
    }

    void applyPreset(Preset p) {
        preset = p.name;
        gain = p.g.clone();
        bass = p.bass; spatial = p.spatial; loud = p.loud;
    }

    String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("on", enabled);
            o.put("preset", preset);
            JSONArray a = new JSONArray();
            for (float v : gain) a.put((double) v);
            o.put("g", a);
            o.put("bass", bass);
            o.put("sp", spatial);
            o.put("loud", loud);
            o.put("safe", safe);
            o.put("method", method);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    static EqConfig fromJson(String s) {
        EqConfig c = new EqConfig();
        try {
            JSONObject o = new JSONObject(s);
            c.enabled = o.optBoolean("on", false);
            c.preset = o.optString("preset", "Flat");
            JSONArray a = o.optJSONArray("g");
            if (a != null) for (int i = 0; i < N && i < a.length(); i++) c.gain[i] = clamp((float) a.optDouble(i, 0), -MAX_DB, MAX_DB);
            c.bass = clampI(o.optInt("bass", 0), 0, 1000);
            c.spatial = clampI(o.optInt("sp", 0), 0, 1000);
            c.loud = clampI(o.optInt("loud", 0), 0, 1000);
            c.safe = o.optBoolean("safe", true);
            c.method = clampI(o.optInt("method", 0), 0, 2);
        } catch (Exception ignored) { }
        return c;
    }

    private static final String PREFS = "ark_eq";

    static EqConfig load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = sp.getString("cfg", null);
        return s == null ? new EqConfig() : fromJson(s);
    }

    void save(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("cfg", toJson()).apply();
    }

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : v > hi ? hi : v; }
    static int clampI(int v, int lo, int hi) { return v < lo ? lo : v > hi ? hi : v; }
}
