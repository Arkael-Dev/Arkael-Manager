package com.vortex.core;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.IntentFilter;
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
    
    // Static Data
    private int maxFreqKhz = 0;
    private int minFreqKhz = 0;
    private int staticCoreCount = 4;
    private String staticLittleFreq = "N/A";
    private String staticBigFreq = "N/A";
    private String totalRamStr = "Unknown"; // Menyimpan Total RAM Hardware
    
    private boolean isGlassTheme = false;
    private int iconColorMode = 0; 
    private boolean staticInfoLoaded = false;
    
    // Launcher untuk Pick Image
    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("VortexPrefs", 0);
        
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

        // --- AUTH LOGIC ---
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

        // --- PERBAIKAN: SETTING ICON APLIKASI ---
        // Karena nav adalah LinearLayout, kita ambil anak (child) ke-0 yang merupakan TextView
        if (navSystem != null && navSystem.getChildCount() > 0) {
            View child = navSystem.getChildAt(0);
            if(child instanceof TextView) {
                ((TextView) child).setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_info_details, 0, 0, 0);
            }
        }
        if (navTools != null && navTools.getChildCount() > 0) {
            View child = navTools.getChildAt(0);
            if(child instanceof TextView) {
                ((TextView) child).setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_manage, 0, 0, 0);
            }
        }
        if (navSettings != null && navSettings.getChildCount() > 0) {
            View child = navSettings.getChildAt(0);
            if(child instanceof TextView) {
                ((TextView) child).setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_preferences, 0, 0, 0);
            }
        }
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

        findViewById(R.id.btn_cpu_gov).setOnClickListener(v -> pickGov());
        findViewById(R.id.btn_set_zram).setOnClickListener(v -> showZramMenu());
        findViewById(R.id.btn_thermal).setOnClickListener(v -> showThermalMenu());
        findViewById(R.id.btn_clean_ram).setOnClickListener(v -> {
            Toast.makeText(this, "Starting Advanced Clean...", Toast.LENGTH_SHORT).show();
            cleanRam();
        });

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
        findViewById(R.id.btn_bg_transparent).setOnClickListener(v -> setBackgroundMode(3));

        findViewById(R.id.tv_dev_link).setOnClickListener(v -> openUrl("https://t.me/VorteXSU_Dev"));
        findViewById(R.id.tv_channel_link).setOnClickListener(v -> openUrl("https://t.me/vortexgki"));

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
            // 1. GET ACCURATE TOTAL RAM (Hardware Specific)
            ActivityManager memInfo = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            memInfo.getMemoryInfo(mi);
            long totalMemBytes = mi.totalMem;
            // Format RAM: 8.0 GB, 12.0 GB, etc.
            if (totalMemBytes >= 1073741824) {
                double gb = totalMemBytes / (1024.0 * 1024.0 * 1024.0);
                totalRamStr = String.format("%.1f GB", gb);
            } else {
                totalRamStr = (totalMemBytes / 1048576) + " MB";
            }

            String brand = Build.BRAND;
            String model = Build.MODEL;
            String kernelFull = runSuReturn("uname -r");
            if(kernelFull.isEmpty()) kernelFull = "Unknown Kernel";
            
            String platform = runSuReturn("getprop ro.board.platform").toLowerCase();
            String hardware = runSuReturn("getprop ro.hardware").toLowerCase();
            String socModel = runSuReturn("getprop ro.soc.model"); 
            String vendor = "Unknown Device";
            String gpu = "Unknown GPU";

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

            boolean gpuFound = false;
            if (platform.contains("mt") || hardware.contains("mt")) {
                gpu = runSuReturn("cat /sys/class/misc/mali0/device/gpu_model 2>/dev/null");
                if(gpu.isEmpty()) gpu = runSuReturn("cat /sys/kernel/debug/mali0/gpu_id 2>/dev/null");
                if(!gpu.isEmpty()) gpuFound = true;
            } else if (platform.contains("qcom") || platform.contains("msm")) {
                gpu = runSuReturn("cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null");
                if(gpu.isEmpty()) gpu = runSuReturn("cat /sys/devices/platform/soc/soc:qcom,kgsl-3d0/devfreq/soc:qcom,kgsl-3d0/gpu_model 2>/dev/null");
                if(!gpu.isEmpty()) gpuFound = true;
            }

            if (!gpuFound) {
                if (platform.contains("qcom") || platform.contains("msm")) gpu = "Adreno GPU";
                else if (platform.contains("mt") || hardware.contains("mt")) gpu = "Mali GPU";
                else if (platform.contains("exynos")) gpu = "Exynos GPU";
                else if (platform.contains("intel")) gpu = "Intel GPU";
                else gpu = "Generic GPU (" + platform.toUpperCase() + ")";
            }
            
            String gl = runSuReturn("getprop ro.opengles.version");
            if(gl.isEmpty()) gl = "OpenGL ES 3.x";
            else gl = "OpenGL ES " + gl;

            String max = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            if(!max.isEmpty()) maxFreqKhz = Integer.parseInt(max);

            String cpuCount = runSuReturn("cat /proc/cpuinfo | grep 'processor' | wc -l");
            if(!cpuCount.isEmpty()) staticCoreCount = Integer.parseInt(cpuCount.trim());
            
            int lastCore = Math.max(0, staticCoreCount - 1);
            String lit = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            String bigVal = runSuReturn("cat /sys/devices/system/cpu/cpu"+lastCore+"/cpufreq/cpuinfo_max_freq");
            if(bigVal.isEmpty()) bigVal = lit;

            staticLittleFreq = (lit.isEmpty() ? "N/A" : (Integer.parseInt(lit)/1000) + " MHz");
            staticBigFreq = (bigVal.isEmpty() ? "N/A" : (Integer.parseInt(bigVal)/1000) + " MHz");

            final String finalVendor = vendor;
            final String finalKernel = kernelFull;
            final String finalDevice = brand.toUpperCase() + " " + model;
            final String finalGpu = gpu;
            final String finalGl = gl;

            runOnUiThread(() -> {
                if(tvCpuVendor != null) {
                    tvCpuVendor.setText(finalVendor);
                    tvCpuVendor.setTextColor(Color.parseColor("#4CAF50")); // GREEN
                }
                if(tvKernel != null) {
                    tvKernel.setText(finalKernel);
                    tvKernel.setTextColor(Color.parseColor("#4CAF50")); // GREEN
                }
                if(tvDevice != null) {
                    tvDevice.setText(finalDevice);
                    tvDevice.setTextColor(Color.parseColor("#4CAF50")); // GREEN
                }
                if(tvGpuRenderer != null) {
                    tvGpuRenderer.setText(finalGpu);
                    tvGpuRenderer.setTextColor(Color.parseColor("#4CAF50")); // GREEN
                }
                if(tvGpuVersion != null) tvGpuVersion.setText(finalGl);
                if(tvMaxFreq != null) tvMaxFreq.setText((maxFreqKhz/1000) + " MHz");
                if(tvLittleCluster != null) tvLittleCluster.setText(staticLittleFreq);
                if(tvBigCluster != null) tvBigCluster.setText(staticBigFreq);
            });
        }).start();
    }

    private void startLoop() {
        handler.post(new Runnable() {
            @Override public void run() {
                new Thread(() -> {
                    String ramStr = "...";
                    String batStr = "...";
                    String tempStr = "N/A";

                    try {
                        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                        ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
                        
                        // RAM Display: Available / Total Hardware
                        long availMem = mi.availMem;
                        String availRamStr = (availMem / 1048576) + " MB";
                        if (totalRamStr == null) totalRamStr = "Unknown";
                        ramStr = availRamStr + " / " + totalRamStr;

                        // Battery via Intent (Compatible & No Root Popup)
                        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                        Intent batteryStatus = registerReceiver(null, ifilter);
                        
                        if(batteryStatus != null) {
                            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                            float batteryPct = level * 100 / (float)scale;
                            
                            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                                 status == BatteryManager.BATTERY_STATUS_FULL;
                            String statusText = isCharging ? "Charging" : "Discharging";
                            
                            batStr = (int)batteryPct + "% (" + statusText + ")";

                            int temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                            if(temp != -1) {
                                tempStr = (temp / 10.0f) + "°C";
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }

                    String curFreq = "N/A";
                    String govStr = "Unknown";
                    String maxTools = "N/A";
                    
                    try {
                        // Batch Root Commands
                        String script = "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq\necho ---SEPARATOR---\ncat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor\necho ---SEPARATOR---\ncat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq";
                        String output = runSuReturnAll(script);
                        String[] parts = output.split("---SEPARATOR---");
                        
                        if(parts.length >= 1 && !parts[0].trim().isEmpty()) {
                            int cur = Integer.parseInt(parts[0].trim());
                            curFreq = (cur/1000) + " MHz";
                        }
                        if(parts.length >= 2 && !parts[1].trim().isEmpty()) govStr = parts[1].trim();
                        if(parts.length >= 3 && !parts[2].trim().isEmpty()) {
                            int currentMax = Integer.parseInt(parts[2].trim());
                            maxTools = (currentMax/1000) + " MHz";
                        } else {
                            maxTools = (maxFreqKhz/1000) + " MHz";
                        }
                    } catch (Exception e) {
                        curFreq = "Error"; govStr = "Error";
                    }

                    final String fRam = ramStr;
                    final String fBat = batStr;
                    final String fGov = govStr;
                    final String fCurFreq = curFreq;
                    final String fMaxTools = maxTools;
                    final String fTemp = tempStr;

                    runOnUiThread(() -> {
                        if(tvRam != null) tvRam.setText(fRam);
                        if(tvBattery != null) tvBattery.setText(fBat);
                        if(tvCpu != null) tvCpu.setText(fGov.toUpperCase());
                        if(tvCurrentFreq != null) tvCurrentFreq.setText(fCurFreq);
                        if(tvMaxFreqTools != null) tvMaxFreqTools.setText(fMaxTools);
                        if(tvTemp != null) tvTemp.setText(fTemp);
                    });
                }).start(); 
                handler.postDelayed(this, 2000); 
            }
        });
    }

    // --- THEME & UI LOGIC ---

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
        } else if (mode == 3) { // Transparent
            bgCol = Color.parseColor("#00000000");
            cardCol = Color.parseColor("#DD000000");
            textCol = Color.WHITE;
            isDark = true;
        } else { // Gray
            bgCol = Color.parseColor("#808080");
            cardCol = Color.parseColor("#909090");
            textCol = Color.WHITE;
        }

        if(isGlassTheme) {
            if (isDark) cardCol = Color.parseColor("#80000000");
            else cardCol = Color.parseColor("#80FFFFFF");
        }

        prefs.edit().putInt("bg_mode", mode).apply();
        if(rootLayout != null) rootLayout.setBackgroundColor(bgCol);
        
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(cardCol);
        gd.setCornerRadius(20);
        if(isGlassTheme) gd.setStroke(2, Color.parseColor("#33FFFFFF"));
        else gd.setStroke(0, Color.TRANSPARENT);
        
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
        switch(iconColorMode) {
            case 1: color = Color.parseColor("#FF4081"); break;
            case 2: color = Color.parseColor("#00E5FF"); break;
            case 3: color = Color.parseColor("#FF9100"); break;
            case 4: color = Color.parseColor("#D500F9"); break;
            default: color = Color.parseColor("#AAAAAA"); break;
        }
        
        // Apply color to icons we set in initViews (child of LinearLayout)
        if(navSystem != null && navSystem.getChildCount() > 0) {
            tintCompoundDrawables(navSystem.getChildAt(0), color);
        }
        if(navTools != null && navTools.getChildCount() > 0) {
            tintCompoundDrawables(navTools.getChildAt(0), color);
        }
        if(navSettings != null && navSettings.getChildCount() > 0) {
            tintCompoundDrawables(navSettings.getChildAt(0), color);
        }
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
                applyBannerRadius(); // Apply rounded corners
                return;
            }
        }
        try {
            Glide.with(this).load(R.drawable.header_bg).centerCrop().into(headerBanner);
            headerBanner.setVisibility(View.VISIBLE);
            bannerContainer.setVisibility(View.VISIBLE);
            applyBannerRadius();
        } catch (Exception e) {
            headerBanner.setVisibility(View.GONE);
            bannerContainer.setVisibility(View.GONE);
        }
        findViewById(R.id.btn_reset_banner).setVisibility(View.GONE);
    }

    private void applyBannerRadius() {
        // Membuat Banner Rounded sesuai Card (20dp)
        if(headerBanner != null) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadius(20); // Radius sesuai card
            shape.setColor(Color.TRANSPARENT); 
            headerBanner.setBackground(shape);
            // Clip to outline agar gambar ikut terpotong rounded
            headerBanner.setClipToOutline(true);
        }
    }

    private void updateNavUI(int activeIndex) {
        int colorActive = Color.parseColor("#4CAF50");
        int colorInactive = Color.GRAY;

        switch(iconColorMode) {
            case 1: colorInactive = Color.parseColor("#FF4081"); break;
            case 2: colorInactive = Color.parseColor("#00E5FF"); break;
            case 3: colorInactive = Color.parseColor("#FF9100"); break;
            case 4: colorInactive = Color.parseColor("#D500F9"); break;
        }

        if(navSystem != null && navSystem.getChildCount() > 0) 
            ((TextView)navSystem.getChildAt(0)).setTextColor(activeIndex == 0 ? colorActive : colorInactive);
        if(navTools != null && navTools.getChildCount() > 0) 
            ((TextView)navTools.getChildAt(0)).setTextColor(activeIndex == 1 ? colorActive : colorInactive);
        if(navSettings != null && navSettings.getChildCount() > 0) 
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
