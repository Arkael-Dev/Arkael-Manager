package com.arkael.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pick which installed apps are games, edit each game's profile, or start one with its profile applied. */
public class GameListActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LocaleWrap.wrap(base));
    }

    private static final class Item {
        String pkg, name;
        android.graphics.drawable.Drawable icon;
        boolean hint;
    }

    private static final int[] ZRAM_MB = {0, 2048, 3072, 4096, 6144, 8192};
    private final List<Item> items = new ArrayList<Item>();
    private BaseAdapter adapter;
    private TextView empty;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0A0D12"));
        getWindow().setNavigationBarColor(Color.parseColor("#0A0D12"));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Color.parseColor("#0A0D12"));
        col.setPadding(dp(16), dp(24), dp(16), 0);

        TextView h = new TextView(this);
        h.setText("Games & profiles");
        h.setTextColor(Color.WHITE);
        h.setTextSize(24);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        col.addView(h);
        TextView sub = new TextView(this);
        sub.setText("Tick the apps that are games. Tap a game to edit its profile, PLAY to start it with the profile applied. Auto game mode applies the profile whenever a ticked game opens.");
        sub.setTextColor(Color.parseColor("#8B95A3"));
        sub.setTextSize(12);
        sub.setPadding(0, dp(4), 0, dp(10));
        col.addView(sub);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        String[] toolNames = {"Backup", "Restore", "Stats", "Clean"};
        for (int ti = 0; ti < toolNames.length; ti++) {
            final int tk = ti;
            android.widget.Button tb = new android.widget.Button(this);
            tb.setText(toolNames[ti]);
            tb.setAllCaps(false);
            tb.setTextSize(12);
            tb.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (tk == 0) ProfileTools.backup(GameListActivity.this);
                    else if (tk == 1) ProfileTools.restore(GameListActivity.this);
                    else if (tk == 2) ProfileTools.stats(GameListActivity.this);
                    else ProfileTools.cleanNow(GameListActivity.this);
                }
            });
            tools.addView(tb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        col.addView(tools);

        empty = new TextView(this);
        empty.setText("Loading apps...");
        empty.setTextColor(Color.parseColor("#8B95A3"));
        empty.setPadding(0, dp(20), 0, 0);
        col.addView(empty);

        ListView lv = new ListView(this);
        lv.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        lv.setDividerHeight(dp(8));
        lv.setClipToPadding(false);
        lv.setPadding(0, 0, 0, dp(16));
        adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) { return row(items.get(i)); }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                Item it = items.get(i);
                if (!GameProfiles.isGame(GameListActivity.this, it.pkg)) {
                    GameProfiles.setGame(GameListActivity.this, it.pkg, true);
                    adapter.notifyDataSetChanged();
                }
                editProfile(it);
            }
        });
        col.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(col);
        load();
    }

    private View row(final Item it) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(8), dp(8), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#141A22"));
        g.setCornerRadius(dp(12));
        r.setBackground(g);

        ImageView ic = new ImageView(this);
        ic.setImageDrawable(it.icon);
        r.addView(ic, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView n = new TextView(this);
        n.setText(it.name);
        n.setTextColor(Color.WHITE);
        n.setTextSize(14);
        n.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        n.setSingleLine(true);
        TextView s = new TextView(this);
        s.setText(it.hint ? "Looks like a game" : it.pkg);
        s.setTextColor(it.hint ? Color.parseColor("#00E5FF") : Color.parseColor("#8B95A3"));
        s.setTextSize(10);
        s.setSingleLine(true);
        t.addView(n);
        t.addView(s);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = dp(12);
        r.addView(t, tl);

        boolean game = GameProfiles.isGame(this, it.pkg);
        if (game) {
            TextView play = new TextView(this);
            play.setText("PLAY");
            play.setTextColor(Color.parseColor("#0A0D12"));
            play.setTextSize(12);
            play.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            play.setPadding(dp(12), dp(7), dp(12), dp(7));
            GradientDrawable pg = new GradientDrawable();
            pg.setColor(Color.parseColor("#00E5FF"));
            pg.setCornerRadius(dp(8));
            play.setBackground(pg);
            play.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { play(it); }
            });
            r.addView(play);
        }
        CheckBox cb = new CheckBox(this);
        cb.setChecked(game);
        cb.setFocusable(false);
        cb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                GameProfiles.setGame(GameListActivity.this, it.pkg, ((CheckBox) v).isChecked());
                adapter.notifyDataSetChanged();
            }
        });
        r.addView(cb);
        return r;
    }

    private void load() {
        new Thread(new Runnable() {
            @Override public void run() {
                PackageManager pm = getPackageManager();
                Intent main = new Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
                Set<String> seen = new HashSet<String>();
                final List<Item> r = new ArrayList<Item>();
                for (ResolveInfo ri : ris) {
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                    Item it = new Item();
                    it.pkg = pkg;
                    it.name = String.valueOf(ri.loadLabel(pm));
                    it.icon = ri.loadIcon(pm);
                    try {
                        it.hint = Build.VERSION.SDK_INT >= 26 && ri.activityInfo.applicationInfo.category == ApplicationInfo.CATEGORY_GAME;
                    } catch (Exception ignored) {}
                    r.add(it);
                }
                final Set<String> games = GameProfiles.games(GameListActivity.this);
                Collections.sort(r, new Comparator<Item>() {
                    @Override public int compare(Item a, Item b) {
                        boolean ga = games.contains(a.pkg), gb = games.contains(b.pkg);
                        if (ga != gb) return ga ? -1 : 1;
                        if (a.hint != b.hint) return a.hint ? -1 : 1;
                        return a.name.compareToIgnoreCase(b.name);
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        items.clear();
                        items.addAll(r);
                        adapter.notifyDataSetChanged();
                        empty.setVisibility(r.isEmpty() ? View.VISIBLE : View.GONE);
                        empty.setText("No launchable apps found");
                    }
                });
            }
        }).start();
    }

    // ---------------------------------------------------------------- PLAY

    private void play(final Item it) {
        final GameProfiles.Profile p = GameProfiles.get(this, it.pkg);
        final Intent launch = getPackageManager().getLaunchIntentForPackage(it.pkg);
        if (launch == null) { Toast.makeText(this, "Cannot open " + it.name, Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(this, "Preparing " + it.name + "...", Toast.LENGTH_SHORT).show();
        if (!GameProfiles.auto(this) && Settings.canDrawOverlays(this)) {   // so the settings are put back when you leave
            GameProfiles.setAuto(this, true);
            GameService.start(this);
        }
        new Thread(new Runnable() {
            @Override public void run() {
                Booster.applyProfile(GameListActivity.this, p, true);
                if (AppDownscale.get(GameListActivity.this, it.pkg) != 100) AppDownscale.applyFor(GameListActivity.this, it.pkg);
                runOnUiThread(new Runnable() { @Override public void run() { startActivity(launch); } });
            }
        }).start();
    }

    // ---------------------------------------------------------------- profile editor

    private TextView lbl(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextColor(Color.parseColor("#00E5FF"));
        t.setPadding(0, dp(12), 0, dp(2));
        return t;
    }

    private void editProfile(final Item it) {
        final GameProfiles.Profile p = GameProfiles.get(this, it.pkg);
        Toast.makeText(this, "Reading device options...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                final List<String> govs = Booster.governors();
                final Booster.GpuInfo gpu = Booster.gpu();
                runOnUiThread(new Runnable() { @Override public void run() { showEditor(it, p, govs, gpu); } });
            }
        }).start();
    }

    private void showEditor(final Item it, final GameProfiles.Profile p, final List<String> govs, final Booster.GpuInfo gpu) {
        final AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        Context dc = b.getContext();
        ScrollView sv = new ScrollView(dc);
        LinearLayout col = new LinearLayout(dc);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(8), dp(20), dp(4));
        sv.addView(col);

        boolean hasRoot = !govs.isEmpty() || gpu.ok();
        if (!hasRoot) {
            TextView w = new TextView(dc);
            w.setText("No governor or GPU controls found. Root is needed for most options.");
            w.setTextColor(Color.parseColor("#FF6B35"));
            w.setTextSize(12);
            col.addView(w);
        }

        col.addView(lbl(dc, "CPU GOVERNOR"));
        final List<String> gl = new ArrayList<String>();
        gl.add("Default (don't change)");
        gl.addAll(govs);
        final Spinner spGov = new Spinner(dc);
        spGov.setAdapter(new ArrayAdapter<String>(dc, android.R.layout.simple_spinner_dropdown_item, gl));
        spGov.setSelection(Math.max(0, govs.indexOf(p.gov) + 1));
        col.addView(spGov);

        final List<Integer> gv = new ArrayList<Integer>();
        final Spinner spGpu = new Spinner(dc);
        col.addView(lbl(dc, "GPU MAX CLOCK"));
        if (gpu.ok()) {
            List<String> gn = new ArrayList<String>();
            gn.add("Default (don't change)");
            gv.add(0);
            for (int i = gpu.mhz.length - 1; i >= 0; i--) { gn.add(gpu.mhz[i] + " MHz"); gv.add(gpu.mhz[i]); }
            spGpu.setAdapter(new ArrayAdapter<String>(dc, android.R.layout.simple_spinner_dropdown_item, gn));
            spGpu.setSelection(Math.max(0, gv.indexOf(p.gpuMaxMhz)));
            col.addView(spGpu);
        } else {
            TextView n = new TextView(dc);
            n.setText("Not supported on this device");
            n.setTextColor(Color.parseColor("#8B95A3"));
            n.setTextSize(12);
            col.addView(n);
        }

        col.addView(lbl(dc, "ZRAM SIZE (applied when you press PLAY)"));
        List<String> zn = new ArrayList<String>();
        int zsel = 0;
        for (int i = 0; i < ZRAM_MB.length; i++) {
            zn.add(ZRAM_MB[i] == 0 ? "Default (don't change)" : ZRAM_MB[i] + " MB");
            if (ZRAM_MB[i] == p.zramMb) zsel = i;
        }
        final Spinner spZ = new Spinner(dc);
        spZ.setAdapter(new ArrayAdapter<String>(dc, android.R.layout.simple_spinner_dropdown_item, zn));
        spZ.setSelection(zsel);
        col.addView(spZ);

        col.addView(lbl(dc, "DOWNSCALE (game resolution, applied on PLAY)"));
        final android.widget.Button btnDs = new android.widget.Button(dc);
        btnDs.setAllCaps(false);
        final Runnable refreshDs = new Runnable() {
            @Override public void run() {
                btnDs.setText("Downscale: " + AppDownscale.label(GameListActivity.this, AppDownscale.get(GameListActivity.this, it.pkg)));
            }
        };
        refreshDs.run();
        btnDs.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { AppDownscale.showPicker(GameListActivity.this, it.pkg, it.name, refreshDs); }
        });
        col.addView(btnDs);

        col.addView(lbl(dc, "REFRESH RATE LOCK (Auto game mode)"));
        final android.widget.Button btnHz = new android.widget.Button(dc);
        btnHz.setAllCaps(false);
        final Runnable refreshHz = new Runnable() {
            @Override public void run() {
                btnHz.setText("Refresh rate: " + RefreshLock.label(RefreshLock.get(GameListActivity.this, it.pkg)));
            }
        };
        refreshHz.run();
        btnHz.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String[] names = new String[RefreshLock.HZ.length];
                int cur = 0;
                for (int i = 0; i < names.length; i++) {
                    names[i] = RefreshLock.label(RefreshLock.HZ[i]);
                    if (RefreshLock.HZ[i] == RefreshLock.get(GameListActivity.this, it.pkg)) cur = i;
                }
                new android.app.AlertDialog.Builder(GameListActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Refresh rate · " + it.name)
                        .setSingleChoiceItems(names, cur, new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface dlg, int which) {
                                RefreshLock.set(GameListActivity.this, it.pkg, RefreshLock.HZ[which]);
                                refreshHz.run();
                                dlg.dismiss();
                            }
                        }).show();
            }
        });
        col.addView(btnHz);

        col.addView(lbl(dc, "ON GAME START"));
        final CheckBox cThermal = cb(dc, "Relax thermal control (stops thermal services, restored at 78°C)", p.thermalRelax);
        final CheckBox cClean = cb(dc, "Clean RAM", p.cleanRam);
        final CheckBox cDnd = cb(dc, "Do not disturb", p.dnd);
        final CheckBox cHeads = cb(dc, "Block pop-up notifications", p.headsUp);
        final CheckBox cOv = cb(dc, "Show performance overlay", p.overlay);
        col.addView(cThermal); col.addView(cClean); col.addView(cDnd); col.addView(cHeads); col.addView(cOv);

        b.setTitle(it.name);
        b.setView(sv);
        b.setNegativeButton("Cancel", null);
        b.setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int w) {
                GameProfiles.Profile n = new GameProfiles.Profile();
                int gi = spGov.getSelectedItemPosition();
                n.gov = gi > 0 ? gl.get(gi) : "";
                int pi = spGpu.getSelectedItemPosition();
                n.gpuMaxMhz = gpu.ok() && pi >= 0 && pi < gv.size() ? gv.get(pi) : 0;
                n.zramMb = ZRAM_MB[Math.max(0, spZ.getSelectedItemPosition())];
                n.thermalRelax = cThermal.isChecked();
                n.cleanRam = cClean.isChecked();
                n.dnd = cDnd.isChecked();
                n.headsUp = cHeads.isChecked();
                n.overlay = cOv.isChecked();
                GameProfiles.save(GameListActivity.this, it.pkg, n);
                Toast.makeText(GameListActivity.this, "Saved", Toast.LENGTH_SHORT).show();
            }
        });
        b.show();
    }

    private CheckBox cb(Context c, String text, boolean on) {
        CheckBox x = new CheckBox(c);
        x.setText(text);
        x.setChecked(on);
        return x;
    }
}
