package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import java.io.DataOutputStream;

public class PerfTileService extends TileService {
    
    // Daftar file pengatur arus (Ampere)
    private final String CHARGE_PATHS = "/sys/class/power_supply/battery/constant_charge_current " +
                                        "/sys/class/power_supply/battery/constant_charge_current_max " +
                                        "/sys/class/qcom-battery/restricted_current " +
                                        "/sys/class/power_supply/main/constant_charge_current_max " +
                                        "/sys/class/power_supply/usb/current_max";

    // Daftar file pembatas otomatis (Hardware Thermal)
    private final String LIMIT_PATHS = "/sys/class/power_supply/battery/step_charging_enabled " +
                                       "/sys/class/power_supply/battery/thermal_limit";

    // Daftar file True Bypass Charging (Idle Mode / Input Suspend)
    private final String BYPASS_PATHS = "/sys/class/power_supply/battery/input_suspend " +
                                        "/sys/class/qcom-battery/idle_mode";

    @Override
    public void onClick() {
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        if (active) {
            String cmdBoost = "setprop touch.pressure.scale 0.001; setprop persist.sys.composition.type gpu; setprop debug.cpurenderer true; " +
                              "settings put system pointer_speed 7; setprop windowsmgr.max_events_per_sec 300; setprop view.touch_slop 2; " +
                              "stop mi_thermald; stop thermal-engine; stop thermalserviced; stop joyose; setprop thermal.engine 0; " +
                              "for limit in " + LIMIT_PATHS + "; do if [ -f \"$limit\" ]; then chmod 666 \"$limit\"; echo 0 > \"$limit\"; fi; done; " +
                              // UBAH ARUS KE 3.5 AMPERE (17.5 WATT) BIAR KUAT NANGGUNG GAME BERAT
                              "for path in " + CHARGE_PATHS + "; do if [ -f \"$path\" ]; then chmod 666 \"$path\"; echo 3500000 > \"$path\"; fi; done; " +
                              // COBA AKTIFKAN TRUE BYPASS JIKA KERNEL MENDUKUNG
                              "for bypass in " + BYPASS_PATHS + "; do if [ -f \"$bypass\" ]; then chmod 666 \"$bypass\"; echo 1 > \"$bypass\"; fi; done;";
            exec(cmdBoost);
            
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "PERF 🔥 | BYPASS 17W 🔋", Toast.LENGTH_SHORT).show();
        } else {
            String cmdNormal = "setprop touch.pressure.scale 1.0; setprop persist.sys.composition.type c2d; setprop debug.cpurenderer false; " +
                               "settings put system pointer_speed 3; setprop windowsmgr.max_events_per_sec 90; setprop view.touch_slop 8; " +
                               "start mi_thermald; start thermal-engine; start thermalserviced; start joyose; setprop thermal.engine 1; " +
                               "for limit in " + LIMIT_PATHS + "; do if [ -f \"$limit\" ]; then chmod 666 \"$limit\"; echo 1 > \"$limit\"; fi; done; " +
                               // KEMBALIKAN KE FAST CHARGE BAWAAN (6 AMPERE)
                               "for path in " + CHARGE_PATHS + "; do if [ -f \"$path\" ]; then chmod 666 \"$path\"; echo 6000000 > \"$path\"; fi; done; " +
                               // MATIKAN TRUE BYPASS
                               "for bypass in " + BYPASS_PATHS + "; do if [ -f \"$bypass\" ]; then chmod 666 \"$bypass\"; echo 0 > \"$bypass\"; fi; done;";
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
