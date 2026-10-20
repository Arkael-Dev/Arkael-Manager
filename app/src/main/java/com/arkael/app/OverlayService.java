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
import android.os.SystemClock;
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
 * Live-stats overlay made of animated rings (FPS, CPU, TEMP, GPU, RAM, BAT).
 *  - drag with one finger to move it
 *  - drag the pill on the right edge to make it wider or narrower (rings spread out, height stays the same)
 *  - pinch, or drag the corner grip, to change the size of everything
 *  - double-tap switches between the full card and the compact ring bar
 */
public class OverlayService extends Service {
    static final String ACTION_STOP = "com.arkael.app.OVERLAY_STOP";
    static final String ACTION_REFRESH = "com.arkael.app.OVERLAY_REFRESH";
    private static final String CHANNEL = "arkael_overlay";
    private static final int NOTIF_ID = 4711;
    private static final int N = 6;

    private WindowManager wm;
    private FrameLayout root;
    private LinearLayout content;
    private WindowManager.LayoutParams lp;
    private final RingView[] rings = new RingView[N];
    private final TextView[] subs = new TextView[N];
    private final List<LinearLayout> cells = new ArrayList<LinearLayout>();
    private final List<TextView> texts = new ArrayList<TextView>();
    private SparkView spark;
    private TextView titleDot;
    private boolean compact;
    private int accent;
    private float scale = 1f;
    private int minW;

