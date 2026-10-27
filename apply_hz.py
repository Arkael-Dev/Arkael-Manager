import os, sys
D = "app/src/main/java/com/arkael/app/"
def rd(n): return open(D + n, encoding="utf-8").read()
def need(s, a, n):
    if s.count(a) != 1:
        print("GAGAL: anchor tidak unik/hilang di %s: %s" % (n, a[:70])); sys.exit(1)

if os.path.exists(D + "RefreshLock.java"):
    print("SUDAH DITERAPKAN"); sys.exit(0)

gl = rd("GameListActivity.java"); gs = rd("GameService.java")
A_UI  = 'col.addView(lbl(dc, "ON GAME START"));'
A_ENT = 'if (AppDownscale.get(this, pkg) != 100) AppDownscale.applyFor(this, pkg);'
A_LV  = 'toast("Game mode off, settings restored");'
A_LEFT = '} else if (!isGame && Booster.leftover(this)) {'
need(gl, A_UI, "GameListActivity.java")
for a in (A_ENT, A_LV, A_LEFT): need(gs, a, "GameService.java")

ui = '''col.addView(lbl(dc, "REFRESH RATE LOCK (Auto game mode)"));
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

        '''
gl = gl.replace(A_UI, ui + A_UI)
gs = gs.replace(A_ENT, A_ENT + '\n        RefreshLock.apply(this, pkg);')
gs = gs.replace(A_LV, 'RefreshLock.restore(this);\n        ' + A_LV)
gs = gs.replace(A_LEFT, '} else if (!isGame && RefreshLock.pending(this)) {\n                RefreshLock.restore(this);\n            ' + A_LEFT)

java = '''package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Kunci refresh rate per game (butuh root). Dipasang saat game masuk, dikembalikan saat keluar (Auto game mode). */
final class RefreshLock {
    private static final String PREFS = "ark_refresh";
    static final int[] HZ = {0, 60, 90, 120, 144};
    private static final String[] KEYS = {"min_refresh_rate", "peak_refresh_rate", "user_refresh_rate"};

    static int get(Context c, String pkg) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("hz_" + pkg, 0);
    }

    static void set(Context c, String pkg, int hz) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (hz <= 0) e.remove("hz_" + pkg); else e.putInt("hz_" + pkg, hz);
        e.apply();
    }

    static String label(int hz) { return hz <= 0 ? "Default" : hz + " Hz"; }

    static boolean pending(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("saved", false);
    }

    /** Blokir, jalankan di thread latar. */
    static void apply(Context c, String pkg) {
        int hz = get(c, pkg);
        if (hz <= 0) return;
        try {
            if (!RootShell.hasRoot()) return;
            SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (!p.getBoolean("saved", false)) {
                SharedPreferences.Editor e = p.edit();
                for (String k : KEYS) {
                    String v = RootShell.run("settings get system " + k);
                    e.putString("o_" + k, v == null || v.trim().isEmpty() ? "null" : v.trim());
                }
                e.putBoolean("saved", true).apply();
            }
            for (String k : KEYS) {
                if (k.equals("user_refresh_rate") && "null".equals(p.getString("o_" + k, "null"))) continue;
                RootShell.run("settings put system " + k + " " + hz + " 2>&1");
            }
        } catch (Throwable ignored) {}
    }

    /** Kembalikan nilai asli sebelum dikunci. */
    static void restore(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("saved", false)) return;
        try {
            for (String k : KEYS) {
                String o = p.getString("o_" + k, "null");
                if ("null".equals(o)) RootShell.run("settings delete system " + k + " 2>&1");
                else RootShell.run("settings put system " + k + " " + o + " 2>&1");
            }
        } catch (Throwable ignored) {}
        SharedPreferences.Editor e = p.edit();
        for (String k : KEYS) e.remove("o_" + k);
        e.remove("saved").apply();
    }
}
'''
open(D + "RefreshLock.java", "w", encoding="utf-8").write(java)
open(D + "GameListActivity.java", "w", encoding="utf-8").write(gl)
open(D + "GameService.java", "w", encoding="utf-8").write(gs)
print("OK: kunci refresh rate (Default/60/90/120/144 Hz) masuk ke editor profil + Auto game mode")
