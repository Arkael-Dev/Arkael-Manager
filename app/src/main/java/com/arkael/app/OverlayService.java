package com.arkael.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

/** Draggable live-stats overlay. Drag anywhere; double-tap toggles compact mode. */
public class OverlayService extends Service {
    static final String ACTION_STOP = "com.arkael.app.OVERLAY_STOP";
    static final String ACTION_REFRESH = "com.arkael.app.OVERLAY_REFRESH";
    private static final String CHANNEL = "arkael_overlay";
    private static final int NOTIF_ID = 4711;

    private WindowManager wm;
    private LinearLayout root;
    private WindowManager.LayoutParams lp;
    private TextView[] values = new TextView[6];
    private SparkView spark;
    private final FpsProbe fpsProbe = new FpsProbe();
    private TextView compactText;
    private final int[] bits = {OverlayPrefs.M_FPS, OverlayPrefs.M_CPU, OverlayPrefs.M_TEMP, OverlayPrefs.M_GPU, OverlayPrefs.M_RAM, OverlayPrefs.M_BAT};
    private final String[] labels = {"FPS", "CPU", "TEMP", "GPU", "RAM", "BAT"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread worker;
    private Handler bg;
    private final SysProbe probe = new SysProbe();
    private volatile boolean running;

    static void start(Context c) {
        ContextCompat.startForegroundService(c, new Intent(c, OverlayService.class));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, OverlayService.class));
    }

    static void refresh(Context c) {
        if (OverlayPrefs.enabled(c)) {
            Intent i = new Intent(c, OverlayService.class).setAction(ACTION_REFRESH);
            ContextCompat.startForegroundService(c, i);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            OverlayPrefs.setEnabled(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            OverlayPrefs.setEnabled(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        buildOverlay();
        if (!running) {
            running = true;
            worker = new HandlerThread("ark-probe");
            worker.start();
            bg = new Handler(worker.getLooper());
            bg.post(loop);
        }
        return START_STICKY;
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            final SysProbe.Snapshot s = probe.read(OverlayService.this);
            int m = OverlayPrefs.mask(OverlayService.this);
            final int fps = (s.root && (m & (OverlayPrefs.M_FPS | OverlayPrefs.M_GRAPH)) != 0) ? fpsProbe.read() : -1;
            main.post(new Runnable() { @Override public void run() { render(s, fps); } });
            if (bg != null) bg.postDelayed(this, 1000);
        }
    };

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Performance overlay", NotificationManager.IMPORTANCE_LOW));
        }
        Intent stop = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        int pf = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getService(this, 0, stop, pf);
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_cpu)
                .setContentTitle("Arkael Manager")
                .setContentText("Performance overlay is running")
                .setOngoing(true)
                .addAction(0, "Stop", pi)
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        else startForeground(NOTIF_ID, n);
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void buildOverlay() {
        removeOverlay();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        final int accent = OverlayPrefs.color(this);
        int alpha = Math.max(30, Math.min(100, OverlayPrefs.alpha(this)));
        float[] scale = {0.85f, 1f, 1.25f};
        float ts = 11f * scale[Math.max(0, Math.min(2, OverlayPrefs.size(this)))];
        int mask = OverlayPrefs.mask(this);
        boolean compact = OverlayPrefs.compact(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(7), dp(10), dp(7));
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(Color.argb(alpha * 255 / 100, 10, 13, 18));
        bgd.setCornerRadius(dp(12));
        bgd.setStroke(dp(1), (accent & 0x00FFFFFF) | 0xAA000000);
        root.setBackground(bgd);

        TextView title = new TextView(this);
        title.setText("● ARKAEL");
        title.setTextColor(accent);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, ts - 2);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setLetterSpacing(0.1f);

        values = new TextView[6];
        spark = null;
        compactText = null;
        if (compact) {
            compactText = new TextView(this);
            compactText.setTextColor(Color.WHITE);
            compactText.setTextSize(TypedValue.COMPLEX_UNIT_SP, ts);
            compactText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            root.addView(compactText);
        } else {
            root.addView(title);
            for (int i = 0; i < 6; i++) {
                if ((mask & bits[i]) == 0) continue;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dp(2), 0, 0);
                TextView l = new TextView(this);
                l.setText(labels[i]);
                l.setTextColor(accent);
                l.setTextSize(TypedValue.COMPLEX_UNIT_SP, ts);
                l.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                l.setMinWidth(dp(44 * scale[Math.max(0, Math.min(2, OverlayPrefs.size(this)))]));
                TextView v = new TextView(this);
                v.setText("--");
                v.setTextColor(Color.WHITE);
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, ts);
                v.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                row.addView(l);
                row.addView(v);
                root.addView(row);
                values[i] = v;
            }
            if ((mask & OverlayPrefs.M_GRAPH) != 0) {
                float sc = scale[Math.max(0, Math.min(2, OverlayPrefs.size(this)))];
                spark = new SparkView(this, accent, dp(120 * sc), dp(34 * sc));
                LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                gp.topMargin = dp(5);
                root.addView(spark, gp);
            }
        }

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = OverlayPrefs.x(this);
        lp.y = OverlayPrefs.y(this);

        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDoubleTap(MotionEvent e) {
                OverlayPrefs.sp(OverlayService.this).edit().putBoolean("compact", !OverlayPrefs.compact(OverlayService.this)).apply();
                buildOverlay();
                return true;
            }
        });
        root.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY; private float touchX, touchY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                gd.onTouchEvent(e);
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x; startY = lp.y; touchX = e.getRawX(); touchY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        DisplayMetrics dm = getResources().getDisplayMetrics();
                        int nx = startX + (int) (e.getRawX() - touchX), ny = startY + (int) (e.getRawY() - touchY);
                        lp.x = Math.max(0, Math.min(dm.widthPixels - Math.max(root.getWidth(), 1), nx));
                        lp.y = Math.max(0, Math.min(dm.heightPixels - Math.max(root.getHeight(), 1), ny));
                        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        OverlayPrefs.sp(OverlayService.this).edit().putInt("x", lp.x).putInt("y", lp.y).apply();
                        return true;
                }
                return false;
            }
        });
        try { wm.addView(root, lp); } catch (Exception e) { stopSelf(); }
    }

    private void removeOverlay() {
        if (wm != null && root != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
        }
        root = null;
    }

    private static String ghz(int mhz) {
        return mhz < 0 ? "--" : String.format(Locale.US, "%.2fGHz", mhz / 1000f);
    }

    private void render(SysProbe.Snapshot s, int fps) {
        if (root == null) return;
        String cpu = (s.cpuLoad >= 0 ? s.cpuLoad + "% " : "") + (s.cpuMaxMhz > 0 ? ghz(s.cpuMaxMhz) : (s.root ? "" : "no root"));
        if (cpu.isEmpty()) cpu = "--";
        String temp = s.cpuTemp > 0 ? String.format(Locale.US, "%.1f°C", s.cpuTemp) : "--";
        String gpu = (s.gpuLoad >= 0 ? s.gpuLoad + "% " : "") + (s.gpuMhz > 0 ? s.gpuMhz + "MHz" : "");
        if (gpu.isEmpty()) gpu = s.root ? "N/A" : "no root";
        String ram = s.ramPct < 0 ? "--" : s.ramPct + "% " + String.format(Locale.US, "%.1f/%.1fG", s.ramUsedMb / 1024f, s.ramTotalMb / 1024f);
        String bat = (s.batPct >= 0 ? s.batPct + "% " : "") + (s.batMa != Integer.MIN_VALUE ? s.batMa + "mA " : "")
                + (s.batTemp > 0 ? String.format(Locale.US, "%.0f°", s.batTemp) : "");
        String fpsT = fps >= 0 ? fps + " fps" : "--";
        String[] txt = {fpsT, cpu, temp, gpu, ram, bat.trim().isEmpty() ? "--" : bat.trim()};

        if (compactText != null) {
            int mask = OverlayPrefs.mask(this);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                if ((mask & bits[i]) == 0) continue;
                if (sb.length() > 0) sb.append(" | ");
                sb.append(labels[i]).append(' ').append(txt[i]);
            }
            compactText.setText(sb.toString());
        } else {
            for (int i = 0; i < 6; i++) if (values[i] != null) values[i].setText(txt[i]);
            if (spark != null) spark.push(s.cpuLoad, s.cpuTemp, fps);
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (bg != null) bg.removeCallbacksAndMessages(null);
        if (worker != null) worker.quitSafely();
        removeOverlay();
        super.onDestroy();
    }
}
