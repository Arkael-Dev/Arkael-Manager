package com.zixine.engine;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import java.io.DataOutputStream;

public class MainActivity extends AppCompatActivity {

    private boolean isActivated = false;
    private CardView btnIgnite;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnIgnite = findViewById(R.id.btn_ignite);
        statusText = findViewById(R.id.status_text);

        btnIgnite.setOnClickListener(v -> toggleEngine());
    }

    private void toggleEngine() {
        isActivated = !isActivated;
        
        // UI Response instan (Tidak nunggu shell selesai)
        if (isActivated) {
            btnIgnite.setCardBackgroundColor(Color.parseColor("#FF1744"));
            statusText.setText("ON");
            statusText.setTextColor(Color.WHITE);
            runPerformanceMode(true);
        } else {
            btnIgnite.setCardBackgroundColor(Color.parseColor("#131521"));
            statusText.setText("OFF");
            statusText.setTextColor(Color.parseColor("#33FFFFFF"));
            runPerformanceMode(false);
        }
    }

    private void runPerformanceMode(boolean active) {
        // Jalankan di background thread agar UI tidak lag!
        new Thread(() -> {
            String cmd;
            if (active) {
                // Urutan: UI & Touch dulu (biar instan), baru ZRAM & FSTRIM (berat)
                cmd = "settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0; " +
                      "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0; " +
                      "setprop touch.pressure.scale 0.001; setprop debug.touch.filter 0; " +
                      "resetprop ro.min.fling_velocity 8000; resetprop ro.max.fling_velocity 12000; " +
                      "settings put global zen_mode 1; " +
                      "killall -STOP thermald; killall -STOP thermal-engine; " +
                      "swapoff -a; fstrim -v /data;"; 
            } else {
                cmd = "settings put global window_animation_scale 1; settings put global transition_animation_scale 1; settings put global animator_duration_scale 1; " +
                      "settings put system min_refresh_rate 60.0; swapon -a; " +
                      "setprop touch.pressure.scale 1; resetprop ro.min.fling_velocity 50; " +
                      "settings put global zen_mode 0; killall -CONT thermald; killall -CONT thermal-engine;";
            }
            execRoot(cmd);
        }).start();
    }

    private void execRoot(String command) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(command + "\nexit\n");
            os.flush();
            p.waitFor();
        } catch (Exception ignored) {}
    }
}
