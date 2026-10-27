import os, sys
D = "app/src/main/java/com/arkael/app/"
MAN = "app/src/main/AndroidManifest.xml"
RES = "app/src/main/res/drawable/"
def rd(p): return open(p, encoding="utf-8").read()
def wr(p, s):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w", encoding="utf-8").write(s)
def uniq(s, a): return s.count(a) == 1
results = []

SESSION = r'''package com.arkael.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** Statistik sesi game: durasi, rata-rata FPS (kalau overlay hidup), suhu baterai tertinggi, baterai terpakai. */
final class SessionStats {
    private static final String PREFS = "ark_stats";
    private static String pkg;
    private static long t0, fpsSum;
    private static int bat0, fpsN;
    private static float maxT;

    private static int level(Context c) {
        try {
            Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return -1;
            int l = i.getIntExtra("level", -1), s = i.getIntExtra("scale", 100);
            return l < 0 ? -1 : Math.round(l * 100f / s);
        } catch (Throwable t) { return -1; }
    }

    static synchronized void begin(Context c, String p) {
        pkg = p; t0 = System.currentTimeMillis(); bat0 = level(c); maxT = 0; fpsSum = 0; fpsN = 0;
    }

    static synchronized void sample(Context c) {
        if (pkg == null) return;
        try { float t = BatteryInfo.temp(c); if (t > maxT) maxT = t; } catch (Throwable ignored) {}
    }

    static synchronized void fps(int f) {
        if (pkg != null && f > 0 && f <= 240) { fpsSum += f; fpsN++; }
    }

    /** Akhiri sesi. Mengembalikan ringkasan, atau null kalau sesi kurang dari 1 menit. */
    static synchronized String end(Context c) {
        if (pkg == null) return null;
        String p = pkg; pkg = null;
        long sec = (System.currentTimeMillis() - t0) / 1000;
        if (sec < 60) return null;
        int bat1 = level(c);
        StringBuilder sb = new StringBuilder();
        sb.append(sec >= 3600 ? (sec / 3600) + " h " + ((sec % 3600) / 60) + " min" : (sec / 60) + " min");
        if (fpsN > 0) sb.append(" · avg ").append(Math.round(fpsSum / (float) fpsN)).append(" FPS");
        if (maxT > 0) sb.append(" · battery max ").append(String.format(Locale.US, "%.1f", maxT)).append("°C");
        if (bat0 >= 0 && bat1 >= 0) sb.append(" · ").append(bat1 - bat0).append("% battery");
        String sum = sb.toString();
        try {
            JSONArray old = log(c), neu = new JSONArray();
            JSONObject e = new JSONObject();
            e.put("pkg", p); e.put("t", System.currentTimeMillis()); e.put("sum", sum);
            neu.put(e);
            for (int i = 0; i < old.length() && i < 19; i++) neu.put(old.get(i));
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("log", neu.toString()).apply();
        } catch (Throwable ignored) {}
        return sum;
    }

    static JSONArray log(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return new JSONArray(sp.getString("log", "[]"));
        } catch (Throwable t) { return new JSONArray(); }
    }
}
'''

