package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Preset buatan pengguna: simpan, hapus, impor, ekspor (JSON). Format file sama untuk satu preset maupun paket. */
final class EqPresetStore {
    static final class UserPreset {
        String name;
        float[] g = new float[EqConfig.N];
        int bass, spatial, loud;

        EqConfig.Preset toPreset() { return new EqConfig.Preset(name, g.clone(), bass, spatial, loud); }
    }

    private static final String PREFS = "ark_eq_presets", KEY = "list", APP = "ArkaelEqualizer";
    private static final int MAX_NAME = 32, MAX_PRESETS = 200;

    static UserPreset fromConfig(String name, EqConfig c) {
        UserPreset u = new UserPreset();
        u.name = cleanName(name);
        u.g = c.gain.clone();
        u.bass = c.bass; u.spatial = c.spatial; u.loud = c.loud;
        return u;
    }

    static String cleanName(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < MAX_NAME; i++) {
            char ch = s.charAt(i);
            if (ch >= 32 && ch != 127) sb.append(ch);
        }
        return sb.toString().trim();
    }

    static boolean isBuiltIn(String name) {
        return EqConfig.CUSTOM.equalsIgnoreCase(name) || EqConfig.find(name) != null;
    }

    static List<UserPreset> load(Context ctx) {
        List<UserPreset> out = new ArrayList<UserPreset>();
        String s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        if (s == null) return out;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                UserPreset u = parse(a.optJSONObject(i));
                if (u != null) out.add(u);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static void saveAll(Context ctx, List<UserPreset> list) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putString(KEY, toArray(list).toString());
        e.apply();
    }

    static UserPreset find(List<UserPreset> list, String name) {
        for (UserPreset u : list) if (u.name.equals(name)) return u;
        return null;
    }

    /** Simpan (timpa kalau nama sama). */
    static void upsert(Context ctx, UserPreset u) {
        List<UserPreset> list = load(ctx);
        UserPreset old = find(list, u.name);
        if (old != null) list.set(list.indexOf(old), u); else if (list.size() < MAX_PRESETS) list.add(u);
        saveAll(ctx, list);
    }

    static void delete(Context ctx, String name) {
        List<UserPreset> list = load(ctx);
        UserPreset old = find(list, name);
        if (old != null) { list.remove(old); saveAll(ctx, list); }
    }

    // ------------------------------------------------------------------ JSON

    private static JSONObject obj(UserPreset u) throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", u.name);
        JSONArray g = new JSONArray();
        for (float v : u.g) g.put(Math.round(v * 10f) / 10.0);
        o.put("g", g);
        o.put("bass", u.bass);
        o.put("sp", u.spatial);
        o.put("loud", u.loud);
        return o;
    }

    private static JSONArray toArray(List<UserPreset> list) {
        JSONArray a = new JSONArray();
        for (UserPreset u : list) { try { a.put(obj(u)); } catch (Exception ignored) { } }
        return a;
    }

    /** Paket (bisa satu atau banyak preset). */
    static String export(List<UserPreset> list) {
        try {
            JSONObject o = new JSONObject();
            o.put("app", APP);
            o.put("v", 1);
            o.put("presets", toArray(list));
            return o.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static UserPreset parse(JSONObject o) {
        if (o == null) return null;
        JSONArray g = o.optJSONArray("g");
        if (g == null || g.length() != EqConfig.N) return null;
        UserPreset u = new UserPreset();
        u.name = cleanName(o.optString("name", ""));
        if (u.name.isEmpty()) u.name = "Preset";
        for (int i = 0; i < EqConfig.N; i++) {
            double v = g.optDouble(i, Double.NaN);
            if (Double.isNaN(v) || Double.isInfinite(v)) return null;
            u.g[i] = EqConfig.clamp((float) v, -EqConfig.MAX_DB, EqConfig.MAX_DB);
        }
        u.bass = EqConfig.clampI(o.optInt("bass", 0), 0, 1000);
        u.spatial = EqConfig.clampI(o.optInt("sp", 0), 0, 1000);
        u.loud = EqConfig.clampI(o.optInt("loud", 0), 0, 1000);
        return u;
    }

    /**
     * Impor dari teks (isi file atau clipboard). Menerima paket, satu preset, atau array.
     * Teks di sekitar JSON (mis. pesan saat dibagikan) diabaikan. Nama bentrok diberi akhiran " (2)".
     * @return jumlah preset yang berhasil diimpor
     */
    static int importText(Context ctx, String text) {
        if (text == null) return 0;
        List<UserPreset> found = new ArrayList<UserPreset>();
        try {
            int a = text.indexOf('{'), b = text.indexOf('['), s;
            if (a < 0 && b < 0) return 0;
            s = a < 0 ? b : b < 0 ? a : Math.min(a, b);
            Object root = new org.json.JSONTokener(text.substring(s)).nextValue();
            if (root instanceof JSONObject) {
                JSONObject o = (JSONObject) root;
                JSONArray arr = o.optJSONArray("presets");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) { UserPreset u = parse(arr.optJSONObject(i)); if (u != null) found.add(u); }
                } else {
                    UserPreset u = parse(o);
                    if (u != null) found.add(u);
                }
            } else if (root instanceof JSONArray) {
                JSONArray arr = (JSONArray) root;
                for (int i = 0; i < arr.length(); i++) { UserPreset u = parse(arr.optJSONObject(i)); if (u != null) found.add(u); }
            }
        } catch (Exception e) {
            return 0;
        }
        if (found.isEmpty()) return 0;
        List<UserPreset> list = load(ctx);
        int n = 0;
        for (UserPreset u : found) {
            if (list.size() >= MAX_PRESETS) break;
            String base = u.name, nm = base;
            int k = 2;
            while (isBuiltIn(nm) || find(list, nm) != null) {
                String suf = " (" + (k++) + ")";
                nm = (base.length() + suf.length() > MAX_NAME ? base.substring(0, MAX_NAME - suf.length()) : base) + suf;
            }
            u.name = nm;
            list.add(u);
            n++;
        }
        saveAll(ctx, list);
        return n;
    }
}
