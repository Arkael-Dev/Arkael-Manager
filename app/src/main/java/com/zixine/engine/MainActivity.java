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
            // Pasang layout dulu
            setContentView(R.layout.activity_main);
            
            // Inisialisasi ID dengan hati-hati
            tvRam = findViewById(R.id.tv_ram);
            tvZram = findViewById(R.id.tv_zram);
            tvCpu = findViewById(R.id.tv_cpu);
            tvBattery = findViewById(R.id.tv_battery);
            
            prefs = getSharedPreferences("ZixineSecurePrefs", Context.MODE_PRIVATE);

            // Cek status login
            updateUIState();

            // Tombol Unlock
            View btnUnlock = findViewById(R.id.btn_unlock);
            if (btnUnlock != null) {
                btnUnlock.setOnClickListener(v -> {
                    EditText input = findViewById(R.id.input_code);
                    if (input != null && input.getText().toString().trim().equals(BuildConfig.SECRET_PASSKEY)) {
                        prefs.edit().putString("secured_pass_hash", SecurityUtils.generateHash(BuildConfig.SECRET_PASSKEY)).apply();
                        updateUIState();
                    } else {
                        Toast.makeText(this, "PASSKEY SALAH!", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Tombol Dashboad
            View btnGov = findViewById(R.id.btn_cpu_gov);
            if (btnGov != null) btnGov.setOnClickListener(v -> showGovPicker());

            View btnClean = findViewById(R.id.btn_clean_ram);
            if (btnClean != null) btnClean.setOnClickListener(v -> executeClean());

        } catch (Exception e) {
            // Jika tetap error, munculkan pesan agar tidak blank putih
            Toast.makeText(this, "UI Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateUIState() {
        boolean verified = SecurityUtils.isSystemVerified(this);
        View locked = findViewById(R.id.layout_locked);
        View verifiedLayout = findViewById(R.id.layout_verified);
        
        if (locked != null) locked.setVisibility(verified ? View.GONE : View.VISIBLE);
        if (verifiedLayout != null) verifiedLayout.setVisibility(verified ? View.VISIBLE : View.GONE);
        
        if (verified) startDash();
    }

    private void startDash() {
        if (updater != null) return;
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
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null) {
                am.getMemoryInfo(mi);
                if (tvRam != null) tvRam.setText((mi.availMem / 1048576) + " MB Free");
            }

            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (bm != null && tvBattery != null) {
                tvBattery.setText(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "%");
            }

            String gov = runCmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
            if (tvCpu != null) tvCpu.setText(gov.isEmpty() ? "UNKNOWN" : gov.toUpperCase());

            String zsize = runCmd("cat /sys/block/zram0/disksize");
            if (tvZram != null) {
                if (zsize.isEmpty() || zsize.equals("0")) tvZram.setText("OFF / 0 MB");
                else tvZram.setText((Long.parseLong(zsize) / 1048576) + " MB");
            }
        } catch (Exception e) {
            android.util.Log.e("ZIXINE", "Stats Error: " + e.getMessage());
        }
    }

    private void executeClean() {
        Toast.makeText(this, "BRUTAL CLEANING...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "sync; echo 3 > /proc/sys/vm/drop_caches; am kill-all"}).waitFor();
            } catch (Exception e) {}
            runOnUiThread(() -> { finishAffinity(); System.exit(0); });
        }).start();
    }

    private void showGovPicker() {
        try {
            String rawGovs = runCmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors");
            if (rawGovs.isEmpty()) {
                Toast.makeText(this, "Root needed or Not Supported", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] govs = rawGovs.split(" ");
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Select Governor")
                .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, govs), (d, w) -> {
                    new Thread(() -> {
                        try { Runtime.getRuntime().exec(new String[]{"su", "-c", "for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo " + govs[w] + " > $c; done"}).waitFor(); } catch (Exception e) {}
                    }).start();
                }).show();
        } catch (Exception e) {}
    }

    private String runCmd(String c) {
        try {
            Process p = Runtime.getRuntime().exec(c);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            return (line != null) ? line.trim() : "";
        } catch (Exception e) { return ""; }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (updater != null) handler.removeCallbacks(updater);
    }
}
