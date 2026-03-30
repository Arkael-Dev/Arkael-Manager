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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);

        // JALANKAN VERIFIKASI KERNEL SECARA KETAT
        verifyKernelAndAccess(); 
        
        findViewById(R.id.btn_trigger).setOnClickListener(v -> {
            TextView tv = findViewById(R.id.tutorial_view);
            tv.setVisibility(tv.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
        });
    }

    private void verifyKernelAndAccess() {
        String kernelInfo = getKernelVersion();
        boolean isZixine = kernelInfo.toLowerCase().contains("zixine"); //
        boolean isBypassed = prefs.getBoolean("isBypassed", false); //

        if (isZixine) {
            // PENGGUNA ASLI ZIXINE: AKSES OTOMATIS
            findViewById(R.id.lock_overlay).setVisibility(View.GONE);
            Toast.makeText(this, "WELCOME MASTER: ZIXINE KERNEL DETECTED", Toast.LENGTH_LONG).show();
            setupButtons();
        } else if (isBypassed) {
            // PENGGUNA KERNEL LAIN YANG SUDAH MASUKKAN KODE
            findViewById(R.id.lock_overlay).setVisibility(View.GONE);
            setupButtons();
        } else {
            // PENGGUNA ILEGAL/LAIN: TAMPILKAN PANEL SANDI
            findViewById(R.id.lock_overlay).setVisibility(View.VISIBLE);
            findViewById(R.id.main_ui).setAlpha(0.1f);
            setupUnlockLogic();
        }
    }

    private String getKernelVersion() {
        try {
            Process p = Runtime.getRuntime().exec("uname -a"); // Lebih akurat cek string zixine
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            return reader.readLine();
        } catch (Exception e) {
            return System.getProperty("os.version"); // Fallback jika uname gagal
        }
    }

    private void setupUnlockLogic() {
        EditText input = findViewById(R.id.input_code);
        findViewById(R.id.btn_unlock).setOnClickListener(v -> {
            if (input.getText().toString().equals(SECRET_CODE)) {
                prefs.edit().putBoolean("isBypassed", true).apply(); // Simpan status
                verifyKernelAndAccess(); // Refresh tampilan
            } else {
                Toast.makeText(this, "KODE RAHASIA SALAH!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupButtons() {
        findViewById(R.id.main_ui).setAlpha(1.0f);
        
        // PERF: Hz & Touch
        findViewById(R.id.btn_perf).setOnClickListener(v -> {
            isPerf = !isPerf;
            String cmd = isPerf ? 
                "settings put system min_refresh_rate 120.0; setprop touch.pressure.scale 0.001; killall -STOP thermald;" : 
                "settings put system min_refresh_rate 60.0; setprop touch.pressure.scale 1; killall -CONT thermald;";
            execute(cmd);
            updateUI(isPerf, findViewById(R.id.btn_perf), findViewById(R.id.status_perf));
        });

        // GMS KILL: Disable App
        findViewById(R.id.btn_gms).setOnClickListener(v -> {
            isGms = !isGms;
            String cmd = isGms ? 
                "pm disable-user --user 0 com.google.android.gms; pm disable-user --user 0 com.android.vending;" : 
                "pm enable com.google.android.gms; pm enable com.android.vending;";
            execute(cmd);
            updateUI(isGms, findViewById(R.id.btn_gms), findViewById(R.id.status_gms));
        });

        // EXTREME: Suspend & ZRAM OFF
        findViewById(R.id.btn_extreme).setOnClickListener(v -> {
            isExtreme = !isExtreme;
            String cmd = isExtreme ? 
                "pm suspend com.google.android.gms; pm suspend com.android.vending; swapoff -a;" : 
                "pm unsuspend com.google.android.gms; pm unsuspend com.android.vending; swapon -a;";
            execute(cmd);
            updateUI(isExtreme, findViewById(R.id.btn_extreme), findViewById(R.id.status_extreme));
        });
    }

    private void execute(String c) {
        new Thread(() -> {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", c}).waitFor(); } catch (Exception ignored) {} //
        }).start();
    }

    private void updateUI(boolean active, MaterialCardView card, TextView status) {
        card.setCardBackgroundColor(Color.parseColor(active ? "#FF1744" : "#12161F"));
        status.setText(active ? "ON" : "OFF");
        status.setTextColor(active ? Color.WHITE : Color.parseColor("#44FFFFFF"));
    }
}
