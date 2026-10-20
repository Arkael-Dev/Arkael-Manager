package com.arkael.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Control screen for the performance overlay. Built in code, no layout XML. */
public class GameModeActivity extends Activity {
    private Switch swOverlay, swAuto, swPanel;
    private TextView tvBoost, tvGames;
    private TextView tvPerm, tvRoot;
    private boolean ignore;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView label(String text, int color, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#141A22"));
        g.setCornerRadius(dp(14));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0A0D12"));
        getWindow().setNavigationBarColor(Color.parseColor("#0A0D12"));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#0A0D12"));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(24), dp(16), dp(24));
        sv.addView(col);

        col.addView(label("Game Mode", Color.WHITE, 24, true));
        TextView sub = label("Live CPU, GPU, RAM, temperature and battery on top of any game. Drag it anywhere, pinch it (or drag its corner grip) to resize, double-tap to switch between full and compact.", Color.parseColor("#8B95A3"), 12, false);
        sub.setPadding(0, dp(4), 0, dp(16));
        col.addView(sub);

        // --- master switch ---
        LinearLayout c1 = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(label("Performance overlay", Color.WHITE, 15, true));
        tvRoot = label("Root: checking...", Color.parseColor("#8B95A3"), 11, false);
        t.addView(tvRoot);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        swOverlay = new Switch(this);
        row.addView(swOverlay);
        c1.addView(row);
        tvPerm = label("", Color.parseColor("#FF6B35"), 12, true);
        tvPerm.setPadding(0, dp(10), 0, 0);
        c1.addView(tvPerm);
        col.addView(c1);

        // --- auto game mode + side panel ---
        LinearLayout cAuto = card();
        LinearLayout rowA = new LinearLayout(this);
        rowA.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout ta = new LinearLayout(this);
        ta.setOrientation(LinearLayout.VERTICAL);
        ta.addView(label("Auto game mode", Color.WHITE, 15, true));
        ta.addView(label("Applies a game's profile, shows the side panel and the overlay when it opens, and restores everything when you leave. Needs root.", Color.parseColor("#8B95A3"), 11, false));
        rowA.addView(ta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        swAuto = new Switch(this);
        rowA.addView(swAuto);
        cAuto.addView(rowA);
        CheckBox cbPanel = new CheckBox(this);
        cbPanel.setText("Side panel (swipe or tap the edge handle in a game)");
        cbPanel.setTextColor(Color.WHITE);
        cbPanel.setChecked(GameProfiles.panel(this));
        cbPanel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) { GameProfiles.setPanel(GameModeActivity.this, on); }
        });
        cAuto.addView(cbPanel);
        col.addView(cAuto);

        // --- game panel in any app ---
        LinearLayout cPanel = card();
        LinearLayout rowP = new LinearLayout(this);
        rowP.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tp = new LinearLayout(this);
        tp.setOrientation(LinearLayout.VERTICAL);
        tp.addView(label("GAME MODE panel", Color.WHITE, 15, true));
        tp.addView(label("Shows the edge handle in every app right now, no game list needed. Tap or swipe the handle to open the GAME MODE panel (boost, clean RAM, do not disturb, screenshot...). Needs root for the tools.", Color.parseColor("#8B95A3"), 11, false));
        rowP.addView(tp, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        swPanel = new Switch(this);
        rowP.addView(swPanel);
        cPanel.addView(rowP);
        col.addView(cPanel);
        swPanel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                if (ignore) return;
                if (on) {
                    if (!Settings.canDrawOverlays(GameModeActivity.this)) {
                        askOverlayPermission();
                        ignore = true; swPanel.setChecked(false); ignore = false;
                        return;
                    }
                    askNotificationPermission();
                    GameService.panelOn(GameModeActivity.this);
                } else {
                    GameService.panelOff(GameModeActivity.this);
                }
            }
        });

        swAuto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                if (ignore) return;
                if (on) {
                    if (!Settings.canDrawOverlays(GameModeActivity.this)) {
                        askOverlayPermission();
                        ignore = true; swAuto.setChecked(false); ignore = false;
                        return;
                    }
                    askNotificationPermission();
                    GameProfiles.setAuto(GameModeActivity.this, true);
                    GameService.start(GameModeActivity.this);
                } else {
                    GameProfiles.setAuto(GameModeActivity.this, false);
                    GameService.stop(GameModeActivity.this);
                }
            }
        });

        // --- games & profiles ---
        LinearLayout cGames = card();
        cGames.addView(label("Games & profiles", Color.WHITE, 15, true));
        tvGames = label("", Color.parseColor("#8B95A3"), 11, false);
        cGames.addView(tvGames);
        cGames.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(GameModeActivity.this, GameListActivity.class)); }
        });
        col.addView(cGames);

        // --- boost now ---
        LinearLayout cBoost = card();
        cBoost.addView(label("Boost now", Color.WHITE, 15, true));
        TextView bd = label("Drops caches, trims app caches, kills background apps and sets the performance governor until you press Restore.", Color.parseColor("#8B95A3"), 11, false);
        bd.setPadding(0, 0, 0, dp(8));
        cBoost.addView(bd);
        LinearLayout br = new LinearLayout(this);
        Button bBoost = new Button(this);
        bBoost.setText("PERCEPAT");
        bBoost.setAllCaps(false);
        bBoost.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { boostNow(); }
        });
        Button bRestore = new Button(this);
        bRestore.setText("Restore defaults");
        bRestore.setAllCaps(false);
        bRestore.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { restoreNow(); }
        });
        br.addView(bBoost, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        br.addView(bRestore, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cBoost.addView(br);
        tvBoost = label("", Color.parseColor("#00E5FF"), 11, false);
        tvBoost.setPadding(0, dp(6), 0, 0);
        cBoost.addView(tvBoost);
        col.addView(cBoost);

        swOverlay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                if (ignore) return;
                if (on) {
                    if (!Settings.canDrawOverlays(GameModeActivity.this)) {
                        askOverlayPermission();
                        setSwitch(false);
                        return;
                    }
                    askNotificationPermission();
                    OverlayPrefs.setEnabled(GameModeActivity.this, true);
                    OverlayService.start(GameModeActivity.this);
                } else {
                    OverlayPrefs.setEnabled(GameModeActivity.this, false);
                    OverlayService.stop(GameModeActivity.this);
                }
            }
        });
        tvPerm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { askOverlayPermission(); }
        });

        // --- color ---
        LinearLayout c2 = card();
        c2.addView(label("COLOR", Color.parseColor("#00E5FF"), 11, true));
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, dp(10), 0, 0);
        for (final int col1 : OverlayPrefs.COLORS) {
            View chip = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col1);
            chip.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
            lp.rightMargin = dp(12);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    OverlayPrefs.sp(GameModeActivity.this).edit().putInt("color", col1).apply();
                    OverlayService.refresh(GameModeActivity.this);
                }
            });
            chips.addView(chip);
        }
        c2.addView(chips);
        col.addView(c2);

        // --- opacity + size ---
        LinearLayout c3 = card();
        c3.addView(label("OPACITY", Color.parseColor("#00E5FF"), 11, true));
        SeekBar sb = new SeekBar(this);
        sb.setMax(70);   // 30..100
        sb.setProgress(OverlayPrefs.alpha(this) - 30);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {}
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                OverlayPrefs.sp(GameModeActivity.this).edit().putInt("alpha", s.getProgress() + 30).apply();
                OverlayService.refresh(GameModeActivity.this);
            }
        });
        c3.addView(sb);
        TextView sz = label("SIZE", Color.parseColor("#00E5FF"), 11, true);
        sz.setPadding(0, dp(12), 0, dp(6));
        c3.addView(sz);
        LinearLayout sizes = new LinearLayout(this);
        String[] names = {"Small", "Medium", "Large"};
        final float[] scales = {0.8f, 1f, 1.3f};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button bt = new Button(this);
            bt.setText(names[i]);
            bt.setAllCaps(false);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    OverlayPrefs.setScale(GameModeActivity.this, scales[idx]);
                    OverlayService.refresh(GameModeActivity.this);
                }
            });
            sizes.addView(bt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        c3.addView(sizes);
        TextView hint = label("In a game you can also pinch the overlay with two fingers, or drag the little ◢ in its corner, to make it any size.", Color.parseColor("#8B95A3"), 11, false);
        hint.setPadding(0, dp(6), 0, 0);
        c3.addView(hint);
        Button bReset = new Button(this);
        bReset.setText("Reset position and size");
        bReset.setAllCaps(false);
        bReset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                OverlayPrefs.resetLayout(GameModeActivity.this);
                OverlayService.refresh(GameModeActivity.this);
            }
        });
        c3.addView(bReset);
        col.addView(c3);

        // --- temperature source ---
        LinearLayout cT = card();
        cT.addView(label("TEMPERATURE SOURCE", Color.parseColor("#00E5FF"), 11, true));
        final TextView tvSrc = label("", Color.parseColor("#8B95A3"), 11, false);
        tvSrc.setPadding(0, dp(4), 0, dp(6));
        cT.addView(tvSrc);
        tvSrc.setText("Now: " + TempProbe.NAMES[OverlayPrefs.tempSrc(this)] + ". Phones expose different sensors, so pick the one that matches what you expect. The battery temperature is always shown in the BAT row.");
        for (int i = 0; i < TempProbe.NAMES.length; i++) {
            final int idx = i;
            Button bt = new Button(this);
            bt.setText(TempProbe.NAMES[i]);
            bt.setAllCaps(false);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    OverlayPrefs.setTempSrc(GameModeActivity.this, idx);
                    tvSrc.setText("Now: " + TempProbe.NAMES[idx] + ". Phones expose different sensors, so pick the one that matches what you expect. The battery temperature is always shown in the BAT row.");
                    OverlayService.refresh(GameModeActivity.this);
                }
            });
            cT.addView(bt);
        }
        col.addView(cT);

        // --- metrics ---
        LinearLayout c4 = card();
        c4.addView(label("SHOW", Color.parseColor("#00E5FF"), 11, true));
        String[] mn = {"FPS (needs root)", "CPU load and frequency", "Temperature", "GPU load and frequency", "RAM", "Battery", "History graph: CPU, temperature, FPS"};
        final int[] mb = {OverlayPrefs.M_FPS, OverlayPrefs.M_CPU, OverlayPrefs.M_TEMP, OverlayPrefs.M_GPU, OverlayPrefs.M_RAM, OverlayPrefs.M_BAT, OverlayPrefs.M_GRAPH};
        int mask = OverlayPrefs.mask(this);
        for (int i = 0; i < mn.length; i++) {
            final int bit = mb[i];
            CheckBox cb = new CheckBox(this);
            cb.setText(mn[i]);
            cb.setTextColor(Color.WHITE);
            cb.setChecked((mask & bit) != 0);
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                    SharedPreferences sp = OverlayPrefs.sp(GameModeActivity.this);
                    int m = sp.getInt("mask", OverlayPrefs.M_ALL);
                    m = on ? (m | bit) : (m & ~bit);
                    sp.edit().putInt("mask", m).apply();
                    OverlayService.refresh(GameModeActivity.this);
                }
            });
            c4.addView(cb);
        }
        CheckBox cc = new CheckBox(this);
        cc.setText("Compact (single line)");
        cc.setTextColor(Color.WHITE);
        cc.setChecked(OverlayPrefs.compact(this));
        cc.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                OverlayPrefs.sp(GameModeActivity.this).edit().putBoolean("compact", on).apply();
                OverlayService.refresh(GameModeActivity.this);
            }
        });
        c4.addView(cc);
        col.addView(c4);

        setContentView(sv);
    }

    private void boostNow() {
        tvBoost.setText("Boosting...");
        new Thread(new Runnable() {
            @Override public void run() {
                String g = Booster.perfGovernor();
                if (!g.isEmpty()) Booster.setGovernor(GameModeActivity.this, g);
                Booster.markManual(GameModeActivity.this);
                final String r = Booster.cleanRam(GameModeActivity.this, true) + (g.isEmpty() ? "" : " · governor: performance");
                runOnUiThread(new Runnable() { @Override public void run() { tvBoost.setText(r); } });
            }
        }).start();
    }

    private void restoreNow() {
        tvBoost.setText("Restoring...");
        new Thread(new Runnable() {
            @Override public void run() {
                Booster.revertAll(GameModeActivity.this);
                runOnUiThread(new Runnable() { @Override public void run() { tvBoost.setText("Settings restored"); } });
            }
        }).start();
    }

    private void setSwitch(boolean v) {
        ignore = true;
        swOverlay.setChecked(v);
        ignore = false;
    }

    private void askOverlayPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {}
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean can = Settings.canDrawOverlays(this);
        tvPerm.setText(can ? "" : "Tap here to allow \"Display over other apps\"");
        tvPerm.setVisibility(can ? View.GONE : View.VISIBLE);
        setSwitch(can && OverlayPrefs.enabled(this));
        boolean auto = can && GameProfiles.auto(this);
        ignore = true; swAuto.setChecked(auto); ignore = false;
        if (auto) GameService.start(this);
        boolean pan = can && GameProfiles.manual(this);
        ignore = true; swPanel.setChecked(pan); ignore = false;
        if (pan) GameService.panelOn(this);
        int n = GameProfiles.games(this).size();
        tvGames.setText(n == 0 ? "Choose your games and set a profile for each" : n + (n == 1 ? " game" : " games") + " · tap to edit profiles or start one");
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean root = RootShell.hasRoot();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        tvRoot.setText(root ? "Root: granted" : "Root: not available (CPU, GPU and temperature need root)");
                        tvRoot.setTextColor(root ? Color.parseColor("#00E676") : Color.parseColor("#FF6B35"));
                    }
                });
            }
        }).start();
    }
}
