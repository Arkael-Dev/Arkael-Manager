package com.arkael.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Game Turbo style panel (glass look): app dock on the side, CPU/GPU rings, Saver/Balanced/Performance/Turbo
 * switch, a grid of tiles and a Tools tab. The panel can be dragged by its top grip, resized with the corner
 * grip (or the Tools tab), and the dock holds any apps you add with "+" (long-press an icon to remove it).
 * It owns its own overlay window; GameService only provides the actions through {@link Host}.
 */
final class TurboPanel {

    interface Host {
        String activePkg();
        boolean monitorOn();
        boolean toggleMonitor();                 // returns the new state
        void toggleRecord();
        void screenshot();
        void openActivity(Class<?> cls);
        void launchApp(String pkg);
        void resizeHandle(float delta);
        void hideHandle();
        void diagnose();
        void markManual();
        void bg(Runnable r);
        void close();
    }

    private static final float PANEL_W = 330f, DOCK_W = 58f, GAP = 8f;
    private static final float SCALE_MIN = 0.7f, SCALE_MAX = 1.6f;
    private static final int MUTED = 0xFFB4BCC8, DARK = 0xFF0A0D12, TILE_BG = 0x2AFFFFFF;

    private final Context ctx;
    private final WindowManager wm;
    private final Handler main;
    private final Host host;
    private final SharedPreferences sp;
    private final SysProbe probe = new SysProbe();

    private LinearLayout root, card, dockView, body;
    private ScrollView sv;
    private WindowManager.LayoutParams lp;
    private TextView status, clock;
    private LinearLayout tabPanel, tabTools;
    private RingView cpuRing, gpuRing;
    private TextView cpuMain, gpuMain;
    private final TextView[] modeBtns = new TextView[4];
    private ValueAnimator phaseAnim;

    private boolean land, alive, pickerOpen;
    private volatile boolean reading;
    private int tab;
    private float scale = 1f;
    private int accent;

    TurboPanel(Context c, WindowManager wm, Handler main, Host host) {
        this.ctx = c;
        this.wm = wm;
        this.main = main;
        this.host = host;
        this.sp = c.getSharedPreferences("arkael_turbo", Context.MODE_PRIVATE);
    }

    boolean showing() { return root != null; }

    // ------------------------------------------------------------------ helpers

    private boolean indo() {
        String l = Locale.getDefault().getLanguage();
        return "in".equals(l) || "id".equals(l);
    }

