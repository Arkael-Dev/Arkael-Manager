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
        boolean isZixine = System.getProperty("os.version").toLowerCase().contains("zixine");
        boolean isBypassed = prefs.getBoolean("isBypassed", false);

        // LOCKDOWN TOTAL TANPA KECUALI
        if (!isZixine && !isBypassed) {
            Toast.makeText(this, "ZIXINE: AKSES TERKUNCI!", Toast.LENGTH_SHORT).show();
            return; // Berhenti di sini, jangan jalankan shell!
        }

        // Jika lolos sekuriti, baru jalankan perintah
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        // ... Jalankan Shell Command ...
        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
