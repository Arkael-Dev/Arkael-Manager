package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class PerfTileService extends TileService {
    @Override
    public void onClick() {
        SharedPreferences p = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        boolean isZixine = System.getProperty("os.version").toLowerCase().contains("zixine");
        boolean isBypassed = p.getBoolean("isBypassed", false);

        if (!isZixine && !isBypassed) return;

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        String cmd = active ? 
            "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0; " +
            "settings put global window_animation_scale 0; setprop touch.pressure.scale 0.001; " +
            "setprop debug.touch.filter 0; resetprop ro.min.fling_velocity 8000; killall -STOP thermald;" : 
            "settings put system min_refresh_rate 120.0; settings put global window_animation_scale 1; " +
            "setprop touch.pressure.scale 1; setprop debug.touch.filter 1; resetprop ro.min.fling_velocity 50; killall -CONT thermald;";
        
        new Thread(() -> {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); } catch (Exception ignored) {}
        }).start();

        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
