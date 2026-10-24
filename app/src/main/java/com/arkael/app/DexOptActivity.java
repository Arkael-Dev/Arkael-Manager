package com.arkael.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Dex2oat Optimizer: compile semua aplikasi lewat ART ("cmd package compile") dengan root.
 * Proses jalan di thread sendiri dan statusnya disimpan static, jadi layar boleh diputar / ditinggal.
 */
public class DexOptActivity extends AppCompatActivity {
    private static final String PREFS = "ax_dexopt", K_MODE = "mode";
    private static final String CH = "ax_dexopt";
    private static final int NID = 7110;

    // ---- state bersama (tetap hidup walau activity dibuka-tutup)
    private static final Object LOCK = new Object();
    private static final StringBuilder LOG = new StringBuilder();
    private static volatile boolean running, cancelled;
    private static volatile Process proc;
    private static volatile int step, total;
    private static volatile long t0, t1;
    private static volatile String state = "";   // "", done, cancel, noroot, error

    private static String t(String en, String id) { return "in".equals(Tr.lang()) ? id : en; }

    static String cardTitle() { return "Dex2oat Optimizer"; }
    static String cardSub() { return t("Compile all apps with ART for smoother performance", "Compile semua aplikasi lewat ART agar lebih lancar"); }

    private static String[] cmds(int m) {
        if (m == 0) return new String[]{"cmd package compile -m speed-profile -a"};
        if (m == 1) return new String[]{"cmd package compile -m speed -a"};
        return new String[]{
                "cmd package compile -m everything --check-prof false -a",
                "cmd package compile -m everything --secondary-dex -a",
                "cmd package reconcile-secondary-dex-files -a",
                "cmd package compile --compile-layouts -a"};
    }

    // ------------------------------------------------------------------ pekerja

    private static void log(String s) {
        synchronized (LOG) {
            LOG.append(s).append('\n');
            if (LOG.length() > 20000) LOG.delete(0, 10000);
        }
    }

