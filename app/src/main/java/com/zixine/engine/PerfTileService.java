package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class PerfTileService extends TileService {

    @Override
    public void onClick() {
        if (!SecurityUtils.isSystemVerified(getApplicationContext())) {
            Toast.makeText(getApplicationContext(), "AKSES DITOLAK! Kernel/Passkey Invalid.", Toast.LENGTH_SHORT).show(); 
            Tile t = getQsTile(); t.setState(Tile.STATE_INACTIVE); t.updateTile(); return; 
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        Toast.makeText(getApplicationContext(), active ? "ZIXINE PERF: ULTIMATE ON (LAYAR BRUTAL)" : "ZIXINE PERF: NORMAL", Toast.LENGTH_SHORT).show();

        String cmd;
        if (active) {
            // TANPA swapoff. Murni hanya untuk kecepatan responsivitas.
            cmd = "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0; " +
                  "settings put system pointer_speed 7; settings put secure long_press_timeout 250; " +
                  "settings put global window_animation_scale 0.0; settings put global transition_animation_scale 0.0; " +
                  "settings put global animator_duration_scale 0.2; " + 
                  "setprop touch.pressure.scale 0.001; setprop debug.touch.filter 0; " +
                  "setprop view.touch_slop 1; setprop view.scroll_friction 0; setprop view.fading_edge_length 0; " +
                  "setprop debug.sf.latch_unsignaled 1; setprop windowsmgr.max_events_per_sec 1000; " +
                  "resetprop ro.min.fling_velocity 20000; killall -STOP thermald;";
        } else {
            // TANPA swapon. Kembalikan responsivitas ke standar.
            cmd = "settings put system min_refresh_rate 60.0; settings put system peak_refresh_rate 60.0; " +
                  "settings put system pointer_speed 0; settings put secure long_press_timeout 500; " +
                  "settings put global window_animation_scale 1.0; settings put global transition_animation_scale 1.0; " +
                  "settings put global animator_duration_scale 1.0; " +
                  "setprop touch.pressure.scale 1.0; setprop debug.touch.filter 1; " +
                  "setprop view.touch_slop 8; setprop view.scroll_friction 0.015; setprop view.fading_edge_length 10; " +
                  "setprop debug.sf.latch_unsignaled 0; setprop windowsmgr.max_events_per_sec 90; " +
                  "resetprop ro.min.fling_velocity 50; killall -CONT thermald;";
        }
        
        new Thread(() -> { try { Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); } catch (Exception e) {} }).start();
        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE); t.updateTile();
    }
}
