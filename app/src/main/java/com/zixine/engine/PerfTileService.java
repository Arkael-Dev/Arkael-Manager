package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import java.io.DataOutputStream;

public class PerfTileService extends TileService {
    
    // 1. Daftar file pengatur arus (Ampere)
    private final String CHARGE_PATHS = "/sys/class/power_supply/battery/constant_charge_current " +
                                        "/sys/class/power_supply/battery/constant_charge_current_max " +
                                        "/sys/class/qcom-battery/restricted_current " +
                                        "/sys/class/power_supply/main/constant_charge_current_max";

    // 2. Daftar file pembatas otomatis (Hardware Thermal/Step Charging)
    private final String LIMIT_PATHS = "/sys/class/power_supply/battery/step_charging_enabled " +
                                       "/sys/class/power_supply/battery/thermal_limit";

    @Override
    public void onClick() {
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        if (active) {
            // A. GPU Boost & Aim Lengket
            String cmdBoost = "setprop touch.pressure.scale 0.001; setprop persist.sys.composition.type gpu; setprop debug.cpurenderer true; " +
                              "settings put system pointer_speed 7; setprop windowsmgr.max_events_per_sec 300; setprop view.touch_slop 2; " +
            // B. Matikan Thermal System (Software Daemon)
                              "stop mi_thermald; stop thermal-engine; stop thermalserviced; stop joyose; setprop thermal.engine 0; " +
            // C. MATIKAN PEMBATAS OTOMATIS KERNEL (Bypass pengaman)
                              "for limit in " + LIMIT_PATHS + "; do if [ -f \"$limit\" ]; then chmod 666 \"$limit\"; echo 0 > \"$limit\"; fi; done; " +
            // D. Set Arus Paksa ke 10W (2A = 2000000 microAmpere)
                              "for path in " + CHARGE_PATHS + "; do if [ -f \"$path\" ]; then chmod 666 \"$path\"; echo 2000000 > \"$path\"; fi; done;";
            exec(cmdBoost);
            
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "PERF 🔥 | BYPASS 10W 🔋", Toast.LENGTH_SHORT).show();
        } else {
            // A. Kembalikan Layar ke Normal
            String cmdNormal = "setprop touch.pressure.scale 1.0; setprop persist.sys.composition.type c2d; setprop debug.cpurenderer false; " +
                               "settings put system pointer_speed 3; setprop windowsmgr.max_events_per_sec 90; setprop view.touch_slop 8; " +
            // B. Nyalakan ulang Thermal System Software
                               "start mi_thermald; start thermal-engine; start thermalserviced; start joyose; setprop thermal.engine 1; " +
            // C. NYALAKAN ULANG PEMBATAS OTOMATIS KERNEL (Sangat penting agar HP aman saat dipakai harian)
                               "for limit in " + LIMIT_PATHS + "; do if [ -f \"$limit\" ]; then chmod 666 \"$limit\"; echo 1 > \"$limit\"; fi; done; " +
            // D. Kembalikan ke Fast Charge bawaan pabrik (6A = 6000000 microAmpere)
                               "for path in " + CHARGE_PATHS + "; do if [ -f \"$path\" ]; then chmod 666 \"$path\"; echo 6000000 > \"$path\"; fi; done;";
            exec(cmdNormal);
            
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "NORMAL 🌍 | FAST CHARGE ⚡", Toast.LENGTH_SHORT).show();
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
