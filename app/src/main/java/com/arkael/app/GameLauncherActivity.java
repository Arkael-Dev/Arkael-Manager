package com.arkael.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Game Launcher, Xiaomi "Game Turbo" style (landscape): game list on the left, big preview in the
 * middle, "Mainkan" panel on the right, "Setelan Game" tab at the bottom, live battery/CPU/GPU on top.
 * Games = everything the system tags as a game on THIS phone + games added by hand (+ button).
 * Mainkan = apply the game's saved profile (same as Game Mode) then launch with animation + sound.
 */
public class GameLauncherActivity extends Activity {
    private static final String PREFS = "gl_prefs";
    private static final String K_MANUAL = "manual", K_HIDDEN = "hidden", K_MUTE = "mute";

    private static final int BG = Color.parseColor("#0A0D12");
    private static final int CARD = Color.parseColor("#141A22");
    private static final int NEON = Color.parseColor("#00E5FF");
    private static final int SUB = Color.parseColor("#8B95A3");

    private static final class Game {
        String pkg, name;
        Drawable icon;
    }

    private final List<Game> games = new ArrayList<Game>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private PackageManager pm;
    private SharedPreferences sp;

    private LinearLayout listCol, body;
    private FrameLayout root, overlay, previewBox;
    private LinearLayout pvContent, playPanel;
    private ImageView pvBack, pvIcon, ovIcon;
    private TextView pvName, pvSub, emptyView, muteBtn, stBat, stCpu, stGpu, ovText;
    private View ovRing;
    private SoundPool pool;
    private int sOpen, sLaunch, sel = 0;
    private boolean muted, launching, introDone, pollOn;

    private long prevIdle = -1, prevTotal = -1;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        pm = getPackageManager();
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        muted = sp.getBoolean(K_MUTE, false);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#10151D"), Color.parseColor("#0A0D12")}));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(4), 0, 0);

