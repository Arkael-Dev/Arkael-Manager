package com.zixine.engine;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    // DAFTAR GAME MANUAL (PASTIKAN TIDAK ADA TYPO)
    private String GAMES = "com.dts.freefireth|com.dts.freefiremax|com.mobile.legends|com.tencent.ig|com.pubg.imobile|com.miHoYo.GenshinImpact|com.hoYoverse.hkrpg|com.riotgames.league.wildrift|com.garena.game.codm";
    // WHITELIST SISTEM (AGAR HP TIDAK BOOTLOOP/FREEZE)
    private String WHITELIST = "com.zcqptx.dcwihze|com.termux|android|com.android.systemui|com.miui.home|com.zixine.engine|com.android.settings";

    private Button btnGms, btnExt, btnPerf;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("NarukamiV24", MODE_PRIVATE);
        btnGms = findViewById(R.id.btn_gms);
        btnExt = findViewById(R.id.btn_extreme);
        btnPerf = findViewById(R.id.btn_perf);

        updateUI();

        // 1. GMS KILLER (V24 JUDGEMENT)
        btnGms.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("gms", false);
            if (active) {
                execRoot("pm suspend --user 0 com.google.android.gms com.android.vending; " +
                         "am force-stop com.google.android.gms; am force-stop com.android.vending;");
                Toast.makeText(this, "GMS: EXECUTED! 💀", 0).show();
            } else {
                execRoot("pm unsuspend --user 0 com.google.android.gms com.android.vending;");
                Toast.makeText(this, "GMS: RESTORED 🌍", 0).show();
            }
            save("gms", active);
        }));

        // 2. EXTREME SEAL (SURE KILL - TANPA & AGAR PASTI KENA SEMUA)
        btnExt.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("ext", false);
            if (active) {
                // Sikat satu-satu biar gak ada yang lolos (Grep filter)
                String cmd = "PKGS=$(pm list packages -e | cut -d ':' -f2 | grep -Ev '" + WHITELIST + "|" + GAMES + "'); " +
                             "for p in $PKGS; do pm suspend --user 0 $p; done; " +
                             "am kill-all; pm disable com.miui.powerkeeper/.statemachine.PowerStateMachineService;";
                execRoot(cmd);
                Toast.makeText(this, "EXTREME SEAL: ACTIVE ⚡", 0).show();
            } else {
                // Bangunkan semua (Gunakan --user 0 agar sinkron)
                execRoot("PKGS=$(pm list packages -u | cut -d ':' -f2); " +
                         "for p in $PKGS; do pm unsuspend --user 0 $p; done; " +
                         "pm enable com.miui.powerkeeper/.statemachine.PowerStateMachineService;");
                Toast.makeText(this, "SYSTEM: RESTORED 🌍", 0).show();
            }
            save("ext", active);
        }));

        // 3. PERFORMANCE OVERDRIVE (SCRIPT OPTIMASI)
        btnPerf.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("perf", false);
            if (active) {
                String on = "setprop touch.pressure.scale 0.001; setprop persist.sys.composition.type gpu; " +
                            "setprop debug.cpurenderer true; setprop persist.sys.perf.top_app 1; " +
                            "setprop ro.vendor.qti.sys.fw.bg_apps_limit 60; setprop net.tcp.2g_init_rwnd 10;";
                execRoot(on);
                Toast.makeText(this, "PERFORMANCE: BOOSTED 🚀", 0).show();
            } else {
                String off = "setprop touch.pressure.scale 1.0; setprop persist.sys.composition.type c2d; " +
                             "setprop debug.cpurenderer false; setprop persist.sys.perf.top_app 0;";
                execRoot(off);
                Toast.makeText(this, "PERFORMANCE: NORMAL 🌍", 0).show();
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
            // Menggunakan java.lang.Process untuk menghindari error ambigu di GitHub
            java.lang.Process p = Runtime.getRuntime().exec("su");
            DataOutputStream o = new DataOutputStream(p.getOutputStream());
            o.writeBytes(c + "\nexit\n"); o.flush();
        } catch (Exception ignored) {}
    }
}
