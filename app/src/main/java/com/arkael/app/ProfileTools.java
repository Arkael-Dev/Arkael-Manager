package com.arkael.app;

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
