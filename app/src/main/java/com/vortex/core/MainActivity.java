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
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
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
import java.lang.Process;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    
    // CPU Control Data
    private int cpuMaxFreqKhz = 0;
    private int cpuMinFreqKhz = 0;
    private int staticCoreCount = 4;
    private String staticLittleFreq = "N/A";
    private String staticBigFreq = "N/A";
    private String totalRamStr = "Unknown"; 

    // GPU Control Data
    private int gpuMaxFreqKhz = 0;
    private int gpuMinFreqKhz = 0;
    private String gpuMaxFreqPath = "";
    private String gpuCurFreqPath = "";
    private String gpuGovPath = "";
    private String gpuAvailFreqPath = ""; 
    private boolean isGpuControlReady = false;
    private boolean isGpuModeActive = false;
    private List<Integer> gpuAvailableFreqs = new ArrayList<>(); 
    
    // ZRAM State Tracking
    private int currentZramSizeGB = 0;

    // Animation Helper
    private boolean batteryBlinkState = false;

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

    // --- HELPER: SAFE INT PARSING ---
    private int parseIntSafe(String s) {
        if (s == null) return -1;
        String trimmed = s.trim();
        if (!trimmed.matches("\\d+")) return -1;
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // --- HELPER: GET TOTAL RAM BYTES ---
    private long getTotalRamBytes() {
        ActivityManager memInfo = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        memInfo.getMemoryInfo(mi);
        return mi.totalMem;
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

        if(tvRam != null) {
            tvRam.setMaxLines(1);
            tvRam.setSingleLine(true);
            tvRam.setEllipsize(null); 
            tvRam.setHorizontallyScrolling(true);
            tvRam.setGravity(Gravity.CENTER_VERTICAL);
            tvRam.setIncludeFontPadding(false);
        }

        if (navSystem != null && navSystem.getChildCount() > 0) {
            View child = navSystem.getChildAt(0);
            if(child instanceof TextView) ((TextView) child).setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_info_details, 0, 0, 0);
        }
        if (navTools != null && navTools.getChildCount() > 0) {
            View child = navTools.getChildAt(0);
            if(child instanceof TextView) ((TextView) child).setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_manage, 0, 0, 0);
        }
        if (navSettings != null && navSettings.getChildCount() > 0) {
            View child = navSettings.getChildAt(0);
            if(child instanceof TextView) ((TextView) child).setCompoundDrawablesRelativeWithIntrinsicBounds(android.R.drawable.ic_menu_preferences, 0, 0, 0);
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

        findViewById(R.id.btn_gpu_control).setOnClickListener(v -> showGpuFreqMenu());
        findViewById(R.id.btn_io_scheduler).setOnClickListener(v -> showIoSchedulerMenu());
        findViewById(R.id.btn_zram_algo).setOnClickListener(v -> showZramAlgoMenu()); 
        findViewById(R.id.btn_saturation).setOnClickListener(v -> showSaturationMenu()); 
        findViewById(R.id.btn_renderer).setOnClickListener(v -> showRendererMenu());
        findViewById(R.id.btn_vsync).setOnClickListener(v -> toggleCpuVsync());

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

        if(tvMaxFreq != null) {
            tvMaxFreq.setOnLongClickListener(v -> {
                if(!isGpuControlReady) {
                    Toast.makeText(this, "GPU Control Not Supported", Toast.LENGTH_SHORT).show();
                    return true;
                }
                isGpuModeActive = !isGpuModeActive;
                String mode = isGpuModeActive ? "GPU CONTROL ACTIVE" : "CPU CONTROL ACTIVE";
                Toast.makeText(this, mode, Toast.LENGTH_SHORT).show();
                refreshControlMode(); 
                return true;
            });
        }

        seekBarMaxFreq.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int targetFreq = 0;
                    if (isGpuModeActive && isGpuControlReady) {
                        int range = gpuMaxFreqKhz - gpuMinFreqKhz;
                        targetFreq = gpuMinFreqKhz + ((range * progress) / 100);
                        targetFreq = Math.max(gpuMinFreqKhz, Math.min(gpuMaxFreqKhz, targetFreq));
                        setGpuMaxFreq(targetFreq);
                        if(tvMaxFreqTools != null) tvMaxFreqTools.setText((targetFreq/1000) + " MHz");
                    } else if (!isGpuModeActive && cpuMaxFreqKhz > 0 && cpuMinFreqKhz > 0) {
                        int range = cpuMaxFreqKhz - cpuMinFreqKhz;
                        targetFreq = cpuMinFreqKhz + ((range * progress) / 100);
                        targetFreq = Math.max(cpuMinFreqKhz, Math.min(cpuMaxFreqKhz, targetFreq));
                        setCpuMaxFreq(targetFreq);
                        if(tvMaxFreqTools != null) tvMaxFreqTools.setText((targetFreq/1000) + " MHz");
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if(isGpuModeActive) validateGpuFreqLimit();
                else validateCpuFreqLimit();
            }
        });
    }

    // --- FIX ZRAM CONFIGURATION - FINAL TEMPLATE ---
    // Urutan yang benar: swapoff -> reset -> comp_algorithm -> disksize -> mkswap -> swapon -> verifikasi
    public void applyZram(int sizeGB) {
        new Thread(() -> {
            long sizeInBytes = sizeGB * 1073741824L;
            long totalRam = getTotalRamBytes();

            // Safety Check: Don't allow ZRAM larger than Physical RAM
            if (sizeInBytes > totalRam) {
                runOnUiThread(() -> Toast.makeText(this, "Warning: ZRAM Size > Total RAM! Risky.", Toast.LENGTH_LONG).show());
            }

            // FIXED BASH SCRIPT STRUCTURE
            // 1. swapoff
            // 2. reset
            // 3. set comp_algorithm (zstd)
            // 4. set disksize
            // 5. mkswap
            // 6. swapon
            // 7. validasi (/proc/swaps)
            String script = 
                "swapoff /dev/block/zram0 2>/dev/null\n" +
                "echo 1 > /sys/block/zram0/reset 2>/dev/null\n" +
                "echo zstd > /sys/block/zram0/comp_algorithm 2>/dev/null\n" +
                "echo " + sizeInBytes + " > /sys/block/zram0/disksize 2>/dev/null\n" +
                "mkswap /dev/block/zram0 2>/dev/null\n" +
                "swapon /dev/block/zram0 2>/dev/null\n" +
                "grep -q zram /proc/swaps && echo \"OK\" || echo \"FAILED\"";

            String output = runSuReturnAll(script);
            
            // Parse Result
            if (output.contains("FAILED")) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "ZRAM Activation Failed", Toast.LENGTH_LONG).show();
                    if(tvTerminalLog != null) tvTerminalLog.setText(output);
                });
            } else if (output.contains("OK")) {
                currentZramSizeGB = sizeGB; // Save state
                runOnUiThread(() -> {
                    Toast.makeText(this, "ZRAM " + sizeGB + "GB Activated Successfully", Toast.LENGTH_SHORT).show();
                    if(tvTerminalLog != null) tvTerminalLog.setText("ZRAM " + sizeGB + "GB Activated\nAlgorithm: zstd\nSize: " + sizeInBytes + " bytes");
                    if(tvZram != null) tvZram.setText(sizeGB + " GB");
                });
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, "ZRAM Activation Error (Unknown)", Toast.LENGTH_LONG).show();
                    if(tvTerminalLog != null) tvTerminalLog.setText(output);
                });
            }
        }).start();
    }

    private void showGpuFreqMenu() {
        if (!isGpuControlReady || gpuAvailableFreqs.isEmpty()) {
            Toast.makeText(this, "GPU Control Not Ready or No Freqs Found", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("GPU Frequency Control (Universal)");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        TextView tvValue = new TextView(this);
        tvValue.setText("Current: " + (gpuAvailableFreqs.get(gpuAvailableFreqs.size()-1)/1000) + " MHz");
        tvValue.setTextSize(20);
        tvValue.setTextColor(Color.WHITE);
        tvValue.setGravity(Gravity.CENTER);
        layout.addView(tvValue);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(gpuAvailableFreqs.size() - 1);
        int currentMax = parseIntSafe(runSuReturn("cat " + gpuMaxFreqPath));
        int index = gpuAvailableFreqs.indexOf(currentMax);
        if(index == -1) index = gpuAvailableFreqs.size() - 1;
        seekBar.setProgress(index);
        
        layout.addView(seekBar);

        final TextView finalTvValue = tvValue;
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int freq = gpuAvailableFreqs.get(progress);
                finalTvValue.setText((freq/1000) + " MHz");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                int freq = gpuAvailableFreqs.get(seekBar.getProgress());
                runSu("echo " + freq + " > " + gpuMaxFreqPath);
                Toast.makeText(MainActivity.this, "GPU Max Set: " + (freq/1000) + " MHz", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setView(layout);
        builder.setPositiveButton("Close", null);
        builder.show();
    }

    private void showSaturationMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Screen Saturation Control");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        TextView tvValue = new TextView(this);
        tvValue.setText("Current: 1.00");
        tvValue.setTextSize(20);
        tvValue.setTextColor(Color.WHITE);
        tvValue.setGravity(Gravity.CENTER);
        layout.addView(tvValue);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(150); 
        seekBar.setProgress(100); 
        layout.addView(seekBar);

        final TextView finalTvValue = tvValue;
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = progress / 100.0f;
                finalTvValue.setText(String.format("%.2f", val));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                float val = seekBar.getProgress() / 100.0f;
                new Thread(() -> {
                    runSu("service call SurfaceFlinger 1023 i32 0");
                    runSu("service call SurfaceFlinger 1022 f " + val);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Saturation Set: " + val, Toast.LENGTH_SHORT).show();
                        if(tvTerminalLog != null) tvTerminalLog.setText("> Saturation: " + val);
                    });
                }).start();
            }
        });

        builder.setView(layout);
        builder.setPositiveButton("Close", null);
        builder.show();
    }

    private void showIoSchedulerMenu() {
        final String[] options = {"ssg", "mq-deadline", "kyber", "cpq", "none"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select IO Scheduler");
        builder.setItems(options, (dialog, which) -> {
            String selected = options[which];
            new Thread(() -> {
                runSu("for queue in /sys/block/sd*/queue; do echo " + selected + " > \"$queue/scheduler\" 2>/dev/null; echo 0 > \"$queue/iostats\" 2>/dev/null; echo 128 > \"$queue/read_ahead_kb\" 2>/dev/null; done");
                runSu("for queue in /sys/block/mmc*/queue; do echo " + selected + " > \"$queue/scheduler\" 2>/dev/null; echo 0 > \"$queue/iostats\" 2>/dev/null; echo 128 > \"$queue/read_ahead_kb\" 2>/dev/null; done");
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "IO Scheduler: " + selected + " + Optimized", Toast.LENGTH_SHORT).show();
                    if(tvTerminalLog != null) tvTerminalLog.setText("> IO Scheduler set to " + selected + "\n> Read Ahead: 128KB\n> Iostats: Disabled");
                });
            }).start();
        });
        builder.show();
    }

    // --- UPDATED: ZRAM ALGO WITH VERIFICATION ---
    private void showZramAlgoMenu() {
        new Thread(() -> {
            String raw = runSuReturn("cat /sys/block/zram0/comp_algorithm");
            String cleanRaw = raw.replace("[", "").replace("]", "").trim();
            String[] available = cleanRaw.split("\\s+");
            
            if (available.length == 0 || (available.length == 1 && available[0].isEmpty())) {
                available = new String[]{"lzo", "lz4", "zstd", "lzo-rle"};
            }

            final String[] options = available;
            
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("ZRAM Compression Algorithm");
                builder.setItems(options, (dialog, which) -> {
                    String selected = options[which];
                    new Thread(() -> {
                        runOnUiThread(() -> {
                            if(tvTerminalLog != null) tvTerminalLog.setText("> Applying Algo: " + selected + "...\n> Retaining Size: " + currentZramSizeGB + "GB");
                        });

                        // Script with Algo Change
                        // Urutan: swapoff, reset, algo, disksize, mkswap, swapon
                        String script = 
                            "swapoff /dev/block/zram0 2>/dev/null\n" +
                            "echo 1 > /sys/block/zram0/reset 2>/dev/null\n" +
                            // Try to set user choice
                            "echo " + selected + " > /sys/block/zram0/comp_algorithm 2>/dev/null\n" +
                            // Restore Size (Robust)
                            "SIZE=" + (currentZramSizeGB > 0 ? (currentZramSizeGB * 1073741824L) : (4 * 1073741824L)) + "\n" +
                            "echo $SIZE > /sys/block/zram0/disksize\n" +
                            "mkswap /dev/block/zram0\n" +
                            "swapon /dev/block/zram0\n" +
                            // Verification
                            "if grep -q '/dev/block/zram0' /proc/swaps; then echo 'OK'; else echo 'FAILED'; fi";

                        String output = runSuReturnAll(script);

                        String finalStatus = (output.contains("OK")) ? "SUCCESS" : "FAILED";
                        
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Algo: " + finalStatus, Toast.LENGTH_LONG).show();
                            if(tvTerminalLog != null) {
                                tvTerminalLog.setText("> Algo Set: " + selected + "\n> " + finalStatus + "\n" + output);
                            }
                        });
                    }).start();
                });
                builder.show();
            });
        }).start();
    }

    private void showRendererMenu() {
        final String[] options = {"OpenGL Default", "Skia GL", "Vulkan (SkiaVK)"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Graphics Renderer");
        builder.setItems(options, (dialog, which) -> {
            new Thread(() -> {
                if(which == 0) { runSu("setprop debug.hwui.renderer opengl"); } 
                else if(which == 1) { runSu("setprop debug.composition.type skiagl"); runSu("setprop debug.hwui.renderer skiagl"); } 
                else if(which == 2) { runSu("setprop debug.hwui.renderer skiavk"); runSu("setprop ro.config.hw_quickpoweron true"); }
                final String sel = options[which];
                runOnUiThread(() -> {
                    Toast.makeText(this, "Renderer: " + sel, Toast.LENGTH_SHORT).show();
                    if(tvTerminalLog != null) tvTerminalLog.setText("> Renderer: " + sel);
                });
            }).start();
        });
        builder.show();
    }

    private void toggleCpuVsync() {
        new Thread(() -> {
            runSu("setprop debug.cpurend.vsync false");
            runOnUiThread(() -> {
                Toast.makeText(this, "CPU VSync Disabled", Toast.LENGTH_SHORT).show();
                if(tvTerminalLog != null) tvTerminalLog.setText("> CPU VSync: OFF");
            });
        }).start();
    }
    
    public void showThermalMenu() {
        final String[] options = {"DISABLE THERMAL (NO REBOOT)", "ENABLE THERMAL (RESTORE)"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Thermal Control (Direct)");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                new Thread(() -> {
                    runSu("stop android.thermal-hal");
                    runSu("stop mi_thermald");
                    runSu("resetprop -n init.svc.android.thermal-hal stopped");
                    runSu("resetprop -n init.svc.mi_thermald stopped");
                    runSu("find /sys/devices/virtual/thermal -name temp -type f -exec chmod 000 {} +");
                    runOnUiThread(() -> { 
                        tvTerminalLog.setText("> Thermal DISABLED DIRECTLY!\n> Services STOPPED\n> Sensors Blocked"); 
                        Toast.makeText(this, "Thermal Disabled (No Reboot)", Toast.LENGTH_SHORT).show(); 
                    });
                }).start();
            } else {
                new Thread(() -> {
                    runSu("start android.thermal-hal");
                    runSu("start mi_thermald");
                    runSu("find /sys/devices/virtual/thermal -name temp -type f -exec chmod 644 {} + 2>/dev/null");
                    runOnUiThread(() -> { 
                        tvTerminalLog.setText("> Thermal RESTORED DIRECTLY!\n> Services RUNNING\n> Sensors Unblocked"); 
                        Toast.makeText(this, "Thermal Restored (No Reboot)", Toast.LENGTH_SHORT).show(); 
                    });
                }).start();
            }
        });
        builder.show();
    }
    
    private void refreshControlMode() {
        new Thread(() -> {
            int currentLimit = 0;
            int max = isGpuModeActive ? gpuMaxFreqKhz : cpuMaxFreqKhz;
            int min = isGpuModeActive ? gpuMinFreqKhz : cpuMinFreqKhz;
            
            if (isGpuModeActive && isGpuControlReady) {
                 currentLimit = parseIntSafe(runSuReturn("cat " + gpuMaxFreqPath));
            } else {
                 String rawMax = runSuReturnAll("cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq");
                 String[] lines = rawMax.split("\\s+");
                 int highest = 0;
                 for(String line : lines) {
                     int val = parseIntSafe(line);
                     if(val > highest) highest = val;
                 }
                 currentLimit = highest;
            }

            if (max == 0 || min < 0) return; 

            int range = max - min;
            if (range <= 0) {
                runOnUiThread(() -> {
                    if(seekBarMaxFreq != null) seekBarMaxFreq.setProgress(100);
                    if(tvMaxFreqTools != null) tvMaxFreqTools.setText((max/1000) + " MHz " + (isGpuModeActive ? "(GPU)" : "(CPU)"));
                });
                return;
            }

            if (currentLimit > 0) {
                int progress = (int) (((currentLimit - min) / (float) range) * 100);
                final int fProgress = Math.min(100, Math.max(0, progress));
                final String text = (currentLimit/1000) + " MHz " + (isGpuModeActive ? "(GPU)" : "(CPU)");
                
                runOnUiThread(() -> {
                    if(seekBarMaxFreq != null) seekBarMaxFreq.setProgress(fProgress);
                    if(tvMaxFreqTools != null) tvMaxFreqTools.setText(text);
                });
            }
        }).start();
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
            ActivityManager memInfo = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            memInfo.getMemoryInfo(mi);
            long totalMemBytes = mi.totalMem;
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
            String socMan = runSuReturn("getprop ro.soc.manufacturer").toLowerCase();

            String zramDisplay = "Disabled";
            try {
                String zramRaw = runSuReturn("cat /sys/block/zram0/disksize 2>/dev/null");
                if(!zramRaw.isEmpty()) {
                    long zramBytes = Long.parseLong(zramRaw.trim());
                    if (zramBytes > 0) {
                        double zramGB = zramBytes / (1024.0 * 1024.0 * 1024.0);
                        zramDisplay = String.format("%.1f GB", zramGB);
                        currentZramSizeGB = (int) Math.round(zramGB); 
                    }
                }
            } catch (Exception e) { zramDisplay = "Error"; }
            final String fZram = zramDisplay;

            String rawMaxFreqs = runSuReturnAll("cat /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq");
            String[] maxLines = rawMaxFreqs.split("\\s+");
            int detectedMax = 0;
            for (String line : maxLines) {
                int val = parseIntSafe(line);
                if (val > detectedMax) detectedMax = val;
            }

            String rawMinFreqs = runSuReturnAll("cat /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_min_freq");
            String[] minLines = rawMinFreqs.split("\\s+");
            int detectedMin = Integer.MAX_VALUE;
            for (String line : minLines) {
                int val = parseIntSafe(line);
                if (val > 0 && val < detectedMin) detectedMin = val;
            }

            if (detectedMax > 0) cpuMaxFreqKhz = detectedMax;
            if (detectedMin != Integer.MAX_VALUE) cpuMinFreqKhz = detectedMin;
            else cpuMinFreqKhz = 300000;

            if (new File("/sys/class/kgsl/kgsl-3d0/gpuclk").exists()) {
                gpuCurFreqPath = "/sys/class/kgsl/kgsl-3d0/gpuclk";
                gpuMaxFreqPath = "/sys/class/kgsl/kgsl-3d0/max_gpuclk";
                gpuAvailFreqPath = "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies";
                
                String minGpu = runSuReturn("cat /sys/class/kgsl/kgsl-3d0/min_gpuclk");
                if(minGpu.isEmpty()) {
                    String avail = runSuReturn("cat /sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies");
                    String[] freqs = avail.split("\\s+");
                    if(freqs.length > 0) gpuMinFreqKhz = parseIntSafe(freqs[0]);
                    else gpuMinFreqKhz = 0;
                } else {
                    gpuMinFreqKhz = parseIntSafe(minGpu);
                }

                if(new File("/sys/class/kgsl/kgsl-3d0/devfreq/governor").exists()) {
                    gpuGovPath = "/sys/class/kgsl/kgsl-3d0/devfreq/governor";
                }
                
                String sGpuMax = runSuReturn("cat " + gpuMaxFreqPath);
                gpuMaxFreqKhz = parseIntSafe(sGpuMax);
                
                if(gpuMaxFreqKhz > 0) isGpuControlReady = true;
            }
            else if (new File("/sys/class/devfreq/mali0/cur_freq").exists()) {
                gpuCurFreqPath = "/sys/class/devfreq/mali0/cur_freq";
                gpuMaxFreqPath = "/sys/class/devfreq/mali0/max_freq";
                gpuGovPath = "/sys/class/devfreq/mali0/governor";
                gpuAvailFreqPath = "/sys/class/devfreq/mali0/available_frequencies";
                
                String sGpuMax = runSuReturn("cat " + gpuMaxFreqPath);
                gpuMaxFreqKhz = parseIntSafe(sGpuMax);
                
                String sGpuMin = runSuReturn("cat /sys/class/devfreq/mali0/min_freq");
                gpuMinFreqKhz = parseIntSafe(sGpuMin);
                
                if(gpuMaxFreqKhz > 0) isGpuControlReady = true;
            }

            if(!gpuAvailFreqPath.isEmpty()) {
                String availRaw = runSuReturn("cat " + gpuAvailFreqPath);
                if(!availRaw.isEmpty()) {
                    String[] strFreqs = availRaw.split("\\s+");
                    gpuAvailableFreqs.clear();
                    for(String s : strFreqs) {
                        int val = parseIntSafe(s);
                        if(val > 0) gpuAvailableFreqs.add(val);
                    }
                    Collections.sort(gpuAvailableFreqs);
                }
            }

            String vendor = "Unknown";
            if (!socMan.isEmpty()) vendor = socMan.substring(0, 1).toUpperCase() + socMan.substring(1).toLowerCase();
            else if (platform.contains("mt") || hardware.contains("mt")) vendor = "Mediatek";
            else if (platform.contains("qcom") || platform.contains("msm")) vendor = "Qualcomm";
            else vendor = platform.toUpperCase();

            String cpuArch = Build.SUPPORTED_ABIS[0]; 
            String archDisplay = cpuArch.toUpperCase();
            if(!socModel.isEmpty() && !socModel.equals("unknown")) archDisplay += " (" + socModel + ")";
            else archDisplay += " (" + vendor + ")";

            String gpu = "Unknown GPU";
            if (platform.contains("mt") || hardware.contains("mt")) gpu = "Mali GPU";
            else if (platform.contains("qcom") || platform.contains("msm")) gpu = "Adreno GPU";
            else if (platform.contains("exynos")) gpu = "Exynos GPU";
            else gpu = "Generic GPU (" + platform.toUpperCase() + ")";

            String cpuCount = runSuReturn("cat /proc/cpuinfo | grep 'processor' | wc -l");
            if(!cpuCount.isEmpty()) staticCoreCount = Integer.parseInt(cpuCount.trim());
            int lastCore = Math.max(0, staticCoreCount - 1);
            String lit = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            String bigVal = runSuReturn("cat /sys/devices/system/cpu/cpu"+lastCore+"/cpufreq/cpuinfo_max_freq");
            if(bigVal.isEmpty()) bigVal = lit;
            staticLittleFreq = (lit.isEmpty() ? "N/A" : (Integer.parseInt(lit)/1000) + " MHz");
            staticBigFreq = (bigVal.isEmpty() ? "N/A" : (Integer.parseInt(bigVal)/1000) + " MHz");

            final String finalKernel = kernelFull;
            final String finalDevice = brand.toUpperCase() + " " + model;
            final String finalGpu = gpu;
            final String finalArchDisplay = archDisplay;

            runOnUiThread(() -> {
                if(tvCpuVendor != null) tvCpuVendor.setText(finalArchDisplay);
                if(tvKernel != null) tvKernel.setText(finalKernel);
                if(tvDevice != null) tvDevice.setText(finalDevice);
                if(tvGpuRenderer != null) tvGpuRenderer.setText(finalGpu);
                if(tvMaxFreq != null) tvMaxFreq.setText((cpuMaxFreqKhz/1000) + " MHz");
                if(tvLittleCluster != null) tvLittleCluster.setText(staticLittleFreq);
                if(tvBigCluster != null) tvBigCluster.setText(staticBigFreq);
                if(tvZram != null) tvZram.setText(fZram);
                refreshControlMode();
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
                    String curFreq = "N/A";
                    String maxTools = "N/A";
                    String govStr = "Unknown";

                    int level = -1;
                    boolean isCharging = false;
                    int iconId = android.R.drawable.ic_menu_info_details;
                    int batColor = Color.WHITE;

                    try {
                        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                        ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
                        
                        double totalGB = mi.totalMem / (1024.0 * 1024.0 * 1024.0);
                        double usedGB = (mi.totalMem - mi.availMem) / (1024.0 * 1024.0 * 1024.0);
                        ramStr = String.format("%.1f / %.1f GB", usedGB, totalGB);

                        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                        Intent batteryStatus = registerReceiver(null, ifilter);
                        if(batteryStatus != null) {
                            level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                            float batteryPct = level * 100 / (float)scale;
                            
                            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
                            String statusText = isCharging ? "Charging" : "Discharging";
                            batStr = (int)batteryPct + "% (" + statusText + ")";

                            if(isCharging) {
                                iconId = android.R.drawable.ic_menu_upload; 
                                batColor = Color.CYAN;
                            } else if(level <= 20) {
                                iconId = android.R.drawable.ic_dialog_alert; 
                                batColor = Color.RED;
                            } else if(level <= 50) {
                                iconId = android.R.drawable.ic_menu_sort_by_size; 
                                batColor = Color.parseColor("#FFD700"); 
                            } else {
                                iconId = android.R.drawable.ic_menu_info_details; 
                                batColor = Color.parseColor("#00E676"); 
                            }

                            int temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                            if(temp != -1) tempStr = (temp / 10.0f) + "°C";
                        }
                    } catch (Exception e) { e.printStackTrace(); }

                    try {
                        if (isGpuModeActive && isGpuControlReady) {
                            String valCur = runSuReturn("cat " + gpuCurFreqPath);
                            String valMax = runSuReturn("cat " + gpuMaxFreqPath);
                            String valGov = runSuReturn("cat " + gpuGovPath);
                            
                            int c = parseIntSafe(valCur);
                            int m = parseIntSafe(valMax);
                            
                            if(c > 0) curFreq = (c/1000) + " MHz";
                            if(m > 0) maxTools = (m/1000) + " MHz";
                            if(!valGov.isEmpty()) govStr = valGov.trim();
                            else govStr = "N/A";

                        } else {
                            govStr = runSuReturn("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
                            
                            String maxFreqsRaw = runSuReturnAll("cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq");
                            String[] maxLines = maxFreqsRaw.split("\\s+");
                            int systemMaxVal = 0;
                            for(String line : maxLines) {
                                int val = parseIntSafe(line);
                                if(val > systemMaxVal) systemMaxVal = val;
                            }
                            if(systemMaxVal > 0) maxTools = (systemMaxVal/1000) + " MHz";

                            String cpuFreqsRaw = runSuReturnAll("cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq");
                            if(!cpuFreqsRaw.isEmpty()) {
                                String[] lines = cpuFreqsRaw.split("\\s+");
                                int maxCurFreqVal = 0;
                                for(String line : lines) {
                                    int val = parseIntSafe(line);
                                    if(val > maxCurFreqVal) maxCurFreqVal = val;
                                }
                                if(maxCurFreqVal > 0) curFreq = (maxCurFreqVal/1000) + " MHz";
                            }
                        }
                    } catch (Exception e) {
                        curFreq = "Err";
                    }

                    final String fRam = ramStr;
                    final String fBat = batStr;
                    final String fGov = govStr;
                    final String fCurFreq = curFreq;
                    final String fMaxTools = maxTools;
                    final String fTemp = tempStr;
                    final int fIcon = iconId;
                    final int fBatColor = batColor;
                    final boolean fIsCharging = isCharging;

                    runOnUiThread(() -> {
                        if(tvRam != null) tvRam.setText(fRam);
                        if(tvBattery != null) {
                            tvBattery.setText(fBat);
                            tvBattery.setTextColor(fBatColor);
                            tvBattery.setCompoundDrawablesRelativeWithIntrinsicBounds(fIcon, 0, 0, 0);
                            
                            if(fIsCharging) {
                                batteryBlinkState = !batteryBlinkState;
                                Drawable icon = getDrawable(fIcon);
                                if(icon != null) {
                                    icon.mutate(); 
                                    icon.setAlpha(batteryBlinkState ? 255 : 80); 
                                }
                                tvBattery.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
                            }
                        }
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
        if(mode == 0) { bgCol = Color.parseColor("#121212"); cardCol = Color.parseColor("#1E1E1E"); textCol = Color.WHITE; }
        else if (mode == 1) { bgCol = Color.parseColor("#F0F0F0"); cardCol = Color.parseColor("#FFFFFF"); textCol = Color.BLACK; isDark = false; }
        else if (mode == 3) { bgCol = Color.parseColor("#00000000"); cardCol = Color.parseColor("#DD000000"); textCol = Color.WHITE; isDark = true; }
        else { bgCol = Color.parseColor("#808080"); cardCol = Color.parseColor("#909090"); textCol = Color.WHITE; }
        if(isGlassTheme) { if (isDark) cardCol = Color.parseColor("#80000000"); else cardCol = Color.parseColor("#80FFFFFF"); }
        prefs.edit().putInt("bg_mode", mode).apply();
        if(rootLayout != null) rootLayout.setBackgroundColor(bgCol);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE); gd.setColor(cardCol); gd.setCornerRadius(20);
        if(isGlassTheme) gd.setStroke(2, Color.parseColor("#33FFFFFF")); else gd.setStroke(0, Color.TRANSPARENT);
        if(cardRam != null) cardRam.setBackground(gd); if(cardBat != null) cardBat.setBackground(gd);
        int finalText = isDark ? Color.WHITE : Color.BLACK;
        if(tvRam != null) tvRam.setTextColor(finalText); if(tvBattery != null) tvBattery.setTextColor(finalText);
        applyIconColor();
    }
    private void applyThemeSettings() { int savedMode = prefs.getInt("bg_mode", 0); setBackgroundMode(savedMode); }
    private void applyIconColor() {
        int color = Color.GRAY; 
        switch(iconColorMode) {
            case 1: color = Color.parseColor("#FF4081"); break; case 2: color = Color.parseColor("#00E5FF"); break;
            case 3: color = Color.parseColor("#FF9100"); break; case 4: color = Color.parseColor("#D500F9"); break;
            default: color = Color.parseColor("#AAAAAA"); break;
        }
        if(navSystem != null && navSystem.getChildCount() > 0) tintCompoundDrawables(navSystem.getChildAt(0), color);
        if(navTools != null && navTools.getChildCount() > 0) tintCompoundDrawables(navTools.getChildAt(0), color);
        if(navSettings != null && navSettings.getChildCount() > 0) tintCompoundDrawables(navSettings.getChildAt(0), color);
    }
    private void tintCompoundDrawables(View view, int color) {
        if(view instanceof TextView) {
            TextView tv = (TextView) view;
            for(Drawable d : tv.getCompoundDrawables()) { if(d != null) d.setColorFilter(color, PorterDuff.Mode.SRC_IN); }
        }
    }
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*"); pickImageLauncher.launch(intent);
    }
    private void copyImageToInternal(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            String fileName = "custom_banner.jpg"; File outFile = new File(getFilesDir(), fileName);
            OutputStream outputStream = new FileOutputStream(outFile); byte[] buffer = new byte[1024]; int len;
            while ((len = inputStream.read(buffer)) > 0) { outputStream.write(buffer, 0, len); }
            outputStream.close(); inputStream.close();
            String path = outFile.getAbsolutePath();
            prefs.edit().putString("custom_banner_path", path).apply();
            loadCustomBanner(); Toast.makeText(this, "Banner Applied", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show(); }
    }
    private void openUrl(String url) {
        try { Intent i = new Intent(Intent.ACTION_VIEW); i.setData(Uri.parse(url)); startActivity(i); } catch (Exception e) {}
    }
    private void loadCustomBanner() {
        String customPath = prefs.getString("custom_banner_path", "");
        if (!customPath.isEmpty()) {
            File imgFile = new File(customPath);
            if (imgFile.exists()) {
                Glide.with(this).load(imgFile).centerCrop().into(headerBanner);
                headerBanner.setVisibility(View.VISIBLE); bannerContainer.setVisibility(View.VISIBLE);
                findViewById(R.id.btn_reset_banner).setVisibility(View.VISIBLE); applyBannerRadius(); return;
            }
        }
        try {
            Glide.with(this).load(R.drawable.header_bg).centerCrop().into(headerBanner);
            headerBanner.setVisibility(View.VISIBLE); bannerContainer.setVisibility(View.VISIBLE); applyBannerRadius();
        } catch (Exception e) { headerBanner.setVisibility(View.GONE); bannerContainer.setVisibility(View.GONE); }
        findViewById(R.id.btn_reset_banner).setVisibility(View.GONE);
    }
    private void applyBannerRadius() {
        if(headerBanner != null) {
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE); shape.setCornerRadius(20); shape.setColor(Color.TRANSPARENT);
            headerBanner.setBackground(shape); headerBanner.setClipToOutline(true);
        }
    }
    private void updateNavUI(int activeIndex) {
        int colorActive = Color.parseColor("#4CAF50"); int colorInactive = Color.GRAY;
        switch(iconColorMode) { case 1: colorInactive = Color.parseColor("#FF4081"); break; case 2: colorInactive = Color.parseColor("#00E5FF"); break; case 3: colorInactive = Color.parseColor("#FF9100"); break; case 4: colorInactive = Color.parseColor("#D500F9"); break; }
        if(navSystem != null && navSystem.getChildCount() > 0) ((TextView)navSystem.getChildAt(0)).setTextColor(activeIndex == 0 ? colorActive : colorInactive);
        if(navTools != null && navTools.getChildCount() > 0) ((TextView)navTools.getChildAt(0)).setTextColor(activeIndex == 1 ? colorActive : colorInactive);
        if(navSettings != null && navSettings.getChildCount() > 0) ((TextView)navSettings.getChildAt(0)).setTextColor(activeIndex == 2 ? colorActive : colorInactive);
        applyIconColor();
    }
    
    // --- CONTROL LOGIC ---
    private void setCpuMaxFreq(int khz) {
        new Thread(() -> {
            String cmd = "for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq; do echo " + khz + " > $c; done";
            runSu(cmd);
        }).start();
    }

    private void validateCpuFreqLimit() {
        new Thread(() -> {
            String sVal = runSuReturnAll("cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_max_freq");
            String[] lines = sVal.split("\\s+");
            boolean allValid = true;
            int refVal = -1;
            for(String line : lines) {
                int val = parseIntSafe(line);
                if(val <= 0) { allValid = false; break; }
                if(refVal == -1) refVal = val;
            }
            final int finalVal = refVal;
            runOnUiThread(() -> {
                if(tvMaxFreqTools != null) {
                    if(finalVal > 0) tvMaxFreqTools.setText((finalVal/1000) + " MHz");
                    else tvMaxFreqTools.setText("Error");
                }
            });
        }).start();
    }

    private void setGpuMaxFreq(int khz) {
        new Thread(() -> {
            if(!gpuMaxFreqPath.isEmpty()) {
                runSu("echo " + khz + " > " + gpuMaxFreqPath);
            }
        }).start();
    }

    private void validateGpuFreqLimit() {
        new Thread(() -> {
            String sVal = runSuReturn("cat " + gpuMaxFreqPath);
            int val = parseIntSafe(sVal);
            final int fVal = val;
            runOnUiThread(() -> {
                if(tvMaxFreqTools != null && fVal > 0) {
                    tvMaxFreqTools.setText((fVal/1000) + " MHz");
                }
            });
        }).start();
    }

    private void pickGov() {
        new Thread(() -> {
            if (isGpuModeActive && gpuGovPath.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, "GPU Governor Not Supported", Toast.LENGTH_SHORT).show());
                return;
            }

            String title = isGpuModeActive ? "GPU GOVERNOR" : "CPU GOVERNOR";
            String path = isGpuModeActive ? (gpuGovPath.isEmpty() ? "" : "cat " + gpuGovPath) : "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors";
            
            String availPath = "";
            if(isGpuModeActive) {
                if(new File("/sys/class/kgsl/kgsl-3d0/devfreq/available_governors").exists()) availPath = "cat /sys/class/kgsl/kgsl-3d0/devfreq/available_governors";
                else if(new File("/sys/class/devfreq/mali0/available_governors").exists()) availPath = "cat /sys/class/devfreq/mali0/available_governors";
                else { path = ""; } 
            }

            String raw = "";
            if(isGpuModeActive && !availPath.isEmpty()) raw = runSuReturn(availPath);
            else if(!path.isEmpty()) raw = runSuReturn(path);
            
            if(raw.isEmpty() && isGpuModeActive) {
                raw = "performance\npowersave\nsimple_ondemand\nondemand\nconservative"; 
            }

            String[] govs = raw.split("\\s+");
            if(govs.length > 0 && !govs[0].isEmpty()) {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this).setTitle(title)
                        .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, govs), (d, w) -> {
                            new Thread(() -> {
                                String target = govs[w];
                                String cmd = "";
                                if(isGpuModeActive && !gpuGovPath.isEmpty()) {
                                    cmd = "echo " + target + " > " + gpuGovPath;
                                } else {
                                    cmd = "for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo " + target + " > $c; done";
                                }
                                runSu(cmd);
                            }).start();
                            Toast.makeText(this, "Governor set to " + govs[w], Toast.LENGTH_SHORT).show();
                        }).show();
                });
            } else { runOnUiThread(() -> Toast.makeText(this, "Failed to read governors", Toast.LENGTH_SHORT).show()); }
        }).start();
    }

    public void showZramMenu() {
        final String[] options = {"4 GB", "8 GB", "12 GB", "16 GB", "Disable ZRAM"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select ZRAM Size");
        builder.setItems(options, (dialog, which) -> {
            if (which == 4) {
                new Thread(() -> {
                    runSu("swapoff -a 2>/dev/null");
                    runSu("echo 1 > /sys/block/zram0/reset 2>/dev/null");
                    currentZramSizeGB = 0;
                    try { Thread.sleep(500); } catch (Exception e){}
                    runOnUiThread(() -> { if(tvZram != null) tvZram.setText("Disabled"); });
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
            String line = reader.readLine(); p.waitFor();
            return (line == null) ? "" : line.trim();
        } catch (Exception e) { return ""; }
    }
    private String runSuReturnAll(String c) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", c});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder output = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            p.waitFor(); return output.toString().trim();
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
