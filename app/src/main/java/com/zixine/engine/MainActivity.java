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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);

        checkSecurity();
    }

    private void checkSecurity() {
        boolean isZixine = System.getProperty("os.version").toLowerCase().contains("zixine");
        boolean isBypassed = prefs.getBoolean("isBypassed", false);

        if (isZixine || isBypassed) {
            findViewById(R.id.lock_overlay).setVisibility(View.GONE);
            setupButtons(); // Aktifkan tombol
        } else {
            findViewById(R.id.lock_overlay).setVisibility(View.VISIBLE);
            setupUnlockLogic(); // Kunci aplikasi
        }
    }

    private void setupUnlockLogic() {
        EditText input = findViewById(R.id.input_code);
        findViewById(R.id.btn_unlock).setOnClickListener(v -> {
            if (input.getText().toString().equals(SECRET_CODE)) {
                prefs.edit().putBoolean("isBypassed", true).apply();
                Toast.makeText(this, "AKSES DIBUKA!", Toast.LENGTH_SHORT).show();
                checkSecurity(); // Refresh
            } else {
                Toast.makeText(this, "KODE SALAH!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupButtons() {
        findViewById(R.id.btn_perf).setOnClickListener(v -> toggle("perf"));
        findViewById(R.id.btn_gms).setOnClickListener(v -> toggle("gms"));
        findViewById(R.id.btn_extreme).setOnClickListener(v -> toggle("extreme"));
    }

    private void toggle(String mode) {
        // Logika shell sama seperti sebelumnya (Async Thread)
        // updateUI() panggil di sini
    }
}
