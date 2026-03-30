package com.zixine.engine;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {

    private boolean isPerf = false, isGms = false, isExtreme = false;
    private TextView tutorialText, arrow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tutorialText = findViewById(R.id.tutorial_view);
        arrow = findViewById(R.id.arrow_text);

        // Binding tombol dan event klik
        findViewById(R.id.btn_perf).setOnClickListener(v -> toggleMode("perf"));
        findViewById(R.id.btn_gms).setOnClickListener(v -> toggleMode("gms"));
        findViewById(R.id.btn_extreme).setOnClickListener(v -> toggleMode("extreme"));

        // Trigger Tutorial Panel
        findViewById(R.id.btn_trigger).setOnClickListener(v -> {
            if (tutorialText.getVisibility() == View.GONE) {
                tutorialText.setVisibility(View.VISIBLE);
                arrow.setText("▼");
            } else {
                tutorialText.setVisibility(View.GONE);
                arrow.setText("▲");
            }
        });
    }

    private void toggleMode(String mode) {
        MaterialCardView card;
        TextView status;
        String cmd;

        if (mode.equals("perf")) {
            isPerf = !isPerf;
            card = findViewById(R.id.btn_perf);
            status = findViewById(R.id.status_perf);
            cmd = isPerf ? "settings put system min_refresh_rate 120.0;" : "settings put system min_refresh_rate 60.0;";
            applyUI(isPerf, card, status);
        } else if (mode.equals("gms")) {
            isGms = !isGms;
            card = findViewById(R.id.btn_gms);
            status = findViewById(R.id.status_gms);
            cmd = isGms ? "killall -STOP com.google.android.gms;" : "killall -CONT com.google.android.gms;";
            applyUI(isGms, card, status);
        } else {
            isExtreme = !isExtreme;
            card = findViewById(R.id.btn_extreme);
            status = findViewById(R.id.status_extreme);
            cmd = isExtreme ? "swapoff -a; killall -STOP thermald;" : "swapon -a; killall -CONT thermald;";
            applyUI(isExtreme, card, status);
        }

        // Eksekusi Root di Background agar tidak lag
        final String finalCmd = cmd;
        new Thread(() -> execRoot(finalCmd)).start();
    }

    private void applyUI(boolean active, MaterialCardView card, TextView status) {
        if (active) {
            card.setCardBackgroundColor(Color.parseColor("#FF1744"));
            card.setStrokeColor(Color.parseColor("#FF5252"));
            status.setText("ON");
            status.setTextColor(Color.WHITE);
        } else {
            card.setCardBackgroundColor(Color.parseColor("#12161F"));
            card.setStrokeColor(Color.parseColor("#1E2433"));
            status.setText("OFF");
            status.setTextColor(Color.parseColor("#44FFFFFF"));
        }
    }

    private void execRoot(String command) {
        try {
            // Metode su -c jauh lebih stabil untuk Activity
            Runtime.getRuntime().exec(new String[]{"su", "-c", command}).waitFor();
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Root Denied!", Toast.LENGTH_SHORT).show());
        }
    }
}