    private static boolean hasRoot() {
        try {
            Process p = new ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String ln;
                while ((ln = r.readLine()) != null) sb.append(ln);
            }
            p.waitFor();
            return sb.toString().contains("uid=0");
        } catch (Throwable e) {
            return false;
        }
    }

    private static int exec(String cmd) throws Exception {
        Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
        proc = p;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String ln;
            int n = 0;
            while ((ln = r.readLine()) != null) { if (++n <= 200) log(ln); }
        }
        return p.waitFor();
    }

    private static void start(final Context app, final int m) {
        synchronized (LOCK) {
            if (running) return;
            running = true;
        }
        cancelled = false;
        state = "";
        synchronized (LOG) { LOG.setLength(0); }
        final String[] cs = cmds(m);
        step = 0;
        total = cs.length;
        t0 = SystemClock.elapsedRealtime();
        t1 = 0;
        new Thread(() -> {
            try {
                if (!hasRoot()) {
                    state = "noroot";
                    log(t("Root not available (su denied).", "Root tidak tersedia (izin su ditolak)."));
                    return;
                }
                for (int i = 0; i < cs.length && !cancelled; i++) {
                    step = i + 1;
                    log("[" + step + "/" + total + "] " + cs[i]);
                    int code = exec(cs[i]);
                    log("-> " + (code == 0 ? "OK" : t("finished with code ", "selesai dengan kode ") + code));
                }
                state = cancelled ? "cancel" : "done";
            } catch (Throwable e) {
                state = cancelled ? "cancel" : "error";
                log("Error: " + e);
            } finally {
                t1 = SystemClock.elapsedRealtime();
                running = false;
                proc = null;
                try { notifyDone(app); } catch (Throwable ignored) {}
            }
        }).start();
    }

    private static void cancel() {
        cancelled = true;
        Process p = proc;
        if (p != null) { try { p.destroy(); } catch (Throwable ignored) {} }
    }

    private static void notifyDone(Context app) {
        if (!Notifier.canPost(app)) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(new NotificationChannel(CH, "Dex2oat Optimizer", NotificationManager.IMPORTANCE_DEFAULT));
        }
        String txt;
        if ("done".equals(state)) txt = t("Optimization completed.", "Optimasi selesai.");
        else if ("cancel".equals(state)) txt = t("Optimization cancelled.", "Optimasi dibatalkan.");
        else if ("noroot".equals(state)) txt = t("Root not available.", "Root tidak tersedia.");
        else txt = t("Optimization failed.", "Optimasi gagal.");
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(app, NID, new Intent(app, DexOptActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), fl);
        NotificationCompat.Builder b = new NotificationCompat.Builder(app, CH)
                .setSmallIcon(R.drawable.ax_ic_update)
                .setContentTitle("Dex2oat Optimizer")
                .setContentText(txt)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try { NotificationManagerCompat.from(app).notify(NID, b.build()); } catch (SecurityException ignored) {}
    }

    // ------------------------------------------------------------------ UI

    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences sp;
    private int mode;
    private LinearLayout[] modeBox = new LinearLayout[3];
    private TextView btn, status, logView, timeView;
    private ProgressBar bar;
    private ScrollView logScroll;
    private boolean wasRunning;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable box(int fill, int stroke, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(r));
        if (stroke != 0) g.setStroke(dp(1.5f), stroke);
        return g;
    }

    private TextView tv(String s, int color, float size, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(color);
        v.setTextSize(size);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0A0D12"));
        getWindow().setNavigationBarColor(Color.parseColor("#0A0D12"));
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        mode = Math.max(0, Math.min(2, sp.getInt(K_MODE, 2)));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#0A0D12"));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(24));
        sv.addView(col);
        sv.setOnApplyWindowInsetsListener((v, ins) -> {
            v.setPadding(0, ins.getSystemWindowInsetTop(), 0, ins.getSystemWindowInsetBottom());
            return ins;
        });

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = tv("←", Color.WHITE, 24, true);
        back.setPadding(0, 0, dp(14), 0);
        back.setOnClickListener(v -> finish());
        head.addView(back);
        head.addView(tv("DEX2OAT OPTIMIZER", Color.WHITE, 18, true));
        col.addView(head);

        TextView info = tv(t("Compiles installed apps ahead of time (ART). Apps can open faster and run smoother. Needs root.",
                "Meng-compile aplikasi terpasang lebih awal (ART). Aplikasi bisa terbuka lebih cepat dan lebih lancar. Butuh root."),
                Color.parseColor("#8B95A3"), 12, false);
        info.setPadding(0, dp(10), 0, dp(14));
        col.addView(info);

        col.addView(tv(t("MODE", "MODE"), Color.parseColor("#8B95A3"), 11, true));
        String[] names = {t("Fast", "Cepat"), t("Balanced", "Seimbang"), t("Maximum", "Maksimal")};
        String[] descs = {
                t("speed-profile: only the hot code of each app. Quickest, uses little storage.", "speed-profile: hanya kode yang sering dipakai. Paling cepat, hemat penyimpanan."),
                t("speed: compiles all code of every app. Slower, a bit more storage.", "speed: compile semua kode tiap aplikasi. Lebih lama, penyimpanan sedikit bertambah."),
                t("everything: compiles everything incl. secondary dex + layouts (same as the original script). Longest, most storage.", "everything: compile semuanya termasuk secondary dex + layout (sama seperti script asli). Paling lama, penyimpanan paling besar.")};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(dp(14), dp(10), dp(14), dp(10));
            r.addView(tv(names[i], Color.WHITE, 14, true));
            TextView d = tv(descs[i], Color.parseColor("#8B95A3"), 11, false);
            r.addView(d);
            r.setOnClickListener(v -> {
                if (running) return;
                mode = idx;
                sp.edit().putInt(K_MODE, mode).apply();
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(8);
            col.addView(r, lp);
            modeBox[i] = r;
        }

        TextView warn = tv(t("Takes about 5-30+ minutes. Keep the phone charging and this screen open. The phone may get warm. Doesn't guarantee a big difference.",
                "Butuh sekitar 5-30+ menit. Sebaiknya sambil dicas dan layar ini tetap dibuka. HP bisa hangat. Hasilnya tidak dijamin terasa besar."),
                Color.parseColor("#FFB300"), 11, false);
        warn.setPadding(0, dp(12), 0, dp(12));
        col.addView(warn);

        btn = tv("", Color.WHITE, 15, true);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, dp(14), 0, dp(14));
        btn.setOnClickListener(v -> onBtn());
        col.addView(btn, new LinearLayout.LayoutParams(-1, -2));

        status = tv("", Color.WHITE, 13, true);
        status.setPadding(0, dp(14), 0, dp(4));
        col.addView(status);
        timeView = tv("", Color.parseColor("#8B95A3"), 11, false);
        col.addView(timeView);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(6));
        bl.topMargin = dp(8);
        col.addView(bar, bl);

        logScroll = new ScrollView(this);
        logScroll.setBackground(box(Color.parseColor("#05070A"), 0, 10));
        logView = tv("", Color.parseColor("#9FB3C8"), 10, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(dp(10), dp(8), dp(10), dp(8));
        logScroll.addView(logView);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, dp(220));
        ll.topMargin = dp(10);
        col.addView(logScroll, ll);

        setContentView(sv);
        refresh();
    }

    private void onBtn() {
        if (running) {
            cancel();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Dex2oat Optimizer")
                .setMessage(t("Start optimizing all apps now? Don't close the app until it finishes.",
                        "Mulai optimasi semua aplikasi sekarang? Jangan tutup aplikasi sampai selesai."))
                .setNegativeButton(t("Cancel", "Batal"), null)
                .setPositiveButton(t("Start", "Mulai"), (d, w) -> {
                    start(getApplicationContext(), mode);
                    refresh();
                }).show();
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private void refresh() {
        boolean run = running;
        for (int i = 0; i < 3; i++) {
            boolean on = i == mode;
            modeBox[i].setBackground(box(on ? Color.parseColor("#33FF4444") : Color.parseColor("#151A22"),
                    on ? Color.parseColor("#FF4444") : 0, 12));
            modeBox[i].setAlpha(run && !on ? 0.4f : 1f);
        }
        btn.setText(run ? t("CANCEL", "BATALKAN") : t("START OPTIMIZING", "MULAI OPTIMASI"));
        btn.setBackground(box(run ? Color.parseColor("#3A4250") : Color.parseColor("#FF4444"), 0, 12));
        bar.setVisibility(run ? View.VISIBLE : View.GONE);

        String st;
        int color = Color.WHITE;
        if (run) {
            st = t("Running... step ", "Berjalan... langkah ") + step + "/" + total;
        } else if ("done".equals(state)) {
            st = t("Optimization completed.", "Optimasi selesai."); color = Color.parseColor("#4CAF50");
        } else if ("cancel".equals(state)) {
            st = t("Cancelled.", "Dibatalkan."); color = Color.parseColor("#FFB300");
        } else if ("noroot".equals(state)) {
            st = t("Root not available.", "Root tidak tersedia."); color = Color.parseColor("#FF4444");
        } else if ("error".equals(state)) {
            st = t("Failed, see the log.", "Gagal, lihat log."); color = Color.parseColor("#FF4444");
        } else {
            st = t("Ready.", "Siap.");
        }
        status.setText(st);
        status.setTextColor(color);
        long el = run ? SystemClock.elapsedRealtime() - t0 : (t1 > 0 ? t1 - t0 : 0);
        timeView.setText(el > 0 ? t("Elapsed ", "Waktu ") + fmt(el) : "");
        String lg;
        synchronized (LOG) { lg = LOG.toString(); }
        if (!lg.contentEquals(logView.getText())) {
            logView.setText(lg);
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        }
        if (run) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            main.postDelayed(this, 700);
        }
    };

    @Override protected void onResume() {
        super.onResume();
        main.removeCallbacks(tick);
        main.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
    }
}
