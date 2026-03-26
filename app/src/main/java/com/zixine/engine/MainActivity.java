package com.zixine.engine;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    private String modulePath = "/data/adb/modules/garnet_game_boost/service.sh";
    private EditText editPath;
    private TextView txtTemp, txtLog;
    private boolean isBrutal = false;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtTemp = findViewById(R.id.txt_temp);
        txtLog = findViewById(R.id.txt_log);
        editPath = findViewById(R.id.edit_script_path);
        Button btnBrutal = findViewById(R.id.btn_brutal);
        Button btnRun = findViewById(R.id.btn_run_custom);
        Button btnFix = findViewById(R.id.btn_fix);

        SharedPreferences prefs = getSharedPreferences("Zixine", MODE_PRIVATE);
        editPath.setText(prefs.getString("path", "/data/local/tmp/boost.sh"));

        // Cek file saat start
        checkServiceFile();
        startThermalMonitor();

        btnBrutal.setOnClickListener(v -> animate(v, () -> {
            if (!isBrutal) {
                execRoot("pm disable com.miui.powerkeeper/.statemachine.PowerStateMachineService; for a in com.android.vending com.google.android.gms com.xiaomi.joyose; do pm disable-user --user 0 $a; done; am kill-all;");
                btnBrutal.setText("NORMAL MODE");
                isBrutal = true;
            } else {
                execRoot("pm enable com.miui.powerkeeper/.statemachine.PowerStateMachineService; for a in com.android.vending com.google.android.gms com.xiaomi.joyose; do pm enable $a; done;");
                btnBrutal.setText("BRUTAL MODE");
                isBrutal = false;
            }
        }));

        btnRun.setOnClickListener(v -> animate(v, () -> {
            String p = editPath.getText().toString();
            prefs.edit().putString("path", p).apply();
            execRoot("sh " + p);
            Toast.makeText(this, "Script Done!", 0).show();
        }));

        btnFix.setOnClickListener(v -> animate(v, () -> {
            execRoot("chmod 755 " + modulePath);
            Toast.makeText(this, "Permission Fixed to 755!", 0).show();
            checkServiceFile();
        }));
    }

    private void checkServiceFile() {
        File f = new File(modulePath);
        if (!f.exists()) Toast.makeText(this, "⚠️ service.sh TIDAK ADA di folder modul!", 1).show();
        else if (f.length() == 0) Toast.makeText(this, "⚠️ service.sh KOSONG!", 1).show();
    }

    private void startThermalMonitor() {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    BufferedReader r = new BufferedReader(new FileReader("/sys/class/thermal/thermal_zone0/temp"));
                    double t = Double.parseDouble(r.readLine()) / 1000.0;
                    txtTemp.setText("System Temp: " + String.format("%.1f", t) + "°C");
                    if (t > 45) txtTemp.setTextColor(0xFFFF3131); else txtTemp.setTextColor(0xFF00D4FF);
                } catch (Exception e) { txtTemp.setText("Temp: Unsupported"); }
                handler.postDelayed(this, 3000);
            }
        }, 1000);
    }

    private void animate(View v, Runnable r) {
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction(() -> {
            v.animate().scaleX(1f).scaleY(1f).setDuration(80).withEndAction(r).start();
        }).start();
    }

    private void execRoot(String c) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream o = new DataOutputStream(p.getOutputStream());
            o.writeBytes(c + "\nexit\n"); o.flush();
        } catch (Exception e) { txtLog.setText("Root Error!"); }
    }
}
