package com.vortex.core;

import android.app.ActivityManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {
    private TextView tvRam, tvZram, tvCpu, tvBattery;
    private TextView tvKernel, tvDevice, tvTerminalLog;
    // tvCurrentFreq & tvMaxFreq DIHAPUS karena tidak ada di XML baru
    private TextView tvLittleCluster, tvBigCluster, tvCpuVendor, tvTemp, tvGpuRenderer, tvGpuVersion;

    private ViewFlipper viewFlipper;
    private LinearLayout navSystem, navTools, navSettings;
    private LinearLayout rootLayout; // For Theme Changing

    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isDarkTheme = true; // Default Dark

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("VortexPrefs", 0);
        isDarkTheme = prefs.getBoolean("dark_theme", true);

        // --- INIT VIEWS ---
        tvRam = findViewById(R.id.tv_ram);
        tvZram = findViewById(R.id.tv_zram);
        tvCpu = findViewById(R.id.tv_cpu);
        tvBattery = findViewById(R.id.tv_battery);
        tvKernel = findViewById(R.id.tv_kernel);
        tvDevice = findViewById(R.id.tv_device);
        tvTerminalLog = findViewById(R.id.tv_terminal_log);
        
        // Init Stats Views (Tidak termasuk Current/Max yang dihapus)
        tvLittleCluster = findViewById(R.id.tv_little_cluster);
        tvBigCluster = findViewById(R.id.tv_big_cluster);
        tvCpuVendor = findViewById(R.id.tv_cpu_vendor);
        tvTemp = findViewById(R.id.tv_temp);
        tvGpuRenderer = findViewById(R.id.tv_gpu_renderer);
        tvGpuVersion = findViewById(R.id.tv_gpu_version);

        viewFlipper = findViewById(R.id.main_view_flipper);
        navSystem = findViewById(R.id.nav_system);
        navTools = findViewById(R.id.nav_tools);
        navSettings = findViewById(R.id.nav_settings);
        rootLayout = findViewById(R.id.root_layout);

        applyTheme(isDarkTheme); // Apply saved theme
        updateNavUI(0);

        if(tvTerminalLog != null) tvTerminalLog.setMovementMethod(new ScrollingMovementMethod());

        refreshUI();

        // --- LISTENERS ---
        findViewById(R.id.btn_unlock).setOnClickListener(v -> {
            EditText input = findViewById(R.id.input_code);
            if (input.getText().toString().trim().equals(BuildConfig.SECRET_PASSKEY)) {
                prefs.edit().putString("pass_hash", SecurityUtils.generateHash(BuildConfig.SECRET_PASSKEY)).apply();
                refreshUI();
            } else {
                Toast.makeText(this, "DENIED", Toast.LENGTH_SHORT).show();
            }
        });

        // Tool Listeners (LinearLayouts act as buttons)
        findViewById(R.id.btn_cpu_gov).setOnClickListener(v -> pickGov());
        findViewById(R.id.btn_set_zram).setOnClickListener(v -> showZramMenu());
        findViewById(R.id.btn_thermal).setOnClickListener(v -> showThermalMenu());
        findViewById(R.id.btn_clean_ram).setOnClickListener(v -> cleanRam());

        // --- NAVIGATION CLICKS ---
        navSystem.setOnClickListener(v -> { viewFlipper.setDisplayedChild(0); updateNavUI(0); });
        navTools.setOnClickListener(v -> { viewFlipper.setDisplayedChild(1); updateNavUI(1); });
        navSettings.setOnClickListener(v -> { viewFlipper.setDisplayedChild(2); updateNavUI(2); });

        // --- THEME SWITCHER LOGIC ---
        Switch themeSwitch = findViewById(R.id.switch_theme);
        if(themeSwitch != null) {
            themeSwitch.setChecked(!isDarkTheme);
            themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isDarkTheme = !isChecked;
                prefs.edit().putBoolean("dark_theme", isDarkTheme).apply();
                applyTheme(isDarkTheme);
            });
        }
    }

    private void applyTheme(boolean dark) {
        int bgColor, cardColor, textColor, subTextColor;

        if (dark) {
            bgColor = Color.parseColor("#121212");
            cardColor = Color.parseColor("#1E1E1E");
            textColor = Color.parseColor("#FFFFFF");
            subTextColor = Color.parseColor("#AAAAAA");
        } else {
            bgColor = Color.parseColor("#F5F5F5");
            cardColor = Color.parseColor("#FFFFFF");
            textColor = Color.parseColor("#000000");
            subTextColor = Color.parseColor("#666666");
        }

        if(rootLayout != null) rootLayout.setBackgroundColor(bgColor);
        
        ((TextView)navSystem.getChildAt(0)).setTextColor(dark ? Color.parseColor("#4CAF50") : Color.parseColor("#000000"));
        ((TextView)navTools.getChildAt(0)).setTextColor(subTextColor);
        ((TextView)navSettings.getChildAt(0)).setTextColor(subTextColor);
    }

    private void updateNavUI(int activeIndex) {
        int colorActive = isDarkTheme ? Color.parseColor("#4CAF50") : Color.parseColor("#000000");
        int colorInactive = isDarkTheme ? Color.parseColor("#888888") : Color.parseColor("#666666");

        ((TextView)navSystem.getChildAt(0)).setTextColor(activeIndex == 0 ? colorActive : colorInactive);
        ((TextView)navTools.getChildAt(0)).setTextColor(activeIndex == 1 ? colorActive : colorInactive);
        ((TextView)navSettings.getChildAt(0)).setTextColor(activeIndex == 2 ? colorActive : colorInactive);
    }

    private void refreshUI() {
        boolean ok = SecurityUtils.isSystemVerified(this);
        findViewById(R.id.layout_locked).setVisibility(ok ? View.GONE : View.VISIBLE);
        if (ok) startLoop();
    }

    private void startLoop() {
        handler.post(new Runnable() {
            @Override public void run() {
                updateStats();
                updateSystemInfo();
                updateDetailedStats();
                handler.postDelayed(this, 2000);
            }
        });
    }

    private void updateStats() {
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
            if(tvRam != null) tvRam.setText((mi.availMem / 1048576) + " MB");

            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if(tvBattery != null) {
                int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                int status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
                String statusText = (status == BatteryManager.BATTERY_STATUS_CHARGING) ? "Charging" : "Discharging";
                tvBattery.setText(level + "% (" + statusText + ")");
            }

            String gov = runCmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
            if(tvCpu != null) tvCpu.setText(gov.toUpperCase());

            String z = runSuReturn("cat /sys/block/zram0/disksize");
            if(tvZram != null) tvZram.setText((z.isEmpty() ? "0" : (Long.parseLong(z)/1048576)) + " MB");

        } catch (Exception ignored) {}
    }

    private void updateDetailedStats() {
        try {
            // CPU CLUSTERS
            String littleMax = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            String bigMax = runSuReturn("cat /sys/devices/system/cpu/cpu4/cpufreq/cpuinfo_max_freq");
            if(bigMax.isEmpty()) bigMax = runSuReturn("cat /sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_max_freq"); 
            if(bigMax.isEmpty()) bigMax = littleMax;

            if(tvLittleCluster != null) tvLittleCluster.setText((littleMax.isEmpty() ? "N/A" : (Integer.parseInt(littleMax)/1000) + " MHz"));
            if(tvBigCluster != null) tvBigCluster.setText((bigMax.isEmpty() ? "N/A" : (Integer.parseInt(bigMax)/1000) + " MHz"));

            // Kode tvCurrentFreq & tvMaxFreq DIHAPUS DISINI

            // VENDOR
            String vendor = "Unknown";
            String soc = runSuReturn("getprop ro.soc.manufacturer");
            if(soc.isEmpty()) soc = runSuReturn("getprop ro.board.platform");
            if(soc.isEmpty()) soc = runSuReturn("cat /proc/cpuinfo | grep -i 'Hardware' | cut -d':' -f2");

            if(soc.toLowerCase().contains("qualcomm") || soc.toLowerCase().contains("qcom")) vendor = "Qualcomm";
            else if(soc.toLowerCase().contains("mediatek") || soc.toLowerCase().contains("mtk")) vendor = "Mediatek";
            else if(soc.toLowerCase().contains("samsung")) vendor = "Samsung";
            else vendor = soc.trim().isEmpty() ? "Unknown SoC" : soc;
            
            if(tvCpuVendor != null) tvCpuVendor.setText(vendor);

            // TEMPERATURE
            String temp = runSuReturn("cat /sys/class/thermal/thermal_zone0/temp");
            if(temp.isEmpty()) temp = runSuReturn("cat /sys/class/thermal/thermal_zone10/temp");
            if(!temp.isEmpty() && tvTemp != null) {
                try {
                    int t = Integer.parseInt(temp.trim()) / 1000;
                    tvTemp.setText(t + "°C");
                } catch (Exception e) { tvTemp.setText("N/A"); }
            }

            // GPU
            String gpu = "Unknown GPU";
            String platform = runSuReturn("getprop ro.board.platform").toLowerCase();

            if (platform.contains("mt") || platform.contains("mtk")) {
                gpu = runSuReturn("cat /sys/class/misc/mali0/device/gpuinfo");
                if(gpu.isEmpty()) gpu = runSuReturn("dmesg | grep -i 'mali' | head -n 1");
                if(gpu.isEmpty()) gpu = "Mali GPU";
            } else if (platform.contains("qcom") || platform.contains("msm")) {
                gpu = runSuReturn("cat /sys/class/kgsl/kgsl-3d0/gpu_model");
                if(gpu.isEmpty()) gpu = runSuReturn("cat /sys/class/kgsl/kgsl-3d0/gpu_chip_id");
                if(gpu.isEmpty()) gpu = "Adreno GPU";
            } else {
                gpu = runSuReturn("getprop ro.hardware.egl");
                if(gpu.isEmpty()) gpu = "Generic GPU";
            }

            if(tvGpuRenderer != null) tvGpuRenderer.setText(gpu);

            // GPU VERSION
            String gl = runSuReturn("getprop ro.opengles.version");
            if(tvGpuVersion != null) tvGpuVersion.setText("OpenGL ES " + (gl.isEmpty() ? "3.x" : gl));

        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateSystemInfo() {
        try {
            String brand = Build.BRAND;
            String model = Build.MODEL;
            if(tvDevice != null) tvDevice.setText(brand.toUpperCase() + " " + model);

            String kernelFull = runSuReturn("cat /proc/version");
            if(kernelFull.isEmpty()) kernelFull = "Unknown Kernel";
            if(tvKernel != null) tvKernel.setText(kernelFull);
        } catch (Exception ignored) {}
    }

    private void pickGov() {
        String[] govs = runCmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors").split(" ");
        new AlertDialog.Builder(this)
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

    public void showThermalMenu() {
        final String[] options = {"DISABLE THERMAL", "ENABLE THERMAL"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Thermal Control");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                new Thread(() -> {
                    runOnUiThread(() -> tvTerminalLog.setText("> Disabling Thermal..."));
                    String thermalLoop = "for thermal in $(getprop | awk -F '[][]' '/thermal/ {print $2}'); do if [ \"$(getprop $thermal)\" = \"running\" ]; then stop ${thermal/init.svc.} 2>/dev/null; resetprop -n $thermal stopped 2>/dev/null; fi; done";
                    runSu(thermalLoop);
                    String result = runSuReturnAll("getprop | grep thermal");
                    runOnUiThread(() -> { tvTerminalLog.setText(result); Toast.makeText(this, "Thermal DISABLED", Toast.LENGTH_SHORT).show(); });
                }).start();
            } else {
                new Thread(() -> {
                    runOnUiThread(() -> tvTerminalLog.setText("> Enabling Thermal..."));
                    runSu("start thermald 2>/dev/null");
                    runSu("start thermal-engine 2>/dev/null");
                    runSu("start mi_thermald 2>/dev/null");
                    runSu("start android.thermal-hal 2>/dev/null");
                    String result = runSuReturnAll("getprop | grep thermal");
                    runOnUiThread(() -> { tvTerminalLog.setText(result); Toast.makeText(this, "Thermal ENABLED", Toast.LENGTH_SHORT).show(); });
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
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            p.waitFor();
            return output.toString().trim();
        } catch (Exception e) { return ""; }
    }

    private void cleanRam() {
        new Thread(() -> {
            String script = "cache_cleared=0\ncache_total_kb=0\nget_size() { du -sk \"$1\" 2>/dev/null | awk '{print $1}'; }\nshow_cleaning_progress() {\n    local duration=10\n    local i=1\n    local spinner=\"123456789\"\n    local spinner_len=10\n    while [ $i -le $duration ]; do\n        spinner_char=$(echo $spinner | cut -c $(( ( ($i-1) % spinner_len ) + 1 )) )\n        echo -ne \"🧹 [CLEANING CACHE] $i $spinner_char\\r\"\n        sleep 1\n        i=$((i+1))\n    done\n    echo -ne \"\\n\"\n}\nshow_cleaning_progress\napp_cache_dirs=(\n    \"/data/data/*/cache\"\n    \"/data/data/*/code_cache\"\n    \"/data/user_de/*/*/cache\"\n    \"/data/user_de/*/*/code_cache\"\n    \"/sdcard/Android/data/*/cache\"\n    \"/data/system/dropbox\"\n)\nstep=1\ntotal_steps=$(( ${#app_cache_dirs[@]} + 6 ))\nfor dir in \"${app_cache_dirs[@]}\"\ndo\n    size=$(du -cs $dir 2>/dev/null | grep total | cut -f 1)\n    [ -n \"$size\" ] && cache_total_kb=$((cache_total_kb + size))\n    echo -ne \" [$step/$total_steps] Cleaning: $dir ${size:-0} KB\\r\"\n    find $dir/* -delete &>/dev/null\n    sleep 1\n    echo -ne \"\\n\"\n    step=$((step+1))\ndone\nsystem_cache_dirs=(\n    \"/cache\"\n    \"/data/dalvik-cache\"\n    \"/data/system/gpu\"\n    \"/system/cache\"\n    \"/vendor/cache\"\n)\nfor sysdir in \"${system_cache_dirs[@]}\"\ndo\n    [ -d \"$sysdir\" ] && cache_total_kb=$((cache_total_kb + $(get_size \"$sysdir\")))\ndone\n[ -d /cache ] && rm -rf /cache/* && echo \"[$step/$total_steps]  Clearing /cache...\" && cache_cleared=$((cache_cleared + 1)) && sleep 1 && step=$((step+1))\n[ -d /data/dalvik-cache ] && rm -rf /data/dalvik-cache/* && echo \"[$step/$total_steps] Clearing /data/dalvik-cache...\" && cache_cleared=$((cache_cleared + 1)) && sleep 1 && step=$((step+1))\nsync; echo 3 > /proc/sys/vm/drop_caches && echo \"[$step/$total_steps] Dropping system caches...\" && cache_cleared=$((cache_cleared + 1)) && sleep 1 && step=$((step+1))\n[ -d /data/system/gpu ] && rm -rf /data/system/gpu/* && echo \"[$step/$total_steps] Clearing /data/system/gpu...\" && cache_cleared=$((cache_cleared + 1)) && sleep 1 && step=$((step+1))\n[ -d /system/cache ] && rm -rf /system/cache/* && echo \"[$step/$total_steps] Clearing /system/cache...\" && cache_cleared=$((cache_cleared + 1)) && sleep 1 && step=$((step+1))\n[ -d /vendor/cache ] && rm -rf /vendor/cache/* && echo \"[$step/$total_steps] Clearing /vendor/cache...\" && cache_cleared=$((cache_cleared + 1)) && sleep 1 && step=$((step+1))\ncache_total_mb=$(awk \"BEGIN {printf \\\"%.2f\\\", $cache_total_kb/1024}\")\necho \"[OK] Cache Cleanup: Cleared $cache_cleared cache directories.\"\necho \"[OK] Estimated cache cleaned: $cache_total_mb MB\"";

            runOnUiThread(() -> tvTerminalLog.setText("> Starting Advanced Clean..."));

            runSu("echo '" + script + "' > /data/local/tmp/vortex_clean.sh");
            runSu("chmod 755 /data/local/tmp/vortex_clean.sh");

            String output = runSuReturnAll("sh /data/local/tmp/vortex_clean.sh");

            runOnUiThread(() -> {
                tvTerminalLog.setText(output);
                Toast.makeText(this, "Clean Success", Toast.LENGTH_SHORT).show();
            });

            try { Thread.sleep(5000); } catch (Exception e){}
        }).start();
    }
}
