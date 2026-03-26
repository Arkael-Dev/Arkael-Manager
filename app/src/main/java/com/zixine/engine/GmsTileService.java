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
            // SIKAT GMS: Suspend User 0 + Force Stop semua komponen
            String cmd = "pm suspend --user 0 com.google.android.gms com.android.vending; " +
                         "am force-stop com.google.android.gms; " +
                         "am force-stop com.android.vending; " +
                         "am kill com.google.android.gms;";
            exec(cmd);
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "GMS: EXECUTED! 💀", 0).show();
        } else {
            // RESTORE GMS
            exec("pm unsuspend --user 0 com.google.android.gms com.android.vending;");
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "GMS: ALIVE 🌍", 0).show();
        }
        t.updateTile();
    }
    private void exec(String c) {
        try {
            java.lang.Process p = Runtime.getRuntime().exec("su");
            DataOutputStream o = new DataOutputStream(p.getOutputStream());
            o.writeBytes(c + "\nexit\n"); o.flush();
        } catch (Exception e) {}
    }
}
