package com.zixine.engine;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.io.DataOutputStream;

public class MainActivity extends Activity {
    
    // Daftar Target Brutal
    private String[] targets = {
        "com.android.vending", "com.google.android.gms", "com.google.android.youtube", 
        "com.google.android.gm", "com.android.chrome", "com.miui.analytics", 
        "com.xiaomi.joyose", "com.miui.msa.global", "com.whatsapp", 
        "com.brave.browser", "id.co.bri.brimo", "id.dana"
    };

    private boolean isBrutalActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Button btnBrutal = findViewById(R.id.btnBrutal);
        
        btnBrutal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isBrutalActive) {
                    activateBrutalMode();
                    btnBrutal.setText("DEACTIVATE NORMAL MODE");
                    btnBrutal.setBackgroundColor(0xFFFF3131); // Warna Merah
                    isBrutalActive = true;
                } else {
                    deactivateBrutalMode();
                    btnBrutal.setText("ACTIVATE BRUTAL MODE");
                    btnBrutal.setBackgroundColor(0xFF007BFF); // Warna Biru
                    isBrutalActive = false;
                }
            }
        });
    }

    private void activateBrutalMode() {
        StringBuilder sb = new StringBuilder();
        
        // 1. Lumpuhkan PowerKeeper HyperOS
        sb.append("pm disable com.miui.powerkeeper/.statemachine.PowerStateMachineService; ");
        
        // 2. Kill & Suspend semua target
        for (String app : targets) {
            sb.append("am force-stop ").append(app).append("; ");
            sb.append("pm suspend ").append(app).append("; ");
        }
        
        execRoot(sb.toString());
        Toast.makeText(this, "Narukami Seal: ACTIVE! ⚡", Toast.LENGTH_SHORT).show();
    }

    private void deactivateBrutalMode() {
        StringBuilder sb = new StringBuilder();
        
        // 1. Hidupkan PowerKeeper kembali
        sb.append("pm enable com.miui.powerkeeper/.statemachine.PowerStateMachineService; ");
        
        // 2. Hidupkan semua target
        for (String app : targets) {
            sb.append("pm unsuspend ").append(app).append("; ");
        }
        
        execRoot(sb.toString());
        Toast.makeText(this, "Narukami Seal: RELEASED! 🌍", Toast.LENGTH_SHORT).show();
    }

    private void execRoot(String command) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
        } catch (Exception e) {
            Toast.makeText(this, "Root Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
