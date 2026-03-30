package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class PerfTileService extends TileService {

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE).edit().putBoolean("perf_added", true).apply();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE).edit().putBoolean("perf_added", false).apply();
    }

    @Override
    public void onClick() {
        SharedPreferences p = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        String kernelInfo = System.getProperty("os.version").toLowerCase();
        boolean isZixine = kernelInfo.contains("zixine");
        boolean isBypassed = p.getBoolean("isBypassed", false);

        if (!isZixine && !isBypassed) {
            Toast.makeText(getApplicationContext(), "PERF: Belum Verifikasi!", Toast.LENGTH_SHORT).show();
            return; 
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        Toast.makeText(getApplicationContext(), active ? "PERF: AKTIF (BRUTAL)" : "PERF: NORMAL", Toast.LENGTH_SHORT).show();

        String cmd;
        if (active) {
            // MODE ON: Agresif tapi tetap memberikan ruang bagi SystemUI
            cmd = "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0; " +
                  "settings put system pointer_speed 7; settings put secure long_press_timeout 350; " +
                  "settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; " +
                  "settings put global animator_duration_scale 0.8; " +
                  "setprop touch.pressure.scale 0.001; setprop debug.touch.filter 0; " +
                  "resetprop ro.min.fling_velocity 8000; killall -STOP thermald;";
        } else {
            // MODE OFF: Kembalikan semua ke nilai standar Android (BUKAN DELETE)
            cmd = "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0; " +
                  "settings put system pointer_speed 0; settings put secure long_press_timeout 500; " +
                  "settings put global window_animation_scale 1.0; settings put global transition_animation_scale 1.0; " +
                  "settings put global animator_duration_scale 1.0; " +
                  "setprop touch.pressure.scale 1.0; setprop debug.touch.filter 1; " +
                  "resetprop ro.min.fling_velocity 50; killall -CONT thermald;";
        }
        
        new Thread(() -> {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); } catch (Exception ignored) {}
        }).start();

        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
