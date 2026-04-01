package com.zixine.engine;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
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
            
            prefs = getSharedPreferences("ZixineSecurePrefs", Context.MODE_PRIVATE);
            
            tvRam = findViewById(R.id.tv_ram);
            tvZram = findViewById(R.id.tv_zram);
            tvCpu = findViewById(R.id.tv_cpu);
            tvBattery = findViewById(R.id.tv_battery);

            updateUIState();

            findViewById(R.id.btn_unlock).setOnClickListener(v -> {
                EditText input = findViewById(R.id.input_code);
                if (input.getText().toString().trim().equals(BuildConfig.SECRET_PASSKEY)) {
                    prefs.edit().putString("secured_pass_hash", SecurityUtils.generateHash(BuildConfig.SECRET_PASSKEY)).apply();
                    updateUIState();
                } else {
                    Toast.makeText(this, "KODE SALAH!", Toast.LENGTH_SHORT).show();
                }
            });

            findViewById(R.id.btn_cpu_gov).setOnClickListener(v -> showGovPicker());
            findViewById(R.id.btn_clean_ram).setOnClickListener(v -> {
                Toast.makeText(this, "CLEANING...", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    try { Runtime.getRuntime().exec(new String[]{"su", "-c", "sync; echo 3 > /proc/sys/vm/drop_caches; am kill-all"}).waitFor(); } catch (Exception e) {}
                    runOnUiThread(() -> { finishAffinity(); System.exit(0); });
                }).start();
            });

        } catch (Exception e) {
            android.util.Log.e("ZIXINE", "Error: " + e.getMessage());
        }
    }

    private void updateUIState() {
        boolean verified = SecurityUtils.isSystemVerified(this);
        findViewById(R.id.layout_locked).setVisibility(verified ? View.GONE : View.VISIBLE);
        findViewById(R.id.layout_verified).setVisibility(verified ? View.VISIBLE : View.GONE);
        if (verified) startDash();
    }

    private void startDash() {
        updater = new Runnable() {
            @Override public void run() {
                refreshStats();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updater);
    }

    private void refreshStats() {
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
            tvRam.setText((mi.availMem/1048576) + "MB Free");
            
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            tvBattery.setText(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "%");
            
            String gov = runCmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
            tvCpu.setText(gov.toUpperCase());
            
            String zsize = runCmd("cat /sys/block/zram0/disksize");
            tvZram.setText(zsize.isEmpty() ? "OFF" : (Long.parseLong(zsize)/1048576) + "MB");
        } catch (Exception e) {}
    }

    private void showGovPicker() {
        String[] govs = runCmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors").split(" ");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Governor")
            .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, govs), (d, w) -> {
                new Thread(() -> runCmdSu("for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo " + govs[w] + " > $c; done")).start();
            }).show();
    }

    private String runCmd(String c) {
        try { return new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(c).getInputStream())).readLine().trim(); } catch (Exception e) { return ""; }
    }
    
    private void runCmdSu(String c) {
        try { Runtime.getRuntime().exec(new String[]{"su", "-c", c}).waitFor(); } catch (Exception e) {}
    }

    @Override protected void onDestroy() { super.onDestroy(); if(updater != null) handler.removeCallbacks(updater); }
}
