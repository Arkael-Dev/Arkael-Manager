package com.zixine.engine;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
    private static final String TAG = "ZIXINE_CORE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            Log.d(TAG, "Layout set successfully");

            prefs = getSharedPreferences("ZixineSecurePrefs", Context.MODE_PRIVATE);

            // Inisialisasi View
            tvRam = findViewById(R.id.tv_ram);
            tvZram = findViewById(R.id.tv_zram);
            tvCpu = findViewById(R.id.tv_cpu);
            tvBattery = findViewById(R.id.tv_battery);

            updateUIState();

            // Tombol Unlock
            Button btnUnlock = findViewById(R.id.btn_unlock);
            if (btnUnlock != null) {
                btnUnlock.setOnClickListener(v -> {
                    EditText input = findViewById(R.id.input_code);
                    String code = input.getText().toString().trim();
                    if (code.equals(BuildConfig.SECRET_PASSKEY)) {
                        prefs.edit().putString("secured_pass_hash", SecurityUtils.generateHash(BuildConfig.SECRET_PASSKEY)).apply();
                        updateUIState();
                        Toast.makeText(this, "ACCESS GRANTED", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "INVALID PASSKEY", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Tombol Dashboard
            findViewById(R.id.btn_cpu_gov).setOnClickListener(v -> showGovPicker());
            findViewById(R.id.btn_clean_ram).setOnClickListener(v -> {
                Toast.makeText(this, "CLEANING RAM...", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    runCmdSu("sync; echo 3 > /proc/sys/vm/drop_caches; am kill-all");
                    runOnUiThread(() -> { finishAffinity(); System.exit(0); });
                }).start();
            });

        } catch (Exception e) {
            Log.e(TAG, "FATAL ONCREATE: " + e.getMessage());
        }
    }

    private void updateUIState() {
        boolean isVerified = SecurityUtils.isSystemVerified(this);
        Log.d(TAG, "Verification Status: " + isVerified);
        
        findViewById(R.id.layout_locked).setVisibility(isVerified ? View.GONE : View.VISIBLE);
        findViewById(R.id.layout_verified).setVisibility(isVerified ? View.VISIBLE : View.GONE);
        
        if (isVerified) startDashboard();
    }

    private void startDashboard() {
        if (updater != null) return;
        updater = new Runnable() {
            @Override
            public void run() {
                refreshStats();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updater);
    }

    private void refreshStats() {
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
            if (tvRam != null) tvRam.setText((mi.availMem / 1048576) + " MB Free");

            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (tvBattery != null) tvBattery.setText(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "%");

            String gov = runCmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
            if (tvCpu != null) tvCpu.setText(gov.toUpperCase());

            String zramSize = runCmd("cat /sys/block/zram0/disksize");
            if (tvZram != null) {
                if (zramSize.isEmpty() || zramSize.equals("0")) tvZram.setText("OFF");
                else tvZram.setText((Long.parseLong(zramSize) / 1048576) + " MB");
            }
        } catch (Exception e) {
            Log.e(TAG, "Stats Error: " + e.getMessage());
        }
    }

    private void showGovPicker() {
        String raw = runCmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors");
        if (raw.isEmpty()) return;
        String[] govs = raw.split(" ");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Select CPU Governor")
                .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, govs), (d, w) -> {
                    new Thread(() -> runCmdSu("for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo " + govs[w] + " > $c; done")).start();
                    Toast.makeText(this, "Set to: " + govs[w], Toast.LENGTH_SHORT).show();
                }).show();
    }

    private String runCmd(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String res = br.readLine();
            return (res != null) ? res.trim() : "";
        } catch (Exception e) { return ""; }
    }

    private void runCmdSu(String cmd) {
        try { Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); } catch (Exception e) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updater != null) handler.removeCallbacks(updater);
    }
}
