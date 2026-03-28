package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import java.io.DataOutputStream;

public class PerfTileService extends TileService {
    
    private final String CHARGE_PATHS = "/sys/class/power_supply/battery/constant_charge_current " +
                                        "/sys/class/power_supply/battery/constant_charge_current_max " +
                                        "/sys/class/power_supply/battery/fcc_max " +
                                        "/sys/class/power_supply/main/constant_charge_current_max " +
                                        "/sys/class/power_supply/main/fcc_max " +
                                        "/sys/class/qcom-battery/restricted_current " +
                                        "/sys/class/power_supply/usb/pd_allowed";

    @Override
    public void onClick() {
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        if (active) {
            // A. Aim Lengket Extreme & GPU HWUI Rendering
            String cmdBoost = "setprop touch.pressure.scale 0.001; setprop touch.size.scale 0.001; setprop debug.touch.filter 0; " +
                              "setprop persist.sys.composition.type gpu; setprop debug.hwui.renderer opengl; setprop debug.cpurenderer false; " +
                              "settings put system pointer_speed 7; setprop windowsmgr.max_events_per_sec 1000; setprop view.touch_slop 2; " +
                              "settings put secure long_press_timeout 150; ";

            // B. Instan UI (Matikan Animasi) -> Bikin HP terasa sangat ngebut
            String cmdAnimOff = "settings put global window_animation_scale 0.0; " +
                                "settings put global transition_animation_scale 0.0; " +
                                "settings put global animator_duration_scale 0.0; ";

            // C. Anti-Screen Dimming & Anti-FPS Drop
            String cmdAntiDrop = "resetprop ro.vendor.display.framework_thermal_dimming false; " +
                                 "resetprop ro.vendor.fps.switch.thermal false; " +
                                 "resetprop ro.vendor.thermal.dimming.enable false; ";

            // D. Charging Limit Bypass ±18 Watt
            String cmdCharge = "for path in " + CHARGE_PATHS + "; do if [ -f \"$path\" ]; then chmod 666 \"$path\"; echo 3500000 > \"$path\"; fi; done; ";
            
            exec(cmdBoost + cmdAnimOff + cmdAntiDrop + cmdCharge);
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "PERF 🔥 | EXTREME AIM | 0 LAG", Toast.LENGTH_SHORT).show();
            
        } else {
            // Mengembalikan ke setelan pabrik
            String cmdNormal = "setprop touch.pressure.scale 1.0; setprop touch.size.scale 1.0; setprop debug.touch.filter 1; " +
                               "setprop persist.sys.composition.type c2d; setprop debug.hwui.renderer default; setprop debug.cpurenderer false; " +
                               "settings put system pointer_speed 3; setprop windowsmgr.max_events_per_sec 90; setprop view.touch_slop 8; " +
                               "settings put secure long_press_timeout 400; ";

            // Mengembalikan Animasi Sistem
            String cmdAnimOn = "settings put global window_animation_scale 1.0; " +
                               "settings put global transition_animation_scale 1.0; " +
                               "settings put global animator_duration_scale 1.0; ";

            String cmdAntiDropReset = "resetprop ro.vendor.display.framework_thermal_dimming true; " +
                                      "resetprop ro.vendor.fps.switch.thermal true; " +
                                      "resetprop ro.vendor.thermal.dimming.enable true; ";

            String cmdChargeReset = "for path in " + CHARGE_PATHS + "; do if [ -f \"$path\" ]; then chmod 666 \"$path\"; echo 6000000 > \"$path\"; fi; done; ";
            
            exec(cmdNormal + cmdAnimOn + cmdAntiDropReset + cmdChargeReset);
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "NORMAL 🌍 | ANIMATION ON", Toast.LENGTH_SHORT).show();
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
