package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import java.io.DataOutputStream;

public class GmsTileService extends TileService {
    @Override
    public void onClick() {
        SharedPreferences prefs = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        boolean isZixine = System.getProperty("os.version").toLowerCase().contains("zixine");
        boolean isBypassed = prefs.getBoolean("isBypassed", false);

        if (!isZixine && !isBypassed) {
            Toast.makeText(this, "ZIXINE: AKSES TERKUNCI!", Toast.LENGTH_SHORT).show();
            return;
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        new Thread(() -> {
            String cmd = active ? "killall -STOP com.google.android.gms;" : "killall -CONT com.google.android.gms;";
            exec(cmd);
        }).start();

        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }

    private void exec(String c) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(c + "\nexit\n");
            os.flush();
        } catch (Exception ignored) {}
    }
}
