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
            // SCRIPT ON
            String on = "setprop touch.pressure.scale 0.001; setprop persist.sys.composition.type gpu; setprop debug.cpurenderer true; setprop persist.sys.perf.top_app 1; setprop ro.vendor.qti.sys.fw.bg_apps_limit 60; setprop net.tcp.2g_init_rwnd 10; setprop net.tcp.3g_init_rwnd 10; setprop net.tcp.gprs_init_rwnd 10;";
            exec(on);
            t.setState(Tile.STATE_ACTIVE); // Warna Nyala (Hijau/Biru)
            Toast.makeText(this, "PERFORMANCE: BOOST 🚀", Toast.LENGTH_SHORT).show();
        } else {
            // SCRIPT OFF (RESTORE NORMAL)
            String off = "setprop touch.pressure.scale 1.0; setprop persist.sys.composition.type c2d; setprop debug.cpurenderer false; setprop persist.sys.perf.top_app 0; setprop ro.vendor.qti.sys.fw.bg_apps_limit 20;";
            exec(off);
            t.setState(Tile.STATE_INACTIVE); // Warna Mati (Abu-abu)
            Toast.makeText(this, "PERFORMANCE: NORMAL 🌍", Toast.LENGTH_SHORT).show();
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
