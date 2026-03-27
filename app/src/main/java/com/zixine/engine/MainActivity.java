package com.zixine.engine;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.io.DataOutputStream;

public class MainActivity extends Activity {
    // KONFIGURASI PAKET
    private final String GAMES = "com.dts.freefireth com.dts.freefiremax com.mobile.legends com.tencent.ig com.pubg.imobile com.miHoYo.GenshinImpact com.hoYoverse.hkrpg com.riotgames.league.wildrift com.garena.game.codm";
    private final String WHITELIST = "com.zcqptx.dcwihze com.termux android com.android.systemui com.miui.home com.zixine.engine com.android.settings com.miui.securitycenter com.android.phone com.android.server.telecom";
    private final String BLACKLIST_MANUAL = "com.facebook.katana com.facebook.orca com.instagram.android com.ss.android.ugc.trill com.zhiliaoapp.musically com.whatsapp com.whatsapp.w4b com.twitter.android com.shopee.id com.tokopedia.tkpd com.lazada.android com.google.android.youtube com.google.android.apps.docs com.google.android.apps.photos com.google.android.gm com.netflix.mediaclient com.spotify.music";

    private Button btnGms, btnExt, btnPerf;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("NarukamiV27", MODE_PRIVATE);
        btnGms = findViewById(R.id.btn_gms);
        btnExt = findViewById(R.id.btn_extreme);
        btnPerf = findViewById(R.id.btn_perf);
        
        updateUI();

        // LOGIC GMS (DISABLE-USER)
        btnGms.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("gms", false);
            String target = "com.google.android.gms com.android.vending com.google.android.gsf";
            if (active) {
                execRoot("for p in " + target + "; do pm disable-user --user 0 $p; done; am force-stop com.google.android.gms;");
                Toast.makeText(this, "GMS: KOMA 💀", Toast.LENGTH_SHORT).show();
            } else {
                execRoot("for p in " + target + "; do pm enable $p; done;");
                Toast.makeText(this, "GMS: AKTIF 🌍", Toast.LENGTH_SHORT).show();
            }
            save("gms", active);
        }));

        // LOGIC EXTREME (HYBRID SUSPEND)
        btnExt.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("ext", false);
            if (active) {
                // 1. Suspend Blacklist Manual + 2. Scan Apps Pihak Ketiga (-3)
                String cmd = "for p in " + BLACKLIST_MANUAL + "; do pm suspend --user 0 $p; am force-stop $p; done; " +
                             "PKGS=$(pm list packages -3 | cut -d ':' -f2); " +
                             "for p in $PKGS; do " +
                             "  MATCH=false; " +
                             "  for w in " + WHITELIST + " " + GAMES + " " + BLACKLIST_MANUAL + "; do [ \"$p\" == \"$w\" ] && MATCH=true && break; done; " +
                             "  [ \"$MATCH\" == \"false\" ] && pm suspend --user 0 $p && am force-stop $p; " +
                             "done; pm disable com.miui.powerkeeper/.statemachine.PowerStateMachineService;";
                execRoot(cmd);
                Toast.makeText(this, "EXTREME: SEMUA TIDUR 🛡️", Toast.LENGTH_SHORT).show();
            } else {
                // Unsuspend SEMUA aplikasi yang pernah di-suspend
                execRoot("PKGS=$(pm list packages -u | cut -d ':' -f2); for p in $PKGS; do pm unsuspend --user 0 $p & done; pm enable com.miui.powerkeeper/.statemachine.PowerStateMachineService;");
                Toast.makeText(this, "EXTREME: NORMAL 🌍", Toast.LENGTH_SHORT).show();
            }
            save("ext", active);
        }));

        // LOGIC PERFORMANCE
        btnPerf.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("perf", false);
            if (active) {
                execRoot("setprop touch.pressure.scale 0.001; setprop persist.sys.composition.type gpu; setprop debug.cpurenderer true;");
                Toast.makeText(this, "PERF: RATA KANAN 🚀", Toast.LENGTH_SHORT).show();
            } else {
                execRoot("setprop touch.pressure.scale 1.0; setprop persist.sys.composition.type c2d;");
                Toast.makeText(this, "PERF: NORMAL 🌍", Toast.LENGTH_SHORT).show();
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

    private void animate(View v, Runnable r) { 
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).withEndAction(() -> { 
            v.animate().scaleX(1f).scaleY(1f).setDuration(70).withEndAction(r).start(); 
        }).start(); 
    }

    private void execRoot(String c) { 
        try { 
            Process p = Runtime.getRuntime().exec("su"); 
            DataOutputStream o = new DataOutputStream(p.getOutputStream()); 
            o.writeBytes(c + "\nexit\n"); 
            o.flush(); 
        } catch (Exception ignored) {} 
    }
}