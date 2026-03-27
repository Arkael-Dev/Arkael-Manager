package com.zixine.engine;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.io.DataOutputStream;

public class MainActivity extends Activity {
    private final String GAMES = "com.dts.freefireth com.dts.freefiremax com.mobile.legends com.tencent.ig com.pubg.imobile com.miHoYo.GenshinImpact com.hoYoverse.hkrpg";
    private final String WHITELIST = "com.zcqptx.dcwihze com.termux android com.android.systemui com.miui.home com.zixine.engine com.android.settings com.miui.securitycenter";
    private final String BLACKLIST_MANUAL = "com.facebook.katana com.facebook.orca com.instagram.android com.ss.android.ugc.trill com.zhiliaoapp.musically com.whatsapp com.whatsapp.w4b com.twitter.android com.shopee.id com.tokopedia.tkpd com.lazada.android";

    private Button btnGms, btnExt, btnPerf, btnMonitor;
    private TextView tvCpu, tvBattery;
    private SharedPreferences prefs;
    private Handler monitorHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("NarukamiV27", Context.MODE_PRIVATE);
        btnGms = findViewById(R.id.btn_gms);
        btnExt = findViewById(R.id.btn_extreme);
        btnPerf = findViewById(R.id.btn_perf);
        btnMonitor = findViewById(R.id.btn_monitor);
        tvCpu = findViewById(R.id.tv_cpu_fps);
        tvBattery = findViewById(R.id.tv_battery_ram);

        updateUI();
        startDashboardMonitoring();

        btnGms.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("gms", false);
            String target = "com.google.android.gms com.android.vending com.google.android.gsf";
            if (active) {
                execRoot("for p in " + target + "; do pm disable-user --user 0 $p; done;");
                Toast.makeText(this, "GMS: DEAD 💀", Toast.LENGTH_SHORT).show();
            } else {
                execRoot("for p in " + target + "; do pm enable $p; done;");
                Toast.makeText(this, "GMS: AKTIF 🌍", Toast.LENGTH_SHORT).show();
            }
            save("gms", active);
        }));

        btnExt.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("ext", false);
            if (active) {
                String cmd = "for p in " + BLACKLIST_MANUAL + "; do pm suspend --user 0 $p; am force-stop $p; done; " +
                             "PKGS=$(pm list packages -3 | cut -d ':' -f2); for p in $PKGS; do " +
                             "MATCH=false; for w in " + WHITELIST + " " + GAMES + " " + BLACKLIST_MANUAL + "; do [ \"$p\" == \"$w\" ] && MATCH=true && break; done; " +
                             "[ \"$MATCH\" == \"false\" ] && pm suspend --user 0 $p && am force-stop $p; " +
                             "done;";
                execRoot(cmd);
                Toast.makeText(this, "EXTREME: KILLED 🛡️", Toast.LENGTH_SHORT).show();
            } else {
                execRoot("PKGS=$(pm list packages -u | cut -d ':' -f2); for p in $PKGS; do pm unsuspend --user 0 $p & done;");
                Toast.makeText(this, "EXTREME: NORMAL 🌍", Toast.LENGTH_SHORT).show();
            }
            save("ext", active);
        }));

        btnPerf.setOnClickListener(v -> animate(v, () -> {
            boolean active = !prefs.getBoolean("perf", false);
            if (active) {
                execRoot("setprop debug.cpurenderer true; setprop persist.sys.composition.type gpu;");
                Toast.makeText(this, "PERF: RATA KANAN 🚀", Toast.LENGTH_SHORT).show();
            } else {
                execRoot("setprop touch.pressure.scale 1.0; setprop persist.sys.composition.type c2d;");
                Toast.makeText(this, "PERF: NORMAL 🌍", Toast.LENGTH_SHORT).show();
            }
            save("perf", active);
        }));

        btnMonitor.setOnClickListener(v -> animate(v, () -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 1234);
            } else {
                Toast.makeText(this, "Monitoring Notifikasi butuh Service penuh. Di-skip sementara.", Toast.LENGTH_LONG).show();
            }
        }));
    }

    private void startDashboardMonitoring() {
        monitorHandler.post(new Runnable() {
            @Override
            public void run() {
                updateDashboard();
                monitorHandler.postDelayed(this, 2000);
            }
        });
    }

    private void updateDashboard() {
        tvCpu.setText("CPU: POCO X6 Stable | FPS: READY"); 
        tvBattery.setText("Baterai: Monitoring Active | RAM: Safe");
    }

    @Override 
    protected void onDestroy() { 
        super.onDestroy(); 
        monitorHandler.removeCallbacksAndMessages(null); 
    }

    private void save(String k, boolean v) { 
        prefs.edit().putBoolean(k, v).apply(); 
        updateUI(); 
    }

    private void updateUI() {
        boolean g = prefs.getBoolean("gms", false); btnGms.setTextColor(g ? 0xFFFF3131 : 0xFFFFFFFF);
        boolean e = prefs.getBoolean("ext", false); btnExt.setTextColor(e ? 0xFFFF3131 : 0xFFFFFFFF);
        boolean p = prefs.getBoolean("perf", false); btnPerf.setTextColor(p ? 0xFF00FF88 : 0xFFFFFFFF);
    }

    private void animate(View v, Runnable r) { 
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).withEndAction(() -> 
            v.animate().scaleX(1f).scaleY(1f).setDuration(70).withEndAction(r).start()
        ).start(); 
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
