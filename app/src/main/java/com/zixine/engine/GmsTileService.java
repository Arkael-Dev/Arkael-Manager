package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class GmsTileService extends TileService {
    private final String GMS_PACKS = "com.google.android.gms com.android.vending com.google.android.gsf";

    @Override
    public void onClick() {
        if (!SecurityUtils.isSystemVerified(getApplicationContext())) {
            Toast.makeText(getApplicationContext(), "AKSES DITOLAK! Kernel/Passkey Invalid.", Toast.LENGTH_SHORT).show();
            Tile t = getQsTile(); t.setState(Tile.STATE_INACTIVE); t.updateTile(); return;
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        Toast.makeText(getApplicationContext(), active ? "ZIXINE GMS: DIBEKUKAN" : "ZIXINE GMS: AKTIF NORMAL", Toast.LENGTH_SHORT).show();

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