    private String tr(String en, String id) { return indo() ? id : en; }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v * scale, ctx.getResources().getDisplayMetrics());
    }

    private float sz(float sp) { return sp * scale; }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable glass(float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xBE2E3442, 0xB4161A23});
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(Math.max(1, dp(1)), 0x55FFFFFF);
        return g;
    }

    private TextView txt(String s, int color, float sp, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sz(sp));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lpw(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    void say(final String msg) {
        main.post(new Runnable() { @Override public void run() { if (status != null) status.setText(msg); } });
    }

    // ------------------------------------------------------------------ window

    void show() {
        if (root != null) return;
        accent = OverlayPrefs.color(ctx);
        alive = true;
        build();
        place();
        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            root = null; alive = false; return;
        }
        startTicks();
    }

    void dismiss() {
        alive = false;
        main.removeCallbacks(tick);
        if (phaseAnim != null) { phaseAnim.cancel(); phaseAnim = null; }
        if (wm != null && root != null) { try { wm.removeView(root); } catch (Exception ignored) {} }
        root = null; card = null; dockView = null; body = null; sv = null;
        status = null; clock = null; cpuRing = null; gpuRing = null; cpuMain = null; gpuMain = null;
    }

    private void rebuild() {
        dismiss();
        show();
    }

    private WindowManager.LayoutParams makeParams() {
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        return new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                type, flags, PixelFormat.TRANSLUCENT);
    }

    private void place() {
        lp = makeParams();
        if (Build.VERSION.SDK_INT >= 31) {          // frosted glass where the device allows window blur
            try {
                lp.flags |= 0x00000004;
                WindowManager.LayoutParams.class.getMethod("setBlurBehindRadius", int.class).invoke(lp, dp(26));
            } catch (Throwable ignored) {}
        }
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        if (sp.contains("px") && sp.contains("py")) {
            lp.gravity = Gravity.TOP | Gravity.START;
            int w = root.getMeasuredWidth(), h = root.getMeasuredHeight();
            lp.x = Math.max(0, Math.min(Math.max(0, dm.widthPixels - w), sp.getInt("px", 0)));
            lp.y = Math.max(0, Math.min(Math.max(0, dm.heightPixels - h), sp.getInt("py", 0)));
            return;
        }
        int edge = ctx.getSharedPreferences("arkael_handle", Context.MODE_PRIVATE).getInt("edge", 0);
        if (edge == 1) { lp.gravity = Gravity.CENTER_VERTICAL | Gravity.END; lp.x = dp(12); }
        else if (edge == 2) { lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; lp.y = dp(24); }
        else if (edge == 3) { lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL; lp.y = dp(24); }
        else { lp.gravity = Gravity.CENTER_VERTICAL | Gravity.START; lp.x = dp(12); }
    }

    // ------------------------------------------------------------------ build

    private void build() {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        land = dm.widthPixels > dm.heightPixels;
        float want = clamp(sp.getFloat("scale", 1f), SCALE_MIN, SCALE_MAX);
        float baseW = land ? (PANEL_W + DOCK_W + GAP) : PANEL_W;
        float fit = dm.widthPixels * 0.96f / (baseW * dm.density);
        scale = Math.min(want, fit);

        root = new LinearLayout(ctx);
        root.setOrientation(land ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_OUTSIDE) { host.close(); return true; }
                return false;
            }
        });

        dockView = buildDock();
        root.addView(dockView, dockParams());
        card = buildCard();
        root.addView(card, new LinearLayout.LayoutParams(dp(PANEL_W), ViewGroup.LayoutParams.WRAP_CONTENT));
        renderBody();
    }

    private LinearLayout.LayoutParams dockParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (land) { p.rightMargin = dp(GAP); p.gravity = Gravity.CENTER_VERTICAL; }
        else { p.bottomMargin = dp(GAP); p.gravity = Gravity.CENTER_HORIZONTAL; }
        return p;
    }

    private LinearLayout buildCard() {
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(4), dp(14), dp(8));
        c.setBackground(glass(28));

        // drag grip
        FrameLayout gripBox = new FrameLayout(ctx);
        View pill = new View(ctx);
        pill.setBackground(round(0x99FFFFFF, 3));
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(dp(44), dp(4));
        pl.gravity = Gravity.CENTER;
        gripBox.addView(pill, pl);
        gripBox.setOnTouchListener(dragListener());
        c.addView(gripBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));

        // header: brand + clock, then tabs
        LinearLayout brand = new LinearLayout(ctx);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = txt("ARKAEL", accent, 14, true);
        name.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        name.setLetterSpacing(0.15f);
        brand.addView(name, lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clock = txt("", Color.WHITE, 12, true);
        clock.setPadding(dp(10), dp(4), dp(10), dp(4));
        clock.setBackground(round(0x33FFFFFF, 14));
        brand.addView(clock);
        c.addView(brand);

        LinearLayout tabs = new LinearLayout(ctx);
        tabPanel = tabView(tr("Panel", "Panel"), 0);
        tabTools = tabView(tr("Tools", "Alat"), 1);
        tabs.addView(tabPanel);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.leftMargin = dp(18);
        tabs.addView(tabTools, tl);
        c.addView(tabs);
        refreshTabs();
        updateClock();

        // scrolling body
        sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        sv.addView(body);
        LinearLayout.LayoutParams svl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        svl.topMargin = dp(6);
        c.addView(sv, svl);

        // footer: status + resize grip
        LinearLayout foot = new LinearLayout(ctx);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        status = txt("", MUTED, 11, false);
        foot.addView(status, lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView rs = txt("◢", 0xCCFFFFFF, 18, false);
        rs.setPadding(dp(16), dp(6), dp(2), dp(2));
        rs.setOnTouchListener(resizeListener());
        foot.addView(rs);
        c.addView(foot);
        return c;
    }

    private LinearLayout tabView(String label, final int idx) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t = txt(label, Color.WHITE, 15, true);
        t.setPadding(0, dp(6), 0, dp(3));
        View bar = new View(ctx);
        bar.setBackground(round(accent, 2));
        box.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(bar, new LinearLayout.LayoutParams(dp(26), dp(3)));
        box.setTag(new View[]{t, bar});
        box.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { tab = idx; pickerOpen = false; refreshTabs(); renderBody(); }
        });
        return box;
    }

    private void refreshTabs() {
        if (tabPanel == null || tabTools == null) return;
        LinearLayout[] tabs = {tabPanel, tabTools};
        for (int i = 0; i < 2; i++) {
            View[] v = (View[]) tabs[i].getTag();
            ((TextView) v[0]).setTextColor(tab == i ? Color.WHITE : MUTED);
            v[1].setVisibility(tab == i ? View.VISIBLE : View.INVISIBLE);
        }
    }

    // ------------------------------------------------------------------ drag + resize

    private View.OnTouchListener dragListener() {
        return new View.OnTouchListener() {
            private float dx, dy; private int sx, sy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (root == null || lp == null) return false;
                DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: {
                        int[] loc = new int[2];
                        root.getLocationOnScreen(loc);
                        lp.gravity = Gravity.TOP | Gravity.START;
                        lp.x = loc[0]; lp.y = loc[1];
                        sx = lp.x; sy = lp.y; dx = e.getRawX(); dy = e.getRawY();
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        int w = root.getWidth(), h = root.getHeight();
                        lp.x = Math.max(0, Math.min(Math.max(0, dm.widthPixels - w), sx + (int) (e.getRawX() - dx)));
                        lp.y = Math.max(0, Math.min(Math.max(0, dm.heightPixels - h), sy + (int) (e.getRawY() - dy)));
                        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {}
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sp.edit().putInt("px", lp.x).putInt("py", lp.y).apply();
                        return true;
                }
                return true;
            }
        };
    }

    private View.OnTouchListener resizeListener() {
        return new View.OnTouchListener() {
            private float x0, s0, pending;
            @Override public boolean onTouch(View v, MotionEvent e) {
                float panelPx = Math.max(1f, ctx.getResources().getDisplayMetrics().density * PANEL_W * scale);
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = e.getRawX(); s0 = clamp(sp.getFloat("scale", 1f), SCALE_MIN, SCALE_MAX); pending = s0;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        pending = clamp(s0 * (1f + (e.getRawX() - x0) / panelPx), SCALE_MIN, SCALE_MAX);
                        say(tr("Size ", "Ukuran ") + Math.round(pending * 100) + "%  (" + tr("release to apply", Tr.s("lepas untuk terapkan")) + ")");
                        return true;
                    case MotionEvent.ACTION_UP:
                        sp.edit().putFloat("scale", pending).apply();
                        main.post(new Runnable() { @Override public void run() { rebuild(); } });
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return true;
            }
        };
    }

    private void stepScale(float d) {
        float s = clamp(sp.getFloat("scale", 1f) + d, SCALE_MIN, SCALE_MAX);
        sp.edit().putFloat("scale", s).apply();
        rebuild();
    }

    private void resetPanel() {
        sp.edit().remove("scale").remove("px").remove("py").apply();
        rebuild();
    }

    // ------------------------------------------------------------------ fit to screen

    private void fit() {
        if (root == null || sv == null) return;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int maxH = (int) (dm.heightPixels * 0.94f);
        sv.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        root.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int mh = root.getMeasuredHeight();
        if (mh > maxH) {
            int svh = sv.getMeasuredHeight();
            sv.getLayoutParams().height = Math.max(dp(110), svh - (mh - maxH));
        }
        sv.requestLayout();
        if (lp != null && lp.gravity == (Gravity.TOP | Gravity.START)) {
            lp.y = Math.max(0, Math.min(lp.y, dm.heightPixels - Math.min(mh, maxH)));
        }
        if (lp != null && root.getParent() != null) { try { wm.updateViewLayout(root, lp); } catch (Exception ignored) {} }
    }

    // ------------------------------------------------------------------ body pages

    private void renderBody() {
        if (body == null) return;
        body.removeAllViews();
        modeBtns[0] = modeBtns[1] = modeBtns[2] = modeBtns[3] = null;
        cpuRing = null; gpuRing = null; cpuMain = null; gpuMain = null;
        if (pickerOpen) pagePicker();
        else if (tab == 0) pageMain();
        else pageTools();
        fit();
    }

    // ---- main page: rings, modes, tiles
    private void pageMain() {
        // rings
        LinearLayout rings = new LinearLayout(ctx);
        rings.setGravity(Gravity.CENTER_VERTICAL);
        rings.setPadding(0, dp(4), 0, dp(8));
        LinearLayout[] pair = new LinearLayout[2];
        for (int i = 0; i < 2; i++) {
            LinearLayout cell = new LinearLayout(ctx);
            cell.setGravity(Gravity.CENTER);
            RingView r = new RingView(ctx, dp(56));
            r.set(-1f, "--", accent, true, false);
            cell.addView(r);
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(8), 0, 0, 0);
            TextView lab = txt(i == 0 ? "CPU" : "GPU", accent, 11, true);
            lab.setLetterSpacing(0.12f);
            TextView big = txt("--", Color.WHITE, 20, true);
            TextView unit = txt(i == 0 ? "GHz" : "MHz", MUTED, 11, false);
            col.addView(lab); col.addView(big); col.addView(unit);
            cell.addView(col);
            if (i == 0) { cpuRing = r; cpuMain = big; } else { gpuRing = r; gpuMain = big; }
            pair[i] = cell;
        }
        rings.addView(pair[0], lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View div = new View(ctx);
        div.setBackgroundColor(0x33FFFFFF);
        rings.addView(div, new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(40)));
        rings.addView(pair[1], lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(rings);
        startPhase();

        // modes
        LinearLayout seg = new LinearLayout(ctx);
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        seg.setBackground(round(0x44000000, 22));
        String[] names = {tr("Saver", "Hemat"), tr("Balanced", "Seimbang"), tr("Perform", "Performa"), "Turbo"};
        for (int i = 0; i < 4; i++) {
            final int m = i;
            TextView b = txt(names[i], MUTED, 12, true);
            b.setGravity(Gravity.CENTER);
            b.setSingleLine(true);
            b.setPadding(dp(2), dp(9), dp(2), dp(9));
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(m); } });
            modeBtns[i] = b;
            seg.addView(b, lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        body.addView(seg);
        refreshModes(currentMode());

        // tiles
        final String on = "ON", off = "OFF";
        final LinearLayout tMon = tile(tr("FPS overlay", "Overlay FPS"), host.monitorOn() ? on : off, "⏱", host.monitorOn(), null);
        tMon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean now = host.toggleMonitor();
                updateTile(tMon, now ? on : off, now);
            }
        });
        final LinearLayout tDnd = tile(tr("Do not disturb", "Jangan ganggu"), Booster.isDnd(ctx) ? on : off, "🔕", Booster.isDnd(ctx), null);
        tDnd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final boolean want = !Booster.isDnd(ctx);
                host.bg(new Runnable() { @Override public void run() {
                    Booster.setDnd(ctx, want);
                    host.markManual();
                    final boolean now = Booster.isDnd(ctx);
                    main.post(new Runnable() { @Override public void run() { updateTile(tDnd, now ? on : off, now); } });
                } });
            }
        });
        final LinearLayout tBoost = tile("Boost", tr("Clean RAM", "Bersihkan RAM"), "🚀", false, null);
        tBoost.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                say(tr("Cleaning...", "Membersihkan..."));
                host.bg(new Runnable() { @Override public void run() {
                    final String r = Booster.cleanRam(ctx, false);
                    say(r);
                    main.post(new Runnable() { @Override public void run() { updateTile(tBoost, r, false); } });
                } });
            }
        });
        final LinearLayout tPop = tile(tr("Block pop-ups", "Blok pop-up"), Booster.isHeadsUpBlocked(ctx) ? on : off, "💬", Booster.isHeadsUpBlocked(ctx), null);
        tPop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final boolean want = !Booster.isHeadsUpBlocked(ctx);
                host.bg(new Runnable() { @Override public void run() {
                    Booster.setHeadsUp(ctx, want);
                    host.markManual();
                    final boolean now = Booster.isHeadsUpBlocked(ctx);
                    main.post(new Runnable() { @Override public void run() { updateTile(tPop, now ? on : off, now); } });
                } });
            }
        });
        LinearLayout tShot = tile("Screenshot", tr("Take picture", "Ambil gambar"), "📸", false, new View.OnClickListener() {
            @Override public void onClick(View v) { host.screenshot(); }
        });
        boolean rec = Recorder.isRecording();
        LinearLayout tRec = tile(tr("Record screen", Tr.s("Rekam layar")), rec ? "ON" : "OFF", rec ? "⏹" : "⏺", rec, new View.OnClickListener() {
            @Override public void onClick(View v) { host.toggleRecord(); }
        });
        addRows(tMon, tDnd, tBoost, tPop, tShot, tRec);
    }

    // ---- tools page
    private void pageTools() {
        final String act = host.activePkg();
        final GameProfiles.Profile prof = act.isEmpty() ? null : GameProfiles.get(ctx, act);
        final LinearLayout tAuto = tile(tr("Auto clean RAM", "Bersih RAM otomatis"), prof != null && prof.cleanRam ? "ON" : "OFF", "⚙", prof != null && prof.cleanRam, null);
        tAuto.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (prof == null) { say(tr("Open one of your games to change its profile", Tr.s("Buka salah satu game untuk ubah profilnya"))); return; }
                prof.cleanRam = !prof.cleanRam;
                GameProfiles.save(ctx, act, prof);
                updateTile(tAuto, prof.cleanRam ? "ON" : "OFF", prof.cleanRam);
            }
        });
        LinearLayout tStore = tile(tr("Free storage", "Bebaskan penyimpanan"), tr("Clear app caches", Tr.s("Hapus cache app")), "🗑", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                say(tr("Trimming app caches...", "Memangkas cache..."));
                host.bg(new Runnable() { @Override public void run() { say(tr("Caches trimmed · ", "Cache dipangkas · ") + Booster.cleanRam(ctx, true)); } });
            }
        });
        LinearLayout tGames = tile(tr("My games", "Daftar game"), tr("Choose games", Tr.s("Pilih game")), "🎮", false, new View.OnClickListener() {
            @Override public void onClick(View v) { host.openActivity(GameListActivity.class); }
        });
        LinearLayout tPerf = tile(tr("Game settings", Tr.s("Setelan game")), tr("Open Game Mode", Tr.s("Buka Game Mode")), "🛠", false, new View.OnClickListener() {
            @Override public void onClick(View v) { host.openActivity(GameModeActivity.class); }
        });
        LinearLayout tRestore = tile(tr("Restore settings", Tr.s("Pulihkan setelan")), tr("Back to normal", Tr.s("Kembali normal")), "↩", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                say(tr("Restoring...", "Memulihkan..."));
                sp.edit().remove("mode").apply();
                host.bg(new Runnable() { @Override public void run() {
                    Booster.revertAll(ctx);
                    say(tr("Settings restored", Tr.s("Setelan dipulihkan")));
                    main.post(new Runnable() { @Override public void run() { if (alive && tab == 0) renderBody(); } });
                } });
            }
        });
        LinearLayout tReset = tile(tr("FPS overlay", "Overlay FPS"), tr("Reset size/pos", "Reset ukuran/posisi"), "⤢", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                OverlayPrefs.resetLayout(ctx);
                OverlayService.refresh(ctx);
                say(tr("Overlay position and size reset", "Posisi & ukuran overlay direset"));
            }
        });
        LinearLayout tPm = tile(tr("Panel size", "Ukuran panel"), tr("Smaller", "Perkecil"), "➖", false, new View.OnClickListener() {
            @Override public void onClick(View v) { stepScale(-0.1f); }
        });
        LinearLayout tPp = tile(tr("Panel size", "Ukuran panel"), tr("Bigger", "Perbesar"), "➕", false, new View.OnClickListener() {
            @Override public void onClick(View v) { stepScale(0.1f); }
        });
        LinearLayout tPr = tile(tr("Panel size", "Ukuran panel"), tr("Reset", "Reset"), "🔄", false, new View.OnClickListener() {
            @Override public void onClick(View v) { resetPanel(); }
        });
        LinearLayout tHm = tile(tr("Edge handle", "Handle tepi"), tr("Smaller", "Perkecil"), "➖", false, new View.OnClickListener() {
            @Override public void onClick(View v) { host.resizeHandle(-0.25f); }
        });
        LinearLayout tHp = tile(tr("Edge handle", "Handle tepi"), tr("Bigger", "Perbesar"), "➕", false, new View.OnClickListener() {
            @Override public void onClick(View v) { host.resizeHandle(0.25f); }
        });
        LinearLayout tHh = tile(tr("Edge handle", "Handle tepi"), tr("Hide", Tr.s("Sembunyikan")), "🙈", false, new View.OnClickListener() {
            @Override public void onClick(View v) { host.hideHandle(); }
        });
        LinearLayout tDiag = tile(tr("Diagnose", "Diagnosa"), tr("Save FPS/temp log", Tr.s("Simpan log FPS/suhu")), "🧾", false, new View.OnClickListener() {
            @Override public void onClick(View v) { host.diagnose(); }
        });
        addRows(tAuto, tStore, tGames, tPerf, tRestore, tReset, tPm, tPp, tPr, tHm, tHp, tHh, tDiag);
    }

    // ---- tiles
    private LinearLayout tile(String title, String sub, String glyph, boolean on, View.OnClickListener l) {
        LinearLayout t = new LinearLayout(ctx);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(12), dp(9), dp(10), dp(9));
        GradientDrawable bg = round(TILE_BG, 18);
        bg.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
        t.setBackground(bg);
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView a = txt(title, Color.WHITE, 13, true);
        a.setSingleLine(true);
        a.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView b = txt(sub, MUTED, 11, false);
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(a); col.addView(b);
        t.addView(col, lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ic = txt(glyph, on ? DARK : Color.WHITE, 15, false);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(round(on ? accent : 0x33FFFFFF, 18));
        t.addView(ic, new LinearLayout.LayoutParams(dp(34), dp(34)));
        if (l != null) t.setOnClickListener(l);
        t.setTag(new TextView[]{a, b, ic});
        return t;
    }

    private void updateTile(LinearLayout t, String sub, boolean on) {
        if (t == null || t.getTag() == null) return;
        TextView[] v = (TextView[]) t.getTag();
        v[1].setText(sub);
        v[2].setTextColor(on ? DARK : Color.WHITE);
        v[2].setBackground(round(on ? accent : 0x33FFFFFF, 18));
    }

    private void addRows(LinearLayout... tiles) {
        for (int i = 0; i < tiles.length; i += 2) {
            LinearLayout r = new LinearLayout(ctx);
            LinearLayout.LayoutParams a = lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            a.rightMargin = dp(4);
            r.addView(tiles[i], a);
            LinearLayout.LayoutParams b = lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            b.leftMargin = dp(4);
            if (i + 1 < tiles.length) r.addView(tiles[i + 1], b);
            else r.addView(new View(ctx), b);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = dp(8);
            body.addView(r, rl);
        }
    }

    // ------------------------------------------------------------------ performance mode

    private int currentMode() {
        return Booster.dirty(ctx) ? sp.getInt("mode", 1) : 1;
    }

    private void refreshModes(int m) {
        for (int i = 0; i < 4; i++) {
            TextView b = modeBtns[i];
            if (b == null) continue;
            boolean sel = i == m;
            b.setTextColor(sel ? DARK : MUTED);
            b.setBackground(sel ? round(accent, 20) : null);
        }
    }

    private void setMode(final int m) {
        sp.edit().putInt("mode", m).apply();
        refreshModes(m);
        say(tr("Applying...", "Menerapkan..."));
        host.bg(new Runnable() { @Override public void run() {
            String msg = applyMode(m);
            host.markManual();
            say(msg);
        } });
    }

    /** Runs on a background thread (root). */
    private String applyMode(int m) {
        List<String> gov = Booster.governors();
        if (m == 0) {
            if (!gov.contains("powersave")) return tr("This kernel has no powersave governor", Tr.s("Kernel ini tidak punya governor powersave"));
            Booster.setGovernor(ctx, "powersave");
            return tr("Saver: powersave governor", "Hemat: governor powersave");
        }
        if (m == 1) {
            Booster.restoreGovernor(ctx);
            Booster.restoreGpu(ctx);
            return tr("Balanced: your normal CPU/GPU settings", Tr.s("Seimbang: setelan CPU/GPU normal"));
        }
        if (!gov.contains("performance")) return tr("This kernel has no performance governor", Tr.s("Kernel ini tidak punya governor performance"));
        Booster.setGovernor(ctx, "performance");
        if (m == 2) return tr("Performance: CPU at max speed", "Performa: CPU kecepatan maks");
        StringBuilder sb = new StringBuilder(tr("Turbo: CPU max", "Turbo: CPU maks"));
        Booster.GpuInfo g = Booster.gpu();
        if (g.ok() && Booster.setGpuMax(ctx, g.mhz[g.mhz.length - 1])) sb.append(" · GPU ").append(g.mhz[g.mhz.length - 1]).append(" MHz");
        sb.append(" · ").append(Booster.cleanRam(ctx, false));
        return sb.toString();
    }

    // ------------------------------------------------------------------ live stats

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!alive) return;
            updateClock();
            if (cpuRing != null && !reading) {
                reading = true;
                host.bg(new Runnable() { @Override public void run() {
                    SysProbe.Snapshot s = null;
                    try { s = probe.read(ctx); } catch (Exception ignored) {}
                    final SysProbe.Snapshot snap = s;
                    reading = false;
                    main.post(new Runnable() { @Override public void run() { if (alive) applyStats(snap); } });
                } });
            }
            main.postDelayed(this, 2000);
        }
    };

    private void startTicks() {
        main.removeCallbacks(tick);
        main.post(tick);
    }

    private void startPhase() {
        if (phaseAnim != null) phaseAnim.cancel();
        phaseAnim = ValueAnimator.ofFloat(0f, 1f);
        phaseAnim.setDuration(2400);
        phaseAnim.setRepeatCount(ValueAnimator.INFINITE);
        phaseAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float p = (Float) a.getAnimatedValue();
                if (cpuRing != null) cpuRing.setPhase(p);
                if (gpuRing != null) gpuRing.setPhase(p);
            }
        });
        phaseAnim.start();
    }

    private void applyStats(SysProbe.Snapshot s) {
        if (cpuRing == null || gpuRing == null || cpuMain == null || gpuMain == null) return;
        if (s == null || !s.root) {
            cpuRing.set(-1f, "--", accent, true, false);
            gpuRing.set(-1f, "--", accent, true, false);
            cpuMain.setText("--"); gpuMain.setText("--");
            return;
        }
        cpuRing.set(s.cpuLoad, s.cpuLoad >= 0 ? String.valueOf(s.cpuLoad) : "--", accent, true, false);
        gpuRing.set(s.gpuLoad, s.gpuLoad >= 0 ? String.valueOf(s.gpuLoad) : "--", accent, true, false);
        cpuMain.setText(s.cpuMaxMhz > 0 ? String.format(Locale.US, "%.2f", s.cpuMaxMhz / 1000f) : "--");
        gpuMain.setText(s.gpuMhz > 0 ? String.valueOf(s.gpuMhz) : "--");
    }

    private void updateClock() {
        if (clock == null) return;
        String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        String b = "";
        try {
            Intent i = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i != null) {
                int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (lvl >= 0 && sc > 0) b = "  " + (lvl * 100 / sc) + "%";
            }
        } catch (Exception ignored) {}
        clock.setText(t + b);
    }

    // ------------------------------------------------------------------ dock

    private static final String[] DEFAULT_DOCK = {"org.telegram.messenger", "com.google.android.apps.messaging", "com.android.chrome",
            "com.google.android.youtube", "com.whatsapp"};

    private List<String> dockApps() {
        PackageManager pm = ctx.getPackageManager();
        List<String> r = new ArrayList<String>();
        String raw = sp.getString("dock", null);
        if (raw == null && !sp.getBoolean("dock_init", false)) {
            for (String p : DEFAULT_DOCK) if (pm.getLaunchIntentForPackage(p) != null) r.add(p);
            saveDock(r);
            sp.edit().putBoolean("dock_init", true).apply();
            return r;
        }
        if (raw != null && raw.length() > 0) {
            for (String p : raw.split(",")) if (p.length() > 0 && pm.getLaunchIntentForPackage(p) != null) r.add(p);
        }
        return r;
    }

    private void saveDock(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String p : l) { if (sb.length() > 0) sb.append(','); sb.append(p); }
        sp.edit().putString("dock", sb.toString()).apply();
    }

    private int dockMax() {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        float availDp = (land ? dm.heightPixels * 0.92f : dm.widthPixels * 0.96f) / dm.density / scale;
        int n = (int) ((availDp - 12f - 52f) / 52f);
        return Math.max(1, Math.min(7, n));
    }

    private LinearLayout buildDock() {
        LinearLayout d = new LinearLayout(ctx);
        d.setOrientation(land ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        d.setGravity(Gravity.CENTER);
        d.setPadding(dp(6), dp(6), dp(6), dp(6));
        d.setBackground(glass(28));
        PackageManager pm = ctx.getPackageManager();
        List<String> apps = dockApps();
        int max = dockMax();
        for (int i = 0; i < apps.size() && i < max; i++) {
            final String pkg = apps.get(i);
            ImageView iv = new ImageView(ctx);
            try { iv.setImageDrawable(pm.getApplicationIcon(pkg)); } catch (Exception e) { continue; }
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setBackground(oval(0x22FFFFFF));
            iv.setClipToOutline(true);
            iv.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            iv.setPadding(dp(2), dp(2), dp(2), dp(2));
            iv.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { host.launchApp(pkg); } });
            iv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    List<String> l = dockApps();
                    l.remove(pkg);
                    saveDock(l);
                    say(tr("Removed from dock", Tr.s("Dihapus dari dock")));
                    refreshDock();
                    return true;
                }
            });
            d.addView(iv, iconParams());
        }
        TextView plus = txt("+", Color.WHITE, 22, false);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(oval(0x33FFFFFF));
        plus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickerOpen = true; renderBody(); }
        });
        d.addView(plus, iconParams());
        return d;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    private LinearLayout.LayoutParams iconParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44), dp(44));
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        return p;
    }

    private void refreshDock() {
        if (root == null) return;
        root.removeView(dockView);
        dockView = buildDock();
        root.addView(dockView, 0, dockParams());
        fit();
    }

    // ------------------------------------------------------------------ add-apps page

    private static final class AppItem { String pkg, name; Drawable icon; }

    private void pagePicker() {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = txt("←", Color.WHITE, 20, true);
        back.setPadding(0, dp(4), dp(12), dp(4));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickerOpen = false; renderBody(); }
        });
        bar.addView(back);
        bar.addView(txt(tr("Add apps to the dock", Tr.s("Tambah app ke dock")), Color.WHITE, 14, true));
        body.addView(bar);
        final TextView loading = txt(tr("Loading apps...", Tr.s("Memuat aplikasi...")), MUTED, 12, false);
        loading.setPadding(0, dp(10), 0, dp(10));
        body.addView(loading);
        fit();
        host.bg(new Runnable() { @Override public void run() {
            PackageManager pm = ctx.getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
            final List<AppItem> items = new ArrayList<AppItem>();
            java.util.HashSet<String> seen = new java.util.HashSet<String>();
            for (ResolveInfo ri : ris) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(ctx.getPackageName()) || !seen.add(pkg)) continue;
                AppItem it = new AppItem();
                it.pkg = pkg;
                it.name = String.valueOf(ri.loadLabel(pm));
                try { it.icon = ri.loadIcon(pm); } catch (Exception ignored) {}
                items.add(it);
            }
            Collections.sort(items, new Comparator<AppItem>() {
                @Override public int compare(AppItem a, AppItem b) { return a.name.compareToIgnoreCase(b.name); }
            });
            main.post(new Runnable() { @Override public void run() {
                if (!alive || !pickerOpen || body == null) return;
                body.removeView(loading);
                for (AppItem it : items) body.addView(pickerRow(it));
                fit();
            } });
        } });
    }

    private View pickerRow(final AppItem it) {
        LinearLayout r = new LinearLayout(ctx);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(4), dp(7), dp(4), dp(7));
        ImageView iv = new ImageView(ctx);
        if (it.icon != null) iv.setImageDrawable(it.icon);
        r.addView(iv, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView n = txt(it.name, Color.WHITE, 13, false);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nl = lpw(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nl.leftMargin = dp(10);
        r.addView(n, nl);
        final TextView chk = txt("", accent, 18, true);
        r.addView(chk);
        chk.setText(dockApps().contains(it.pkg) ? "✓" : "");
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                List<String> l = dockApps();
                if (l.contains(it.pkg)) {
                    l.remove(it.pkg);
                    chk.setText("");
                } else {
                    if (l.size() >= dockMax()) { say(tr("Dock is full (", "Dock penuh (") + dockMax() + ")"); return; }
                    l.add(it.pkg);
                    chk.setText("✓");
                }
                saveDock(l);
                refreshDock();
            }
        });
        return r;
    }
}
