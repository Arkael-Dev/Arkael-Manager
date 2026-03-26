package com.zixine.engine;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class GmsTileService extends TileService {
    @Override
    public void onClick() {
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        String target = "com.google.android.gms com.android.vending com.google.android.gsf";
        if (active) {
            exec("pm suspend --user 0 " + target + "; am force-stop com.google.android.gms;");
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "GMS: KILLED 💀", 0).show();
        } else {
            exec("pm unsuspend --user 0 " + target + ";");
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "GMS: RESTORED 🌍", 0).show();
        }
        t.updateTile();
    }
    private void exec(String c) { try { java.lang.Process p = Runtime.getRuntime().exec("su"); java.io.DataOutputStream o = new java.io.DataOutputStream(p.getOutputStream()); o.writeBytes(c + "\nexit\n"); o.flush(); } catch (Exception e) {} }
}
