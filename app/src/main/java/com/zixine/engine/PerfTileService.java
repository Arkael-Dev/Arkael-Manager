package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import java.io.DataOutputStream;

public class PerfTileService extends TileService {
    @Override
    public void onClick() {
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        if (active) {
            // 1. Render GPU Boost
            // 2. Aim Lengket (Pointer speed, touch_slop)
            // 3. Thermal Disable Extreme (mi_thermald, joyose, thermal-engine)
            String cmdBoost = "setprop touch.pressure.scale 0.001; setprop persist.sys.composition.type gpu; setprop debug.cpurenderer true; " +
                              "settings put system pointer_speed 7; setprop windowsmgr.max_events_per_sec 300; setprop view.touch_slop 2; " +
                              "stop mi_thermald; stop thermal-engine; stop thermalserviced; stop joyose; setprop thermal.engine 0;";
            exec(cmdBoost);
            
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "PERF+AIM BOOST 🚀 | THERMAL OFF 🔥", Toast.LENGTH_SHORT).show();
        } else {
            // Mengembalikan semua ke setelan Normal / Aman
            String cmdNormal = "setprop touch.pressure.scale 1.0; setprop persist.sys.composition.type c2d; setprop debug.cpurenderer false; " +
                               "settings put system pointer_speed 3; setprop windowsmgr.max_events_per_sec 90; setprop view.touch_slop 8; " +
                               "start mi_thermald; start thermal-engine; start thermalserviced; start joyose; setprop thermal.engine 1;";
            exec(cmdNormal);
            
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "PERF NORMAL 🌍 | THERMAL ON ❄️", Toast.LENGTH_SHORT).show();
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
