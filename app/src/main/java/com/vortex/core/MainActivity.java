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
    private TextView tvKernel, tvUptime, tvLoad, tvBattHealth, tvBattTemp, tvBattStatus;
    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("VortexPrefs", 0);
        
        // Top Monitor
        tvRam = findViewById(R.id.tv_ram);
        tvZram = findViewById(R.id.tv_zram);
        tvCpu = findViewById(R.id.tv_cpu);
        tvBattery = findViewById(R.id.tv_battery);

        // System & Battery Cards
        tvKernel = findViewById(R.id.tv_kernel);
        tvUptime = findViewById(R.id.tv_uptime);
        tvLoad = findViewById(R.id.tv_load);
        tvBattHealth = findViewById(R.id.tv_batt_health);
        tvBattTemp = findViewById(R.id.tv_batt_temp);
        tvBattStatus = findViewById(R.id.tv_batt_status);

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
                updateSystemAndBatteryInfo();
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

    private void updateSystemAndBatteryInfo() {
        try {
            // System Info
            String kernelFull = runSuReturn("cat /proc/version");
            // Ambil 50 karakter pertama biar muat di layar
            if(tvKernel != null) tvKernel.setText("Kernel: " + (kernelFull.length() > 50 ? kernelFull.substring(0, 50) + "..." : kernelFull));

            String uptimeRaw = runCmd("cat /proc/uptime").split(" ")[0];
            long seconds = (long) Double.parseDouble(uptimeRaw);
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            long minutes = (seconds % 3600) / 60;
            if(tvUptime != null) tvUptime.setText("Uptime: " + days + "d " + hours + "h " + minutes + "m");

            String load = runCmd("cat /proc/loadavg").split(" ")[0];
            if(tvLoad != null) tvLoad.setText("Load Avg: " + load);

            // Battery Info
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            int health = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_HEALTH);
            String healthStr = "Unknown";
            switch (health) {
                case BatteryManager.BATTERY_HEALTH_COLD: healthStr = "Cold"; break;
                case BatteryManager.BATTERY_HEALTH_DEAD: healthStr = "Dead"; break;
                case BatteryManager.BATTERY_HEALTH_GOOD: healthStr = "Good"; break;
                case BatteryManager.BATTERY_HEALTH_OVERHEAT: healthStr = "Overheat"; break;
                case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: healthStr = "Over Voltage"; break;
                case BatteryManager.BATTERY_HEALTH_UNKNOWN: healthStr = "Unknown"; break;
                case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: healthStr = "Failure"; break;
            }
            if(tvBattHealth != null) tvBattHealth.setText("Health: " + healthStr);

            int temp = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_TEMPERATURE) / 10;
            if(tvBattTemp != null) tvBattTemp.setText("Temp: " + temp + "°C");

            int status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
            String statusStr = (status == BatteryManager.BATTERY_STATUS_CHARGING) ? "Charging" : "Discharging";
            if(tvBattStatus != null) tvBattStatus.setText("Status: " + statusStr);

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

    private void cleanRam() {
        new Thread(() -> {
            runSu("sync; echo 3 > /proc/sys/vm/drop_caches; am kill-all");
            runOnUiThread(() -> { finishAffinity(); System.exit(0); });
        }).start();
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
