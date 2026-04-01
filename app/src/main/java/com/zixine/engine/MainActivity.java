package com.zixine.engine;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private TextView tvRam, tvZram, tvCpu, tvBattery;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);
            
            tvRam = findViewById(R.id.tv_ram);
            tvZram = findViewById(R.id.tv_zram);
            tvCpu = findViewById(R.id.tv_cpu);
            tvBattery = findViewById(R.id.tv_battery);
            prefs = getSharedPreferences("ZixineSecurePrefs", Context.MODE_PRIVATE);

            updateUI();

            Button btnUnlock = findViewById(R.id.btn_unlock);
            if (btnUnlock != null) {
                btnUnlock.setOnClickListener(v -> {
                    EditText input = findViewById(R.id.input_code);
                    if (input != null && input.getText().toString().trim().equals(BuildConfig.SECRET_PASSKEY)) {
                        prefs.edit().putString("secured_pass_hash", SecurityUtils.generateHash(BuildConfig.SECRET_PASSKEY)).apply();
                        updateUI();
                    } else {
                        Toast.makeText(this, "SALAH!", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            findViewById(R.id.btn_cpu_gov).setOnClickListener(v -> openGov());
            findViewById(R.id.btn_clean_ram).setOnClickListener(v -> clean());

        } catch (Exception e) {
            Toast.makeText(this, "Critical Init Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateUI() {
        boolean v = SecurityUtils.isSystemVerified(this);
        findViewById(R.id.layout_locked).setVisibility(v ? View.GONE : View.VISIBLE);
        findViewById(R.id.layout_verified).setVisibility(v ? View.VISIBLE : View.GONE);
        if (v) startLoop();
    }

    private void startLoop() {
        updater = new Runnable() {
            @Override public void run() {
                refresh();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updater);
    }

    private void refresh() {
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
            if (tvRam != null) tvRam.setText((mi.availMem / 1048576) + " MB Free");

            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (tvBattery != null) tvBattery.setText(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "%");

            String g = cmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
            if (tvCpu != null) tvCpu.setText(g.toUpperCase());

            String z = cmd("cat /sys/block/zram0/disksize");
            if (tvZram != null) tvZram.setText(z.isEmpty() ? "OFF" : (Long.parseLong(z)/1048576) + " MB");
        } catch (Exception ignored) {}
    }

    private void clean() {
        new Thread(() -> {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", "sync; echo 3 > /proc/sys/vm/drop_caches; am kill-all"}).waitFor(); } catch (Exception e) {}
            runOnUiThread(() -> { finishAffinity(); System.exit(0); });
        }).start();
    }

    private void openGov() {
        String r = cmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors");
        if (r.isEmpty()) return;
        String[] gs = r.split(" ");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Governor")
            .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, gs), (d, w) -> {
                new Thread(() -> {
                    try { Runtime.getRuntime().exec(new String[]{"su", "-c", "for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo " + gs[w] + " > $c; done"}).waitFor(); } catch (Exception e) {}
                }).start();
            }).show();
    }

    private String cmd(String c) {
        try { return new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(c).getInputStream())).readLine().trim(); } catch (Exception e) { return ""; }
    }

    @Override protected void onDestroy() { super.onDestroy(); if (updater != null) handler.removeCallbacks(updater); }
}
