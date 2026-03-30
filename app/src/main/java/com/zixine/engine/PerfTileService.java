package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class PerfTileService extends TileService {
    @Override
    public void onClick() {
        SharedPreferences prefs = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        String version = System.getProperty("os.version");
        boolean isZixine = version != null && version.toLowerCase().contains("zixine");
        boolean isBypassed = prefs.getBoolean("isBypassed", false);

        if (!isZixine && !isBypassed) {
            Toast.makeText(this, "AKSES TERKUNCI! Buka Aplikasi.", Toast.LENGTH_SHORT).show();
            return;
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        String cmd = active ? "settings put system min_refresh_rate 120.0;" : "settings put system min_refresh_rate 60.0;";
        
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor();
            } catch (Exception ignored) {}
        }).start();

        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
