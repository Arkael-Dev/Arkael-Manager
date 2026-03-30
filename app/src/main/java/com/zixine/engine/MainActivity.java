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

        checkSecurity();
        
        findViewById(R.id.btn_trigger).setOnClickListener(v -> {
            TextView tv = findViewById(R.id.tutorial_view);
            TextView arrow = findViewById(R.id.arrow_text);
            if (tv.getVisibility() == View.GONE) {
                tv.setVisibility(View.VISIBLE);
                arrow.setText("▼");
            } else {
                tv.setVisibility(View.GONE);
                arrow.setText("▲");
            }
        });
    }

    private void checkSecurity() {
        String version = System.getProperty("os.version");
        boolean isZixine = version != null && version.toLowerCase().contains("zixine");
        boolean isBypassed = prefs.getBoolean("isBypassed", false);

        if (isZixine || isBypassed) {
            lockOverlay.setVisibility(View.GONE);
            mainUI.setAlpha(1.0f);
            setupButtons();
        } else {
            lockOverlay.setVisibility(View.VISIBLE);
            mainUI.setAlpha(0.1f);
            setupUnlockLogic();
        }
    }

    private void setupUnlockLogic() {
        EditText input = findViewById(R.id.input_code);
        findViewById(R.id.btn_unlock).setOnClickListener(v -> {
            if (input.getText().toString().equals(SECRET_CODE)) {
                prefs.edit().putBoolean("isBypassed", true).apply();
                Toast.makeText(this, "AKSES DIBUKA!", Toast.LENGTH_SHORT).show();
                checkSecurity();
            } else {
                Toast.makeText(this, "KODE SALAH!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupButtons() {
        findViewById(R.id.btn_perf).setOnClickListener(v -> toggleMode("perf"));
        findViewById(R.id.btn_gms).setOnClickListener(v -> toggleMode("gms"));
        findViewById(R.id.btn_extreme).setOnClickListener(v -> toggleMode("extreme"));
    }

    private void toggleMode(String mode) {
        MaterialCardView card;
        TextView status;
        String cmd;

        if (mode.equals("perf")) {
            isPerf = !isPerf;
            card = findViewById(R.id.btn_perf);
            status = findViewById(R.id.status_perf);
            cmd = isPerf ? "settings put system min_refresh_rate 120.0; settings put global window_animation_scale 0; setprop touch.pressure.scale 0.001; resetprop ro.min.fling_velocity 8000; killall -STOP thermald; settings put global zen_mode 1;" : 
                           "settings put system min_refresh_rate 60.0; settings put global window_animation_scale 1; setprop touch.pressure.scale 1; resetprop ro.min.fling_velocity 50; killall -CONT thermald; settings put global zen_mode 0;";
            updateUI(isPerf, card, status);
        } else if (mode.equals("gms")) {
            isGms = !isGms;
            card = findViewById(R.id.btn_gms);
            status = findViewById(R.id.status_gms);
            cmd = isGms ? "for app in gms google; do killall -STOP \"$app\"; done;" : "for app in gms google; do killall -CONT \"$app\"; done;";
            updateUI(isGms, card, status);
        } else {
            isExtreme = !isExtreme;
            card = findViewById(R.id.btn_extreme);
            status = findViewById(R.id.status_extreme);
            cmd = isExtreme ? "settings put system min_refresh_rate 120.0; swapoff -a; killall -STOP thermald; for app in gms google; do killall -STOP \"$app\"; done;" : 
                               "settings put system min_refresh_rate 60.0; swapon -a; killall -CONT thermald; for app in gms google; do killall -CONT \"$app\"; done;";
            updateUI(isExtreme, card, status);
        }

        final String finalCmd = cmd;
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", finalCmd}).waitFor();
            } catch (Exception ignored) {}
        }).start();
    }

    private void updateUI(boolean active, MaterialCardView card, TextView status) {
        if (active) {
            card.setCardBackgroundColor(Color.parseColor("#FF1744"));
            status.setText("ON");
            status.setTextColor(Color.WHITE);
        } else {
            card.setCardBackgroundColor(Color.parseColor("#12161F"));
            status.setText("OFF");
            status.setTextColor(Color.parseColor("#44FFFFFF"));
        }
    }
}
