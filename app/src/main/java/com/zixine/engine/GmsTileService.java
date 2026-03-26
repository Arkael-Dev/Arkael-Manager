package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import java.io.DataOutputStream;

public class GmsTileService extends TileService {
    @Override
    public void onClick() {
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        if (active) {
            exec("pm suspend com.google.android.gms com.android.vending &");
            t.setState(Tile.STATE_ACTIVE); // Warna Nyala
            Toast.makeText(this, "GMS: LOCKED 🛡️", Toast.LENGTH_SHORT).show();
        } else {
            exec("pm unsuspend com.google.android.gms com.android.vending &");
            t.setState(Tile.STATE_INACTIVE); // Warna Mati
            Toast.makeText(this, "GMS: RESTORED 🌍", Toast.LENGTH_SHORT).show();
        }
        t.updateTile();
    }

    private void exec(String c) {
        try {
            java.lang.Process p = Runtime.getRuntime().exec("su");
            DataOutputStream o = new DataOutputStream(p.getOutputStream());
            o.writeBytes(c + "\nexit\n"); o.flush();
        } catch (Exception ignored) {}
    }
}
