package com.vortex.core;

import android.app.ActivityManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
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
    private TextView tvKernel, tvDevice, tvTerminalLog;
    
    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("VortexPrefs", 0);
        
        tvRam = findViewById(R.id.tv_ram);
        tvZram = findViewById(R.id.tv_zram);
        tvCpu = findViewById(R.id.tv_cpu);
        tvBattery = findViewById(R.id.tv_battery);

        tvKernel = findViewById(R.id.tv_kernel);
        tvDevice = findViewById(R.id.tv_device);
        tvTerminalLog = findViewById(R.id.tv_terminal_log);
        
        if(tvTerminalLog != null) tvTerminalLog.setMovementMethod(new ScrollingMovementMethod());

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
            String brand = Build.BRAND;
            String model = Build.MODEL;
            if(tvDevice != null) tvDevice.setText("Device: " + brand.toUpperCase() + " " + model);

            String kernelFull = runSuReturn("cat /proc/version");
            if(kernelFull.isEmpty()) kernelFull = "Unknown Kernel";
            if(tvKernel != null) tvKernel.setText("Kernel: " + (kernelFull.length() > 40 ? kernelFull.substring(0, 40) + "..." : kernelFull));
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

    // --- THERMAL CONTROL (RAW GETPROP OUTPUT) ---
    public void showThermalMenu() {
        final String[] options = {"DISABLE THERMAL (Gaming)", "ENABLE THERMAL (Normal)"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Thermal Control");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // DISABLE
                new Thread(() -> {
                    runOnUiThread(() -> tvTerminalLog.setText("> Disabling Thermal Services..."));
                    
                    String thermalLoop = 
                        "for thermal in $(getprop | awk -F '[][]' '/thermal/ {print $2}'); do " +
                        "  if [ \"$(getprop $thermal)\" = \"running\" ]; then " +
                        "    stop ${thermal/init.svc.} 2>/dev/null; " +
                        "    resetprop -n $thermal stopped 2>/dev/null; " +
                        "  fi; " +
                        "done";
                    
                    runSu(thermalLoop);
                    
                    // PERBAIKAN: Tampilkan hasil MENTAH `getprop | grep thermal`
                    String result = runSuReturnAll("getprop | grep thermal");
                    final String logOutput = "> Thermal DISABLED.\n> Status Check (Full):\n" + result;
                    
                    runOnUiThread(() -> {
                        tvTerminalLog.setText(logOutput);
                        Toast.makeText(this, "Thermal DISABLED", Toast.LENGTH_SHORT).show();
                    });
                }).start();
            } else {
                // ENABLE
                new Thread(() -> {
                    runOnUiThread(() -> tvTerminalLog.setText("> Enabling Thermal Services..."));
                    
                    runSu("start thermald 2>/dev/null");
                    runSu("start thermal-engine 2>/dev/null");
                    runSu("start mi_thermald 2>/dev/null");
                    runSu("start android.thermal-hal 2>/dev/null");
                    runSu("setprop vendor.thermal.config 1 2>/dev/null");
                    
                    // PERBAIKAN: Tampilkan hasil MENTAH `getprop | grep thermal`
                    String result = runSuReturnAll("getprop | grep thermal");
                    final String logOutput = "> Thermal ENABLED.\n> Status Check (Full):\n" + result;

                    runOnUiThread(() -> {
                        tvTerminalLog.setText(logOutput);
                        Toast.makeText(this, "Thermal ENABLED", Toast.LENGTH_SHORT).show();
                    });
                }).start();
            }
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
    
    private String runSuReturnAll(String c) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", c});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            p.waitFor();
            return output.toString().trim();
        } catch (Exception e) { return ""; }
    }
    private void cleanRam() {
        new Thread(() -> {
            String script = "cache_cleared=0\ncache_total_kb=0\nget_size() { du -sk \"$1\" 2>/dev/null | awk '{print $1}'; }\nshow_cleaning_progress() {\n    local duration=10\n    local i=1\n    local spinner=\"123456789\"\n    local spinner_len=10\n    while [ $i -le $duration ]; do\n        spinner_char=$(echo $spinner | cut -c $(( ( ($i-1) % spinner_len ) + 1 )) )\n        echo -ne \"🧹 [CLEANING CACHE] $i $spinner_char\\r\"\n        sleep 1\n        i=$((i+1))\n    done\n    echo -ne \"\\n\"\n}\nshow_cleaning_progress\napp_cache_dirs=(\n    \"/data/data/*/cache\"\n    \"/data/data/*/code_cache\"\n    \"/data/user_de/*/*/cache\"\n    \"/data/user_de/*/*/code_cache\"\n    \"/sdcard/Android/data/*/cache\"\n    \"/data/system/dropbox\"\n)\nstep=1\ntotal_steps=$(( ${#app_cache_dirs[@]} + 6 ))\nfor dir in \"${app_cache_dirs[@]}\"\ndo\n    size=$(du -cs $dir 2>/dev/null | grep total | cut -f 1)\n    [ -n \"$size\" ] && cache_total_kb=$((cache_total_kb + size))\n    echo -ne \" [$step/$total_steps] Cleaning: $dir ${size:-0} KB\\r\"\n    find $dir/* -delete &>/dev/null\n    sleep 1\n    echo -ne \"\\n\"\n    step=$((step+1))\ndone\nsystem_cache_dirs=(\n    \"/cache\"\n    \"/data/dalvik-cache\"\n    \"/data/system/gpu\"\n    \"/system/cache\"\n    \"/vendor/cache\"\n)\nfor sysdir in \"${system_cache_dirs[@]}\"\ndo\n    [ -d \"$sysdir\" ] && cache_total_kb=$((cache_total_kb + $(get_size \"$sysdir\")))\ndone\n[ -d /cache ] && rm -rf /cache/* && echo \"[$step/$total_steps]  Clearing /cache...\" && cache_cleared=$((cache_cleared + 1)) && sleep 1 && step=$((step+1))\n[ -d /data/dalvik-cache ] && rm -rf /data/dalvik-cache/* && echo \"[$step/$total_steps] Clearing /data/dalvik-cache...\" && cache_cleared=$((cache_cleared + 1)) && sleep 1 && step=$((step+1))\nsync; echo 3 > /proc/sys/vm/drop_caches && echo \"[$step/$total_steps] Dropping system caches...\" && cache_cleared=$((cache_cleared + 1)) && sleep 1 && step=$((step+1))\n[ -d /data/system/gpu ] && rm -rf /data/system/gpu/* && echo \"[$step/$total_steps] Clearing /data/system/gpu...\" && cache_cleared=$((cache_cleared + 1)) && sleep 1 && step=$((step+1))\n[ -d /system/cache ] && rm -rf /system/cache/* && echo \"[$step/$total_steps] Clearing /system/cache...\" && cache_cleared=$((cache_cleared + 1)) && sleep 1 && step=$((step+1))\n[ -d /vendor/cache ] && rm -rf /vendor/cache/* && echo \"[$step/$total_steps] Clearing /vendor/cache...\" && cache_cleared=$((cache_cleared + 1)) && sleep 1 && step=$((step+1))\ncache_total_mb=$(awk \"BEGIN {printf \\\"%.2f\\\", $cache_total_kb/1024}\")\necho \"[OK] Cache Cleanup: Cleared $cache_cleared cache directories.\"\necho \"[OK] Estimated cache cleaned: $cache_total_mb MB\"";

            runOnUiThread(() -> tvTerminalLog.setText("> Starting Advanced Clean..."));

            // Tulis script ke file temporary di HP
            runSu("echo '" + script + "' > /data/local/tmp/vortex_clean.sh");
            runSu("chmod 755 /data/local/tmp/vortex_clean.sh");

            // Jalankan script dan ambil outputnya
            String output = runSuReturnAll("sh /data/local/tmp/vortex_clean.sh");

            runOnUiThread(() -> {
                tvTerminalLog.setText(output);
                Toast.makeText(this, "Clean Success", Toast.LENGTH_SHORT).show();
            });

            // Tunggu 5 detik agar user bisa baca log, lalu exit seperti fitur asli
            try { Thread.sleep(5000); } catch (Exception e){}
        }).start();
    }
}
