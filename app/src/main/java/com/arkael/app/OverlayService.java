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
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Live-stats overlay.
 *  - drag with one finger to move it
 *  - pinch with two fingers, or drag the little corner grip, to resize it freely
 *  - double-tap toggles compact mode
 */
public class OverlayService extends Service {
    static final String ACTION_STOP = "com.arkael.app.OVERLAY_STOP";
    static final String ACTION_REFRESH = "com.arkael.app.OVERLAY_REFRESH";
    private static final String CHANNEL = "arkael_overlay";
    private static final int NOTIF_ID = 4711;

    private WindowManager wm;
    private FrameLayout root;
    private LinearLayout content;
    private WindowManager.LayoutParams lp;
    private TextView[] values = new TextView[6];
    private final List<TextView> texts = new ArrayList<TextView>();
    private final List<TextView> labelViews = new ArrayList<TextView>();
    private SparkView spark;
    private TextView compactText;
    private float scale = 1f;

    private final FpsProbe fpsProbe = new FpsProbe();
    private final TempProbe tempProbe = new TempProbe();
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
            try {
                final SysProbe.Snapshot s = probe.read(OverlayService.this);
                int m = OverlayPrefs.mask(OverlayService.this);
                final int fps = (s.root && (m & (OverlayPrefs.M_FPS | OverlayPrefs.M_GRAPH)) != 0) ? fpsProbe.read() : -1;
                final float temp = (s.root && (m & (OverlayPrefs.M_TEMP | OverlayPrefs.M_GRAPH)) != 0) ? tempProbe.read(true) : -1f;
                main.post(new Runnable() { @Override public void run() { render(s, fps, temp); } });
            } catch (Exception ignored) {}
            if (bg != null && running) bg.postDelayed(this, 1000);
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

    private TextView text(String s, int color, float baseSp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTag(Float.valueOf(baseSp));
        t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        texts.add(t);
        return t;
    }

    /** Applies the current scale to the views already on screen, so a pinch never rebuilds (and never loses) the touch. */
    private void applyScale() {
        if (root == null) return;
        for (TextView t : texts) t.setTextSize(TypedValue.COMPLEX_UNIT_SP, ((Float) t.getTag()).floatValue() * scale);
        for (TextView t : labelViews) t.setMinWidth(dp(44 * scale));
        content.setPadding(dp(10 * scale), dp(7 * scale), dp(14 + 2 * scale), dp(7 * scale));
        if (spark != null) spark.setSizePx(dp(120 * scale), dp(34 * scale));
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
    }

    private void buildOverlay() {
        removeOverlay();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        final int accent = OverlayPrefs.color(this);
        int alpha = Math.max(30, Math.min(100, OverlayPrefs.alpha(this)));
        int mask = OverlayPrefs.mask(this);
        boolean compact = OverlayPrefs.compact(this);
        scale = OverlayPrefs.scale(this);
        texts.clear();
        labelViews.clear();

        root = new FrameLayout(this);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(Color.argb(alpha * 255 / 100, 10, 13, 18));
        bgd.setCornerRadius(dp(12));
        bgd.setStroke(dp(1), (accent & 0x00FFFFFF) | 0xAA000000);
        root.setBackground(bgd);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        values = new TextView[6];
        spark = null;
        compactText = null;
        if (compact) {
            compactText = text("", Color.WHITE, 11f);
            content.addView(compactText);
        } else {
            TextView title = text("● ARKAEL", accent, 9f);
            title.setLetterSpacing(0.1f);
            content.addView(title);
            for (int i = 0; i < 6; i++) {
                if ((mask & bits[i]) == 0) continue;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dp(2), 0, 0);
                TextView l = text(labels[i], accent, 11f);
                labelViews.add(l);
                TextView v = text("--", Color.WHITE, 11f);
                row.addView(l);
                row.addView(v);
                content.addView(row);
                values[i] = v;
            }
            if ((mask & OverlayPrefs.M_GRAPH) != 0) {
                spark = new SparkView(this, accent, dp(120), dp(34));
                LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                gp.topMargin = dp(5);
                content.addView(spark, gp);
            }
        }

