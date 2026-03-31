package com.zixine.engine;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private final String SECRET_CODE = "778789"; 
    private final String DONATE_URL = "https://t.me/zenixoooooo";

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

        boolean perfAdded = prefs.getBoolean("perf_added", false);
        boolean gmsAdded = prefs.getBoolean("gms_added", false);
        boolean extAdded = prefs.getBoolean("extreme_added", false);
        boolean isAllTogglesAdded = (perfAdded && gmsAdded && extAdded);

        // 1. Siapkan Custom Dialog
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_info);
        
        // Buat background transparan agar lengkungan (radius) desain kita terlihat
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        // 2. Hubungkan elemen-elemen di dalam dialog
        TextView tvStatus = dialog.findViewById(R.id.dialog_status);
        TextView tvMessage = dialog.findViewById(R.id.dialog_message);
        Button btnDonate = dialog.findViewById(R.id.btn_dialog_donate);
        Button btnOk = dialog.findViewById(R.id.btn_dialog_ok);

        // 3. Atur logika teks dan tombol
        StringBuilder msg = new StringBuilder();
        msg.append("Modul sistem eksklusif untuk responsivitas layar brutal dan manajemen memori tingkat dewa.\n\n");

        if (!isVerified) {
            tvStatus.setText("STATUS: AKSES TERKUNCI");
            tvStatus.setTextColor(Color.parseColor("#FF1744")); // Merah
            
            msg.append("⚠️ CARA VERIFIKASI:\nAplikasi ini mendeteksi Kernel Zixine. Jika Anda menggunakan kernel lain, masukkan Passkey di layar utama.\n\n");
            msg.append("💰 CARA MENDAPATKAN PASSKEY:\nKlik tombol Donasi di bawah ini untuk mendukung developer dan mendapatkan kode rahasia.");
            
            // Tampilkan tombol donasi
            btnDonate.setVisibility(View.VISIBLE);
        } else {
            tvStatus.setText("STATUS: SYSTEM VERIFIED");
            tvStatus.setTextColor(Color.parseColor("#00E5FF")); // Cyan
            
            if (!isAllTogglesAdded) {
                msg.append("💡 LANGKAH SELANJUTNYA:\nTarik panel notifikasi atas, klik edit (ikon pensil), cari toggle ZIXINE (Perf, GMS, Extreme), lalu seret ke atas untuk dipasang.");
            } else {
                msg.append("✅ SIAP TEMPUR!\nKetiga toggle telah terpasang di panel atas. Anda tidak perlu membuka aplikasi ini lagi. Kendalikan semuanya langsung dari Status Bar!");
            }
        }

        tvMessage.setText(msg.toString());

        // 4. Aksi Tombol
        btnDonate.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(DONATE_URL));
            startActivity(i);
        });

        btnOk.setOnClickListener(v -> dialog.dismiss());

        // 5. Tampilkan Dialog
        dialog.show();
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
