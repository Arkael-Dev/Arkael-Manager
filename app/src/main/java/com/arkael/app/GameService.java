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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * Watches which app is on screen. When it is one of your games: applies its profile, shows a side
 * handle that opens the game panel, and starts the stats overlay. When you leave: puts everything back.
 */
public class GameService extends Service {
    static final String ACTION_STOP = "com.arkael.app.GAME_STOP";
    private static final String CHANNEL = "arkael_game";
    private static final int NOTIF_ID = 4712;
    private static final float HEAT_LIMIT_C = 78f;

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread worker;
    private Handler bg;
    private volatile boolean running;
    private boolean rootChecked;

    private WindowManager wm;
    private View handle;
    private LinearLayout panel;
    private TextView status;

    private volatile String active = "";
    private volatile boolean startedOverlay, thermalActive;
    private int tick;
    private final SysProbe probe = new SysProbe();

    static void start(Context c) { ContextCompat.startForegroundService(c, new Intent(c, GameService.class)); }
    static void stop(Context c) { c.stopService(new Intent(c, GameService.class)); }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            GameProfiles.setAuto(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!GameProfiles.auto(this)) { stopSelf(); return START_NOT_STICKY; }
        if (!running) {
            running = true;
            worker = new HandlerThread("ark-game");
            worker.start();
            bg = new Handler(worker.getLooper());
            bg.post(loop);
        }
        return START_STICKY;
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try { step(); } catch (Exception ignored) {}
            if (bg != null && running) bg.postDelayed(this, 2000);
        }
    };

    private void step() {
        if (!rootChecked) {
            rootChecked = true;
            if (!RootShell.hasRoot()) {
                GameProfiles.setAuto(this, false);
                toast("Auto game mode needs root");
                running = false;
                main.post(new Runnable() { @Override public void run() { stopSelf(); } });
                return;
            }
        }
        String pkg = Foreground.pkg();
        if (!pkg.isEmpty() && !pkg.equals("com.android.systemui")) {      // the shade is not "leaving the game"
            boolean isGame = GameProfiles.isGame(this, pkg);
            if (isGame && !pkg.equals(active)) {
                if (!active.isEmpty()) leave();
                enter(pkg);
            } else if (!isGame && !active.isEmpty()) {
                leave();
            } else if (!isGame && Booster.leftover(this)) {
                Booster.revertAll(this);                                  // a session that ended badly
            }
        }
        if (!active.isEmpty() && thermalActive && (++tick % 4) == 0) checkHeat();
    }

    private void enter(String pkg) {
        active = pkg;
        GameProfiles.Profile p = GameProfiles.get(this, pkg);
        thermalActive = p.thermalRelax;
        if (GameProfiles.panel(this)) main.post(new Runnable() { @Override public void run() { addHandle(); } });
        Booster.applyProfile(this, p, false);
        if (p.overlay && !OverlayPrefs.enabled(this) && Settings.canDrawOverlays(this)) {
            try {
                OverlayService.start(this);
            } catch (Exception e) {      // Android blocks starting a foreground service from the background; root can
                RootShell.run("am start-foreground-service -n " + getPackageName() + "/com.arkael.app.OverlayService");
            }
            startedOverlay = true;
        }
        toast("Game mode on");
    }

    private void leave() {
        active = "";
        thermalActive = false;
        main.post(new Runnable() { @Override public void run() { closePanel(); removeHandle(); } });
        Booster.revertAll(this);
        if (startedOverlay) {
            stopService(new Intent(this, OverlayService.class));
            startedOverlay = false;
        }
        toast("Game mode off, settings restored");
    }

    private void checkHeat() {
        SysProbe.Snapshot s = probe.read(this);
        if (s.cpuTemp >= HEAT_LIMIT_C) {
            Booster.setThermalRelax(this, false);
            thermalActive = false;
            toast("Thermal control restored at " + Math.round(s.cpuTemp) + "°C");
        }
    }

    // ---------------------------------------------------------------- side handle + panel

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void addHandle() {
        if (handle != null || active.isEmpty() || !Settings.canDrawOverlays(this)) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        View h = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor((OverlayPrefs.color(this) & 0x00FFFFFF) | 0x99000000);
        g.setCornerRadii(new float[]{0, 0, dp(5), dp(5), dp(5), dp(5), 0, 0});
        h.setBackground(g);
        h.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
                if (e.getAction() == MotionEvent.ACTION_UP) openPanel();      // tap or swipe both open it
                return true;
            }
        });
        DisplayMetrics dm = getResources().getDisplayMetrics();
        WindowManager.LayoutParams lp = params(dp(12), dp(84), false);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;
        lp.y = (int) (dm.heightPixels * 0.30f);
        try { wm.addView(h, lp); handle = h; } catch (Exception ignored) {}
    }

    private WindowManager.LayoutParams params(int w, int h, boolean watchOutside) {
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (watchOutside) flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        return new WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT);
    }

    private void removeHandle() {
        if (wm != null && handle != null) { try { wm.removeView(handle); } catch (Exception ignored) {} }
        handle = null;
    }

    private void closePanel() {
        if (wm != null && panel != null) { try { wm.removeView(panel); } catch (Exception ignored) {} }
        panel = null;
        status = null;
    }

    private TextView button(String text, boolean primary) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(primary ? Color.parseColor("#0A0D12") : Color.WHITE);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(11), dp(10), dp(11));
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? OverlayPrefs.color(this) : Color.parseColor("#232B36"));
        g.setCornerRadius(dp(10));
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(7);
        t.setLayoutParams(lp);
        return t;
    }

    private void setToggle(TextView t, String name, boolean on) {
        t.setText(name + ":  " + (on ? "ON" : "OFF"));
        t.setTextColor(on ? OverlayPrefs.color(this) : Color.WHITE);
    }

    private void say(final String msg) {
        main.post(new Runnable() { @Override public void run() { if (status != null) status.setText(msg); } });
    }

    private void bgRun(Runnable r) { new Thread(r).start(); }

    private void openPanel() {
        if (panel != null || wm == null) return;
        final int accent = OverlayPrefs.color(this);
        final LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(12), dp(10), dp(12), dp(12));
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(Color.parseColor("#EE0A0D12"));
        bgd.setCornerRadius(dp(14));
        bgd.setStroke(dp(1), (accent & 0x00FFFFFF) | 0xAA000000);
        p.setBackground(bgd);

        TextView title = new TextView(this);
        title.setText("● ARKAEL GAME PANEL");
        title.setTextColor(accent);
        title.setTextSize(12);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        p.addView(title);

        TextView boost = button("PERCEPAT  (boost)", true);
        boost.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                say("Boosting...");
                bgRun(new Runnable() { @Override public void run() {
                    String g = Booster.perfGovernor();
                    if (!g.isEmpty()) Booster.setGovernor(GameService.this, g);
                    say("Boost done · " + Booster.cleanRam(GameService.this, false) + (g.isEmpty() ? "" : " · performance"));
                } });
            }
        });
        p.addView(boost);

        TextView clean = button("Clean RAM", false);
        clean.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                say("Cleaning...");
                bgRun(new Runnable() { @Override public void run() { say(Booster.cleanRam(GameService.this, false)); } });
            }
        });
        p.addView(clean);

        final TextView dnd = button("", false);
        setToggle(dnd, "Do not disturb", Booster.isDnd(this));
        dnd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final boolean on = !Booster.isDnd(GameService.this);
                bgRun(new Runnable() { @Override public void run() {
                    Booster.setDnd(GameService.this, on);
                    main.post(new Runnable() { @Override public void run() { setToggle(dnd, "Do not disturb", Booster.isDnd(GameService.this)); } });
                } });
            }
        });
        p.addView(dnd);

        final TextView heads = button("", false);
        setToggle(heads, "Block pop-up notifications", Booster.isHeadsUpBlocked(this));
        heads.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final boolean on = !Booster.isHeadsUpBlocked(GameService.this);
                bgRun(new Runnable() { @Override public void run() {
                    Booster.setHeadsUp(GameService.this, on);
                    main.post(new Runnable() { @Override public void run() { setToggle(heads, "Block pop-up notifications", Booster.isHeadsUpBlocked(GameService.this)); } });
                } });
            }
        });
        p.addView(heads);

        final TextView ov = button("", false);
        setToggle(ov, "Stats overlay", startedOverlay || OverlayPrefs.enabled(this));
        ov.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (startedOverlay || OverlayPrefs.enabled(GameService.this)) {
                    OverlayPrefs.setEnabled(GameService.this, false);
                    stopService(new Intent(GameService.this, OverlayService.class));
                    startedOverlay = false;
                    setToggle(ov, "Stats overlay", false);
                } else {
                    try { OverlayService.start(GameService.this); }
                    catch (Exception e) { bgRun(new Runnable() { @Override public void run() {
                        RootShell.run("am start-foreground-service -n " + getPackageName() + "/com.arkael.app.OverlayService"); } }); }
                    startedOverlay = true;
                    setToggle(ov, "Stats overlay", true);
                }
            }
        });
        p.addView(ov);

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#8B95A3"));
        status.setTextSize(11);
        status.setPadding(dp(2), dp(8), dp(2), 0);
        p.addView(status);

        TextView close = button("Close", false);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closePanel(); }
        });
        p.addView(close);

        p.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_OUTSIDE) { closePanel(); return true; }
                return false;
            }
        });

        DisplayMetrics dm = getResources().getDisplayMetrics();
        WindowManager.LayoutParams lp = params(dp(250), WindowManager.LayoutParams.WRAP_CONTENT, true);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(14);
        lp.y = Math.max(dp(8), (int) (dm.heightPixels * 0.30f) - dp(60));
        try { wm.addView(p, lp); panel = p; } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------- plumbing

    private void toast(final String msg) {
        main.post(new Runnable() { @Override public void run() { Toast.makeText(GameService.this, msg, Toast.LENGTH_SHORT).show(); } });
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Game mode", NotificationManager.IMPORTANCE_LOW));
        }
        Intent stop = new Intent(this, GameService.class).setAction(ACTION_STOP);
        int pf = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getService(this, 1, stop, pf);
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_cpu)
                .setContentTitle("Arkael Manager")
                .setContentText("Auto game mode is watching for your games")
                .setOngoing(true)
                .addAction(0, "Stop", pi)
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        else startForeground(NOTIF_ID, n);
    }

    @Override
    public void onDestroy() {
        running = false;
        if (bg != null) bg.removeCallbacksAndMessages(null);
        if (worker != null) worker.quitSafely();
        closePanel();
        removeHandle();
        if (startedOverlay) stopService(new Intent(this, OverlayService.class));
        if (!active.isEmpty()) {
            final Context app = getApplicationContext();
            new Thread(new Runnable() { @Override public void run() { Booster.revertAll(app); } }).start();
        }
        super.onDestroy();
    }
}
