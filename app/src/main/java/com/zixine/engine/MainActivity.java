package com.zixine.engine;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    // LIST GAME MANUAL (Sesuai Permintaan)
    private String GAMES = "com.dts.freefireth|com.dts.freefiremax|com.mobile.legends|com.tencent.ig|com.pubg.imobile|com.miHoYo.GenshinImpact|com.hoYoverse.hkrpg|com.riotgames.league.wildrift|com.garena.game.codm";
    private String WHITELIST = "com.zcqptx.dcwihze|com.termux|android|com.android.systemui|com.miui.home|com.zixine.engine|com.android.settings";

    private Button btnGms, btnExt, btnPerf;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("NarukamiV23", MODE_PRIVATE);
        btnGms = findViewById(R.id.btn_gms);
        btnExt = findViewById(R.id.btn_extreme);
        btnPerf = findViewById(R.id.btn_perf);

        updateUI();

        btnGms.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("gms", false);
            execRoot(active ? "pm suspend com.google.android.gms com.android.vending &" : "pm unsuspend com.google.android.gms com.android.vending &");
            save("gms", active);
        }));

        btnExt.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("ext", false);
            String cmd = active ? "PKGS=$(pm list packages -e | cut -d ':' -f2 | grep -Ev '" + WHITELIST + "|" + GAMES + "'); for p in $PKGS; do pm suspend $p & done;" : "PKGS=$(pm list packages -u | cut -d ':' -f2); for p in $PKGS; do pm unsuspend $p & done;";
            execRoot(cmd);
            save("ext", active);
        }));

        btnPerf.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("perf", false);
            if (active) {
                String on = "setprop touch.pressure.scale 0.001; setprop persist.sys.composition.type gpu; setprop debug.cpurenderer true; setprop persist.sys.perf.top_app 1; setprop ro.vendor.qti.sys.fw.bg_apps_limit 60; setprop net.tcp.2g_init_rwnd 10; setprop net.tcp.3g_init_rwnd 10; setprop net.tcp.gprs_init_rwnd 10;";
                execRoot(on);
                Toast.makeText(this, "PERFORMANCE ON ⚡", 0).show();
            } else {
                String off = "setprop touch.pressure.scale 1.0; setprop persist.sys.composition.type c2d; setprop debug.cpurenderer false; setprop persist.sys.perf.top_app 0; setprop ro.vendor.qti.sys.fw.bg_apps_limit 20;";
                execRoot(off);
                Toast.makeText(this, "PERFORMANCE NORMAL 🌍", 0).show();
            }
            save("perf", active);
        }));
    }

    private void save(String key, boolean val) {
        prefs.edit().putBoolean(key, val).apply();
        updateUI();
    }

    private void updateUI() {
        boolean g = prefs.getBoolean("gms", false);
        btnGms.setText(g ? "GMS: KILLED" : "GMS: NORMAL");
        btnGms.setBackgroundColor(g ? 0xFFFF3131 : 0xFF444444);

        boolean e = prefs.getBoolean("ext", false);
        btnExt.setText(e ? "EXTREME: SEALED" : "EXTREME: NORMAL");
        btnExt.setBackgroundColor(e ? 0xFFFF3131 : 0xFF007BFF);

        boolean p = prefs.getBoolean("perf", false);
        btnPerf.setText(p ? "PERFORMANCE: BOOST" : "PERFORMANCE: NORMAL");
        btnPerf.setBackgroundColor(p ? 0xFF00FF88 : 0xFF444444);
    }

    private void animate(View v, Runnable r) {
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).withEndAction(() -> {
            v.animate().scaleX(1f).scaleY(1f).setDuration(70).withEndAction(r).start();
        }).start();
    }

    private void execRoot(String c) {
        try {
            java.lang.Process p = Runtime.getRuntime().exec("su");
            DataOutputStream o = new DataOutputStream(p.getOutputStream());
            o.writeBytes(c + "\nexit\n"); o.flush();
        } catch (Exception ignored) {}
    }
}
