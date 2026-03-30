package com.zixine.engine;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import java.io.DataOutputStream;

public class MainActivity extends AppCompatActivity {

    private boolean isPerf = false, isGms = false, isExtreme = false;
    private TextView tutorial, arrow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tutorial = findViewById(R.id.tutorial_text);
        arrow = findViewById(R.id.trigger_arrow);

        // Tombol Klik
        findViewById(R.id.btn_perf).setOnClickListener(v -> toggle("perf"));
        findViewById(R.id.btn_gms).setOnClickListener(v -> toggle("gms"));
        findViewById(R.id.btn_extreme).setOnClickListener(v -> toggle("extreme"));

        // Panel Tutorial
        findViewById(R.id.trigger_panel).setOnClickListener(v -> {
            if (tutorial.getVisibility() == View.GONE) {
                tutorial.setVisibility(View.VISIBLE);
                arrow.setText("▼");
            } else {
                tutorial.setVisibility(View.GONE);
                arrow.setText("▲");
            }
        });
    }

    private void toggle(String type) {
        MaterialCardView card;
        TextView status;
        String cmd;

        if (type.equals("perf")) {
            isPerf = !isPerf;
            card = findViewById(R.id.btn_perf);
            status = findViewById(R.id.status_perf);
            cmd = isPerf ? "settings put system min_refresh_rate 120.0;" : "settings put system min_refresh_rate 60.0;";
            updateUI(isPerf, card, status);
        } else if (type.equals("gms")) {
            isGms = !isGms;
            card = findViewById(R.id.btn_gms);
            status = findViewById(R.id.status_gms);
            cmd = isGms ? "killall -STOP com.google.android.gms;" : "killall -CONT com.google.android.gms;";
            updateUI(isGms, card, status);
        } else {
            isExtreme = !isExtreme;
            card = findViewById(R.id.btn_extreme);
            status = findViewById(R.id.status_extreme);
            cmd = isExtreme ? "swapoff -a; killall -STOP thermald;" : "swapon -a; killall -CONT thermald;";
            updateUI(isExtreme, card, status);
        }

        final String finalCmd = cmd;
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes(finalCmd + "\nexit\n");
                os.flush();
            } catch (Exception ignored) {}
        }).start();
    }

    private void updateUI(boolean active, MaterialCardView card, TextView status) {
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
}
