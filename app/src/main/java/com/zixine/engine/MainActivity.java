package com.zixine.engine;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    private TextView txtTemp, txtCpu, txtGpu;
    private EditText editPath;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtTemp = findViewById(R.id.txt_temp);
        txtCpu = findViewById(R.id.txt_cpu);
        txtGpu = findViewById(R.id.txt_gpu);
        editPath = findViewById(R.id.edit_script_path);

        Button btnBrutal = findViewById(R.id.btn_brutal);
        Button btnRun = findViewById(R.id.btn_run_custom);
        Button btnCpu = findViewById(R.id.btn_cpu_perf);
        Button btnGpu = findViewById(R.id.btn_gpu_max);
        Button btnFix = findViewById(R.id.btn_fix);

        SharedPreferences prefs = getSharedPreferences("Zixine", MODE_PRIVATE);
        editPath.setText(prefs.getString("path", "/data/local/tmp/boost.sh"));

        startMonitoring();

        btnCpu.setOnClickListener(v -> animate(v, () -> {
            execRoot("echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
            Toast.makeText(this, "CPU Performance Active!", 0).show();
        }));

        btnGpu.setOnClickListener(v -> animate(v, () -> {
            execRoot("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor");
            Toast.makeText(this, "GPU Performance Active!", 0).show();
        }));

        btnBrutal.setOnClickListener(v -> animate(v, () -> {
            execRoot("pm disable com.miui.powerkeeper/.statemachine.PowerStateMachineService; pm disable-user --user 0 com.android.vending; am kill-all;");
            Toast.makeText(this, "BRUTAL MODE: ON", 0).show();
        }));

        btnRun.setOnClickListener(v -> animate(v, () -> {
            String p = editPath.getText().toString();
            prefs.edit().putString("path", p).apply();
            execRoot("sh " + p);
            Toast.makeText(this, "Script Executed!", 0).show();
        }));

        btnFix.setOnClickListener(v -> animate(v, () -> {
            execRoot("chmod 755 /data/adb/modules/garnet_game_boost/service.sh");
            Toast.makeText(this, "Permission Fixed!", 0).show();
        }));
    }

    private void startMonitoring() {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    BufferedReader rTemp = new BufferedReader(new FileReader("/sys/class/thermal/thermal_zone0/temp"));
                    txtTemp.setText("System Temp: " + (Double.parseDouble(rTemp.readLine())/1000) + "°C");
                    BufferedReader rCpu = new BufferedReader(new FileReader("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"));
                    txtCpu.setText("CPU Gov: " + rCpu.readLine());
                    BufferedReader rGpu = new BufferedReader(new FileReader("/sys/class/kgsl/kgsl-3d0/devfreq/governor"));
                    txtGpu.setText("GPU Gov: " + rGpu.readLine());
                } catch (Exception e) {}
                handler.postDelayed(this, 2000);
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
            java.lang.Process p = Runtime.getRuntime().exec("su");
            DataOutputStream o = new DataOutputStream(p.getOutputStream());
            o.writeBytes(c + "\nexit\n"); o.flush();
        } catch (Exception ignored) {}
    }
}