TOOLS = r'''package com.arkael.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.StatFs;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Alat di layar Games & profiles: backup/impor profil, statistik sesi, bersih-bersih. */
final class ProfileTools {
    private static final String[] FILES = {"arkael_games", "ark_downscale", "ark_refresh"};
    private static final String OUT = "/sdcard/Download/arkael_backup.json";

    private static void say(final Activity a, final String m) {
        a.runOnUiThread(() -> Toast.makeText(a, m, Toast.LENGTH_LONG).show());
    }

    private static AlertDialog.Builder dlg(Activity a) {
        return new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    // ------------------------------------------------------------ backup / restore
    static void backup(final Activity a) {
        new Thread(() -> {
            try {
                if (!RootShell.hasRoot()) { say(a, "Backup needs root"); return; }
                JSONObject files = new JSONObject();
                int n = 0;
                for (String f : FILES) {
                    JSONObject o = new JSONObject();
                    for (Map.Entry<String, ?> e : a.getSharedPreferences(f, 0).getAll().entrySet()) {
                        String k = e.getKey();
                        if (f.equals("ark_refresh") && (k.equals("saved") || k.startsWith("o_"))) continue;
                        Object v = e.getValue();
                        JSONObject t = new JSONObject();
                        if (v instanceof Boolean) { t.put("t", "b"); t.put("v", ((Boolean) v).booleanValue()); }
                        else if (v instanceof Integer) { t.put("t", "i"); t.put("v", ((Integer) v).intValue()); }
                        else if (v instanceof Long) { t.put("t", "l"); t.put("v", ((Long) v).longValue()); }
                        else if (v instanceof Float) { t.put("t", "f"); t.put("v", ((Float) v).doubleValue()); }
                        else if (v instanceof String) { t.put("t", "s"); t.put("v", (String) v); }
                        else if (v instanceof Set) {
                            JSONArray arr = new JSONArray();
                            for (Object x : (Set<?>) v) arr.put(String.valueOf(x));
                            t.put("t", "ss"); t.put("v", arr);
                        } else continue;
                        o.put(k, t);
                        n++;
                    }
                    files.put(f, o);
                }
                JSONObject root = new JSONObject();
                root.put("app", "arkael-manager");
                root.put("version", 1);
                root.put("files", files);
                File tmp = new File(a.getCacheDir(), "arkael_backup.json");
                try (FileOutputStream fo = new FileOutputStream(tmp)) { fo.write(root.toString(1).getBytes("UTF-8")); }
                RootShell.run("cp '" + tmp.getAbsolutePath() + "' " + OUT + " 2>&1; chmod 666 " + OUT + " 2>&1");
                String chk = RootShell.run("ls -l " + OUT + " 2>&1");
                tmp.delete();
                boolean ok = chk != null && chk.contains("arkael_backup.json") && !chk.contains("No such");
                say(a, ok ? "Backup saved: Download/arkael_backup.json (" + n + " settings)" : "Backup failed");
            } catch (Throwable t) { say(a, "Backup failed: " + t.getMessage()); }
        }).start();
    }

    static void restore(final Activity a) {
        new Thread(() -> {
            try {
                if (!RootShell.hasRoot()) { say(a, "Restore needs root"); return; }
                String js = RootShell.run("cat " + OUT + " 2>&1");
                if (js == null || !js.contains("\"arkael-manager\"")) { say(a, "No valid backup in Download/arkael_backup.json"); return; }
                final JSONObject files = new JSONObject(js.trim()).getJSONObject("files");
                a.runOnUiThread(() -> dlg(a).setTitle("Restore backup")
                        .setMessage("This replaces your game profiles, Downscale and Refresh rate settings with the backup.")
                        .setPositiveButton("Restore", (d, w) -> new Thread(() -> applyBackup(a, files)).start())
                        .setNegativeButton("Cancel", null).show());
            } catch (Throwable t) { say(a, "Restore failed: " + t.getMessage()); }
        }).start();
    }

    private static void applyBackup(final Activity a, JSONObject files) {
        try {
            int n = 0;
            for (String f : FILES) {
                JSONObject o = files.optJSONObject(f);
                if (o == null) continue;
                SharedPreferences sp = a.getSharedPreferences(f, 0);
                SharedPreferences.Editor ed = sp.edit();
                if (f.equals("ark_refresh")) {
                    for (String k : new ArrayList<String>(sp.getAll().keySet())) if (k.startsWith("hz_")) ed.remove(k);
                } else ed.clear();
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject t = o.getJSONObject(k);
                    String ty = t.getString("t");
                    if (ty.equals("b")) ed.putBoolean(k, t.getBoolean("v"));
                    else if (ty.equals("i")) ed.putInt(k, t.getInt("v"));
                    else if (ty.equals("l")) ed.putLong(k, t.getLong("v"));
                    else if (ty.equals("f")) ed.putFloat(k, (float) t.getDouble("v"));
                    else if (ty.equals("s")) ed.putString(k, t.getString("v"));
                    else if (ty.equals("ss")) {
                        Set<String> s = new HashSet<String>();
                        JSONArray arr = t.getJSONArray("v");
                        for (int i = 0; i < arr.length(); i++) s.add(arr.getString(i));
                        ed.putStringSet(k, s);
                    } else continue;
                    n++;
                }
                ed.commit();
            }
            final int cnt = n;
            a.runOnUiThread(() -> {
                if (GameProfiles.auto(a)) {
                    if (Settings.canDrawOverlays(a)) GameService.start(a); else GameProfiles.setAuto(a, false);
                }
                Toast.makeText(a, "Restored " + cnt + " settings", Toast.LENGTH_LONG).show();
                a.recreate();
            });
        } catch (Throwable t) { say(a, "Restore failed: " + t.getMessage()); }
    }

    // ------------------------------------------------------------ statistik
    static void stats(Activity a) {
        JSONArray log = SessionStats.log(a);
        PackageManager pm = a.getPackageManager();
        SimpleDateFormat df = new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < log.length(); i++) {
            JSONObject e = log.optJSONObject(i);
            if (e == null) continue;
            String pkg = e.optString("pkg"), name = pkg;
            try { name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Throwable ignored) {}
            sb.append(name).append("  ·  ").append(df.format(new Date(e.optLong("t")))).append("\n")
              .append(e.optString("sum")).append("\n\n");
        }
        String msg = sb.length() == 0
                ? "No sessions yet.\nTurn on Auto game mode and play for at least a minute. Average FPS needs the performance overlay turned on."
                : sb.toString().trim();
        dlg(a).setTitle("Game sessions").setMessage(msg).setPositiveButton("OK", null).show();
    }

    // ------------------------------------------------------------ bersih-bersih
    static void cleanNow(final Activity a) {
        dlg(a).setTitle("Clean now")
                .setMessage("Clears app caches (pm trim-caches) and runs fstrim on /data. Needs root and takes about a minute. Caches are rebuilt as you use apps.")
                .setPositiveButton("Clean", (d, w) -> new Thread(() -> {
                    try {
                        if (!RootShell.hasRoot()) { say(a, "Clean needs root"); return; }
                        long before = free();
                        RootShell.run("pm trim-caches 999G 2>&1");
                        String ft = RootShell.run("fstrim -v /data 2>&1");
                        long after = free();
                        String line = ft == null ? "" : ft.trim().split("\n")[0];
                        final String msg = "Free space: " + mb(before) + " → " + mb(after)
                                + (after > before ? " (+" + mb(after - before) + ")" : "")
                                + (line.isEmpty() ? "" : "\nfstrim: " + line);
                        a.runOnUiThread(() -> dlg(a).setTitle("Clean done").setMessage(msg).setPositiveButton("OK", null).show());
                    } catch (Throwable t) { say(a, "Clean failed: " + t.getMessage()); }
                }).start())
                .setNegativeButton("Cancel", null).show();
    }

    private static long free() {
        try { return new StatFs("/data").getAvailableBytes(); } catch (Throwable t) { return 0; }
    }

    private static String mb(long b) {
        return b >= (1L << 30) ? String.format(Locale.US, "%.2f GB", b / 1073741824.0) : (b / 1048576) + " MB";
    }
}
'''

