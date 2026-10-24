package com.arkael.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;

/**
 * Downscale resolusi per aplikasi/game lewat Game Manager Android (butuh root, Android 12+).
 * Nilai disimpan per paket. Diterapkan saat game dibuka lewat Game Launcher / profil, berlaku di peluncuran game berikutnya.
 */
final class AppDownscale {
    private static final String PREFS = "ark_downscale";
    /** Persen resolusi yang ditawarkan. 100 = normal (tanpa downscale). */
    static final int[] STEPS = {100, 90, 80, 70, 60, 50, 40};

    static int get(Context c, String pkg) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(pkg, 100);
    }

    static void set(Context c, String pkg, int pct) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (pct >= 100) e.remove(pkg); else e.putInt(pkg, pct);
        e.apply();
    }

    static String label(Context c, int pct) {
        return pct >= 100 ? c.getString(R.string.ax_ds_off) : pct + "%";
    }

    private static boolean safePkg(String p) {
        return p != null && p.matches("[A-Za-z0-9_.]{1,200}");
    }

    /** Terapkan nilai tersimpan untuk paket (blokir, jalankan di thread latar). Mengembalikan true kalau perintah root sukses. */
    static boolean applyFor(Context c, String pkg) {
        return push(pkg, get(c, pkg));
    }

    static boolean push(String pkg, int pct) {
        if (!safePkg(pkg) || Build.VERSION.SDK_INT < 31) return false;
        try {
            if (!RootShell.hasRoot()) return false;
            if (pct >= 100) {
                RootShell.run("cmd game reset " + pkg + " 2>/dev/null");
                RootShell.run("device_config delete game_overlay " + pkg + " 2>/dev/null");
                return true;
            }
            String f = String.format(java.util.Locale.US, "%.2f", pct / 100f);
            String o = RootShell.run("cmd game set --mode performance --downscale " + f + " " + pkg + " 2>&1");
            if (o == null || o.toLowerCase(java.util.Locale.US).contains("error") || o.toLowerCase(java.util.Locale.US).contains("unknown")) {
                RootShell.run("device_config put game_overlay " + pkg + " mode=2:downscaleFactor=" + f + " 2>&1");
            }
            RootShell.run("cmd game mode performance " + pkg + " 2>/dev/null");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Dialog pilih persen resolusi. onChange dipanggil di thread UI setelah dipilih. */
    static void showPicker(final Activity a, final String pkg, String appName, final Runnable onChange) {
        int cur = get(a, pkg), sel = 0;
        final String[] items = new String[STEPS.length];
        for (int i = 0; i < STEPS.length; i++) {
            items[i] = label(a, STEPS[i]);
            if (STEPS[i] == cur) sel = i;
        }
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.getString(R.string.ax_ds_title) + (appName == null ? "" : " · " + appName))
                .setSingleChoiceItems(items, sel, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(final android.content.DialogInterface d, int which) {
                        final int pct = STEPS[which];
                        set(a, pkg, pct);
                        d.dismiss();
                        if (onChange != null) onChange.run();
                        if (Build.VERSION.SDK_INT < 31) {
                            Toast.makeText(a, R.string.ax_ds_need12, Toast.LENGTH_LONG).show();
                            return;
                        }
                        new Thread(new Runnable() {
                            @Override public void run() {
                                final boolean ok = push(pkg, pct);
                                a.runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        Toast.makeText(a, ok ? a.getString(R.string.ax_ds_saved) : a.getString(R.string.ax_ds_noroot), Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }).start();
                    }
                })
                .setNegativeButton(R.string.ax_eq_cancel, null)
                .show();
    }
}
