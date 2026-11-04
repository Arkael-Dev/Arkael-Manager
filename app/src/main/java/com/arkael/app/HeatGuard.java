package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.media.Ringtone;

/**
 * Penjaga suhu baterai saat main game. Kalau baterai makin panas, kasih peringatan,
 * dan (kalau diminta) turunkan setelan CPU/GPU otomatis supaya tidak throttling parah.
 */
final class HeatGuard {
    private HeatGuard() {}

    static final float[] LIMITS = {40f, 42f, 44f, 46f};

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("arkael_games", Context.MODE_PRIVATE); }

    static boolean enabled(Context c) { return sp(c).getBoolean("guard", true); }
    static void setEnabled(Context c, boolean v) { sp(c).edit().putBoolean("guard", v).apply(); }

    static float limit(Context c) { return sp(c).getFloat("guard_c", 43f); }
    static void setLimit(Context c, float v) { sp(c).edit().putFloat("guard_c", v).apply(); }

    /** true = turunkan setelan otomatis saat limit tercapai. */
    static boolean autoCool(Context c) { return sp(c).getBoolean("guard_cool", false); }
    static void setAutoCool(Context c, boolean v) { sp(c).edit().putBoolean("guard_cool", v).apply(); }

    /** Bunyi notifikasi singkat. Gagal diam-diam kalau mode senyap. */
    static void beep(Context c) {
        try {
            Ringtone r = RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (r != null) r.play();
        } catch (Throwable ignored) {}
    }
}