        // resize grip, bottom-right corner
        final TextView grip = new TextView(this);
        grip.setText("◢");
        grip.setTextColor((accent & 0x00FFFFFF) | 0xCC000000);
        grip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        grip.setGravity(Gravity.BOTTOM | Gravity.END);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.BOTTOM | Gravity.END);
        root.addView(grip, gl);
        grip.setOnTouchListener(new View.OnTouchListener() {
            private float sx, sy, s0; private int sw, sh;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY(); s0 = scale;
                        sw = Math.max(1, root.getWidth()); sh = Math.max(1, root.getHeight());
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float ratio = (sw + sh + (e.getRawX() - sx) + (e.getRawY() - sy)) / (float) (sw + sh);
                        scale = Math.max(OverlayPrefs.SCALE_MIN, Math.min(OverlayPrefs.SCALE_MAX, s0 * ratio));
                        applyScale();
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        OverlayPrefs.setScale(OverlayService.this, scale);
                        return true;
                }
                return false;
            }
        });

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
                main.post(new Runnable() { @Override public void run() { buildOverlay(); } });
                return true;
            }
        });
        final ScaleGestureDetector sgd = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                scale = Math.max(OverlayPrefs.SCALE_MIN, Math.min(OverlayPrefs.SCALE_MAX, scale * d.getScaleFactor()));
                applyScale();
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d) { OverlayPrefs.setScale(OverlayService.this, scale); }
        });
        root.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY; private float touchX, touchY; private boolean rebase;
            @Override public boolean onTouch(View v, MotionEvent e) {
                sgd.onTouchEvent(e);
                gd.onTouchEvent(e);
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_POINTER_DOWN || a == MotionEvent.ACTION_POINTER_UP) { rebase = true; return true; }
                if (sgd.isInProgress() || e.getPointerCount() > 1) return true;
                switch (a) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x; startY = lp.y; touchX = e.getRawX(); touchY = e.getRawY(); rebase = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (rebase) { startX = lp.x; startY = lp.y; touchX = e.getRawX(); touchY = e.getRawY(); rebase = false; }
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
        try { wm.addView(root, lp); } catch (Exception e) { stopSelf(); return; }
        applyScale();
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

    private void render(SysProbe.Snapshot s, int fps, float temp) {
        if (root == null) return;
        String cpu = (s.cpuLoad >= 0 ? s.cpuLoad + "% " : "") + (s.cpuMaxMhz > 0 ? ghz(s.cpuMaxMhz) : (s.root ? "" : "no root"));
        if (cpu.isEmpty()) cpu = "--";
        float tShown = temp > 0f ? temp : (float) s.cpuTemp;
        String tempT = tShown > 0 ? String.format(Locale.US, "%.1f°C", tShown) : "--";
        String gpu = (s.gpuLoad >= 0 ? s.gpuLoad + "% " : "") + (s.gpuMhz > 0 ? s.gpuMhz + "MHz" : "");
        if (gpu.isEmpty()) gpu = s.root ? "N/A" : "no root";
        String ram = s.ramPct < 0 ? "--" : s.ramPct + "% " + String.format(Locale.US, "%.1f/%.1fG", s.ramUsedMb / 1024f, s.ramTotalMb / 1024f);
        String bat = (s.batPct >= 0 ? s.batPct + "% " : "") + (s.batMa != Integer.MIN_VALUE ? s.batMa + "mA " : "")
                + (s.batTemp > 0 ? String.format(Locale.US, "%.0f°", s.batTemp) : "");
        String fpsT = fps >= 0 ? fps + " fps" : (s.root ? "-- " + fpsProbe.status() : "no root");
        String[] txt = {fpsT, cpu, tempT, gpu, ram, bat.trim().isEmpty() ? "--" : bat.trim()};

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
            if (spark != null) spark.push(s.cpuLoad, tShown > 0 ? tShown : -1f, fps);
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
