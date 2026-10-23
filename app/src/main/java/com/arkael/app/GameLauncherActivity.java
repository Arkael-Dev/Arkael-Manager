package com.arkael.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Game Launcher (Xiaomi-style): grid of every game installed on THIS phone (detected by the
 * system's "game" category, plus games the user adds by hand). Tap = play the game profile
 * (same one Game Mode uses) then launch with an animation + sound. Gear = that game's settings.
 * Long-press = hide the game from the launcher.
 */
public class GameLauncherActivity extends Activity {
    private static final String PREFS = "gl_prefs";
    private static final String K_MANUAL = "manual", K_HIDDEN = "hidden", K_MUTE = "mute";

    private static final int BG = Color.parseColor("#070A10");
    private static final int CARD = Color.parseColor("#121821");
    private static final int NEON = Color.parseColor("#00E5FF");
    private static final int SUB = Color.parseColor("#8B95A3");
    private static final int[] ACCENTS = {
            Color.parseColor("#00E5FF"), Color.parseColor("#FF2E93"), Color.parseColor("#9D6BFF"), Color.parseColor("#3DFFA2")};

    private static final class Game {
        String pkg, name;
        Drawable icon;
    }

    private final List<Game> games = new ArrayList<Game>();
    private final Set<Integer> animated = new HashSet<Integer>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private PackageManager pm;
    private SharedPreferences sp;
    private BaseAdapter adapter;
    private TextView count, empty, muteBtn;
    private FrameLayout root, overlay;
    private ImageView ovIcon;
    private View ovRing;
    private TextView ovText;
    private SoundPool pool;
    private int sOpen, sLaunch;
    private boolean muted, launching;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        muted = sp.getBoolean(K_MUTE, false);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#0E1426"), BG});
        root.setBackground(bg);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(26), dp(16), 0);

        // ---- header ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText("GAME LAUNCHER");
        t.setTextColor(Color.WHITE);
        t.setTextSize(22);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLetterSpacing(0.12f);
        t.setShadowLayer(18, 0, 0, NEON);
        titles.addView(t);
        count = new TextView(this);
        count.setTextColor(SUB);
        count.setTextSize(12);
        count.setPadding(0, dp(2), 0, 0);
        titles.addView(count);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        muteBtn = roundBtn(muted ? "🔇" : "🔊", v -> toggleMute());
        head.addView(muteBtn);
        head.addView(roundBtn("＋", v -> showAddDialog()));
        col.addView(head);

        View bar = new View(this);
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{NEON, Color.parseColor("#FF2E93"), Color.TRANSPARENT}));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        bl.topMargin = dp(12);
        bl.bottomMargin = dp(6);
        col.addView(bar, bl);

        empty = new TextView(this);
        empty.setText("Belum ada game terdeteksi.\nTekan ＋ untuk menambah game secara manual.");
        empty.setTextColor(SUB);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(60), 0, 0);
        empty.setVisibility(View.GONE);
        col.addView(empty);

        GridView grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(dp(10));
        grid.setVerticalSpacing(dp(10));
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(10), 0, dp(24));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        adapter = new BaseAdapter() {
            @Override public int getCount() { return games.size(); }
            @Override public Object getItem(int i) { return games.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup p) { return card(games.get(i), i); }
        };
        grid.setAdapter(adapter);
        col.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        buildOverlay();
        setContentView(root);

        // intro animation: header slides in, sound plays
        col.setAlpha(0f);
        col.setTranslationY(-dp(16));
        col.animate().alpha(1f).translationY(0).setDuration(380).start();

        initSound();
        loadGames();
    }

    // ---------------------------------------------------------------- UI pieces

    private TextView roundBtn(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(NEON);
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(CARD);
        g.setStroke(dp(1), Color.parseColor("#5500E5FF"));
        t.setBackground(g);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.leftMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private View card(final Game g, int pos) {
        final int accent = ACCENTS[pos % ACCENTS.length];
        FrameLayout f = new FrameLayout(this);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(CARD);
        bgd.setCornerRadius(dp(16));
        bgd.setStroke(dp(1), (accent & 0x00FFFFFF) | 0x66000000);
        f.setBackground(bgd);
        f.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, dp(132)));

        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setGravity(Gravity.CENTER);
        in.setPadding(dp(6), dp(12), dp(6), dp(8));
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(g.icon);
        in.addView(ic, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView n = new TextView(this);
        n.setText(g.name);
        n.setTextColor(Color.WHITE);
        n.setTextSize(12);
        n.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        n.setPadding(0, dp(8), 0, 0);
        in.addView(n);
        f.addView(in, new FrameLayout.LayoutParams(-1, -1));

        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextColor(accent);
        gear.setTextSize(16);
        gear.setGravity(Gravity.CENTER);
        gear.setOnClickListener(v -> openProfile(g.pkg));
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(dp(34), dp(34));
        gl.gravity = Gravity.TOP | Gravity.END;
        f.addView(gear, gl);

        f.setOnClickListener(v -> launch(g));
        f.setOnLongClickListener(v -> { confirmHide(g); return true; });

        if (animated.add(pos)) {
            f.setAlpha(0f);
            f.setTranslationY(dp(26));
            f.setScaleX(0.9f);
            f.setScaleY(0.9f);
            f.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f)
                    .setStartDelay(Math.min(pos, 12) * 45L).setDuration(340).start();
        }
        return f;
    }

    private void buildOverlay() {
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.parseColor("#F0070A10"));
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);   // swallow taps while launching

        ovRing = new View(this);
        GradientDrawable rg = new GradientDrawable();
        rg.setShape(GradientDrawable.OVAL);
        rg.setColor(Color.TRANSPARENT);
        rg.setStroke(dp(3), NEON);
        ovRing.setBackground(rg);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(dp(120), dp(120));
        rl.gravity = Gravity.CENTER;
        overlay.addView(ovRing, rl);

        ovIcon = new ImageView(this);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(dp(84), dp(84));
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
        tl.topMargin = dp(120);
        overlay.addView(ovText, tl);

        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
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
                games.clear();
                games.addAll(res);
                animated.clear();
                adapter.notifyDataSetChanged();
                count.setText(games.size() + " game");
                empty.setVisibility(games.isEmpty() ? View.VISIBLE : View.GONE);
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
                final boolean[] sel = new boolean[pk.size()];
                new AlertDialog.Builder(this)
                        .setTitle("Tambah game")
                        .setMultiChoiceItems(nm.toArray(new String[0]), sel, (d, i, c) -> sel[i] = c)
                        .setPositiveButton("Tambah", (d, w) -> {
                            Set<String> man = getSet(K_MANUAL), hid = getSet(K_HIDDEN);
                            for (int i = 0; i < sel.length; i++) if (sel[i]) { man.add(pk.get(i)); hid.remove(pk.get(i)); }
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
        ovRing.animate().scaleX(2.6f).scaleY(2.6f).alpha(0f).setDuration(950).start();
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
