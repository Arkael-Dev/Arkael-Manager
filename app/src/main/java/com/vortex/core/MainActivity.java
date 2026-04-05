package com.vortex.core;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
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
import android.util.Log;
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
import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    // UI Components
    private TextView tvRam, tvZram, tvCpu, tvBattery;
    private TextView tvKernel, tvDevice, tvTerminalLog;
    private TextView tvLittleCluster, tvBigCluster, tvCurrentFreq, tvMaxFreq, tvCpuVendor, tvTemp, tvGpuRenderer, tvGpuVersion, tvMaxFreqTools, tvAuthActive;
    private ImageView headerBanner;
    private SeekBar seekBarMaxFreq;
    private LinearLayout cardRam, cardBat, rootLayout, bannerContainer;
    private ViewFlipper viewFlipper;
    private LinearLayout navSystem, navTools, navSettings;
    private EditText inputCode; 

    // System Vars
    private SharedPreferences prefs;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int maxFreqKhz = 0;
    private int minFreqKhz = 0;
    private boolean isGlassTheme = false;
    private int iconColorMode = 0; // 0: Gray, 1: Rainbow, 2: Cyan, 3: Orange, 4: Purple
    private boolean staticInfoLoaded = false;
    
    // Launcher untuk Pick Image
    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("VortexPrefs", 0);
        
        // Register Image Picker
        pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    copyImageToInternal(imageUri);
                }
            }
        );

        initViews();
        loadThemeSettings();

        // --- AUTH LOGIC (KERNEL CHECK) ---
        String kernelName = "";
        try {
            Process p = Runtime.getRuntime().exec("uname -r");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            kernelName = reader.readLine();
            if(kernelName == null) kernelName = "";
        } catch (Exception e) {
            kernelName = "";
        }

        Log.d("VortexAuth", "Kernel: " + kernelName);
        
        // Cek Kernel VorteX
        boolean isEKernel = kernelName.toLowerCase().contains("vortex");

        if(isEKernel) {
            prefs.edit().putBoolean("is_unlocked", true).apply();
            if(tvAuthActive != null) tvAuthActive.setVisibility(View.VISIBLE);
            Toast.makeText(this, "VorteX Kernel Detected!", Toast.LENGTH_SHORT).show();
        } else {
            if(tvAuthActive != null) tvAuthActive.setVisibility(View.GONE);
        }

        setupClickListeners();
        refreshUI();
        loadCustomBanner();
    }

    private String getPasskeyFromAssets() {
        try {
            AssetManager am = getAssets();
            InputStream is = am.open("VORTEX_PASSKEY.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("PASSKEY:")) {
                    String key = line.substring("PASSKEY:".length()).trim();
                    reader.close();
                    return key;
                }
            }
            reader.close();
        } catch (IOException e) {
            Log.e("VortexAuth", "Error reading passkey from assets", e);
        }
        return "vortex"; 
    }

    private void initViews() {
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

        inputCode = findViewById(R.id.input_code);

        if(tvTerminalLog != null) tvTerminalLog.setMovementMethod(new ScrollingMovementMethod());
    }

    private void loadThemeSettings() {
        isGlassTheme = prefs.getBoolean("glass_theme", false);
        iconColorMode = prefs.getInt("icon_color_mode", 0);
        applyThemeSettings();
    }

    private void setupClickListeners() {
        Button btnUnlock = findViewById(R.id.btn_unlock);
        if(btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> {
                if(inputCode == null) inputCode = findViewById(R.id.input_code);
                String userInput = inputCode.getText().toString().trim();
                String validKey = getPasskeyFromAssets(); 
                
                if (userInput.equals(validKey)) {
                    prefs.edit().putBoolean("is_unlocked", true).apply();
                    refreshUI();
                    Toast.makeText(this, "ACCESS GRANTED", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "DENIED: Invalid Key", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Tools Clicks
        findViewById(R.id.btn_cpu_gov).setOnClickListener(v -> pickGov());
        findViewById(R.id.btn_set_zram).setOnClickListener(v -> showZramMenu());
        findViewById(R.id.btn_thermal).setOnClickListener(v -> showThermalMenu());
        findViewById(R.id.btn_clean_ram).setOnClickListener(v -> {
            Toast.makeText(this, "Starting Advanced Clean...", Toast.LENGTH_SHORT).show();
            cleanRam();
        });

        // Nav
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

        findViewById(R.id.btn_theme_glass).setOnClickListener(v -> {
            isGlassTheme = !isGlassTheme;
            prefs.edit().putBoolean("glass_theme", isGlassTheme).apply();
            applyThemeSettings();
            Toast.makeText(this, "Glass Effect: " + (isGlassTheme?"ON":"OFF"), Toast.LENGTH_SHORT).show();
        });

        // ICON COLOR CYCLE
        findViewById(R.id.btn_theme_color).setOnClickListener(v -> {
            iconColorMode = (iconColorMode + 1) % 5;
            prefs.edit().putInt("icon_color_mode", iconColorMode).apply();
            applyThemeSettings();
            String modeName = "";
            switch(iconColorMode) {
                case 0: modeName = "Default Gray"; break;
                case 1: modeName = "Rainbow Pink"; break;
                case 2: modeName = "Neon Cyan"; break;
                case 3: modeName = "Bright Orange"; break;
                case 4: modeName = "Electric Purple"; break;
            }
            Toast.makeText(this, "Icon Color: " + modeName, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_bg_black).setOnClickListener(v -> setBackgroundMode(0));
        findViewById(R.id.btn_bg_white).setOnClickListener(v -> setBackgroundMode(1));
        findViewById(R.id.btn_bg_transparent).setOnClickListener(v -> setBackgroundMode(3)); // Mode 3 Transparent

        // Links
        findViewById(R.id.tv_dev_link).setOnClickListener(v -> openUrl("https://t.me/VorteXSU_Dev"));
        findViewById(R.id.tv_channel_link).setOnClickListener(v -> openUrl("https://t.me/vortexgki"));

        // Slider Logic
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
    }
    
    // --- BACKGROUND THREAD LOGIC ---
    
    private void refreshUI() {
        boolean ok = prefs.getBoolean("is_unlocked", false);
        findViewById(R.id.layout_locked).setVisibility(ok ? View.GONE : View.VISIBLE);
        if (ok) {
            if(!staticInfoLoaded) {
                loadStaticHardwareInfo();
                staticInfoLoaded = true;
            }
            startLoop();
            if(viewFlipper.getDisplayedChild() == 0) bannerContainer.setVisibility(View.VISIBLE);
            else bannerContainer.setVisibility(View.GONE);
        }
    }

    private void loadStaticHardwareInfo() {
        new Thread(() -> {
            String brand = Build.BRAND;
            String model = Build.MODEL;
            String kernelFull = runSuReturn("uname -r");
            if(kernelFull.isEmpty()) kernelFull = "Unknown Kernel";
            
            String platform = runSuReturn("getprop ro.board.platform").toLowerCase();
            String hardware = runSuReturn("getprop ro.hardware").toLowerCase();
            String socModel = runSuReturn("getprop ro.soc.model"); 
            String vendor = "Unknown Device";
            String gpu = "Unknown GPU";

            // CPU Vendor Logic
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

            // --- IMPROVED GPU LOGIC ---
            boolean gpuFound = false;
            
            // Try Direct Root Path first
            if (platform.contains("mt") || hardware.contains("mt")) {
                gpu = runSuReturn("cat /sys/class/misc/mali0/device/gpu_model 2>/dev/null");
                if(gpu.isEmpty()) gpu = runSuReturn("cat /sys/kernel/debug/mali0/gpu_id 2>/dev/null");
                if(!gpu.isEmpty()) gpuFound = true;
            } else if (platform.contains("qcom") || platform.contains("msm")) {
                gpu = runSuReturn("cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null");
                if(gpu.isEmpty()) gpu = runSuReturn("cat /sys/devices/platform/soc/soc:qcom,kgsl-3d0/devfreq/soc:qcom,kgsl-3d0/gpu_model 2>/dev/null");
                if(!gpu.isEmpty()) gpuFound = true;
            }

            // Fallback Logic if Root Read Fails or returns empty
            if (!gpuFound) {
                if (platform.contains("qcom") || platform.contains("msm")) {
                    gpu = "Adreno GPU";
                } else if (platform.contains("mt") || hardware.contains("mt")) {
                    gpu = "Mali GPU";
                } else if (platform.contains("exynos")) {
                    gpu = "Exynos GPU";
                } else if (platform.contains("intel")) {
                    gpu = "Intel GPU";
                } else {
                    gpu = "Generic GPU (" + platform.toUpperCase() + ")";
                }
            }
            
            String gl = runSuReturn("getprop ro.opengles.version");
            if(gl.isEmpty()) gl = "OpenGL ES 3.x";
            else gl = "OpenGL ES " + gl;

            final String finalVendor = vendor;
            final String finalKernel = kernelFull;
            final String finalDevice = brand.toUpperCase() + " " + model;
            final String finalGpu = gpu;
            final String finalGl = gl;

            runOnUiThread(() -> {
                if(tvCpuVendor != null) tvCpuVendor.setText(finalVendor);
                if(tvKernel != null) tvKernel.setText(finalKernel);
                if(tvDevice != null) tvDevice.setText(finalDevice);
                if(tvGpuRenderer != null) tvGpuRenderer.setText(finalGpu);
                if(tvGpuVersion != null) tvGpuVersion.setText(finalGl);
            });
        }).start();
    }

    private void startLoop() {
        handler.post(new Runnable() {
            @Override public void run() {
                new Thread(() -> {
                    String ramStr = "...";
                    String batStr = "...";
                    String govStr = "...";
                    String zramStr = "0 MB";
                    
                    try {
                        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                        ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
                        ramStr = (mi.availMem / 1048576) + " MB";

                        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
                        int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                        int status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
                        String statusText = (status == BatteryManager.BATTERY_STATUS_CHARGING) ? "Charging" : "Discharging";
                        batStr = level + "% (" + statusText + ")";

                        govStr = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
                        if(govStr.isEmpty()) govStr = "Unknown";

                        String z = runSuReturn("cat /sys/block/zram0/disksize");
                        if(!z.isEmpty()) zramStr = (Long.parseLong(z)/1048576) + " MB";
                    } catch (Exception e) { e.printStackTrace(); }

                    String curFreq = "N/A";
                    String maxFreqVal = "N/A";
                    String little = "N/A";
                    String big = "N/A";
                    String temp = "N/A";
                    String maxTools = "N/A";

                    try {
                        String max = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
                        String scalingMax = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq");
                        String cur = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");

                        if(!max.isEmpty()) maxFreqKhz = Integer.parseInt(max);
                        if(!cur.isEmpty()) curFreq = (Integer.parseInt(cur)/1000) + " MHz";
                        
                        int currentMaxVal = 0;
                        if(!scalingMax.isEmpty()) currentMaxVal = Integer.parseInt(scalingMax);
                        else if (!max.isEmpty()) currentMaxVal = maxFreqKhz;

                        maxFreqVal = (currentMaxVal/1000) + " MHz";
                        maxTools = maxFreqVal;

                        String cpuCount = runSuReturn("cat /proc/cpuinfo | grep 'processor' | wc -l");
                        int cores = cpuCount.isEmpty() ? 4 : Integer.parseInt(cpuCount.trim());
                        int lastCore = Math.max(0, cores - 1);
                        
                        String lit = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
                        String bigVal = runSuReturn("cat /sys/devices/system/cpu/cpu"+lastCore+"/cpufreq/cpuinfo_max_freq");
                        if(bigVal.isEmpty()) bigVal = lit;

                        little = (lit.isEmpty() ? "N/A" : (Integer.parseInt(lit)/1000) + " MHz");
                        big = (bigVal.isEmpty() ? "N/A" : (Integer.parseInt(bigVal)/1000) + " MHz");

                        String tempScript = "for f in /sys/class/thermal/thermal_zone*/type; do t=$(cat $f 2>/dev/null); if [[ \"$t\" == *\"batt\"* ]] || [[ \"$t\" == *\"tsens\"* ]]; then cat ${f%type}/temp 2>/dev/null; break; fi; done";
                        String t = runSuReturn(tempScript);
                        if(!t.isEmpty()) {
                            try {
                                int tempVal = Integer.parseInt(t.trim());
                                if(tempVal > 1000) tempVal = tempVal / 1000;
                                if(tempVal > 0) temp = tempVal + "°C";
                            } catch (Exception e) { temp = "N/A"; }
                        } else {
                            t = runSuReturn("cat /sys/class/power_supply/battery/temp 2>/dev/null");
                             if(!t.isEmpty()) {
                                try {
                                    int tempVal = Integer.parseInt(t.trim());
                                    if(tempVal > 1000) tempVal = tempVal / 1000;
                                    temp = tempVal + "°C";
                                } catch (Exception e) {}
                            }
                        }

                    } catch (Exception e) { e.printStackTrace(); }

                    final String fRam = ramStr;
                    final String fBat = batStr;
                    final String fGov = govStr;
                    final String fZram = zramStr;
                    final String fCurFreq = curFreq;
                    final String fMaxFreq = maxFreqVal;
                    final String fLittle = little;
                    final String fBig = big;
                    final String fTemp = temp;
                    final String fMaxTools = maxTools;

                    runOnUiThread(() -> {
                        if(tvRam != null) tvRam.setText(fRam);
                        if(tvBattery != null) tvBattery.setText(fBat);
                        if(tvCpu != null) tvCpu.setText(fGov.toUpperCase());
                        if(tvZram != null) tvZram.setText(fZram);
                        
                        if(tvCurrentFreq != null) tvCurrentFreq.setText(fCurFreq);
                        if(tvMaxFreq != null) tvMaxFreq.setText(fMaxFreq);
                        if(tvMaxFreqTools != null) tvMaxFreqTools.setText(fMaxTools);
                        if(tvLittleCluster != null) tvLittleCluster.setText(fLittle);
                        if(tvBigCluster != null) tvBigCluster.setText(fBig);
                        if(tvTemp != null) tvTemp.setText(fTemp);
                    });

                }).start(); 

                handler.postDelayed(this, 2000); 
            }
        });
    }

    // --- THEME & GLASS LOGIC ---

    private void setBackgroundMode(int mode) {
        int bgCol, cardCol, textCol;
        boolean isDark = true;
        
        if(mode == 0) { // Black
            bgCol = Color.parseColor("#121212");
            cardCol = Color.parseColor("#1E1E1E");
            textCol = Color.WHITE;
        } else if (mode == 1) { // White
            bgCol = Color.parseColor("#F0F0F0");
            cardCol = Color.parseColor("#FFFFFF");
            textCol = Color.BLACK;
            isDark = false;
        } else if (mode == 3) { // Transparent (NEW)
            bgCol = Color.parseColor("#00000000"); // Fully Transparent
            cardCol = Color.parseColor("#DD000000"); // Semi-transparent dark for cards
            textCol = Color.WHITE;
            isDark = true;
        } else { // Gray (Fallback)
            bgCol = Color.parseColor("#808080");
            cardCol = Color.parseColor("#909090");
            textCol = Color.WHITE;
        }

        // --- REAL GLASS EFFECT ---
        if(isGlassTheme) {
            // Card becomes Glassy: Semi-transparent + Border
            if (isDark) {
                cardCol = Color.parseColor("#80000000"); // 50% Black Glass
            } else {
                cardCol = Color.parseColor("#80FFFFFF"); // 50% White Glass
            }
        }

        prefs.edit().putInt("bg_mode", mode).apply();

        if(rootLayout != null) rootLayout.setBackgroundColor(bgCol);
        
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(cardCol);
        gd.setCornerRadius(20);
        
        // Add Stroke if Glass is active to create the "Glass Edge" look
        if(isGlassTheme) {
            gd.setStroke(2, Color.parseColor("#33FFFFFF")); // Thin white border
        } else {
            gd.setStroke(0, Color.TRANSPARENT);
        }
        
        if(cardRam != null) cardRam.setBackground(gd);
        if(cardBat != null) cardBat.setBackground(gd);
        
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
        
        // Cycle Colors
        switch(iconColorMode) {
            case 1: color = Color.parseColor("#FF4081"); // Rainbow Pink
                break;
            case 2: color = Color.parseColor("#00E5FF"); // Cyan
                break;
            case 3: color = Color.parseColor("#FF9100"); // Orange
                break;
            case 4: color = Color.parseColor("#D500F9"); // Purple
                break;
            default: color = Color.parseColor("#AAAAAA"); // Default Gray
                break;
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
        int colorInactive = Color.GRAY; // Default inactive

        // Get Active Color based on mode
        switch(iconColorMode) {
            case 1: colorInactive = Color.parseColor("#FF4081"); break;
            case 2: colorInactive = Color.parseColor("#00E5FF"); break;
            case 3: colorInactive = Color.parseColor("#FF9100"); break;
            case 4: colorInactive = Color.parseColor("#D500F9"); break;
        }

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

    private void pickGov() {
        new Thread(() -> {
            String raw = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors");
            String[] govs = raw.split("\\s+");
            if(govs.length > 0 && !govs[0].isEmpty()) {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                        .setTitle("CPU GOVERNOR")
                        .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, govs), (d, w) -> {
                            new Thread(() -> {
                                String cmd = "for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo " + govs[w] + " > $c; done";
                                runSu(cmd);
                            }).start();
                            Toast.makeText(this, "Governor set to " + govs[w], Toast.LENGTH_SHORT).show();
                        }).show();
                });
            } else {
                runOnUiThread(() -> Toast.makeText(this, "Failed to read governors", Toast.LENGTH_SHORT).show());
            }
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
