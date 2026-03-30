package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class GmsTileService extends TileService {
    private final String GMS_PACKS = "com.google.android.gms com.android.vending com.google.android.gsf";

    @Override
    public void onClick() {
        SharedPreferences p = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        boolean isZixine = System.getProperty("os.version").toLowerCase().contains("zixine");
        boolean isBypassed = p.getBoolean("isBypassed", false);

        if (!isZixine && !isBypassed) return;

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        String cmd = active ? 
            "for p in " + GMS_PACKS + "; do pm disable-user --user 0 $p; done;" : 
            "for p in " + GMS_PACKS + "; do pm enable $p; done;";
        
        new Thread(() -> {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); } catch (Exception ignored) {}
        }).start();

        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
