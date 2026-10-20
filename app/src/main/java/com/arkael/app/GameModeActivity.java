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

/** Control screen for the performance overlay. Built in code, no layout XML. */
public class GameModeActivity extends Activity {
    private Switch swOverlay;
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
        TextView sub = label("Live CPU, GPU, RAM, temperature and battery on top of any game. Drag it anywhere, double-tap to switch between full and compact.", Color.parseColor("#8B95A3"), 12, false);
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
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button bt = new Button(this);
            bt.setText(names[i]);
            bt.setAllCaps(false);
            bt.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    OverlayPrefs.sp(GameModeActivity.this).edit().putInt("size", idx).apply();
                    OverlayService.refresh(GameModeActivity.this);
                }
            });
            sizes.addView(bt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        c3.addView(sizes);
        col.addView(c3);

        // --- metrics ---
        LinearLayout c4 = card();
        c4.addView(label("SHOW", Color.parseColor("#00E5FF"), 11, true));
        String[] mn = {"CPU load and frequency", "Temperature", "GPU load and frequency", "RAM", "Battery"};
        final int[] mb = {OverlayPrefs.M_CPU, OverlayPrefs.M_TEMP, OverlayPrefs.M_GPU, OverlayPrefs.M_RAM, OverlayPrefs.M_BAT};
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
