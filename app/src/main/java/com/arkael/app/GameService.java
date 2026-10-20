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
    private static final String CHANNEL = "arkael_game";
    private static final int NOTIF_ID = 4712;
    private static final float HEAT_LIMIT_C = 78f;
    private static final int BG = 0xF2101419, CARD = 0xFF1B222C, TILE = 0xFF262E3A, MUTED = 0xFF8B95A3;

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread worker;
    private Handler bg;
    private volatile boolean running;
    private boolean rootChecked;

    private WindowManager wm;
    private View handle;
    private WindowManager.LayoutParams handleLp;
    private LinearLayout panel;
    private TextView status;

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
        main.post(new Runnable() { @Override public void run() { closePanel(); if (!GameProfiles.manual(GameService.this)) removeHandle(); } });
        Booster.revertAll(this);
        if (startedOverlay) {
            stopService(new Intent(this, OverlayService.class));
            startedOverlay = false;
        }
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
        g.setColor((OverlayPrefs.color(this) & 0x00FFFFFF) | 0x99000000);
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
        if (wm != null && panel != null) { try { wm.removeView(panel); } catch (Exception ignored) {} }
        panel = null;
        status = null;
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
        main.post(new Runnable() { @Override public void run() { if (status != null) status.setText(msg); } });
    }

    private void bgRun(Runnable r) { new Thread(r).start(); }

    private void openActivity(Class<?> cls) {
        closePanel();
        try { startActivity(new Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { toast("Open Arkael Manager > Game Mode"); }
    }

    private void openPanel() {
        if (panel != null || wm == null) return;
        final int accent = OverlayPrefs.color(this);
        final LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bgd = round(BG, 18);
        bgd.setStroke(dp(1), (accent & 0x00FFFFFF) | 0xAA000000);
        p.setBackground(bgd);

        // ---- header
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = txt("GAME MODE", accent, 17, true);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setLetterSpacing(0.12f);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView x = txt("✕", MUTED, 18, false);
        x.setPadding(dp(10), dp(2), dp(2), dp(2));
        x.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closePanel(); } });
        head.addView(x);
        p.addView(head);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(body);
        p.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- card 1: three switches + boost
        LinearLayout c1 = card();
        final GameProfiles.Profile prof = active.isEmpty() ? null : GameProfiles.get(this, active);
        final LinearLayout autoClean = line("⚙", "Auto clean memory", true, prof != null && prof.cleanRam, null);
        autoClean.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (prof == null) { say("Open one of your games to change its profile"); return; }
                prof.cleanRam = !prof.cleanRam;
                GameProfiles.save(GameService.this, active, prof);
                setPill(autoClean, prof.cleanRam);
            }
        });
        c1.addView(autoClean);
        c1.addView(line("🗑", "Free up storage", false, false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                say("Trimming app caches...");
                bgRun(new Runnable() { @Override public void run() { say("Caches trimmed · " + Booster.cleanRam(GameService.this, true)); } });
            }
        }));
        final LinearLayout mon = line("📈", "Monitor performance", true, startedOverlay || OverlayPrefs.enabled(this), null);
        mon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (startedOverlay || OverlayPrefs.enabled(GameService.this)) {
                    OverlayPrefs.setEnabled(GameService.this, false);
                    stopService(new Intent(GameService.this, OverlayService.class));
                    startedOverlay = false;
                    setPill(mon, false);
                } else {
                    try { OverlayService.start(GameService.this); }
                    catch (Exception e) { bgRun(new Runnable() { @Override public void run() {
                        RootShell.run("am start-foreground-service -n " + getPackageName() + "/com.arkael.app.OverlayService"); } }); }
                    startedOverlay = true;
                    setPill(mon, true);
                }
            }
        });
        c1.addView(mon);

        TextView boost = txt("BOOST  ·  Percepat", accent, 15, true);
        boost.setGravity(Gravity.CENTER);
        boost.setPadding(dp(10), dp(12), dp(10), dp(12));
        GradientDrawable bb = round(0x22000000 | (accent & 0x00FFFFFF), 12);
        bb.setStroke(dp(2), accent);
        boost.setBackground(bb);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = dp(6); bl.bottomMargin = dp(6);
        boost.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                say("Boosting...");
                bgRun(new Runnable() { @Override public void run() {
                    String g = Booster.perfGovernor();
                    if (!g.isEmpty()) Booster.setGovernor(GameService.this, g);
                    if (active.isEmpty()) Booster.markManual(GameService.this);
                    say("Boost done · " + Booster.cleanRam(GameService.this, false) + (g.isEmpty() ? "" : " · performance governor"));
                } });
            }
        });
        c1.addView(boost, bl);
        body.addView(c1);

        // ---- two shortcuts
        LinearLayout two = new LinearLayout(this);
        two.setPadding(0, dp(8), 0, 0);
        TextView games = txt("🎮  Games", Color.WHITE, 13, false);
        TextView perf = txt("⚙  Performance settings", Color.WHITE, 13, false);
        TextView[] sc = {games, perf};
        for (int i = 0; i < 2; i++) {
            sc[i].setGravity(Gravity.CENTER);
            sc[i].setPadding(dp(6), dp(12), dp(6), dp(12));
            sc[i].setBackground(round(CARD, 12));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i == 0) sl.rightMargin = dp(8);
            two.addView(sc[i], sl);
        }
        games.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openActivity(GameListActivity.class); } });
        perf.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openActivity(GameModeActivity.class); } });
        body.addView(two);

        // ---- tool grid
        LinearLayout grid = card();
        grid.setPadding(dp(4), dp(2), dp(4), dp(4));
        final LinearLayout tClean = tile("🚀", "Clean RAM", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                say("Cleaning...");
                bgRun(new Runnable() { @Override public void run() { say(Booster.cleanRam(GameService.this, false)); } });
            }
        });
        final LinearLayout tDnd = tile("🔕", "Do not disturb", Booster.isDnd(this), null);
        tDnd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final boolean on = !Booster.isDnd(GameService.this);
                bgRun(new Runnable() { @Override public void run() {
                    Booster.setDnd(GameService.this, on);
                    main.post(new Runnable() { @Override public void run() { setTile(tDnd, Booster.isDnd(GameService.this)); } });
                } });
            }
        });
        final LinearLayout tHeads = tile("💬", "Block pop-ups", Booster.isHeadsUpBlocked(this), null);
        tHeads.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final boolean on = !Booster.isHeadsUpBlocked(GameService.this);
                bgRun(new Runnable() { @Override public void run() {
                    Booster.setHeadsUp(GameService.this, on);
                    main.post(new Runnable() { @Override public void run() { setTile(tHeads, Booster.isHeadsUpBlocked(GameService.this)); } });
                } });
            }
        });
        LinearLayout tShot = tile("📸", "Screenshot", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                closePanel();                                              // keep the panel out of the picture
                bgRun(new Runnable() { @Override public void run() {
                    try { Thread.sleep(600); } catch (InterruptedException ignored) {}
                    RootShell.run("mkdir -p /sdcard/Pictures/Arkael; screencap -p /sdcard/Pictures/Arkael/shot_$(date +%Y%m%d_%H%M%S).png");
                    toast("Screenshot saved in Pictures/Arkael");
                } });
            }
        });
        grid.addView(tileRow(tClean, tDnd, tHeads, tShot));
        LinearLayout tRestore = tile("↩", "Restore settings", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                say("Restoring...");
                bgRun(new Runnable() { @Override public void run() { Booster.revertAll(GameService.this); say("Settings restored"); } });
            }
        });
        LinearLayout tReset = tile("⤢", "Reset overlay size", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                OverlayPrefs.resetLayout(GameService.this);
                OverlayService.refresh(GameService.this);
                say("Overlay position and size reset");
            }
        });
        grid.addView(tileRow(tRestore, tReset));
        LinearLayout hMinus = tile("➖", "Handle smaller", false, new View.OnClickListener() {
            @Override public void onClick(View v) { resizeHandle(-0.25f); }
        });
        LinearLayout hPlus = tile("➕", "Handle bigger", false, new View.OnClickListener() {
            @Override public void onClick(View v) { resizeHandle(0.25f); }
        });
        LinearLayout hHide = tile("🙈", "Hide handle", false, new View.OnClickListener() {
            @Override public void onClick(View v) { hideHandle(); }
        });
        LinearLayout tDiag = tile("🧾", "Diagnose FPS / temp", false, new View.OnClickListener() {
            @Override public void onClick(View v) { diagnose(); }
        });
        grid.addView(tileRow(hMinus, hPlus, hHide, tDiag));
        body.addView(grid);

        status = txt("", MUTED, 11, false);
        status.setPadding(dp(4), dp(8), dp(4), 0);
        body.addView(status);

        p.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_OUTSIDE) { closePanel(); return true; }
                return false;
            }
        });

        DisplayMetrics dm = getResources().getDisplayMetrics();
        boolean land = dm.widthPixels > dm.heightPixels;
        int w = Math.min(dp(340), (int) (dm.widthPixels * (land ? 0.55f : 0.92f)));
        WindowManager.LayoutParams lp = params(w, WindowManager.LayoutParams.WRAP_CONTENT, true);
        int edge = hp().getInt("edge", 0);
        if (edge == 1) { lp.gravity = Gravity.CENTER_VERTICAL | Gravity.END; lp.x = dp(12); }
        else if (edge == 2) { lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; lp.y = dp(24); }
        else if (edge == 3) { lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL; lp.y = dp(24); }
        else { lp.gravity = Gravity.CENTER_VERTICAL | Gravity.START; lp.x = dp(12); }
        try { wm.addView(p, lp); panel = p; } catch (Exception ignored) {}
    }

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
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_cpu)
                .setContentTitle("Arkael Manager")
                .setContentText("Game mode is ready")
                .setOngoing(true)
                .addAction(0, "Stop", pi)
                .addAction(0, "Show handle", showPi)
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
