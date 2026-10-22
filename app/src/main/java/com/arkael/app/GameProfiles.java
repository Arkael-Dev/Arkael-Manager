package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Which apps count as games, and the profile of each one. */
final class GameProfiles {
    static final class Profile {
        String gov = "";            // "" = leave as is
        int gpuMaxMhz = 0;          // 0 = leave as is
        int zramMb = 0;             // 0 = leave as is (only applied when started with PLAY)
        boolean thermalRelax = false;
        boolean cleanRam = true;
        boolean dnd = true;
        boolean headsUp = true;
        boolean overlay = true;
    }

    private GameProfiles() {}

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("arkael_games", Context.MODE_PRIVATE); }

    static boolean auto(Context c) { return sp(c).getBoolean("auto", false); }
    static void setAuto(Context c, boolean v) { sp(c).edit().putBoolean("auto", v).apply(); }
    static boolean panel(Context c) { return sp(c).getBoolean("panel", true); }
    static void setPanel(Context c, boolean v) { sp(c).edit().putBoolean("panel", v).apply(); }

    /** Side handle shown in every app, without a game being detected (to try the panel out or use it anywhere). */
    static boolean manual(Context c) { return sp(c).getBoolean("manual_panel", false); }
    static void setManual(Context c, boolean v) { sp(c).edit().putBoolean("manual_panel", v).apply(); }

    static Set<String> games(Context c) { return new HashSet<String>(sp(c).getStringSet("games", new HashSet<String>())); }
    static boolean isGame(Context c, String pkg) { return sp(c).getStringSet("games", new HashSet<String>()).contains(pkg); }

    static void setGame(Context c, String pkg, boolean on) {
        Set<String> s = games(c);
        if (on) s.add(pkg); else s.remove(pkg);
        sp(c).edit().putStringSet("games", s).apply();
    }

    static Profile get(Context c, String pkg) {
        Profile p = new Profile();
        String j = sp(c).getString("p:" + pkg, null);
        if (j == null) return p;
        try {
            JSONObject o = new JSONObject(j);
            p.gov = o.optString("gov", "");
            p.gpuMaxMhz = o.optInt("gpu", 0);
            p.zramMb = o.optInt("zram", 0);
            p.thermalRelax = o.optBoolean("thermal", false);
            p.cleanRam = o.optBoolean("clean", true);
            p.dnd = o.optBoolean("dnd", true);
            p.headsUp = o.optBoolean("heads", true);
            p.overlay = o.optBoolean("overlay", true);
        } catch (Exception ignored) {}
        return p;
    }

    static void save(Context c, String pkg, Profile p) {
        try {
            JSONObject o = new JSONObject();
            o.put("gov", p.gov);
            o.put("gpu", p.gpuMaxMhz);
            o.put("zram", p.zramMb);
            o.put("thermal", p.thermalRelax);
            o.put("clean", p.cleanRam);
            o.put("dnd", p.dnd);
            o.put("heads", p.headsUp);
            o.put("overlay", p.overlay);
            sp(c).edit().putString("p:" + pkg, o.toString()).apply();
        } catch (Exception ignored) {}
    }
}
