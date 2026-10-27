package com.arkael.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * Watches which app is on screen. When it is one of your games: applies its profile, shows a side
 * handle that opens the GAME MODE panel, and starts the stats overlay. When you leave: puts everything back.
 * The handle can also be switched on by hand (GameProfiles.manual) to use the panel in any app.
 */
public class GameService extends Service {
    static final String ACTION_STOP = "com.arkael.app.GAME_STOP";
    static final String ACTION_PANEL_ON = "com.arkael.app.GAME_PANEL_ON";
    static final String ACTION_PANEL_OFF = "com.arkael.app.GAME_PANEL_OFF";
    static final String ACTION_HANDLE_SHOW = "com.arkael.app.GAME_HANDLE_SHOW";
    static final String ACTION_REC_STOP = "com.arkael.app.GAME_REC_STOP";
    private static final String CHANNEL = "arkael_game";
    private static final int NOTIF_ID = 4712;
    private static final float HEAT_LIMIT_C = 78f;
    private static final int BG = 0xF2101419, CARD = 0xFF1B222C, TILE = 0xFF262E3A, MUTED = 0xFF8B95A3;

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread worker;
    private Handler bg;
    private volatile boolean running;
    private boolean rootChecked;
    private volatile boolean recDead;
    private RecOverlay recOv;

    private WindowManager wm;
    private View handle;
    private WindowManager.LayoutParams handleLp;
    private TurboPanel turbo;

    private volatile String active = "";
    private volatile boolean startedOverlay, thermalActive;
    private int tick;
    private final SysProbe probe = new SysProbe();
    private final TempProbe temp = new TempProbe();

