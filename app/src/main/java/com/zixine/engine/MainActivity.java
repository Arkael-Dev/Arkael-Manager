package com.zixine.engine;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    // LIST GAME MANUAL (EDIT DISINI)
    private String GAMES = "com.dts.freefireth com.dts.freefiremax com.mobile.legends com.tencent.ig com.pubg.imobile com.miHoYo.GenshinImpact com.hoYoverse.hkrpg com.riotgames.league.wildrift com.garena.game.codm";
    // WHITELIST (JANGAN DISUSPEND)
    private String WHITELIST = "com.zcqptx.dcwihze com.termux android com.android.systemui com.miui.home com.zixine.engine com.android.settings com.miui.securitycenter com.android.phone com.android.server.telecom";

    private Button btnGms, btnExt, btnPerf;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("NarukamiV25", MODE_PRIVATE);
        btnGms = findViewById(R.id.btn_gms);
        btnExt = findViewById(R.id.btn_extreme);
        btnPerf = findViewById(R.id.btn_perf);
        updateUI();

        btnGms.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("gms", false);
            String target = "com.google.android.gms com.android.vending com.google.android.gsf";
            if (active) {
                execRoot("pm suspend --user 0 " + target + "; am force-stop com.google.android.gms;");
                Toast.makeText(this, "GMS: KILLED 💀", 0).show();
            } else {
                execRoot("pm unsuspend --user 0 " + target + ";");
                Toast.makeText(this, "GMS: RESTORED 🌍", 0).show();
            }
            save("gms", active);
        }));

        btnExt.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("ext", false);
            if (active) {
                String cmd = "PKGS=$(pm list packages -e | cut -d ':' -f2); " +
                             "for p in $PKGS; do " +
                             "  MATCH=false; " +
                             "  for w in " + WHITELIST + " " + GAMES + "; do [ \"$p\" == \"$w\" ] && MATCH=true && break; done; " +
                             "  [ \"$MATCH\" == \"false\" ] && pm suspend --user 0 $p && am force-stop $p; " +
                             "done; pm disable com.miui.powerkeeper/.statemachine.PowerStateMachineService;";
                execRoot(cmd);
                Toast.makeText(this, "EXTREME: SEALED 🛡️", 0).show();
            } else {
                execRoot("PKGS=$(pm list packages -u | cut -d ':' -f2); for p in $PKGS; do pm unsuspend --user 0 $p & done; pm enable com.miui.powerkeeper/.statemachine.PowerStateMachineService;");
                Toast.makeText(this, "SYSTEM: NORMAL 🌍", 0).show();
            }
            save("ext", active);
        }));

        btnPerf.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("perf", false);
            if (active) {
                execRoot("setprop touch.pressure.scale 0.001; setprop persist.sys.composition.type gpu; setprop debug.cpurenderer true; setprop persist.sys.perf.top_app 1; setprop net.tcp.2g_init_rwnd 10;");
                Toast.makeText(this, "PERF: ON 🚀", 0).show();
            } else {
                execRoot("setprop touch.pressure.scale 1.0; setprop persist.sys.composition.type c2d; setprop debug.cpurenderer false; setprop persist.sys.perf.top_app 0;");
                Toast.makeText(this, "PERF: NORMAL 🌍", 0).show();
            }
            save("perf", active);
        }));
    }

    private void save(String k, boolean v) { prefs.edit().putBoolean(k, v).apply(); updateUI(); }
    private void updateUI() {
        boolean g = prefs.getBoolean("gms", false); btnGms.setBackgroundColor(g ? 0xFFFF3131 : 0xFF444444);
        boolean e = prefs.getBoolean("ext", false); btnExt.setBackgroundColor(e ? 0xFFFF3131 : 0xFF007BFF);
        boolean p = prefs.getBoolean("perf", false); btnPerf.setBackgroundColor(p ? 0xFF00FF88 : 0xFF444444);
    }
    private void animate(View v, Runnable r) { v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).withEndAction(() -> { v.animate().scaleX(1f).scaleY(1f).setDuration(70).withEndAction(r).start(); }).start(); }
    private void execRoot(String c) { try { java.lang.Process p = Runtime.getRuntime().exec("su"); DataOutputStream o = new DataOutputStream(p.getOutputStream()); o.writeBytes(c + "\nexit\n"); o.flush(); } catch (Exception ignored) {} }
}