        // ---------------- top bar: live stats (left) + buttons (right)
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(2), dp(14), dp(2));
        stBat = stat("🔋 --%");
        stCpu = stat("CPU --");
        stGpu = stat("GPU --");
        top.addView(stBat);
        top.addView(stCpu);
        top.addView(stGpu);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        muteBtn = roundBtn(muted ? "🔇" : "🔊", v -> toggleMute());
        top.addView(muteBtn);
        top.addView(roundBtn("＋", v -> showAddDialog()));
        col.addView(top, new LinearLayout.LayoutParams(-1, dp(46)));

        // ---------------- body: list | preview | play
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);

        ScrollView listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(false);
        listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        listCol.setPadding(0, dp(8), 0, dp(8));
        listScroll.addView(listCol, new ViewGroup.LayoutParams(-1, -2));
        body.addView(listScroll, new LinearLayout.LayoutParams(0, -1, 3f));

        previewBox = new FrameLayout(this);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(CARD);
        pbg.setCornerRadius(dp(18));
        previewBox.setBackground(pbg);
        previewBox.setClipToOutline(true);

        pvBack = new ImageView(this);
        pvBack.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pvBack.setScaleX(3.4f);
        pvBack.setScaleY(3.4f);
        pvBack.setAlpha(0.32f);
        previewBox.addView(pvBack, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#10000000"), Color.parseColor("#E6000000")}));
        previewBox.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        pvContent = new LinearLayout(this);
        pvContent.setOrientation(LinearLayout.VERTICAL);
        pvContent.setGravity(Gravity.CENTER_HORIZONTAL);
        pvContent.setPadding(dp(20), dp(24), dp(20), dp(18));
        pvIcon = new ImageView(this);
        LinearLayout.LayoutParams ipl = new LinearLayout.LayoutParams(dp(104), dp(104));
        ipl.topMargin = dp(18);
        pvContent.addView(pvIcon, ipl);
        View spacer = new View(this);
        pvContent.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        pvName = new TextView(this);
        pvName.setTextColor(Color.WHITE);
        pvName.setTextSize(24);
        pvName.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        pvName.setShadowLayer(16, 0, 0, NEON);
        pvName.setSingleLine(true);
        pvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pvName.setGravity(Gravity.CENTER);
        pvContent.addView(pvName, new LinearLayout.LayoutParams(-1, -2));
        pvSub = new TextView(this);
        pvSub.setTextColor(Color.parseColor("#B8C2D0"));
        pvSub.setTextSize(11);
        pvSub.setGravity(Gravity.CENTER);
        pvSub.setPadding(0, dp(4), 0, 0);
        pvSub.setMaxLines(2);
        pvContent.addView(pvSub, new LinearLayout.LayoutParams(-1, -2));
        previewBox.addView(pvContent, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams pvl = new LinearLayout.LayoutParams(0, -1, 6f);
        pvl.topMargin = dp(10);
        pvl.bottomMargin = dp(54);
        pvl.leftMargin = dp(8);
        pvl.rightMargin = dp(14);
        body.addView(previewBox, pvl);

        FrameLayout rightCol = new FrameLayout(this);
        playPanel = new LinearLayout(this);
        playPanel.setOrientation(LinearLayout.VERTICAL);
        playPanel.setGravity(Gravity.CENTER_VERTICAL);
        playPanel.setPadding(dp(18), dp(16), dp(14), dp(16));
        GradientDrawable pg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#1F6BFF"), Color.parseColor("#0B3CD0")});
        pg.setCornerRadii(new float[]{dp(14), dp(14), 0, 0, 0, 0, dp(14), dp(14)});
        playPanel.setBackground(pg);
        TextView play = new TextView(this);
        play.setText("⚡ Mainkan");
        play.setTextColor(Color.WHITE);
        play.setTextSize(22);
        play.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        playPanel.addView(play);
        TextView ps = new TextView(this);
        ps.setText("Profil Game Mode diterapkan otomatis");
        ps.setTextColor(Color.parseColor("#CFE0FF"));
        ps.setTextSize(12);
        ps.setPadding(0, dp(6), 0, 0);
        playPanel.addView(ps);
        playPanel.setOnClickListener(v -> { if (!games.isEmpty()) launch(games.get(sel)); });
        FrameLayout.LayoutParams ppl = new FrameLayout.LayoutParams(-1, -2);
        ppl.gravity = Gravity.CENTER_VERTICAL;
        rightCol.addView(playPanel, ppl);
        body.addView(rightCol, new LinearLayout.LayoutParams(0, -1, 3f));

        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // ---------------- bottom tab: per-game settings
        TextView tab = new TextView(this);
        tab.setText("Setelan Game");
        tab.setTextColor(Color.parseColor("#DDE6F2"));
        tab.setTextSize(14);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(48), dp(10), dp(48), dp(12));
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(Color.parseColor("#E61A2230"));
        tg.setStroke(dp(1), Color.parseColor("#4400E5FF"));
        tg.setCornerRadii(new float[]{dp(16), dp(16), dp(16), dp(16), 0, 0, 0, 0});
        tab.setBackground(tg);
        tab.setOnClickListener(v -> { if (!games.isEmpty()) openProfile(games.get(sel).pkg); });
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2);
        tl.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        root.addView(tab, tl);

        emptyView = new TextView(this);
        emptyView.setText("Belum ada game terdeteksi.\nTekan ＋ untuk menambah game secara manual.");
        emptyView.setTextColor(SUB);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));

        buildOverlay();
        setContentView(root);

        // intro: whole screen fades/slides in
        col.setAlpha(0f);
        col.setTranslationY(dp(14));
        col.animate().alpha(1f).translationY(0).setDuration(420).start();
        tab.setTranslationY(dp(60));
        tab.animate().translationY(0).setStartDelay(200).setDuration(380).start();

        initSound();
        loadGames();
    }

    // ---------------------------------------------------------------- UI pieces

    private TextView stat(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(13);
        t.setPadding(0, 0, dp(18), 0);
        return t;
    }

    private TextView roundBtn(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(NEON);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(CARD);
        g.setStroke(dp(1), Color.parseColor("#5500E5FF"));
        t.setBackground(g);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
        lp.leftMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void renderList() {
        listCol.removeAllViews();
        for (int i = 0; i < games.size(); i++) {
            final int idx = i;
            final Game g = games.get(i);
            boolean on = i == sel;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(10), dp(10), dp(10));
            if (on) {
                GradientDrawable hl = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{Color.parseColor("#3300E5FF"), Color.parseColor("#0800E5FF")});
                row.setBackground(hl);
            } else {
                row.setBackground(new ColorDrawable(Color.TRANSPARENT));
            }
            ImageView ic = new ImageView(this);
            ic.setImageDrawable(g.icon);
            row.addView(ic, new LinearLayout.LayoutParams(dp(38), dp(38)));
            TextView n = new TextView(this);
            n.setText(g.name);
            n.setTextColor(on ? Color.WHITE : Color.parseColor("#9AA5B4"));
            n.setTextSize(on ? 16 : 14);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
            nl.leftMargin = dp(12);
            row.addView(n, nl);
            if (on) {
                View bar = new View(this);
                bar.setBackgroundColor(NEON);
                row.addView(bar, new LinearLayout.LayoutParams(dp(3), dp(26)));
            }
            row.setOnClickListener(v -> select(idx));
            row.setOnLongClickListener(v -> { confirmHide(g); return true; });
            if (!introDone) {
                row.setAlpha(0f);
                row.setTranslationX(-dp(30));
                row.animate().alpha(1f).translationX(0).setStartDelay(Math.min(i, 10) * 50L + 150).setDuration(320).start();
            }
            listCol.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        introDone = true;
    }

    private void select(int i) {
        if (i == sel || i < 0 || i >= games.size()) return;
        sel = i;
        renderList();
        updatePreview(true);
    }

    private void updatePreview(boolean anim) {
        if (games.isEmpty()) return;
        if (sel >= games.size()) sel = 0;
        Game g = games.get(sel);
        pvBack.setImageDrawable(g.icon);
        pvIcon.setImageDrawable(g.icon);
        pvName.setText(g.name);
        pvSub.setText(profileSummary(g.pkg));
        if (anim) {
            pvContent.setAlpha(0f);
            pvContent.setTranslationX(dp(26));
            pvContent.animate().alpha(1f).translationX(0).setDuration(240).start();
            pvBack.setAlpha(0f);
            pvBack.animate().alpha(0.32f).setDuration(300).start();
        }
    }

    /** One-line summary of what Mainkan will turn on for this game (from the saved Game Mode profile). */
    private String profileSummary(String pkg) {
        try {
            GameProfiles.Profile p = GameProfiles.get(this, pkg);
            List<String> on = new ArrayList<String>();
            if (p.gov != null && !p.gov.isEmpty()) on.add("CPU " + p.gov);
            if (p.gpuMaxMhz > 0) on.add("GPU " + p.gpuMaxMhz + " MHz");
            if (p.dnd) on.add("Jangan ganggu");
            if (p.headsUp) on.add("Pop-up diblok");
            if (p.thermalRelax) on.add("Thermal longgar");
            if (p.cleanRam) on.add("Bersih RAM");
            if (on.isEmpty()) return "Belum ada boost aktif. Atur di Setelan Game.";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < on.size(); i++) { if (i > 0) sb.append("  •  "); sb.append(on.get(i)); }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private void buildOverlay() {
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.parseColor("#F00A0D12"));
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);

        ovRing = new View(this);
        GradientDrawable rg = new GradientDrawable();
        rg.setShape(GradientDrawable.OVAL);
        rg.setColor(Color.TRANSPARENT);
        rg.setStroke(dp(3), NEON);
        ovRing.setBackground(rg);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(dp(110), dp(110));
        rl.gravity = Gravity.CENTER;
        overlay.addView(ovRing, rl);

        ovIcon = new ImageView(this);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(dp(80), dp(80));
        il.gravity = Gravity.CENTER;
        overlay.addView(ovIcon, il);

        ovText = new TextView(this);
        ovText.setTextColor(Color.WHITE);
        ovText.setTextSize(14);
        ovText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        ovText.setLetterSpacing(0.15f);
        ovText.setShadowLayer(14, 0, 0, NEON);
        ovText.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-1, -2);
        tl.gravity = Gravity.CENTER;
        tl.topMargin = dp(110);
        overlay.addView(ovText, tl);

        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    // ---------------------------------------------------------------- live stats

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!pollOn) return;
            new Thread(() -> {
                final int bat = battery();
                final int cpu = cpuLoad();
                final int gpu = gpuLoad();
                runOnUiThread(() -> {
                    stBat.setText("🔋 " + (bat < 0 ? "--" : bat) + "%");
                    stCpu.setText("CPU " + (cpu < 0 ? "--" : cpu + "%"));
                    stGpu.setText("GPU " + (gpu < 0 ? "--" : gpu + "%"));
                });
            }).start();
            main.postDelayed(this, 2000);
        }
    };

    private int battery() {
        try {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return -1;
            int l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            return l < 0 ? -1 : l * 100 / Math.max(1, s);
        } catch (Throwable t) { return -1; }
    }

    private static String readFile(String path) {
        BufferedReader r = null;
        try {
            File f = new File(path);
            if (!f.canRead()) return null;
            r = new BufferedReader(new FileReader(f));
            String line = r.readLine();
            return line == null ? null : line.trim();
        } catch (Throwable t) { return null; }
        finally { if (r != null) try { r.close(); } catch (Throwable ignored) {} }
    }

    private static int firstInt(String s) {
        if (s == null) return -1;
        String d = s.replaceAll("[^0-9]", " ").trim();
        if (d.isEmpty()) return -1;
        try { return Integer.parseInt(d.split("\\s+")[0]); } catch (Throwable t) { return -1; }
    }

    /** Real load from /proc/stat when readable; otherwise average CPU clock vs its max clock. */
    private int cpuLoad() {
        String line = readFile("/proc/stat");
        if (line != null && line.startsWith("cpu")) {
            try {
                String[] p = line.split("\\s+");
                long total = 0;
                for (int i = 1; i < Math.min(p.length, 9); i++) total += Long.parseLong(p[i]);
                long idle = Long.parseLong(p[4]) + Long.parseLong(p[5]);
                int out = -1;
                if (prevTotal >= 0 && total > prevTotal) out = (int) (100L * ((total - prevTotal) - (idle - prevIdle)) / (total - prevTotal));
                prevTotal = total;
                prevIdle = idle;
                if (out >= 0) return Math.max(0, Math.min(100, out));
            } catch (Throwable ignored) {}
        }
        long cur = 0, max = 0;
        for (int c = 0; c < 16; c++) {
            String base = "/sys/devices/system/cpu/cpu" + c + "/cpufreq/";
            int a = firstInt(readFile(base + "scaling_cur_freq")), m = firstInt(readFile(base + "cpuinfo_max_freq"));
            if (a > 0 && m > 0) { cur += a; max += m; }
        }
        return max > 0 ? (int) (100 * cur / max) : -1;
    }

    private int gpuLoad() {
        String[] paths = {
                "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
                "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
                "/sys/kernel/gpu/gpu_busy",
                "/sys/class/devfreq/gpufreq/load"};
        for (String p : paths) {
            int v = firstInt(readFile(p));
            if (v >= 0) return Math.min(100, v);
        }
        return -1;
    }

    // ---------------------------------------------------------------- sound

    private void initSound() {
        try {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(aa).build();
            sOpen = pool.load(this, R.raw.gl_open, 1);
            sLaunch = pool.load(this, R.raw.gl_launch, 1);
            pool.setOnLoadCompleteListener((p, id, status) -> {
                if (status == 0 && id == sOpen && !muted) p.play(id, 0.9f, 0.9f, 1, 0, 1f);
            });
        } catch (Throwable ignored) { pool = null; }
    }

    private void toggleMute() {
        muted = !muted;
        sp.edit().putBoolean(K_MUTE, muted).apply();
        muteBtn.setText(muted ? "🔇" : "🔊");
    }

    // ---------------------------------------------------------------- game list

    private Set<String> getSet(String k) { return new HashSet<String>(sp.getStringSet(k, new HashSet<String>())); }
    private void putSet(String k, Set<String> v) { sp.edit().putStringSet(k, v).apply(); }

    @SuppressWarnings("deprecation")
    private void loadGames() {
        new Thread(() -> {
            Set<String> manual = getSet(K_MANUAL), hidden = getSet(K_HIDDEN), seen = new HashSet<String>();
            List<Game> out = new ArrayList<Game>();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || hidden.contains(pkg) || !seen.add(pkg)) continue;
                ApplicationInfo ai = ri.activityInfo.applicationInfo;
                boolean isGame = (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
                if (Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME) isGame = true;
                if (!isGame && !manual.contains(pkg)) continue;
                Game g = new Game();
                g.pkg = pkg;
                g.name = String.valueOf(ri.loadLabel(pm));
                g.icon = ri.loadIcon(pm);
                out.add(g);
            }
            Collections.sort(out, new Comparator<Game>() {
                @Override public int compare(Game a, Game b) { return a.name.compareToIgnoreCase(b.name); }
            });
            final List<Game> res = out;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                String keep = (!games.isEmpty() && sel < games.size()) ? games.get(sel).pkg : null;
                games.clear();
                games.addAll(res);
                sel = 0;
                if (keep != null) for (int i = 0; i < games.size(); i++) if (games.get(i).pkg.equals(keep)) sel = i;
                boolean none = games.isEmpty();
                body.setVisibility(none ? View.INVISIBLE : View.VISIBLE);
                emptyView.setVisibility(none ? View.VISIBLE : View.GONE);
                renderList();
                updatePreview(false);
            });
        }).start();
    }

    private void showAddDialog() {
        new Thread(() -> {
            Set<String> have = new HashSet<String>();
            for (Game g : games) have.add(g.pkg);
            final List<String> pk = new ArrayList<String>(), nm = new ArrayList<String>();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> all = pm.queryIntentActivities(q, 0);
            Collections.sort(all, new ResolveInfo.DisplayNameComparator(pm));
            for (ResolveInfo ri : all) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName()) || have.contains(p) || pk.contains(p)) continue;
                pk.add(p);
                nm.add(String.valueOf(ri.loadLabel(pm)));
            }
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (pk.isEmpty()) { Toast.makeText(this, "Tidak ada aplikasi lain", Toast.LENGTH_SHORT).show(); return; }
                final boolean[] selected = new boolean[pk.size()];
                new AlertDialog.Builder(this)
                        .setTitle("Tambah game")
                        .setMultiChoiceItems(nm.toArray(new String[0]), selected, (d, i, c) -> selected[i] = c)
                        .setPositiveButton("Tambah", (d, w) -> {
                            Set<String> man = getSet(K_MANUAL), hid = getSet(K_HIDDEN);
                            for (int i = 0; i < selected.length; i++) if (selected[i]) { man.add(pk.get(i)); hid.remove(pk.get(i)); }
                            putSet(K_MANUAL, man);
                            putSet(K_HIDDEN, hid);
                            loadGames();
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            });
        }).start();
    }

    private void confirmHide(final Game g) {
        new AlertDialog.Builder(this)
                .setTitle(g.name)
                .setMessage("Sembunyikan dari Game Launcher? (Game tidak dihapus dari HP. Bisa ditambah lagi lewat tombol ＋)")
                .setPositiveButton("Sembunyikan", (d, w) -> {
                    Set<String> hid = getSet(K_HIDDEN), man = getSet(K_MANUAL);
                    hid.add(g.pkg);
                    man.remove(g.pkg);
                    putSet(K_HIDDEN, hid);
                    putSet(K_MANUAL, man);
                    loadGames();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // ---------------------------------------------------------------- launch / settings

    private void launch(final Game g) {
        if (launching) return;
        launching = true;
        ovIcon.setImageDrawable(g.icon);
        ovText.setText("MEMUAT  " + g.name.toUpperCase());
        overlay.setAlpha(0f);
        overlay.setVisibility(View.VISIBLE);
        overlay.animate().alpha(1f).setDuration(180).start();
        ovIcon.setScaleX(0.5f); ovIcon.setScaleY(0.5f); ovIcon.setAlpha(0f);
        ovIcon.animate().scaleX(1.1f).scaleY(1.1f).alpha(1f).setDuration(520).start();
        ovRing.setScaleX(0.7f); ovRing.setScaleY(0.7f); ovRing.setAlpha(1f);
        ovRing.animate().scaleX(3.2f).scaleY(3.2f).alpha(0f).setDuration(950).start();
        ovText.setAlpha(0f);
        ovText.animate().alpha(1f).setStartDelay(250).setDuration(400).start();
        if (!muted && pool != null) pool.play(sLaunch, 1f, 1f, 1, 0, 1f);

        final long t0 = System.currentTimeMillis();
        new Thread(() -> {
            // Same thing Game Mode does when a game starts: the profile saved for this game.
            try { Booster.applyProfile(this, GameProfiles.get(this, g.pkg), false); } catch (Throwable ignored) {}
            long wait = 1100 - (System.currentTimeMillis() - t0);
            if (wait > 0) { try { Thread.sleep(wait); } catch (InterruptedException ignored) {} }
            runOnUiThread(() -> {
                Intent i = pm.getLaunchIntentForPackage(g.pkg);
                if (i == null) {
                    Toast.makeText(this, "Tidak bisa membuka " + g.name, Toast.LENGTH_SHORT).show();
                    hideOverlay();
                    return;
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i); } catch (Throwable e) {
                    Toast.makeText(this, "Gagal membuka " + g.name, Toast.LENGTH_SHORT).show();
                    hideOverlay();
                }
            });
        }).start();
    }

    private void hideOverlay() {
        launching = false;
        overlay.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (launching) main.postDelayed(this::hideOverlay, 400);   // came back from the game
        pollOn = true;
        main.removeCallbacks(poll);
        main.post(poll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollOn = false;
        main.removeCallbacks(poll);
    }

    /** Opens the existing per-game profile settings. Tries the likely screen names, else the game list. */
    private void openProfile(String pkg) {
        String[] cands = {"GameProfileActivity", "GameSettingsActivity", "GameProfileSettingsActivity", "ProfileActivity"};
        for (String c : cands) {
            try {
                Intent i = new Intent(this, Class.forName("com.arkael.app." + c));
                i.putExtra("pkg", pkg);
                i.putExtra("package", pkg);
                startActivity(i);
                return;
            } catch (Throwable ignored) {}
        }
        Intent i = new Intent(this, GameListActivity.class);
        i.putExtra("pkg", pkg);
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
        if (pool != null) { pool.release(); pool = null; }
    }
}
