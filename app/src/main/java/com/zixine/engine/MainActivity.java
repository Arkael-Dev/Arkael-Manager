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
    
    // DATA VITAL DARI KOMANDAN
    private final String BLACKLIST = "com.facebook.katana com.facebook.orca com.instagram.android com.ss.android.ugc.trill com.zhiliaoapp.musically com.whatsapp com.whatsapp.w4b com.twitter.android com.shopee.id com.tokopedia.tkpd com.lazada.android com.google.android.youtube com.google.android.apps.docs com.google.android.apps.photos com.google.android.gm com.netflix.mediaclient com.spotify.music";
    private final String GMS_PACKS = "com.google.android.gms com.android.vending com.google.android.gsf";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        verifyKernelAndAccess();
        
        findViewById(R.id.btn_trigger).setOnClickListener(v -> {
            TextView tv = findViewById(R.id.tutorial_view);
            TextView arrow = findViewById(R.id.arrow_text);
            if (tv.getVisibility() == View.GONE) {
                tv.setVisibility(View.VISIBLE); arrow.setText("▼");
            } else {
                tv.setVisibility(View.GONE); arrow.setText("▲");
            }
        });
    }

    private void verifyKernelAndAccess() {
        String kernelInfo = getKernelVersion();
        boolean isZixine = kernelInfo.toLowerCase().contains("zixine"); 
        boolean isBypassed = prefs.getBoolean("isBypassed", false); 

        if (isZixine || isBypassed) {
            findViewById(R.id.lock_overlay).setVisibility(View.GONE);
            findViewById(R.id.main_ui).setAlpha(1.0f);
            setupButtons();
        } else {
            findViewById(R.id.lock_overlay).setVisibility(View.VISIBLE);
            findViewById(R.id.main_ui).setAlpha(0.1f);
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
        // PERF: 120Hz & Responsivitas Sentuhan Penuh
        findViewById(R.id.btn_perf).setOnClickListener(v -> {
            isPerf = !isPerf;
            String cmd = isPerf ? 
                "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0; " +
                "settings put global window_animation_scale 0; settings put global transition_animation_scale 0; " +
                "settings put global animator_duration_scale 0; setprop touch.pressure.scale 0.001; " +
                "setprop debug.touch.filter 0; resetprop ro.min.fling_velocity 8000; killall -STOP thermald;" : 
                "settings put system min_refresh_rate 60.0; settings put global window_animation_scale 1; " +
                "setprop touch.pressure.scale 1; setprop debug.touch.filter 1; resetprop ro.min.fling_velocity 50; killall -CONT thermald;";
            execute(cmd);
            updateUI(isPerf, findViewById(R.id.btn_perf), findViewById(R.id.status_perf));
        });

        // GMS KILL: pm disable
        findViewById(R.id.btn_gms).setOnClickListener(v -> {
            isGms = !isGms;
            String cmd = isGms ? 
                "for p in " + GMS_PACKS + "; do pm disable-user --user 0 $p; done;" : 
                "for p in " + GMS_PACKS + "; do pm enable $p; done;";
            execute(cmd);
            updateUI(isGms, findViewById(R.id.btn_gms), findViewById(R.id.status_gms));
        });

        // EXTREME: pm suspend BLACKLIST & ZRAM OFF
        findViewById(R.id.btn_extreme).setOnClickListener(v -> {
            isExtreme = !isExtreme;
            String cmd = isExtreme ? 
                "for app in " + BLACKLIST + " " + GMS_PACKS + "; do pm suspend $app; done; swapoff -a; settings put system min_refresh_rate 120.0;" : 
                "for app in " + BLACKLIST + " " + GMS_PACKS + "; do pm unsuspend $app; done; swapon -a; settings put system min_refresh_rate 60.0;";
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
