package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Simpan konfigurasi AI: base URL, API key, model. Key diisi manual oleh pengguna. */
final class AiStore {
    private static final String P = "arkael_ai";

    static final String DEF_BASE = "https://api.deepseek.com/v1";
    static final String DEF_MODEL = "deepseek-chat";

    private AiStore() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(P, Context.MODE_PRIVATE);
    }

    static String base(Context c)  { return sp(c).getString("base", DEF_BASE); }
    static String key(Context c)   { return sp(c).getString("key", ""); }
    static String model(Context c) { return sp(c).getString("model", DEF_MODEL); }
    static boolean agent(Context c) { return sp(c).getBoolean("agent", false); }

    static void save(Context c, String base, String key, String model, boolean agent) {
        sp(c).edit()
            .putString("base", base == null ? "" : base.trim())
            .putString("key", key == null ? "" : key.trim())
            .putString("model", model == null ? "" : model.trim())
            .putBoolean("agent", agent)
            .apply();
    }

    static boolean ready(Context c) { return !key(c).isEmpty(); }
}
