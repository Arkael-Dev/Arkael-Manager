package com.zixine.engine;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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
    private final String DONATE_URL = "https://link-donasi-atau-qris-anda.com";

    private TextView tvRam, tvZram, tvCpu, tvBattery;
    private Handler dashboardHandler = new Handler(Looper.getMainLooper());
    private Runnable dashboardUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // === 1. SISTEM PELACAK CRASH (CRASH CATCHER) ===
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            StringBuilder errorLog = new StringBuilder(throwable.toString() + "\n");
            for (StackTraceElement element : throwable.getStackTrace()) {
                errorLog.append(element.toString()).append("\n");
            }
            getSharedPreferences("ZixineCrashLog", Context.MODE_PRIVATE)
                    .edit().putString("crash_data", errorLog.toString()).commit();
            System.exit(2);
        });

        super.onCreate(savedInstanceState);

        // === 2. BACA JIKA SEBELUMNYA TERJADI CRASH ===
        SharedPreferences crashPrefs = getSharedPreferences("ZixineCrashLog", Context.MODE_PRIVATE);
        String lastCrash = crashPrefs.getString("crash_data", null);
        if (lastCrash != null) {
            crashPrefs.edit().remove("crash_data").apply(); // Hapus setelah dibaca
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("CRASH DETECTED!")
                    .setMessage(lastCrash)
                    .setPositiveButton("COPY LOG", (dialog, which) -> {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("Crash Log", lastCrash);
                        if (clipboard != null) clipboard.setPrimaryClip(clip);
                        finishAffinity();
                    })
                    .setCancelable(false)
                    .show();
            return; // Hentikan proses pembukaan aplikasi agar tidak crash ulang
        }

        // === 3. MULAI APLIKASI DENGAN BAJU ZIRAH ===
        try {
            setContentView(R.layout.activity_main);

            prefs = getSharedPreferences("ZixineSecurePrefs", Context.MODE_PRIVATE);

            tvRam = findViewById(R.id.tv_ram);
            tvZram = findViewById(R.id.tv_zram);
            tvCpu = findViewById(R.id.tv_cpu);
            tvBattery = findViewById(R.id.tv_battery);

            verifyKernelAndAccess();

            findViewById(R.id.btn_unlock).setOnClickListener(v -> {
                EditText input = findViewById(R.id.input_code);
                String inputCode = input.getText().toString().trim();
                if (inputCode.equals(BuildConfig.SECRET_PASSKEY)) {
                    prefs.edit().putString("secured_pass_hash", SecurityUtils.generateHash(BuildConfig.SECRET_PASSKEY)).apply();
                    Toast.makeText(this, "BYPASS SUCCESS!", Toast.LENGTH_SHORT).show();
                    verifyKernelAndAccess();
                } else {
                    Toast.makeText(this, "WRONG CODE!", Toast.LENGTH_SHORT).show();
                    input.setText("");
                }
            });

            findViewById(R.id.btn_info).setOnClickListener(v -> showTutorialDialog());
            findViewById(R.id.btn_cpu_gov).setOnClickListener(v -> showGovernorPopup());
            findViewById(R.id.btn_clean_ram).setOnClickListener(v -> executeBrutalClean());

        } catch (Exception e) {
            Toast.makeText(this, "Init Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void verifyKernelAndAccess() {
        try {
            if (SecurityUtils.isSystemVerified(this)) {
                findViewById(R.id.layout_locked).setVisibility(View.GONE);
                findViewById(R.id.layout_verified).setVisibility(View.VISIBLE);
                startDashboardUpdater();
            } else {
                findViewById(R.id.layout_locked).setVisibility(View.VISIBLE);
                findViewById(R.id.layout_verified).setVisibility(View.GONE);
                stopDashboardUpdater();
                prefs.edit().remove("secured_pass_hash").apply();
            }
        } catch (Exception e) {}
    }

    private void startDashboardUpdater() {
        dashboardUpdater = new Runnable() {
            @Override
            public void run() {
                updateSystemInfo();
                dashboardHandler.postDelayed(this, 2000);
            }
        };
        dashboardHandler.post(dashboardUpdater);
    }

    private void stopDashboardUpdater() {
        if (dashboardUpdater != null) dashboardHandler.removeCallbacks(dashboardUpdater);
    }

    private void updateSystemInfo() {
        try {
            ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (actManager != null) {
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                actManager.getMemoryInfo(memInfo);
                long totalRamG = memInfo.totalMem / 1073741824;
                long availRamG = memInfo.availMem / 1048576;
                if (tvRam != null) tvRam.setText((totalRamG > 0 ? totalRamG + 1 : 0) + " GB (" + availRamG + "MB Free)");
            }
        } catch (Exception e) { if (tvRam != null) tvRam.setText("Error"); }

        try {
            String gov = runShell("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
            if (tvCpu != null) tvCpu.setText(gov.isEmpty() ? "Unknown" : gov.toUpperCase());
        } catch (Exception e) { if (tvCpu != null) tvCpu.setText("Error"); }

        try {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            if (bm != null) {
                int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (tvBattery != null) tvBattery.setText(level + "%");
            } else {
                if (tvBattery != null) tvBattery.setText("Unknown");
            }
        } catch (Exception e) { if (tvBattery != null) tvBattery.setText("Error"); }

        try {
            String zram = runShell("cat /sys/block/zram0/disksize");
            if (!zram.isEmpty()) {
                long zramMB = Long.parseLong(zram) / 1048576;
                if (tvZram != null) tvZram.setText(zramMB + " MB");
            } else {
                if (tvZram != null) tvZram.setText("OFF");
            }
        } catch (Exception e) { if (tvZram != null) tvZram.setText("DISABLED"); }
    }

    private void showGovernorPopup() {
        try {
            String availableGovs = runShell("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors");
            if (availableGovs.isEmpty()) {
                Toast.makeText(this, "Root Access Required", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] govList = availableGovs.split(" ");
            AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
            builder.setTitle("Pilih CPU Governor");
            builder.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, govList), (dialog, which) -> {
                String selectedGov = govList[which];
                new Thread(() -> runShellSu("for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo " + selectedGov + " > $cpu; done")).start();
                Toast.makeText(this, "Governor diubah ke: " + selectedGov.toUpperCase(), Toast.LENGTH_SHORT).show();
                updateSystemInfo();
            });
            builder.show();
        } catch (Exception e) {}
    }

    private void executeBrutalClean() {
        Toast.makeText(this, "🧹 BRUTAL CLEANING INITIATED...", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "sync; echo 3 > /proc/sys/vm/drop_caches; am kill-all;"}).waitFor();
                runOnUiThread(() -> { finishAffinity(); System.exit(0); });
            } catch (Exception e) {}
        }).start();
    }

    private void showTutorialDialog() {
        try {
            Dialog dialog = new Dialog(this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_info);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            TextView tvStatus = dialog.findViewById(R.id.dialog_status);
            TextView tvMessage = dialog.findViewById(R.id.dialog_message);
            Button btnDonate = dialog.findViewById(R.id.btn_dialog_donate);

            if (!SecurityUtils.isSystemVerified(this)) {
                if (tvStatus != null) { tvStatus.setText("STATUS: AKSES TERKUNCI"); tvStatus.setTextColor(Color.parseColor("#FF1744")); }
                if (tvMessage != null) tvMessage.setText("Aplikasi mendeteksi Kernel Non-Zixine. Silakan masukkan Passkey.");
                if (btnDonate != null) btnDonate.setVisibility(View.VISIBLE);
            } else {
                if (tvStatus != null) { tvStatus.setText("STATUS: SYSTEM VERIFIED"); tvStatus.setTextColor(Color.parseColor("#00E5FF")); }
                if (tvMessage != null) tvMessage.setText("Sistem aktif! Pantau performa melalui Dashboard atau atur mode brutal dari Quick Settings Panel.");
                if (btnDonate != null) btnDonate.setVisibility(View.GONE);
            }

            if (btnDonate != null) btnDonate.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(DONATE_URL))));
            Button btnOk = dialog.findViewById(R.id.btn_dialog_ok);
            if (btnOk != null) btnOk.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this, "Error memuat dialog", Toast.LENGTH_SHORT).show();
        }
    }

    private String runShell(String command) {
        try {
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String res = r.readLine();
            return res != null ? res.trim() : "";
        } catch (Exception e) { return ""; }
    }

    private void runShellSu(String command) {
        try { Runtime.getRuntime().exec(new String[]{"su", "-c", command}).waitFor(); } catch (Exception e) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopDashboardUpdater();
    }
}