    static void start(Context c) { ContextCompat.startForegroundService(c, new Intent(c, GameService.class)); }
    static void stop(Context c) { c.stopService(new Intent(c, GameService.class)); }
    static void panelOn(Context c) {
        GameProfiles.setManual(c, true);
        c.getSharedPreferences("arkael_handle", Context.MODE_PRIVATE).edit().putBoolean("hidden", false).apply();
        ContextCompat.startForegroundService(c, new Intent(c, GameService.class).setAction(ACTION_PANEL_ON));
    }
    static void panelOff(Context c) {
        GameProfiles.setManual(c, false);
        if (GameProfiles.auto(c)) ContextCompat.startForegroundService(c, new Intent(c, GameService.class).setAction(ACTION_PANEL_OFF));
        else c.stopService(new Intent(c, GameService.class));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        String act = intent != null ? intent.getAction() : null;
        if (ACTION_REC_STOP.equals(act)) Recorder.stopAsync();
        if (ACTION_STOP.equals(act)) {
            GameProfiles.setAuto(this, false);
            GameProfiles.setManual(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_HANDLE_SHOW.equals(act)) {
            hp().edit().putBoolean("hidden", false).apply();
            main.post(new Runnable() { @Override public void run() { removeHandle(); addHandle(); } });
        }
        if (ACTION_PANEL_OFF.equals(act)) {
            main.post(new Runnable() { @Override public void run() { closePanel(); if (active.isEmpty()) removeHandle(); } });
        }
        boolean auto = GameProfiles.auto(this), manual = GameProfiles.manual(this);
        if (!auto && !manual) { stopSelf(); return START_NOT_STICKY; }
        if (manual) main.post(new Runnable() { @Override public void run() { addHandle(); } });
        if (auto && !running) {
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
                if (!GameProfiles.manual(this)) main.post(new Runnable() { @Override public void run() { stopSelf(); } });
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
            } else if (!isGame && RefreshLock.pending(this)) {
                RefreshLock.restore(this);
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
        if (AppDownscale.get(this, pkg) != 100) AppDownscale.applyFor(this, pkg);
        RefreshLock.apply(this, pkg);
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
        main.post(new Runnable() { @Override public void run() { closePanel(); if (!GameProfiles.manual(GameService.this)) removeHandle(); } });
        Booster.revertAll(this);
        if (startedOverlay) {
            stopService(new Intent(this, OverlayService.class));
            startedOverlay = false;
        }
        RefreshLock.restore(this);
        toast("Game mode off, settings restored");
    }

    private void checkHeat() {
        float t = temp.read(true, TempProbe.CPU_MAX, BatteryInfo.temp(this));
        if (t <= 0f) t = (float) probe.read(this).cpuTemp;
        if (t >= HEAT_LIMIT_C) {
            Booster.setThermalRelax(this, false);
            thermalActive = false;
            toast("Thermal control restored at " + Math.round(t) + "°C");
        }
    }

    // ---------------------------------------------------------------- side handle

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // The handle: drag it anywhere, it snaps to the nearest edge (left, right, top or bottom).
    // Resize it and hide it from the panel; "Show handle" in the notification brings it back.

    private SharedPreferences hp() { return getSharedPreferences("arkael_handle", MODE_PRIVATE); }

    private void addHandle() {
        if (handle != null || hp().getBoolean("hidden", false) || !Settings.canDrawOverlays(this)) return;
        if (Recorder.isRecording() && (Recorder.hideHandle(this) || Recorder.clean(this))) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        final int edge = hp().getInt("edge", 0);               // 0 left, 1 right, 2 top, 3 bottom
        float pos = hp().getFloat("pos", 0.30f);               // 0..1 along that edge
        float sz = hp().getFloat("size", 1f);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int len = dp(90 * sz), th = dp(14 * sz);
        boolean vertical = edge < 2;
        int w = vertical ? th : len, h = vertical ? len : th;
        float r = dp(6);
        float[] radii;
        if (edge == 0) radii = new float[]{0, 0, r, r, r, r, 0, 0};
        else if (edge == 1) radii = new float[]{r, r, 0, 0, 0, 0, r, r};
        else if (edge == 2) radii = new float[]{0, 0, 0, 0, r, r, r, r};
        else radii = new float[]{r, r, r, r, 0, 0, 0, 0};
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(((Recorder.isRecording() ? 0xFFFF3B30 : OverlayPrefs.color(this)) & 0x00FFFFFF) | 0x99000000);
        g.setCornerRadii(radii);
        v.setBackground(g);

        final WindowManager.LayoutParams lp = params(w, h, false);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (edge == 0) { lp.x = 0; lp.y = (int) ((dm.heightPixels - h) * pos); }
        else if (edge == 1) { lp.x = dm.widthPixels - w; lp.y = (int) ((dm.heightPixels - h) * pos); }
        else if (edge == 2) { lp.y = 0; lp.x = (int) ((dm.widthPixels - w) * pos); }
        else { lp.y = dm.heightPixels - h; lp.x = (int) ((dm.widthPixels - w) * pos); }
        final int hw = w, hh = h;
        v.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY; private int sx, sy; private boolean moved;
            @Override public boolean onTouch(View view, MotionEvent e) {
                DisplayMetrics m = getResources().getDisplayMetrics();
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY(); sx = lp.x; sy = lp.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!moved && Math.hypot(dx, dy) < dp(8)) return true;
                        moved = true;
                        lp.x = Math.max(0, Math.min(m.widthPixels - hw, sx + (int) dx));
                        lp.y = Math.max(0, Math.min(m.heightPixels - hh, sy + (int) dy));
                        try { wm.updateViewLayout(handle, lp); } catch (Exception ignored) {}
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved) { openPanel(); return true; }
                        snap(lp.x + hw / 2f, lp.y + hh / 2f, hw, hh);
                        return true;
                }
                return true;
            }
        });
        try { wm.addView(v, lp); handle = v; handleLp = lp; } catch (Exception ignored) {}
    }

    /** Remember the nearest edge and where along it, then draw the handle again in the right orientation. */
    private void snap(float cx, float cy, int w, int h) {
        DisplayMetrics m = getResources().getDisplayMetrics();
        float dl = cx, dr = m.widthPixels - cx, dt = cy, db = m.heightPixels - cy;
        int edge = 0; float best = dl;
        if (dr < best) { best = dr; edge = 1; }
        if (dt < best) { best = dt; edge = 2; }
        if (db < best) { edge = 3; }
        boolean vertical = edge < 2;
        float pos = vertical ? (cy - h / 2f) / Math.max(1f, m.heightPixels - h) : (cx - w / 2f) / Math.max(1f, m.widthPixels - w);
        hp().edit().putInt("edge", edge).putFloat("pos", Math.max(0f, Math.min(1f, pos))).apply();
        main.post(new Runnable() { @Override public void run() { removeHandle(); addHandle(); } });
    }

    private void resizeHandle(float delta) {
        float sz = Math.max(0.5f, Math.min(3f, hp().getFloat("size", 1f) + delta));
        hp().edit().putFloat("size", sz).apply();
        removeHandle(); addHandle();
        say("Handle size " + Math.round(sz * 100) + "%");
    }

    private void hideHandle() {
        hp().edit().putBoolean("hidden", true).apply();
        closePanel();
        removeHandle();
        toast("Handle hidden. Tap \"Show handle\" in the Arkael notification to bring it back");
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
        handleLp = null;
    }

    private void closePanel() {
        if (turbo != null) { turbo.dismiss(); turbo = null; }
    }

    // ---------------------------------------------------------------- GAME MODE panel (GameTurbo style)

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView txt(String s, int color, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(10), dp(6), dp(10), dp(6));
        c.setBackground(round(CARD, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    /** A line like GameTurbo's: icon, title, and on the right either ON/OFF or nothing. */
    private LinearLayout line(String glyph, String title, boolean pill, boolean on, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(4), dp(10), dp(4), dp(10));
        TextView g = txt(glyph, Color.WHITE, 18, false);
        g.setGravity(Gravity.CENTER);
        r.addView(g, new LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView t = txt(title, 0xFFD5DAE1, 14, false);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (pill) {
            TextView p = txt("", Color.WHITE, 11, true);
            p.setPadding(dp(9), dp(3), dp(9), dp(3));
            r.addView(p);
            setPill(r, on);
        }
        r.setOnClickListener(l);
        return r;
    }

    private void setPill(LinearLayout row, boolean on) {
        TextView p = (TextView) row.getChildAt(row.getChildCount() - 1);
        p.setText(on ? "ON" : "OFF");
        p.setTextColor(on ? 0xFF0A0D12 : MUTED);
        p.setBackground(round(on ? OverlayPrefs.color(this) : TILE, 10));
    }

    /** Round icon with a label below, 4 per row. */
    private LinearLayout tile(String glyph, String label, boolean on, View.OnClickListener l) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(0, dp(8), 0, dp(6));
        TextView c = txt(glyph, on ? 0xFF0A0D12 : Color.WHITE, 20, false);
        c.setGravity(Gravity.CENTER);
        c.setBackground(round(on ? OverlayPrefs.color(this) : TILE, 24));
        t.addView(c, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView n = txt(label, MUTED, 11, false);
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        n.setPadding(0, dp(4), 0, 0);
        t.addView(n, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        t.setOnClickListener(l);
        t.setTag(new TextView[]{c, n});
        return t;
    }

    private void setTile(LinearLayout t, boolean on) {
        TextView c = ((TextView[]) t.getTag())[0];
        c.setTextColor(on ? 0xFF0A0D12 : Color.WHITE);
        c.setBackground(round(on ? OverlayPrefs.color(this) : TILE, 24));
    }

    private LinearLayout tileRow(LinearLayout... tiles) {
        LinearLayout r = new LinearLayout(this);
        for (int i = 0; i < 4; i++) {
            View v;
            if (i < tiles.length) v = tiles[i];
            else v = new View(this);
            r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return r;
    }

    private void say(final String msg) {
        main.post(new Runnable() { @Override public void run() { if (turbo != null) turbo.say(msg); } });
    }

    private void bgRun(Runnable r) { new Thread(r).start(); }

    // ---- screen recording
    private Runnable recDone() {
        return new Runnable() { @Override public void run() {
            Recorder.stopKeyWatch();
            hideRecBar();
            if (recDead) return;
            String e = Recorder.lastError();
            toast(e != null ? e : "Recording saved in Movies/Arkael");
            refreshRecUi();
        } };
    }

    private void refreshRecUi() {
        main.post(new Runnable() { @Override public void run() {
            if (recDead) return;
            startAsForeground();
            removeHandle(); addHandle();
        } });
    }

    private void toggleRecord() {
        if (Recorder.isRecording()) {
            closePanel();
            toast("Stopping recording...");
            Recorder.stopAsync();
            return;
        }
        if (recOv != null && recOv.counting()) return;
        closePanel();                                                  // keep the panel out of the video
        if (Recorder.hideHandle(this) || Recorder.clean(this)) removeHandle();
        if (recOv == null) recOv = new RecOverlay(this, main, new Runnable() { @Override public void run() {
            toast("Stopping recording...");
            Recorder.stopAsync();
        } });
        recOv.countdown(3, new Runnable() { @Override public void run() {
            Recorder.start(GameService.this, recDone());
            if (Recorder.clean(GameService.this)) Recorder.watchStopKeys(new Runnable() { @Override public void run() {
                toast("Stopping recording...");
                Recorder.stopAsync();
            } });
            main.post(new Runnable() { @Override public void run() { showRecBar(); } });
            refreshRecUi();
        } }, new Runnable() { @Override public void run() {
            toast("Recording cancelled");
            refreshRecUi();
        } });
    }

    private void showRecBar() {
        if (recOv != null && Recorder.isRecording() && Recorder.showBar(this) && !Recorder.clean(this)) recOv.showBar();
    }

    private void hideRecBar() {
        main.post(new Runnable() { @Override public void run() { if (recOv != null) recOv.hideBar(); } });
    }

    private void openActivity(Class<?> cls) {
        closePanel();
        try { startActivity(new Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { toast("Open Arkael Manager > Game Mode"); }
    }

    private void openPanel() {
        if (turbo != null || wm == null) return;
        turbo = new TurboPanel(this, wm, main, turboHost);
        turbo.show();
        if (!turbo.showing()) turbo = null;
    }

    private final TurboPanel.Host turboHost = new TurboPanel.Host() {
        @Override public String activePkg() { return active; }
        @Override public boolean monitorOn() { return startedOverlay || OverlayPrefs.enabled(GameService.this); }
        @Override public boolean toggleMonitor() {
            if (startedOverlay || OverlayPrefs.enabled(GameService.this)) {
                OverlayPrefs.setEnabled(GameService.this, false);
                stopService(new Intent(GameService.this, OverlayService.class));
                startedOverlay = false;
                return false;
            }
            try { OverlayService.start(GameService.this); }
            catch (Exception e) { bgRun(new Runnable() { @Override public void run() {
                RootShell.run("am start-foreground-service -n " + getPackageName() + "/com.arkael.app.OverlayService"); } }); }
            startedOverlay = true;
            return true;
        }
        @Override public void toggleRecord() { GameService.this.toggleRecord(); }
        @Override public void screenshot() {
            closePanel();                                              // keep the panel out of the picture
            bgRun(new Runnable() { @Override public void run() {
                try { Thread.sleep(600); } catch (InterruptedException ignored) {}
                RootShell.run("mkdir -p /sdcard/Pictures/Arkael; screencap -p /sdcard/Pictures/Arkael/shot_$(date +%Y%m%d_%H%M%S).png");
                toast("Screenshot saved in Pictures/Arkael");
            } });
        }
        @Override public void openActivity(Class<?> cls) { GameService.this.openActivity(cls); }
        @Override public void launchApp(final String pkg) {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) { toast("App not found"); return; }
            closePanel();
            try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception e) {
                if (!pkg.matches("[A-Za-z0-9_.]+")) return;
                bgRun(new Runnable() { @Override public void run() {
                    RootShell.run("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1"); } });
            }
        }
        @Override public void resizeHandle(float delta) { GameService.this.resizeHandle(delta); }
        @Override public void hideHandle() { GameService.this.hideHandle(); }
        @Override public void diagnose() { GameService.this.diagnose(); }
        @Override public void markManual() { if (active.isEmpty()) Booster.markManual(GameService.this); }
        @Override public void bg(Runnable r) { bgRun(r); }
        @Override public void close() { closePanel(); }
    };

    /** One tap while the game is on screen: writes the raw sensor and frame data to Download/arkael_diag_ingame.txt. */
    private void diagnose() {
        final String p = Foreground.pkg();
        say("Collecting data for ~6 s, keep the game on screen...");
        bgRun(new Runnable() { @Override public void run() {
            String grep = p.matches("[A-Za-z0-9_.]+") && !p.isEmpty() ? "SurfaceView|" + p : "SurfaceView";
            RootShell.run(
                "dumpsys SurfaceFlinger --timestats -enable >/dev/null 2>&1\n" +
                "sleep 2\n" +
                "{\n" +
                "echo '### FOREGROUND'; echo '" + (p.matches("[A-Za-z0-9_.]*") ? p : "?") + "'; dumpsys activity activities | grep -m1 topResumedActivity\n" +
                "echo '### THERMAL'; for z in /sys/class/thermal/thermal_zone*; do echo \"$(basename $z)|$(cat $z/type 2>/dev/null)|$(cat $z/temp 2>/dev/null)\"; done\n" +
                "echo '### BATTERY'; cat /sys/class/power_supply/battery/temp; dumpsys battery | grep -E 'level|temperature'\n" +
                "echo '### LIST'; dumpsys SurfaceFlinger --list | grep -i -E '" + grep + "'\n" +
                "echo '### TS1'; dumpsys SurfaceFlinger --timestats -dump | grep -E 'layerName|totalFrames'\n" +
                "sleep 3\n" +
                "echo '### TS2'; dumpsys SurfaceFlinger --timestats -dump | grep -E 'layerName|totalFrames'\n" +
                "echo '### FOREGROUND AFTER'; dumpsys activity activities | grep -m1 topResumedActivity\n" +
                "} > /sdcard/Download/arkael_diag_ingame.txt 2>&1");
            say("Saved: Download/arkael_diag_ingame.txt");
        } });
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
        PendingIntent showPi = PendingIntent.getService(this, 2, new Intent(this, GameService.class).setAction(ACTION_HANDLE_SHOW), pf);
        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_cpu)
                .setContentTitle("Arkael Manager")
                .setContentText("Game mode is ready")
                .setOngoing(true)
                .addAction(0, "Stop", pi)
                .addAction(0, "Show handle", showPi);
        if (Recorder.isRecording()) {
            PendingIntent recPi = PendingIntent.getService(this, 3, new Intent(this, GameService.class).setAction(ACTION_REC_STOP), pf);
            nb.setContentText("Recording screen...").addAction(0, "Stop recording", recPi);
        }
        Notification n = nb.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        else startForeground(NOTIF_ID, n);
    }

    @Override
    public void onDestroy() {
        running = false;
        if (bg != null) bg.removeCallbacksAndMessages(null);
        if (worker != null) worker.quitSafely();
        recDead = true;
        Recorder.stopAsync();
        Recorder.stopKeyWatch();
        if (recOv != null) { recOv.cancelCountdown(); recOv.hideBar(); }
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
