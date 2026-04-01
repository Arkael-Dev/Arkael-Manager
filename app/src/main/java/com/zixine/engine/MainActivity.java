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
            // Pasang Layout
            setContentView(R.layout.activity_main);
            
            // Inisialisasi SharedPreferences
            prefs = getSharedPreferences("ZixineSecurePrefs", Context.MODE_PRIVATE);

            // Cari View ID (Wajib Sinkron sama XML)
            tvRam = findViewById(R.id.tv_ram);
            tvZram = findViewById(R.id.tv_zram);
            tvCpu = findViewById(R.id.tv_cpu);
            tvBattery = findViewById(R.id.tv_battery);

            // Cek Keamanan & Update UI
            refreshSecurityUI();

            // Logika Tombol Unlock
            Button btnUnlock = findViewById(R.id.btn_unlock);
            if (btnUnlock != null) {
                btnUnlock.setOnClickListener(v -> {
                    EditText input = findViewById(R.id.input_code);
                    if (input != null && input.getText().toString().trim().equals(BuildConfig.SECRET_PASSKEY)) {
                        prefs.edit().putString("secured_pass_hash", SecurityUtils.generateHash(BuildConfig.SECRET_PASSKEY)).apply();
                        refreshSecurityUI();
                        Toast.makeText(this, "ACCESS GRANTED", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "INVALID CODE", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Logika Tombol Governor
            View btnGov = findViewById(R.id.btn_cpu_gov);
            if (btnGov != null) btnGov.setOnClickListener(v -> openGovPicker());

            // Logika Tombol Clean RAM
            View btnClean = findViewById(R.id.btn_clean_ram);
            if (btnClean != null) btnClean.setOnClickListener(v -> runBrutalClean());

        } catch (Exception e) {
            // Jika terjadi kegagalan render, munculkan pesan error
            Toast.makeText(this, "Render Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshSecurityUI() {
        boolean isVerified = SecurityUtils.isSystemVerified(this);
        View lockedLayout = findViewById(R.id.layout_locked);
        View dashLayout = findViewById(R.id.layout_verified);

        if (lockedLayout != null) lockedLayout.setVisibility(isVerified ? View.GONE : View.VISIBLE);
        if (dashLayout != null) dashLayout.setVisibility(isVerified ? View.VISIBLE : View.GONE);

        if (isVerified) startDashboardLoop();
    }

    private void startDashboardLoop() {
        if (updater != null) return;
        updater = new Runnable() {
            @Override
            public void run() {
                updateStats();
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(updater);
    }

    private void updateStats() {
        try {
            // RAM
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
            if (tvRam != null) tvRam.setText((mi.availMem / 1048576) + " MB Free");

            // Battery
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (tvBattery != null) tvBattery.setText(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "%");

            // CPU
            String gov = runShell("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
            if (tvCpu != null) tvCpu.setText(gov.toUpperCase());

            // ZRAM
            String zram = runShell("cat /sys/block/zram0/disksize");
            if (tvZram != null) {
                if (zram.isEmpty() || zram.equals("0")) tvZram.setText("OFF");
                else tvZram.setText((Long.parseLong(zram) / 1048576) + " MB");
            }
        } catch (Exception ignored) {}
    }

    private void runBrutalClean() {
        Toast.makeText(this, "BRUTAL CLEANING...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "sync; echo 3 > /proc/sys/vm/drop_caches; am kill-all"}).waitFor();
            } catch (Exception e) {}
            runOnUiThread(() -> { finishAffinity(); System.exit(0); });
        }).start();
    }

    private void openGovPicker() {
        String available = runShell("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors");
        if (available.isEmpty()) return;
        String[] govs = available.split(" ");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select Governor")
            .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, govs), (d, w) -> {
                new Thread(() -> {
                    try { Runtime.getRuntime().exec(new String[]{"su", "-c", "for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo " + govs[w] + " > $c; done"}).waitFor(); } catch (Exception e) {}
                }).start();
            }).show();
    }

    private String runShell(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String res = br.readLine();
            return (res != null) ? res.trim() : "";
        } catch (Exception e) { return ""; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updater != null) handler.removeCallbacks(updater);
    }
}
