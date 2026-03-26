package com.zixine.engine;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    // 1. LIST GAME (Sesuai permintaan: Tulis Manual)
    private String GAMES = "com.dts.freefireth com.dts.freefiremax com.mobile.legends com.tencent.ig com.pubg.imobile com.miHoYo.GenshinImpact com.hoYoverse.hkrpg";
    
    // 2. WHITELIST (Aplikasi yang HARUS TETAP HIDUP)
    private String WHITELIST = "com.zcqptx.dcwihze com.termux android com.android.systemui com.miui.home com.zixine.engine com.android.settings";

    // 3. BLACKLIST (Aplikasi Populer yang mau dibantai)
    private String BLACKLIST = "com.facebook.katana com.facebook.orca com.instagram.android com.ss.android.ugc.trill com.zhiliaoapp.musically com.whatsapp com.whatsapp.w4b com.twitter.android com.shopee.id com.tokopedia.tkpd com.lazada.android com.google.android.youtube com.google.android.apps.docs com.google.android.apps.photos com.google.android.gm com.netflix.mediaclient";

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

        // TOGGLE EXTREME (DENGAN WHITELIST CHECK)
        btnExt.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("ext", false);
            if (active) {
                // SIKAT: Hanya yang ada di Blacklist DAN bukan bagian dari Whitelist/Game
                String cmd = "for p in " + BLACKLIST + "; do " +
                             "MATCH=false; " +
                             "for w in " + WHITELIST + " " + GAMES + "; do [ \"$p\" == \"$w\" ] && MATCH=true && break; done; " +
                             "[ \"$MATCH\" == \"false\" ] && pm suspend --user 0 $p && am force-stop $p; " +
                             "done; pm disable com.miui.powerkeeper/.statemachine.PowerStateMachineService;";
                execRoot(cmd);
                Toast.makeText(this, "EXTREME: SEALED 🛡️", 0).show();
            } else {
                // RESTORE: Bangunkan semua yang ada di daftar Blacklist
                String cmd = "for p in " + BLACKLIST + "; do pm unsuspend --user 0 $p; done; " +
                             "pm enable com.miui.powerkeeper/.statemachine.PowerStateMachineService;";
                execRoot(cmd);
                Toast.makeText(this, "EXTREME: NORMAL 🌍", 0).show();
            }
            save("ext", active);
        }));

        // GMS KILLER
        btnGms.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("gms", false);
            String target = "com.google.android.gms com.android.vending com.google.android.gsf";
            if (active) {
                execRoot("pm suspend --user 0 " + target + "; am force-stop com.google.android.gms;");
                Toast.makeText(this, "GMS: KILLED 💀", 0).show();
            } else {
                execRoot("pm unsuspend --user 0 " + target + ";");
                Toast.makeText(this, "GMS: ALIVE 🌍", 0).show();
            }
            save("gms", active);
        }));

        // PERFORMANCE
        btnPerf.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("perf", false);
            if (active) {
                execRoot("setprop touch.pressure.scale 0.001; setprop persist.sys.composition.type gpu;");
                Toast.makeText(this, "PERFORMANCE: ON ⚡", 0).show();
            } else {
                execRoot("setprop touch.pressure.scale 1.0; setprop persist.sys.composition.type c2d;");
                Toast.makeText(this, "PERFORMANCE: OFF 🌍", 0).show();
            }
            save("perf", active);
        }));
    }

    private void save(String key, boolean val) { prefs.edit().putBoolean(key, val).apply(); updateUI(); }
    
    private void updateUI() {
        boolean g = prefs.getBoolean("gms", false);
        btnGms.setBackgroundColor(g ? 0xFFFF3131 : 0xFF444444);
        boolean e = prefs.getBoolean("ext", false);
        btnExt.setBackgroundColor(e ? 0xFFFF3131 : 0xFF007BFF);
        boolean p = prefs.getBoolean("perf", false);
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
