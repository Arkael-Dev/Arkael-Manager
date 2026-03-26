package com.zixine.engine;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.io.DataOutputStream;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnBrutal = findViewById(R.id.btnBrutal);
        btnBrutal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Jalankan Skrip Brutal kita via ROOT
                execRoot("sh /data/adb/modules/garnet_game_boost/toggle.sh");
                Toast.makeText(MainActivity.this, "Seal of Narukami Executed! ⚡", Toast.LENGTH_SHORT).show();
            }
        });
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
            Toast.makeText(this, "Root Access Denied!", Toast.LENGTH_LONG).show();
        }
    }
}