    private final FpsProbe fpsProbe = new FpsProbe();
    private final TempProbe tempProbe = new TempProbe();
    private final int[] bits = {OverlayPrefs.M_FPS, OverlayPrefs.M_CPU, OverlayPrefs.M_TEMP, OverlayPrefs.M_GPU, OverlayPrefs.M_RAM, OverlayPrefs.M_BAT};
    private final String[] labels = {"FPS", "CPU", "TEMP", "GPU", "RAM", "BAT"};
    private final int[] ringColors = {0, 0xFF00E5FF, 0xFFFF6B35, 0xFF00E676, 0xFFB388FF, 0xFFFFC107};   // [0] = user accent

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
                final float temp = (m & (OverlayPrefs.M_TEMP | OverlayPrefs.M_GRAPH)) != 0
                        ? tempProbe.read(s.root, OverlayPrefs.tempSrc(OverlayService.this), BatteryInfo.temp(OverlayService.this)) : -1f;
                main.post(new Runnable() { @Override public void run() { render(s, fps, temp); } });
            } catch (Exception ignored) {}
            if (bg != null && running) bg.postDelayed(this, 1000);
        }
    };

    /** Light animation clock (about 15 frames per second): moves the light streak on each ring and the glow. */
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (root == null) return;
            float ph = (SystemClock.uptimeMillis() % 2600L) / 2600f;
            for (RingView r : rings) if (r != null) r.setPhase(ph);
            if (titleDot != null) titleDot.setAlpha(0.55f + 0.45f * (float) Math.sin(ph * 2.0 * Math.PI));
            main.postDelayed(this, 66);
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
        t.setSingleLine(false);
        t.setMaxLines(2);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        texts.add(t);
        return t;
    }

    /** Applies the current scale to the views already on screen (a pinch never rebuilds the overlay). */
    private void applyScale() {
        if (root == null) return;
        int ringPx = dp((compact ? 34 : 46) * scale);
        int cellPx = dp((compact ? 40 : 54) * scale);
        for (RingView r : rings) if (r != null) r.setSizePx(ringPx);
        for (LinearLayout c : cells) {
            LinearLayout.LayoutParams cl = (LinearLayout.LayoutParams) c.getLayoutParams();
            cl.width = cellPx;
            c.setLayoutParams(cl);
        }
        for (TextView t : texts) t.setTextSize(TypedValue.COMPLEX_UNIT_SP, ((Float) t.getTag()).floatValue() * scale);
        int padL = dp(10 * scale), padR = dp(16 * scale), padV = dp(7 * scale);
        content.setPadding(padL, padV, padR, padV);
        minW = Math.max(dp(90), cells.size() * cellPx + padL + padR);
        if (spark != null) spark.setSizePx(Math.max(dp(80), minW - padL - padR), dp(34 * scale));
        int explicit = OverlayPrefs.widthPx(this);
        lp.width = explicit > 0 ? Math.max(explicit, minW) : WindowManager.LayoutParams.WRAP_CONTENT;
        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
    }

    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(6));
        return g;
    }

    private void buildOverlay() {
        removeOverlay();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        accent = OverlayPrefs.color(this);
        ringColors[0] = accent;
        int alpha = Math.max(30, Math.min(100, OverlayPrefs.alpha(this)));
        int mask = OverlayPrefs.mask(this);
        compact = OverlayPrefs.compact(this);
        final boolean anim = OverlayPrefs.anim(this);
        scale = OverlayPrefs.scale(this);
        texts.clear();
        cells.clear();
        titleDot = null;
        spark = null;
        for (int i = 0; i < N; i++) { rings[i] = null; subs[i] = null; }

        root = new FrameLayout(this);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(Color.argb(alpha * 255 / 100, 10, 13, 18));
        bgd.setCornerRadius(dp(14));
        bgd.setStroke(dp(1), (accent & 0x00FFFFFF) | 0xAA000000);
        root.setBackground(bgd);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!compact) {
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            titleDot = text("●", accent, 8f);
            TextView title = text(" ARKAEL", accent, 9f);
            title.setLetterSpacing(0.12f);
            head.addView(titleDot);
            head.addView(title);
            content.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(compact ? 0 : 4), 0, 0);
        for (int i = 0; i < N; i++) {
            if ((mask & bits[i]) == 0) continue;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            RingView r = new RingView(this, dp(46));
            r.setAnimated(anim);
            TextView lab = text(labels[i], ringColors[i], compact ? 7.5f : 8.5f);
            cell.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.addView(lab, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (!compact) {
                TextView sub = text("", 0xFF8B95A3, 7.5f);
                cell.addView(sub, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                subs[i] = sub;
            }
            rings[i] = r;
            cells.add(cell);
            row.addView(cell, new LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!compact && (mask & OverlayPrefs.M_GRAPH) != 0) {
            spark = new SparkView(this, accent, dp(120), dp(34));
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gp.topMargin = dp(6);
            content.addView(spark, gp);
        }

        // resize grip (everything gets bigger/smaller), bottom-right corner
        final TextView grip = new TextView(this);
        grip.setText("◢");
        grip.setTextColor((accent & 0x00FFFFFF) | 0xCC000000);
        grip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        grip.setGravity(Gravity.BOTTOM | Gravity.END);
        root.addView(grip, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.BOTTOM | Gravity.END));
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

        // width handle: a pill on the right edge. Drag sideways to widen; height stays the same.
        FrameLayout handle = new FrameLayout(this);
        View bar = new View(this);
        bar.setBackground(pill((accent & 0x00FFFFFF) | 0x99000000));
        handle.addView(bar, new FrameLayout.LayoutParams(dp(4), dp(26), Gravity.CENTER));
        root.addView(handle, new FrameLayout.LayoutParams(dp(16), dp(52), Gravity.END | Gravity.CENTER_VERTICAL));
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float sx; private int w0;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); w0 = Math.max(1, root.getWidth());
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        DisplayMetrics dm = getResources().getDisplayMetrics();
                        int nw = w0 + (int) (e.getRawX() - sx);
                        lp.width = Math.max(minW, Math.min(dm.widthPixels - lp.x, nw));
                        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (lp.width > 0) OverlayPrefs.setWidthPx(OverlayService.this, lp.width);
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
        main.removeCallbacks(tick);
        if (anim) main.post(tick);
    }

    private void removeOverlay() {
        main.removeCallbacks(tick);
        if (wm != null && root != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
        }
        root = null;
    }

    private void setRing(int i, float pct, String center, String sub, boolean heat, boolean inverted) {
        if (rings[i] != null) rings[i].set(pct, center, ringColors[i], heat, inverted);
        if (subs[i] != null) subs[i].setText(sub);
    }

    private void render(SysProbe.Snapshot s, int fps, float temp) {
        if (root == null) return;
        float tShown = temp > 0f ? temp : (float) s.cpuTemp;
        float bt = BatteryInfo.temp(this);
        if (bt <= 0f && s.batTemp > 0) bt = (float) s.batTemp;

        // FPS ring: full ring = 60 fps (or 120 / 240 on high-refresh games)
        int target = fps > 125 ? 240 : (fps > 66 ? 120 : 60);
        if (fps >= 0) setRing(0, fps * 100f / target, String.valueOf(fps), fpsProbe.mark().isEmpty() ? "" : "approx", true, true);
        else setRing(0, -1f, "--", s.root ? fpsProbe.status() : "no root", false, false);

        setRing(1, s.cpuLoad, s.cpuLoad >= 0 ? s.cpuLoad + "%" : "--",
                s.cpuMaxMhz > 0 ? String.format(Locale.US, "%.2fGHz", s.cpuMaxMhz / 1000f) : (s.root ? "" : "no root"), true, false);
        setRing(2, tShown > 0 ? tShown : -1f, tShown > 0 ? String.format(Locale.US, "%.0f°", tShown) : "--",
                tShown > 0 ? String.format(Locale.US, "%.1f°C", tShown) : "", true, false);
        setRing(3, s.gpuLoad, s.gpuLoad >= 0 ? s.gpuLoad + "%" : "--",
                s.gpuMhz > 0 ? s.gpuMhz + "MHz" : (s.root ? "N/A" : "no root"), true, false);
        setRing(4, s.ramPct, s.ramPct >= 0 ? s.ramPct + "%" : "--",
                s.ramPct >= 0 ? String.format(Locale.US, "%.1f/%.0fG", s.ramUsedMb / 1024f, s.ramTotalMb / 1024f) : "", true, false);
        String batSub = (s.batMa != Integer.MIN_VALUE ? s.batMa + "mA" : "")
                + (bt > 0 ? (s.batMa != Integer.MIN_VALUE ? "\n" : "") + String.format(Locale.US, "%.1f°C", bt) : "");
        setRing(5, s.batPct, s.batPct >= 0 ? s.batPct + "%" : "--", batSub, true, true);

        if (spark != null) spark.push(s.cpuLoad, tShown > 0 ? tShown : -1f, fps);
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
