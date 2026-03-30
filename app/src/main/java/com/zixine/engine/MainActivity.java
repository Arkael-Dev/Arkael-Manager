package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private boolean isPerf = false, isGms = false, isExtreme = false;
    private SharedPreferences prefs;
    private final String SECRET_CODE = "445456"; 
    private MaterialCardView lockOverlay, mainUI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        lockOverlay = findViewById(R.id.lock_overlay);
        mainUI = findViewById(R.id.main_ui);

        verifyKernelAndAccess();
        
        findViewById(R.id.btn_trigger).setOnClickListener(v -> {
            TextView tv = findViewById(R.id.tutorial_view);
            tv.setVisibility(tv.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
        });
    }

    private void verifyKernelAndAccess() {
        String kernelInfo = getKernelVersion();
        boolean isZixine = kernelInfo.toLowerCase().contains("zixine"); 
        boolean isBypassed = prefs.getBoolean("isBypassed", false); 

        if (isZixine || isBypassed) {
            lockOverlay.setVisibility(View.GONE);
            mainUI.setAlpha(1.0f);
            setupButtons();
        } else {
            lockOverlay.setVisibility(View.VISIBLE);
            mainUI.setAlpha(0.1f);
            setupUnlock();
        }
    }

    private String getKernelVersion() {
        try {
            Process p = Runtime.getRuntime().exec("uname -a");
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            return r.readLine();
        } catch (Exception e) { return System.getProperty("os.version"); }
    }

    private void setupUnlock() {
        EditText input = findViewById(R.id.input_code);
        findViewById(R.id.btn_unlock).setOnClickListener(v -> {
            if (input.getText().toString().equals(SECRET_CODE)) {
                prefs.edit().putBoolean("isBypassed", true).apply();
                verifyKernelAndAccess();
            } else {
                Toast.makeText(this, "WRONG CODE!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupButtons() {
        // PERF: Responsivitas & 120Hz
        findViewById(R.id.btn_perf).setOnClickListener(v -> {
            isPerf = !isPerf;
            String cmd = isPerf ? 
                "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0; " +
                "settings put global window_animation_scale 0; setprop touch.pressure.scale 0.001; " +
                "setprop debug.touch.filter 0; resetprop ro.min.fling_velocity 8000; killall -STOP thermald;" : 
                "settings put system min_refresh_rate 60.0; settings put global window_animation_scale 1; " +
                "setprop touch.pressure.scale 1; setprop debug.touch.filter 1; resetprop ro.min.fling_velocity 50; killall -CONT thermald;";
            execute(cmd);
            updateUI(isPerf, findViewById(R.id.btn_perf), findViewById(R.id.status_perf));
        });

        // GMS KILL: List Disable
        findViewById(R.id.btn_gms).setOnClickListener(v -> {
            isGms = !isGms;
            String cmd = isGms ? 
                "pm disable-user --user 0 com.google.android.gms; pm disable-user --user 0 com.android.vending; pm disable-user --user 0 com.google.android.gsf;" : 
                "pm enable com.google.android.gms; pm enable com.android.vending; pm enable com.google.android.gsf;";
            execute(cmd);
            updateUI(isGms, findViewById(R.id.btn_gms), findViewById(R.id.status_gms));
        });

        // EXTREME: Manual Suspend List & ZRAM
        findViewById(R.id.btn_extreme).setOnClickListener(v -> {
            isExtreme = !isExtreme;
            // List Manual: GMS, Vending, GSF, YouTube, Chrome
            String apps = "com.google.android.gms com.android.vending com.google.android.gsf com.google.android.youtube com.android.chrome";
            String cmd = isExtreme ? 
                "for app in " + apps + "; do pm suspend $app; done; swapoff -a; settings put system min_refresh_rate 120.0;" : 
                "for app in " + apps + "; do pm unsuspend $app; done; swapon -a; settings put system min_refresh_rate 60.0;";
            execute(cmd);
            updateUI(isExtreme, findViewById(R.id.btn_extreme), findViewById(R.id.status_extreme));
        });
    }

    private void execute(String c) {
        new Thread(() -> {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", c}).waitFor(); } catch (Exception ignored) {}
        }).start();
    }

    private void updateUI(boolean active, View card, TextView status) {
        ((MaterialCardView)card).setCardBackgroundColor(Color.parseColor(active ? "#FF1744" : "#12161F"));
        status.setText(active ? "ON" : "OFF");
        status.setTextColor(active ? Color.WHITE : Color.parseColor("#44FFFFFF"));
    }
}
