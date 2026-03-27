package com.zixine.engine;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import com.google.android.material.card.MaterialCardView;
import java.io.DataOutputStream;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView tvCpu, tvBattery;
    private Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("zixine_v29", MODE_PRIVATE);
        tvCpu = findViewById(R.id.tv_cpu_fps);
        tvBattery = findViewById(R.id.tv_battery_ram);

        findViewById(R.id.card_gms).setOnClickListener(v -> toggleGms());
        findViewById(R.id.card_extreme).setOnClickListener(v -> toggleExtreme());
        findViewById(R.id.card_perf).setOnClickListener(v -> togglePerf());
        findViewById(R.id.card_monitor).setOnClickListener(v -> startService(new Intent(this, MonitorService.class)));

        h.post(new Runnable() { @Override public void run() { updateDash(); h.postDelayed(this, 2000); }});
    }

    private void updateDash() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        tvCpu.setText("Model: POCO X6 5G | Baterai: " + bat.getIntExtra("level", 0) + "%");
        tvBattery.setText("RAM: " + (mi.totalMem - mi.availMem)/1048576L/1024 + "GB / " + mi.totalMem/1048576L/1024 + "GB");
    }

    private void toggleGms() {
        boolean act = !prefs.getBoolean("gms", false);
        exec(act ? "for p in com.google.android.gms com.android.vending; do pm disable-user --user 0 $p; done" : "pm enable com.google.android.gms; pm enable com.android.vending");
        prefs.edit().putBoolean("gms", act).apply();
        Toast.makeText(this, act ? "GMS: DEAD 💀" : "GMS: ALIVE 🌍", 0).show();
    }

    private void toggleExtreme() {
        boolean act = !prefs.getBoolean("ext", false);
        exec(act ? "PKGS=$(pm list packages -3 | cut -d ':' -f2); for p in $PKGS; do [ \"$p\" != \"com.zixine.engine\" ] && pm suspend --user 0 $p; done" : "PKGS=$(pm list packages -u | cut -d ':' -f2); for p in $PKGS; do pm unsuspend --user 0 $p; done");
        prefs.edit().putBoolean("ext", act).apply();
        Toast.makeText(this, act ? "EXTREME: ACTIVE 🔥" : "EXTREME: OFF 🌍", 0).show();
    }

    private void togglePerf() {
        boolean act = !prefs.getBoolean("perf", false);
        exec(act ? "setprop debug.cpurenderer true; setprop persist.sys.composition.type gpu" : "setprop debug.cpurenderer false");
        prefs.edit().putBoolean("perf", act).apply();
        Toast.makeText(this, "PERF: " + (act ? "ULTRA 🚀" : "NORMAL"), 0).show();
    }

    private void exec(String c) { try { java.lang.Process p = Runtime.getRuntime().exec("su"); DataOutputStream o = new DataOutputStream(p.getOutputStream()); o.writeBytes(c + "\nexit\n"); o.flush(); } catch (Exception e) {} }
}
