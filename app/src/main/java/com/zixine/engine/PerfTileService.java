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
        boolean isVerified = System.getProperty("os.version").toLowerCase().contains("zixine") || p.getBoolean("isBypassed", false);

        if (!isVerified) {
            Toast.makeText(getApplicationContext(), "PERF: Akses Ditolak! Belum Verifikasi.", Toast.LENGTH_SHORT).show();
            return; 
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        Toast.makeText(getApplicationContext(), active ? "ZIXINE PERF: ULTIMATE ON (ZRAM OFF)" : "ZIXINE PERF: NORMAL MODE (ZRAM ON)", Toast.LENGTH_SHORT).show();

        String cmd;
        if (active) {
            // MODE ON: Latensi Nol & Memori Penuh (swapoff)
            cmd = "swapoff -a; " +
                  "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0; " +
                  "settings put system pointer_speed 7; settings put secure long_press_timeout 250; " +
                  "settings put global window_animation_scale 0.0; settings put global transition_animation_scale 0.0; " +
                  "settings put global animator_duration_scale 0.2; " + 
                  "setprop touch.pressure.scale 0.001; setprop debug.touch.filter 0; " +
                  "setprop view.touch_slop 1; setprop view.scroll_friction 0; setprop view.fading_edge_length 0; " +
                  "setprop debug.sf.latch_unsignaled 1; setprop windowsmgr.max_events_per_sec 1000; " +
                  "resetprop ro.min.fling_velocity 20000; killall -STOP thermald;";
        } else {
            // MODE OFF: Pembersihan ke standar pabrik & Kembalikan ZRAM (swapon)
            cmd = "swapon -a; " +
                  "settings put system min_refresh_rate 60.0; settings put system peak_refresh_rate 60.0; " +
                  "settings put system pointer_speed 0; settings put secure long_press_timeout 500; " +
                  "settings put global window_animation_scale 1.0; settings put global transition_animation_scale 1.0; " +
                  "settings put global animator_duration_scale 1.0; " +
                  "setprop touch.pressure.scale 1.0; setprop debug.touch.filter 1; " +
                  "setprop view.touch_slop 8; setprop view.scroll_friction 0.015; setprop view.fading_edge_length 10; " +
                  "setprop debug.sf.latch_unsignaled 0; setprop windowsmgr.max_events_per_sec 90; " +
                  "resetprop ro.min.fling_velocity 50; killall -CONT thermald;";
        }
        
        new Thread(() -> {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); } catch (Exception ignored) {}
        }).start();

        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
