package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private final String SECRET_CODE = "Jembud Mambu"; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        verifyKernelAndAccess();

        findViewById(R.id.btn_unlock).setOnClickListener(v -> {
            EditText input = findViewById(R.id.input_code);
            if (input.getText().toString().equals(SECRET_CODE)) {
                prefs.edit().putBoolean("isBypassed", true).apply();
                Toast.makeText(this, "BYPASS SUCCESS", Toast.LENGTH_SHORT).show();
                verifyKernelAndAccess();
            } else {
                Toast.makeText(this, "WRONG CODE!", Toast.LENGTH_SHORT).show();
                input.setText("");
            }
        });

        // Tombol INFO diklik
        findViewById(R.id.btn_info).setOnClickListener(v -> showTutorialDialog());
    }

    private void showTutorialDialog() {
        String kernelInfo = getKernelVersion().toLowerCase();
        boolean isZixine = kernelInfo.contains("zixine"); 
        boolean isBypassed = prefs.getBoolean("isBypassed", false); 
        boolean isVerified = isZixine || isBypassed;

        // Cek apakah 3 toggle sudah ditarik ke Status Bar
        boolean perfAdded = prefs.getBoolean("perf_added", false);
        boolean gmsAdded = prefs.getBoolean("gms_added", false);
        boolean extAdded = prefs.getBoolean("extreme_added", false);
        boolean isAllTogglesAdded = (perfAdded && gmsAdded && extAdded);

        StringBuilder msg = new StringBuilder();
        
        // 1. Pengenalan Singkat (Selalu Tampil)
        msg.append("ZIXINE ENGINE CORE adalah modul sistem eksklusif untuk memaksimalkan performa, responsivitas layar brutal, dan membekukan aplikasi tak penting.\n\n");

        if (!isVerified) {
            // 2. Jika Belum Verifikasi
            msg.append("⚠️ CARA VERIFIKASI:\nAplikasi ini otomatis mendeteksi Kernel Zixine. Jika Anda menggunakan kernel lain, masukkan Passkey (minta pada developer/donate untuk mendapatkan pasword) pada layar utama untuk membuka kunci.\n\n");
            msg.append("💡 CARA MENGGUNAKAN:\nSetelah berhasil masuk, tarik panel notifikasi Anda ke bawah, lalu tambahkan 3 Toggle Zixine ke menu Quick Settings.");
        } else {
            // 3. Jika Sudah Verifikasi
            if (!isAllTogglesAdded) {
                // Belum tambah 3 toggle
                msg.append("💡 CARA MENGGUNAKAN:\nTarik panel notifikasi layar atas ke bawah, edit Quick Settings (ikon pensil), cari 3 toggle ZIXINE (Perf, GMS, Extreme), lalu seret (drag) ke atas agar bisa ditekan.");
            } else {
                // Sudah tambah 3 toggle
                msg.append("✅ STATUS: SIAP TEMPUR!\nKetiga toggle (Perf, GMS, Extreme) sudah terpasang di Status Bar Anda. Aplikasi ini sudah tidak perlu dibuka lagi, silakan kontrol Engine langsung dari panel atas!");
            }
        }

        new AlertDialog.Builder(this)
            .setTitle("INFO ZIXINE ENGINE")
            .setMessage(msg.toString())
            .setPositiveButton("MENGERTI", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private void verifyKernelAndAccess() {
        String kernelInfo = getKernelVersion().toLowerCase();
        boolean isZixine = kernelInfo.contains("zixine"); 
        boolean isBypassed = prefs.getBoolean("isBypassed", false); 

        if (isZixine || isBypassed) {
            findViewById(R.id.layout_locked).setVisibility(View.GONE);
            findViewById(R.id.layout_verified).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.layout_locked).setVisibility(View.VISIBLE);
            findViewById(R.id.layout_verified).setVisibility(View.GONE);
        }
    }

    private String getKernelVersion() {
        try {
            Process p = Runtime.getRuntime().exec("uname -a");
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String res = r.readLine();
            return (res != null) ? res : System.getProperty("os.version");
        } catch (Exception e) { return System.getProperty("os.version"); }
    }
}