TILE = r'''package com.arkael.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Tile Quick Settings: nyalakan/matikan Auto game mode dari panel notifikasi. */
public class AutoTileService extends TileService {
    @Override public void onStartListening() { refresh(); }

    @Override public void onClick() {
        if (GameProfiles.auto(this)) {
            GameProfiles.setAuto(this, false);
            GameService.stop(this);
        } else {
            if (!Settings.canDrawOverlays(this)) { openApp(); return; }
            GameProfiles.setAuto(this, true);
            try {
                GameService.start(this);
            } catch (Throwable t) {
                GameProfiles.setAuto(this, false);
                openApp();
                return;
            }
        }
        refresh();
    }

    private void openApp() {
        Intent i = new Intent(this, GameListActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(GameProfiles.auto(this) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Auto game mode");
        t.updateTile();
    }
}
'''

BOLT = '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M13,2L5,13h6l-1,9 8,-11h-6z"/>
</vector>
'''

BUTTONS = '''LinearLayout tools = new LinearLayout(this);
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

        '''

# ===== A. Backup/Restore + Stats viewer + Clean (+ tombol di Games & profiles)
try:
    if os.path.exists(D + "ProfileTools.java"):
        results.append("= Backup/Stats/Clean: sudah ada")
    else:
        gl = rd(D + "GameListActivity.java")
        A = "empty = new TextView(this);"
        if not uniq(gl, A): raise Exception("anchor tombol tidak unik/hilang di GameListActivity.java")
        wr(D + "GameListActivity.java", gl.replace(A, BUTTONS + A))
        wr(D + "SessionStats.java", SESSION)
        wr(D + "ProfileTools.java", TOOLS)
        results.append("OK   : Backup, Restore, Stats, Clean (4 tombol di Games & profiles)")
except Exception as ex:
    results.append("GAGAL: Backup/Stats/Clean -> %s" % ex)

# ===== B. Pengumpul statistik sesi (butuh A)
try:
    if not os.path.exists(D + "SessionStats.java"):
        raise Exception("dilewati, blok A belum terpasang")
    gs = rd(D + "GameService.java")
    if "SessionStats.begin" in gs:
        results.append("= Pengumpul statistik: sudah ada")
    else:
        E = 'if (AppDownscale.get(this, pkg) != 100) AppDownscale.applyFor(this, pkg);'
        L = 'toast("Game mode off, settings restored");'
        T = 'if (!active.isEmpty() && thermalActive && (++tick % 4) == 0) checkHeat();'
        for a in (E, L, T):
            if not uniq(gs, a): raise Exception("anchor hilang di GameService.java: " + a[:50])
        gs = gs.replace(E, E + "\n        SessionStats.begin(this, pkg);")
        gs = gs.replace(L, L + "\n        { String ss = SessionStats.end(this); if (ss != null) toast(ss); }")
        gs = gs.replace(T, T + "\n        if (!active.isEmpty()) SessionStats.sample(this);")
        wr(D + "GameService.java", gs)
        msg = "OK   : Statistik sesi (durasi, suhu baterai, baterai terpakai)"
        ov = rd(D + "OverlayService.java")
        O = "main.post(new Runnable() { @Override public void run() { render(s, fps, temp); } });"
        if uniq(ov, O) and "SessionStats.fps" not in ov:
            wr(D + "OverlayService.java", ov.replace(O, "SessionStats.fps(fps);\n                " + O))
            msg += " + rata-rata FPS dari overlay"
        else:
            msg += " (FPS rata-rata belum terhubung: anchor OverlayService tidak cocok)"
        results.append(msg)
except Exception as ex:
    results.append("GAGAL: Pengumpul statistik -> %s" % ex)

# ===== C. Tile Quick Settings
try:
    if os.path.exists(D + "AutoTileService.java"):
        results.append("= Tile Quick Settings: sudah ada")
    else:
        mf = rd(MAN)
        M = "</application>"
        if not uniq(mf, M): raise Exception("</application> tidak unik/hilang di AndroidManifest.xml")
        svc = ('    <service android:name="com.arkael.app.AutoTileService" android:exported="true"\n'
               '        android:label="Auto game mode" android:icon="@drawable/ax_ic_tile"\n'
               '        android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">\n'
               '        <intent-filter><action android:name="android.service.quicksettings.action.QS_TILE"/></intent-filter>\n'
               '    </service>\n    ')
        wr(MAN, mf.replace(M, svc + M))
        wr(RES + "ax_ic_tile.xml", BOLT)
        wr(D + "AutoTileService.java", TILE)
        results.append("OK   : Tile Quick Settings 'Auto game mode'")
except Exception as ex:
    results.append("GAGAL: Tile Quick Settings -> %s" % ex)

print("\n".join(results))
