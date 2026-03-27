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
        
        // Target GMS agar RAM lega saat gaming
        String target = "com.google.android.gms com.android.vending com.google.android.gsf";
        
        if (active) {
            // GMS Koma (Disable)
            exec("for p in " + target + "; do pm disable-user --user 0 $p; done;");
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "GMS: OFF (GAMING MODE) 🚀", Toast.LENGTH_SHORT).show();
        } else {
            // GMS Bangun
            exec("for p in " + target + "; do pm enable $p; done;");
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "GMS: ON (NORMAL MODE) 🌍", Toast.LENGTH_SHORT).show();
        }
        t.updateTile();
    }

    private void exec(String c) { 
        try { 
            Process p = Runtime.getRuntime().exec("su"); 
            DataOutputStream o = new DataOutputStream(p.getOutputStream()); 
            o.writeBytes(c + "\nexit\n"); 
            o.flush(); 
        } catch (Exception ignored) {} 
    }
}