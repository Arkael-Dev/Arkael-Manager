package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class GmsTileService extends TileService {
    
    private final String GMS_PACKS = "com.google.android.gms com.android.vending com.google.android.gsf";

    // Melacak apakah pengguna sudah menambahkan toggle ke panel atas
    @Override
    public void onTileAdded() {
        super.onTileAdded();
        getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE).edit().putBoolean("gms_added", true).apply();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE).edit().putBoolean("gms_added", false).apply();
    }

    @Override
    public void onClick() {
        SharedPreferences p = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        String kernelInfo = System.getProperty("os.version").toLowerCase();
        boolean isZixine = kernelInfo.contains("zixine");
        boolean isBypassed = p.getBoolean("isBypassed", false);

        // Jika belum verifikasi
        if (!isZixine && !isBypassed) {
            Toast.makeText(getApplicationContext(), "GMS: Akses Ditolak! Belum Verifikasi.", Toast.LENGTH_SHORT).show();
            return;
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        Toast.makeText(getApplicationContext(), active ? "ZIXINE GMS: SUSPENDED" : "ZIXINE GMS: NORMAL", Toast.LENGTH_SHORT).show();

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
