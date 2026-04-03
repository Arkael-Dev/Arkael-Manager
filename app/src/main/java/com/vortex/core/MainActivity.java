package com.vortex.core;

import android.app.ActivityManager;
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
    private TextView tvRam, tvZram, tvCpu, tvBattery;
    private TextView tvKernel;
    
    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("VortexPrefs", 0);
        
        // Monitor Atas
        tvRam = findViewById(R.id.tv_ram);
        tvZram = findViewById(R.id.tv_zram);
        tvCpu = findViewById(R.id.tv_cpu);
        tvBattery = findViewById(R.id.tv_battery);

        // System Kernel Only
        tvKernel = findViewById(R.id.tv_kernel);

        refreshUI();

        findViewById(R.id.btn_unlock).setOnClickListener(v -> {
            EditText input = findViewById(R.id.input_code);
            if (input.getText().toString().trim().equals(BuildConfig.SECRET_PASSKEY)) {
                prefs.edit().putString("pass_hash", SecurityUtils.generateHash(BuildConfig.SECRET_PASSKEY)).apply();
                refreshUI();
            } else {
                Toast.makeText(this, "DENIED", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_clean_ram).setOnClickListener(v -> cleanRam());
        findViewById(R.id.btn_cpu_gov).setOnClickListener(v -> pickGov());
        findViewById(R.id.btn_set_zram).setOnClickListener(v -> showZramMenu());
        findViewById(R.id.btn_thermal).setOnClickListener(v -> showThermalMenu());
    }

    private void refreshUI() {
        boolean ok = SecurityUtils.isSystemVerified(this);
        findViewById(R.id.layout_locked).setVisibility(ok ? View.GONE : View.VISIBLE);
        findViewById(R.id.layout_dashboard).setVisibility(ok ? View.VISIBLE : View.GONE);
        if (ok) startLoop();
    }

    private void startLoop() {
        handler.post(new Runnable() {
            @Override public void run() {
                updateStats();
                updateSystemInfo();
                handler.postDelayed(this, 2000);
            }
        });
    }

    private void updateStats() {
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
            if(tvRam != null) tvRam.setText("RAM: " + (mi.availMem / 1048576) + " MB Free");

            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if(tvBattery != null) tvBattery.setText("BAT: " + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "%");

            String gov = runCmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
            if(tvCpu != null) tvCpu.setText("GOV: " + gov.toUpperCase());

            String z = runSuReturn("cat /sys/block/zram0/disksize");
            if(tvZram != null) tvZram.setText("ZRAM: " + (z.isEmpty() ? "0" : (Long.parseLong(z)/1048576)) + " MB");

        } catch (Exception ignored) {}
    }

    private void updateSystemInfo() {
        try {
            String kernelFull = runSuReturn("cat /proc/version");
            if(kernelFull.isEmpty()) kernelFull = "Unknown Kernel";
            if(tvKernel != null) tvKernel.setText("Kernel: " + (kernelFull.length() > 45 ? kernelFull.substring(0, 45) + "..." : kernelFull));
        } catch (Exception ignored) {}
    }

    private void pickGov() {
        String[] govs = runCmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors").split(" ");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("CPU GOVERNOR")
            .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, govs), (d, w) -> {
                new Thread(() -> runSu("for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo " + govs[w] + " > $c; done")).start();
            }).show();
    }

    public void applyZram(int sizeGB) {
        new Thread(() -> {
            long sizeInBytes = sizeGB * 1073741824L;
            runSu("swapoff /dev/block/zram0 2>/dev/null");
            runSu("echo 1 > /sys/block/zram0/reset 2>/dev/null");
            runSu("echo " + sizeInBytes + " > /sys/block/zram0/disksize 2>/dev/null");
            runSu("echo zstd > /sys/block/zram0/comp_algorithm 2>/dev/null");
            runSu("mkswap /dev/block/zram0 2>/dev/null");
            runSu("swapon /dev/block/zram0 2>/dev/null");
            
            try { Thread.sleep(500); } catch (Exception e){}
            runOnUiThread(() -> updateStats());
        }).start();
    }

    public void showZramMenu() {
        final String[] options = {"4 GB", "8 GB", "12 GB", "16 GB", "Disable ZRAM"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select ZRAM Size");
        builder.setItems(options, (dialog, which) -> {
            if (which == 4) {
                new Thread(() -> {
                    runSu("swapoff /dev/block/zram0 2>/dev/null");
                    runSu("echo 1 > /sys/block/zram0/reset 2>/dev/null");
                    try { Thread.sleep(500); } catch (Exception e){}
                    runOnUiThread(() -> updateStats());
                }).start();
            } else {
                applyZram(new int[]{4, 8, 12, 16}[which]);
            }
            Toast.makeText(this, "ZRAM " + options[which] + " Applied", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    // --- THERMAL CONTROL FUNCTIONS ---
    public void showThermalMenu() {
        final String[] options = {"DISABLE THERMAL (Gaming)", "ENABLE THERMAL (Normal)"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Thermal Control");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // Disable Thermal
                new Thread(() -> {
                    // Stop common thermal daemons (Universal commands)
                    runSu("stop thermald 2>/dev/null");
                    runSu("stop thermal-engine 2>/dev/null");
                    runSu("setprop vendor.thermal.config 0 2>/dev/null");
                    runOnUiThread(() -> Toast.makeText(this, "Thermal DISABLED (Gaming Mode)", Toast.LENGTH_SHORT).show());
                }).start();
            } else {
                // Enable Thermal
                new Thread(() -> {
                    // Start thermal daemons
                    runSu("start thermald 2>/dev/null");
                    runSu("start thermal-engine 2>/dev/null");
                    runSu("setprop vendor.thermal.config 1 2>/dev/null");
                    runOnUiThread(() -> Toast.makeText(this, "Thermal ENABLED (Normal Mode)", Toast.LENGTH_SHORT).show());
                }).start();
            }
        });
        builder.show();
    }

    private void cleanRam() {
        new Thread(() -> {
            runSu("sync; echo 3 > /proc/sys/vm/drop_caches; am kill-all");
            runOnUiThread(() -> { finishAffinity(); System.exit(0); });
        }).start();
    }

    private String runCmd(String c) {
        try { return new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(c).getInputStream())).readLine().trim(); } catch (Exception e) { return ""; }
    }

    private void runSu(String c) {
        try { Runtime.getRuntime().exec(new String[]{"su", "-c", c}).waitFor(); } catch (Exception ignored) {}
    }

    private String runSuReturn(String c) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", c});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            p.waitFor();
            return (line == null) ? "" : line.trim();
        } catch (Exception e) { return ""; }
    }
}
