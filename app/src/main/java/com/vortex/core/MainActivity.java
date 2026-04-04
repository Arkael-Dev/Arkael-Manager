package com.vortex.core;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    private TextView tvRam, tvZram, tvCpu, tvBattery;
    private TextView tvKernel, tvDevice, tvTerminalLog;
    private TextView tvLittleCluster, tvBigCluster, tvCurrentFreq, tvMaxFreq, tvCpuVendor, tvTemp, tvGpuRenderer, tvGpuVersion, tvMaxFreqTools, tvAuthActive;
    private ImageView headerBanner;
    private SeekBar seekBarMaxFreq;
    private LinearLayout cardRam, cardBat, rootLayout, bannerContainer;
    private ViewFlipper viewFlipper;
    private LinearLayout navSystem, navTools, navSettings;

    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int maxFreqKhz = 0;
    private int minFreqKhz = 0;
    private boolean isGlassTheme = false;
    private boolean isRainbowTheme = false;
    
    // SECRET KEY (AKAN DIBACA OTOMATIS DARI FILE)
    private String SECRET_PASSKEY = "vortex"; // Default fallback
    
    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("VortexPrefs", 0);
        
        // 1. BACA PASSKEY OTOMATIS DARI FILE ASSETS
        readPasskeyFromAssets();
        
        pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    copyImageToInternal(imageUri);
                }
            }
        );

        // --- INIT VIEWS ---
        tvRam = findViewById(R.id.tv_ram);
        tvZram = findViewById(R.id.tv_zram);
        tvCpu = findViewById(R.id.tv_cpu);
        tvBattery = findViewById(R.id.tv_battery);
        tvKernel = findViewById(R.id.tv_kernel);
        tvDevice = findViewById(R.id.tv_device);
        tvTerminalLog = findViewById(R.id.tv_terminal_log);
        
        tvLittleCluster = findViewById(R.id.tv_little_cluster);
        tvBigCluster = findViewById(R.id.tv_big_cluster);
        tvCurrentFreq = findViewById(R.id.tv_current_freq);
        tvMaxFreq = findViewById(R.id.tv_max_freq);
        tvCpuVendor = findViewById(R.id.tv_cpu_vendor);
        tvTemp = findViewById(R.id.tv_temp);
        tvGpuRenderer = findViewById(R.id.tv_gpu_renderer);
        tvGpuVersion = findViewById(R.id.tv_gpu_version);
        tvMaxFreqTools = findViewById(R.id.tv_max_freq_tools);
        tvAuthActive = findViewById(R.id.tv_auth_active);

        headerBanner = findViewById(R.id.header_banner);
        seekBarMaxFreq = findViewById(R.id.seekbar_max_freq);
        cardRam = findViewById(R.id.card_ram);
        cardBat = findViewById(R.id.card_bat);
        rootLayout = findViewById(R.id.root_layout);
        bannerContainer = findViewById(R.id.banner_container);

        viewFlipper = findViewById(R.id.main_view_flipper);
        navSystem = findViewById(R.id.nav_system);
        navTools = findViewById(R.id.nav_tools);
        navSettings = findViewById(R.id.nav_settings);

        // Load Theme Settings
        isGlassTheme = prefs.getBoolean("glass_theme", false);
        isRainbowTheme = prefs.getBoolean("rainbow_theme", false);
        applyThemeSettings();

        if(tvTerminalLog != null) tvTerminalLog.setMovementMethod(new ScrollingMovementMethod());

        // 2. AUTH LOGIC (FIX & AUTO-UNLOCK KERNEL)
        String kernelVer = System.getProperty("os.version").toLowerCase();
        String devName = Build.DEVICE.toLowerCase();
        String prodName = Build.PRODUCT.toLowerCase();
        
        // Cek apakah pakai Kernel VorteX E-Sport (Auto Unlock)
        boolean isEKernel = kernelVer.contains("vortex-e-sport") || 
                            kernelVer.contains("vortex_esport") || 
                            kernelVer.contains("vortex");

        if(isEKernel) {
            prefs.edit().putBoolean("is_unlocked", true).apply();
            if(tvAuthActive != null) {
                tvAuthActive.setVisibility(View.VISIBLE);
                tvAuthActive.setText("● AUTH ACTIVE (VORTEX-E-SPORT)");
            }
        } else {
            if(tvAuthActive != null) tvAuthActive.setVisibility(View.GONE);
        }

        refreshUI();

        // --- LISTENERS ---
        findViewById(R.id.btn_unlock).setOnClickListener(v -> {
            EditText input = findViewById(R.id.input_code);
            String userInput = input.getText().toString().trim();
            
            // Cek Passkey (Yang sudah dibaca otomatis dari file assets)
            if(userInput.equals(SECRET_PASSKEY)) {
                prefs.edit().putBoolean("is_unlocked", true).apply();
                refreshUI();
            } else {
                Toast.makeText(this, "DENIED: Wrong Code", Toast.LENGTH_SHORT).show();
            }
        });

        // Tools Clicks
        findViewById(R.id.btn_cpu_gov).setOnClickListener(v -> pickGov());
        findViewById(R.id.btn_set_zram).setOnClickListener(v -> showZramMenu());
        findViewById(R.id.btn_thermal).setOnClickListener(v -> showThermalMenu());
        findViewById(R.id.btn_clean_ram).setOnClickListener(v -> {
            Toast.makeText(this, "Starting Advanced Clean...", Toast.LENGTH_SHORT).show();
            cleanRam();
        });

        // Nav (Handle Banner Visibility)
        navSystem.setOnClickListener(v -> { 
            viewFlipper.setDisplayedChild(0); 
            updateNavUI(0);
            bannerContainer.setVisibility(View.VISIBLE); 
        });
        navTools.setOnClickListener(v -> { 
            viewFlipper.setDisplayedChild(1); 
            updateNavUI(1);
            bannerContainer.setVisibility(View.GONE); 
        });
        navSettings.setOnClickListener(v -> { 
            viewFlipper.setDisplayedChild(2); 
            updateNavUI(2);
            bannerContainer.setVisibility(View.GONE); 
        });

        // Settings Actions
        findViewById(R.id.banner_click_area).setOnClickListener(v -> openGallery());
        findViewById(R.id.btn_reset_banner).setOnClickListener(v -> {
            prefs.edit().putString("custom_banner_path", "").apply();
            loadCustomBanner();
            Toast.makeText(this, "Banner Reset", Toast.LENGTH_SHORT).show();
        });

        // Pro Theme Actions
        findViewById(R.id.btn_theme_glass).setOnClickListener(v -> {
            isGlassTheme = !isGlassTheme;
            prefs.edit().putBoolean("glass_theme", isGlassTheme).apply();
            applyThemeSettings();
            Toast.makeText(this, "Glass Effect: " + (isGlassTheme?"ON":"OFF"), Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_theme_rainbow).setOnClickListener(v -> {
            isRainbowTheme = !isRainbowTheme;
            prefs.edit().putBoolean("rainbow_theme", isRainbowTheme).apply();
            applyThemeSettings();
            Toast.makeText(this, "Rainbow Icons: " + (isRainbowTheme?"ON":"OFF"), Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_bg_black).setOnClickListener(v -> setBackgroundMode(0));
        findViewById(R.id.btn_bg_white).setOnClickListener(v -> setBackgroundMode(1));
        findViewById(R.id.btn_bg_gray).setOnClickListener(v -> setBackgroundMode(2));

        // Links
        findViewById(R.id.tv_dev_link).setOnClickListener(v -> openUrl("https://t.me/VorteXSU_Dev"));
        findViewById(R.id.tv_channel_link).setOnClickListener(v -> openUrl("https://t.me/vortexgki"));

        // Slider Logic (MANUAL ONLY)
        seekBarMaxFreq.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && maxFreqKhz > 0 && minFreqKhz > 0) {
                    int range = maxFreqKhz - minFreqKhz;
                    int targetFreq = minFreqKhz + ((range * progress) / 100);
                    setMaxFreq(targetFreq);
                    if(tvMaxFreqTools != null) tvMaxFreqTools.setText((targetFreq/1000) + " MHz");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        loadCustomBanner();
    }

    // FUNGSI MEMBACA PASSKEY DARI FILE ASSETS
    private void readPasskeyFromAssets() {
        try {
            // Membaca file VORTEX_PASSKEY.txt yang ada di folder app/src/main/assets/
            InputStream is = getAssets().open("VORTEX_PASSKEY.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line = reader.readLine();
            if (line != null && line.contains("PASSKEY:")) {
                // Ambil kata setelah "PASSKEY:"
                String[] parts = line.split(":");
                if(parts.length > 1) {
                    SECRET_PASSKEY = parts[1].trim();
                }
            }
            is.close();
        } catch (Exception e) {
            // Jika file tidak ketemu, gunakan default atau abaikan
            // SECRET_PASSKEY tetap "vortex" (default)
        }
    }

    private void setBackgroundMode(int mode) {
        int bgCol, cardCol, textCol;
        
        if(mode == 0) { // Black
            bgCol = Color.parseColor("#121212");
            cardCol = Color.parseColor("#1E1E1E");
            textCol = Color.WHITE;
        } else if (mode == 1) { // White
            bgCol = Color.parseColor("#F0F0F0");
            cardCol = Color.parseColor("#FFFFFF");
            textCol = Color.BLACK;
        } else { // Gray
            bgCol = Color.parseColor("#808080");
            cardCol = Color.parseColor("#909090");
            textCol = Color.WHITE;
        }

        if(isGlassTheme) {
            cardCol = Color.parseColor("#CC000000");
            if(mode==1) cardCol = Color.parseColor("#CCFFFFFF");
        }

        prefs.edit().putInt("bg_mode", mode).apply();

        if(rootLayout != null) rootLayout.setBackgroundColor(bgCol);
        
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(cardCol);
        gd.setCornerRadius(20);
        
        if(cardRam != null) cardRam.setBackground(gd);
        if(cardBat != null) cardBat.setBackground(gd);
        
        boolean isDark = (mode == 0 || mode == 2);
        int finalText = isDark ? Color.WHITE : Color.BLACK;
        if(tvRam != null) tvRam.setTextColor(finalText);
        if(tvBattery != null) tvBattery.setTextColor(finalText);

        applyIconColor();
    }

    private void applyThemeSettings() {
        int savedMode = prefs.getInt("bg_mode", 0);
        setBackgroundMode(savedMode);
    }

    private void applyIconColor() {
        int color = Color.GRAY; 
        if(isRainbowTheme) {
            color = Color.parseColor("#FF4081"); 
        } else {
            color = Color.parseColor("#AAAAAA");
        }
        
        if(navSystem != null) tintCompoundDrawables(navSystem.getChildAt(0), color);
        if(navTools != null) tintCompoundDrawables(navTools.getChildAt(0), color);
        if(navSettings != null) tintCompoundDrawables(navSettings.getChildAt(0), color);
    }

    private void tintCompoundDrawables(View view, int color) {
        if(view instanceof TextView) {
            TextView tv = (TextView) view;
            for(Drawable d : tv.getCompoundDrawables()) {
                if(d != null) d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        pickImageLauncher.launch(intent);
    }

    private void copyImageToInternal(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            String fileName = "custom_banner.jpg";
            File outFile = new File(getFilesDir(), fileName);
            OutputStream outputStream = new FileOutputStream(outFile);
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.close();
            inputStream.close();

            String path = outFile.getAbsolutePath();
            prefs.edit().putString("custom_banner_path", path).apply();
            loadCustomBanner();
            Toast.makeText(this, "Banner Applied", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {}
    }

    private void loadCustomBanner() {
        String customPath = prefs.getString("custom_banner_path", "");
        
        if (!customPath.isEmpty()) {
            File imgFile = new File(customPath);
            if (imgFile.exists()) {
                Glide.with(this).load(imgFile).centerCrop().into(headerBanner);
                headerBanner.setVisibility(View.VISIBLE);
                bannerContainer.setVisibility(View.VISIBLE);
                findViewById(R.id.btn_reset_banner).setVisibility(View.VISIBLE);
                return;
            }
        }
        
        try {
            Glide.with(this).load(R.drawable.header_bg).centerCrop().into(headerBanner);
            headerBanner.setVisibility(View.VISIBLE);
            bannerContainer.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            headerBanner.setVisibility(View.GONE);
            bannerContainer.setVisibility(View.GONE);
        }
        
        findViewById(R.id.btn_reset_banner).setVisibility(View.GONE);
    }

    private void updateNavUI(int activeIndex) {
        int colorActive = Color.parseColor("#4CAF50");
        int colorInactive = isRainbowTheme ? Color.parseColor("#FF4081") : Color.parseColor("#888888");

        ((TextView)navSystem.getChildAt(0)).setTextColor(activeIndex == 0 ? colorActive : colorInactive);
        ((TextView)navTools.getChildAt(0)).setTextColor(activeIndex == 1 ? colorActive : colorInactive);
        ((TextView)navSettings.getChildAt(0)).setTextColor(activeIndex == 2 ? colorActive : colorInactive);
        applyIconColor(); 
    }

    private void setMaxFreq(int khz) {
        new Thread(() -> {
            String cmd = "for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq; do echo " + khz + " > $c; done";
            runSu(cmd);
        }).start();
    }

    private void refreshUI() {
        boolean ok = prefs.getBoolean("is_unlocked", false);
        findViewById(R.id.layout_locked).setVisibility(ok ? View.GONE : View.VISIBLE);
        if (ok) {
            startLoop();
            if(viewFlipper.getDisplayedChild() == 0) bannerContainer.setVisibility(View.VISIBLE);
            else bannerContainer.setVisibility(View.GONE);
        }
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

            String gov = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
            if(tvCpu != null) tvCpu.setText(gov.toUpperCase());

            String z = runSuReturn("cat /sys/block/zram0/disksize");
            if(tvZram != null) tvZram.setText((z.isEmpty() ? "0" : (Long.parseLong(z)/1048576)) + " MB");
        } catch (Exception ignored) {}
    }

    private void updateDetailedStats() {
        try {
            String platform = runSuReturn("getprop ro.board.platform").toLowerCase();
            String hardware = runSuReturn("getprop ro.hardware").toLowerCase();
            String socModel = runSuReturn("getprop ro.soc.model"); 
            String vendor = "Unknown Device";

            if (platform.contains("qcom") || platform.contains("msm")) {
                vendor = "Qualcomm";
                if(!socModel.isEmpty()) vendor += " (" + socModel + ")";
            } 
            else if (platform.contains("mt") || hardware.contains("mt")) {
                vendor = "Mediatek";
                if(!socModel.isEmpty()) vendor += " (" + socModel + ")";
            }
            else if (platform.contains("exynos")) vendor = "Exynos";
            else if (platform.contains("universal") || platform.contains("sp98")) vendor = "Unisoc";
            else vendor = platform.toUpperCase();

            if(tvCpuVendor != null) tvCpuVendor.setText(vendor);

            String max = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            String scalingMax = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq");
            String cur = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
            
            if(!max.isEmpty()) maxFreqKhz = Integer.parseInt(max);

            if(!cur.isEmpty() && tvCurrentFreq != null) tvCurrentFreq.setText((Integer.parseInt(cur)/1000) + " MHz");
            
            int currentMaxVal = 0;
            if(!scalingMax.isEmpty()) {
                currentMaxVal = Integer.parseInt(scalingMax);
            } else if (!max.isEmpty()) {
                currentMaxVal = maxFreqKhz;
            }

            if(tvMaxFreq != null) tvMaxFreq.setText((currentMaxVal/1000) + " MHz");
            if(tvMaxFreqTools != null) tvMaxFreqTools.setText((currentMaxVal/1000) + " MHz");

            // CLUSTERS
            String cpuCount = runSuReturn("cat /proc/cpuinfo | grep 'processor' | wc -l");
            int cores = cpuCount.isEmpty() ? 4 : Integer.parseInt(cpuCount.trim());
            int lastCore = Math.max(0, cores - 1);
            
            String little = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            String big = runSuReturn("cat /sys/devices/system/cpu/cpu"+lastCore+"/cpufreq/cpuinfo_max_freq");
            if(big.isEmpty()) big = little;

            if(tvLittleCluster != null) tvLittleCluster.setText((little.isEmpty() ? "N/A" : (Integer.parseInt(little)/1000) + " MHz"));
            if(tvBigCluster != null) tvBigCluster.setText((big.isEmpty() ? "N/A" : (Integer.parseInt(big)/1000) + " MHz"));

            // TEMP
            String temp = "";
            for(int i=0; i<20; i++) {
                String type = runSuReturn("cat /sys/class/thermal/thermal_zone"+i+"/type 2>/dev/null");
                if(type.toLowerCase().contains("batt") || type.toLowerCase().contains("battery") || type.toLowerCase().contains("tsens")) {
                    temp = runSuReturn("cat /sys/class/thermal/thermal_zone"+i+"/temp 2>/dev/null");
                    if(!temp.isEmpty() && Integer.parseInt(temp) > 0) break;
                }
            }
            if(temp.isEmpty()) temp = runSuReturn("cat /sys/class/power_supply/battery/temp 2>/dev/null");
            
            if(!temp.isEmpty() && tvTemp != null) {
                try {
                    int t = Integer.parseInt(temp.trim());
                    if(t > 1000) t = t / 1000; 
                    if(t == 0) throw new Exception(); 
                    tvTemp.setText(t + "°C");
                } catch (Exception e) { tvTemp.setText("N/A"); }
            }

            // GPU
            String gpu = "Unknown GPU";
            if (platform.contains("mt") || hardware.contains("mt")) {
                gpu = runSuReturn("cat /sys/class/misc/mali0/device/gpu_model 2>/dev/null");
                if(gpu.isEmpty()) gpu = runSuReturn("cat /sys/kernel/debug/mali0/gpu_id 2>/dev/null");
                if(gpu.isEmpty()) gpu = "Mali GPU";
            } else if (platform.contains("qcom") || platform.contains("msm")) {
                gpu = runSuReturn("cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null");
                if(gpu.isEmpty()) gpu = runSuReturn("cat /sys/devices/platform/soc/soc:qcom,kgsl-3d0/devfreq/soc:qcom,kgsl-3d0/gpu_model 2>/dev/null");
                if(gpu.isEmpty()) gpu = "Adreno GPU";
            } else if (platform.contains("exynos")) {
                 gpu = "Exynos GPU";
            }

            if(tvGpuRenderer != null) tvGpuRenderer.setText(gpu);

            String gl = runSuReturn("getprop ro.opengles.version");
            if(tvGpuVersion != null) {
                if(gl.isEmpty()) tvGpuVersion.setText("OpenGL ES 3.x");
                else tvGpuVersion.setText("OpenGL ES " + gl);
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateSystemInfo() {
        try {
            String brand = Build.BRAND;
            String model = Build.MODEL;
            if(tvDevice != null) tvDevice.setText(brand.toUpperCase() + " " + model);

            String kernelFull = runSuReturn("uname -r");
            if(kernelFull.isEmpty()) kernelFull = "Unknown Kernel";
            if(tvKernel != null) tvKernel.setText(kernelFull);
        } catch (Exception ignored) {}
    }

    private void pickGov() {
        String[] govs = runCmd("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors").split(" ");
        new AlertDialog.Builder(this)
            .setTitle("CPU GOVERNOR")
            .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, govs), (d, w) -> {
                new Thread(() -> {
                    String cmd = "for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo " + govs[w] + " > $c; done";
                    runSu(cmd);
                }).start();
                Toast.makeText(this, "Governor set to " + govs[w], Toast.LENGTH_SHORT).show();
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
                    runSu("stop thermald 2>/dev/null");
                    runSu("stop thermal-engine 2>/dev/null");
                    runOnUiThread(() -> { 
                        tvTerminalLog.setText("Thermal Disabled\n(Unlocked Performance)");
                        Toast.makeText(this, "Thermal DISABLED", Toast.LENGTH_SHORT).show(); 
                    });
                }).start();
            } else {
                new Thread(() -> {
                    runOnUiThread(() -> tvTerminalLog.setText("> Enabling Thermal..."));
                    runSu("start thermald 2>/dev/null");
                    runSu("start thermal-engine 2>/dev/null");
                    runOnUiThread(() -> { 
                        tvTerminalLog.setText("Thermal Enabled\n(Safe Mode)");
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
