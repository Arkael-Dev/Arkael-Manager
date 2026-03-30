package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import java.io.DataOutputStream;

public class PerfTileService extends TileService {

    @Override
    public void onClick() {
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        new Thread(() -> {
            String cmd;
            if (active) {
                cmd = "settings put global window_animation_scale 0; settings put system min_refresh_rate 120.0; " +
                      "setprop touch.pressure.scale 0.001; resetprop ro.min.fling_velocity 8000; " +
                      "swapoff -a; killall -STOP thermald; settings put global zen_mode 1;";
                exec(cmd);
            } else {
                cmd = "settings put global window_animation_scale 1; settings put system min_refresh_rate 60.0; " +
                      "swapon -a; killall -CONT thermald; settings put global zen_mode 0;";
                exec(cmd);
            }
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
