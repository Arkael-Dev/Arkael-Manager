package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class PerfTileService extends TileService {
    @Override
    public void onClick() {
        SharedPreferences p = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        String kernelInfo = System.getProperty("os.version").toLowerCase();
        boolean isZixine = kernelInfo.contains("zixine");
        boolean isBypassed = p.getBoolean("isBypassed", false);

        // Jika belum verifikasi (bukan kernel zixine & belum masukin kode)
        if (!isZixine && !isBypassed) {
            Toast.makeText(getApplicationContext(), "PERF: Akses Ditolak! Belum Verifikasi.", Toast.LENGTH_SHORT).show();
            return; // Berhenti di sini, kode di bawahnya tidak akan dieksekusi
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        // Memunculkan Toast pemberitahuan status ON/OFF
        Toast.makeText(getApplicationContext(), active ? "ZIXINE PERF: ON (BRUTAL MODE)" : "ZIXINE PERF: OFF (DEFAULT)", Toast.LENGTH_SHORT).show();

        String cmd;
        if (active) {
            cmd = "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0; " +
                  "settings put system pointer_speed 7; settings put secure long_press_timeout 150; " +
                  "settings put global window_animation_scale 0; settings put global transition_animation_scale 0; " +
                  "settings put global animator_duration_scale 0; " +
                  "setprop windowsmgr.max_events_per_sec 300; setprop view.touch_slop 2; " +
                  "setprop touch.pressure.scale 0.001; setprop debug.touch.filter 0; " +
                  "resetprop ro.min.fling_velocity 8000; killall -STOP thermald;";
        } else {
            cmd = "settings delete system min_refresh_rate; settings delete system peak_refresh_rate; " +
                  "settings delete system pointer_speed; settings delete secure long_press_timeout; " +
                  "settings put global window_animation_scale 1; settings put global transition_animation_scale 1; " +
                  "settings put global animator_duration_scale 1; " +
                  "setprop windowsmgr.max_events_per_sec 90; setprop view.touch_slop 8; " +
                  "setprop touch.pressure.scale 1; setprop debug.touch.filter 1; " +
                  "resetprop ro.min.fling_velocity 50; killall -CONT thermald;";
        }
        
        new Thread(() -> {
            try { 
                Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); 
            } catch (Exception ignored) {}
        }).start();

        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
