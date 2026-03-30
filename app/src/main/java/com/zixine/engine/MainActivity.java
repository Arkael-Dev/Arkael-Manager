package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private final String SECRET_CODE = "445456"; 

    private LinearLayout layoutVerified;
    private MaterialCardView layoutLocked;
    private EditText inputCode;
    private Button btnUnlock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        
        layoutVerified = findViewById(R.id.layout_verified);
        layoutLocked = findViewById(R.id.layout_locked);
        inputCode = findViewById(R.id.input_code);
        btnUnlock = findViewById(R.id.btn_unlock);

        verifyKernelAndAccess();

        btnUnlock.setOnClickListener(v -> {
            if (inputCode.getText().toString().equals(SECRET_CODE)) {
                prefs.edit().putBoolean("isBypassed", true).apply();
                Toast.makeText(this, "BYPASS SUCCESSFUL", Toast.LENGTH_SHORT).show();
                verifyKernelAndAccess();
            } else {
                Toast.makeText(this, "ACCESS DENIED: WRONG PASSKEY", Toast.LENGTH_SHORT).show();
                inputCode.setText("");
            }
        });
    }

    private void verifyKernelAndAccess() {
        String kernelInfo = getKernelVersion().toLowerCase();
        boolean isZixine = kernelInfo.contains("zixine"); 
        boolean isBypassed = prefs.getBoolean("isBypassed", false); 

        if (isZixine || isBypassed) {
            // Jika berhasil terverifikasi, sembunyikan gembok, tampilkan instruksi
            layoutLocked.setVisibility(View.GONE);
            layoutVerified.setVisibility(View.VISIBLE);
        } else {
            // Jika gagal, tampilkan input gembok
            layoutLocked.setVisibility(View.VISIBLE);
            layoutVerified.setVisibility(View.GONE);
        }
    }

    private String getKernelVersion() {
        try {
            // Membaca versi kernel langsung dari sistem Linux/Android
            Process p = Runtime.getRuntime().exec("uname -a");
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String output = r.readLine();
            if (output != null) return output;
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
        // Fallback jika uname gagal
        return System.getProperty("os.version"); 
    }
}
