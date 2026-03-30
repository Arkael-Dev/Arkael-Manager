package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class PerfTileService extends TileService {
    @Override
    public void onClick() {
        SharedPreferences p = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        String kernelInfo = System.getProperty("os.version").toLowerCase();
        boolean isZixine = kernelInfo.contains("zixine");
        boolean isBypassed = p.getBoolean("isBypassed", false);

        if (!isZixine && !isBypassed) return;

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        String cmd;
        if (active) {
            // ON: Paksa 120Hz dan matikan thermal
            cmd = "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0; " +
                  "settings put global window_animation_scale 0; setprop touch.pressure.scale 0.001; " +
                  "setprop debug.touch.filter 0; resetprop ro.min.fling_velocity 8000; killall -STOP thermald;";
        } else {
            // OFF: HAPUS setelan kustom agar kembali ke DEFAULT ponsel (Bawaan)
            cmd = "settings delete system min_refresh_rate; settings delete system peak_refresh_rate; " +
                  "settings put global window_animation_scale 1; setprop touch.pressure.scale 1; " +
                  "setprop debug.touch.filter 1; resetprop ro.min.fling_velocity 50; killall -CONT thermald;";
        }
        
        new Thread(() -> {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); } catch (Exception ignored) {}
        }).start();

        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
