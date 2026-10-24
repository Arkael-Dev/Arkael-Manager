package com.arkael.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Downscale resolusi per aplikasi/game lewat Game Manager Android (butuh root, Android 12+).
 * Dua cara: MANUAL (geser sendiri 30-100%) atau OTOMATIS (dihitung dari layar dan RAM HP ini).
 * Nilai disimpan per paket; berlaku di peluncuran game berikutnya.
 */
final class AppDownscale {
    private static final String PREFS = "ark_downscale";
    /** Nilai simpanan khusus: ikuti perhitungan otomatis. */
    static final int AUTO = -1;
    static final int MIN = 30, MAX = 100;
    /** Dipertahankan agar kode lama yang memakai STEPS tetap jalan. */
    static final int[] STEPS = {100, 90, 80, 70, 60, 50, 40};

    /** Nilai tersimpan: AUTO, atau persen manual (100 = normal). */
    static int get(Context c, String pkg) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(pkg, 100);
    }

    static void set(Context c, String pkg, int pct) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (pct == AUTO) e.putInt(pkg, AUTO);
        else if (pct >= 100) e.remove(pkg);
        else e.putInt(pkg, Math.max(MIN, pct));
        e.apply();
    }

    /** Persen otomatis: target sisi pendek layar menurut RAM, dibulatkan 5%, minimal 50%. */
    static int autoPct(Context c) {
        try {
            DisplayMetrics dm = new DisplayMetrics();
            WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getRealMetrics(dm);
            int shortSide = Math.min(dm.widthPixels, dm.heightPixels);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
            double gb = mi.totalMem / (1024.0 * 1024.0 * 1024.0);
            int target;
            if (gb <= 3.3) target = 540;
            else if (gb <= 4.5) target = 640;
            else if (gb <= 6.5) target = 720;
            else if (gb <= 8.5) target = 900;
            else return 100;
            if (shortSide <= target) return 100;
            int pct = Math.round(target * 100f / shortSide / 5f) * 5;
            return Math.max(50, Math.min(100, pct));
        } catch (Throwable t) {
            return 100;
        }
    }

    /** Ubah nilai simpanan (AUTO atau persen) jadi persen nyata. */
    static int resolve(Context c, int stored) {
        return stored == AUTO ? autoPct(c) : stored;
    }

    static String label(Context c, int pct) {
        if (pct == AUTO) return Tr.s("Otomatis") + " (" + autoPct(c) + "%)";
        return pct >= 100 ? c.getString(R.string.ax_ds_off) : pct + "%";
    }

    private static boolean safePkg(String p) {
        return p != null && p.matches("[A-Za-z0-9_.]{1,200}");
    }

    /** Terapkan nilai tersimpan untuk paket (blokir, jalankan di thread latar). Mengembalikan true kalau perintah root sukses. */
    static boolean applyFor(Context c, String pkg) {
        return push(pkg, resolve(c, get(c, pkg)));
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
            RootShell.run("cmd game set --mode 2 --downscale " + f + " " + pkg + " 2>&1");
            RootShell.run("device_config put game_overlay " + pkg + " mode=2,downscaleFactor=" + f + ":mode=3,downscaleFactor=" + f + " 2>&1");
            RootShell.run("cmd game mode 2 " + pkg + " 2>&1");
            String chk = RootShell.run("cmd game list-configs " + pkg + " 2>&1");
            try { java.io.FileWriter w = new java.io.FileWriter("/sdcard/Download/arkael_downscale_log.txt", true);
                w.write(pkg + " " + f + "\n" + chk + "\n\n"); w.close(); } catch (Throwable ignore) {}
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void commit(final Activity a, final String pkg, final int stored, final Runnable onChange) {
        set(a, pkg, stored);
        if (onChange != null) onChange.run();
        if (Build.VERSION.SDK_INT < 31) {
            Toast.makeText(a, R.string.ax_ds_need12, Toast.LENGTH_LONG).show();
            return;
        }
        final int pct = resolve(a, stored);
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

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Dialog: slider manual 30-100% (tombol -/+ untuk 1%), tombol Otomatis, Terapkan. */
    static void showPicker(final Activity a, final String pkg, String appName, final Runnable onChange) {
        final int cur = get(a, pkg);
        final int[] val = {cur == AUTO ? autoPct(a) : Math.max(MIN, Math.min(MAX, cur))};

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 22), dp(a, 8), dp(a, 22), 0);

        final TextView big = new TextView(a);
        big.setTextSize(30);
        big.setTextColor(Color.WHITE);
        big.setGravity(Gravity.CENTER);
        box.addView(big);

        TextView sub = new TextView(a);
        sub.setText(Tr.s("Geser untuk atur resolusi"));
        sub.setTextSize(12);
        sub.setTextColor(Color.parseColor("#AAAAAA"));
        sub.setGravity(Gravity.CENTER);
        box.addView(sub);

        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final SeekBar sb = new SeekBar(a);
        sb.setMax(MAX - MIN);
        sb.setProgress(val[0] - MIN);
        Button minus = new Button(a, null, android.R.attr.borderlessButtonStyle);
        minus.setText("−");
        minus.setTextColor(Color.WHITE);
        Button plus = new Button(a, null, android.R.attr.borderlessButtonStyle);
        plus.setText("+");
        plus.setTextColor(Color.WHITE);
        row.addView(minus, new LinearLayout.LayoutParams(dp(a, 48), -2));
        row.addView(sb, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(plus, new LinearLayout.LayoutParams(dp(a, 48), -2));
        box.addView(row);

        TextView hint = new TextView(a);
        hint.setText(Tr.s("Otomatis: disesuaikan dengan HP ini") + " → " + autoPct(a) + "%");
        hint.setTextSize(12);
        hint.setTextColor(Color.parseColor("#888888"));
        hint.setGravity(Gravity.CENTER);
        box.addView(hint);

        final Runnable refresh = new Runnable() {
            @Override public void run() {
                big.setText(Tr.s("Resolusi") + " " + label(a, val[0]));
            }
        };
        refresh.run();
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                val[0] = MIN + p; refresh.run();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        minus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sb.setProgress(Math.max(0, sb.getProgress() - 1)); }
        });
        plus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sb.setProgress(Math.min(MAX - MIN, sb.getProgress() + 1)); }
        });

        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.getString(R.string.ax_ds_title) + (appName == null ? "" : " · " + appName))
                .setView(box)
                .setPositiveButton(Tr.s("Terapkan"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        commit(a, pkg, val[0], onChange);
                    }
                })
                .setNeutralButton(Tr.s("Otomatis"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        commit(a, pkg, AUTO, onChange);
                    }
                })
                .setNegativeButton(R.string.ax_eq_cancel, null)
                .show();
    }
}
