package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import java.io.DataOutputStream;

public class PerfTileService extends TileService {
    
    // Daftar file pengatur arus (Ditambah node FCC khusus Snapdragon/POCO)
    private final String CHARGE_PATHS = "/sys/class/power_supply/battery/constant_charge_current " +
                                        "/sys/class/power_supply/battery/constant_charge_current_max " +
                                        "/sys/class/power_supply/battery/fcc_max " +
                                        "/sys/class/power_supply/main/constant_charge_current_max " +
                                        "/sys/class/power_supply/main/fcc_max " +
                                        "/sys/class/qcom-battery/restricted_current " +
                                        "/sys/class/power_supply/usb/pd_allowed";

    private final String LIMIT_PATHS = "/sys/class/power_supply/battery/step_charging_enabled " +
                                       "/sys/class/power_supply/battery/thermal_limit";

    // Daftar service thermal (Tanpa membunuh sensor fisik thermal_zone agar IC tidak Failsafe)
    private final String THERMAL_SERVICES = "logd android.thermal-hal vendor.thermal-engine vendor.thermal_manager vendor.thermal-manager vendor.thermal-hal-2-0 vendor.thermal-symlinks thermal_mnt_hal_service thermal mi_thermald thermald thermalloadalgod thermalservice sec-thermal-1-0 debug_pid.sec-thermal-1-0 thermal-engine vendor.thermal-hal-1-0 vendor-thermal-1-0 thermal-hal joyose";

    @Override
    public void onClick() {
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        if (active) {
            String cmdBoost = "setprop touch.pressure.scale 0.001; setprop persist.sys.composition.type gpu; setprop debug.cpurenderer true; " +
                              "settings put system pointer_speed 7; setprop windowsmgr.max_events_per_sec 300; setprop view.touch_slop 2; ";

            // Thermal Disabler yang disempurnakan (Aman dari Failsafe)
            String cmdThermalOff = "echo 0 > /proc/sys/kernel/sched_boost; echo N > /sys/module/msm_thermal/parameters/enabled; " +
                                   "echo 0 > /sys/module/msm_thermal/core_control/enabled; echo 0 > /sys/kernel/msm_thermal/enabled; " +
                                   "for s in " + THERMAL_SERVICES + "; do stop $s; setprop init.svc.$s stopped; done; ";

            // Charging Limit Bypass & Arus ±18 Watt (3.5A ~ 4A)
            String cmdCharge = "for limit in " + LIMIT_PATHS + "; do if [ -f \"$limit\" ]; then chmod 666 \"$limit\"; echo 0 > \"$limit\"; fi; done; " +
                               "for path in " + CHARGE_PATHS + "; do if [ -f \"$path\" ]; then chmod 666 \"$path\"; echo 3500000 > \"$path\"; fi; done; ";
            
            exec(cmdBoost + cmdThermalOff + cmdCharge);
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "PERF 🔥 | THERMAL OFF | CHARGE BOOST", Toast.LENGTH_SHORT).show();
            
        } else {
            String cmdNormal = "setprop touch.pressure.scale 1.0; setprop persist.sys.composition.type c2d; setprop debug.cpurenderer false; " +
                               "settings put system pointer_speed 3; setprop windowsmgr.max_events_per_sec 90; setprop view.touch_slop 8; ";

            String cmdThermalOn = "echo 1 > /proc/sys/kernel/sched_boost; echo Y > /sys/module/msm_thermal/parameters/enabled; " +
                                  "echo 1 > /sys/module/msm_thermal/core_control/enabled; echo 1 > /sys/kernel/msm_thermal/enabled; " +
                                  "for s in " + THERMAL_SERVICES + "; do start $s; done; ";

            String cmdChargeReset = "for limit in " + LIMIT_PATHS + "; do if [ -f \"$limit\" ]; then chmod 666 \"$limit\"; echo 1 > \"$limit\"; fi; done; " +
                                    "for path in " + CHARGE_PATHS + "; do if [ -f \"$path\" ]; then chmod 666 \"$path\"; echo 6000000 > \"$path\"; fi; done; ";
            
            exec(cmdNormal + cmdThermalOn + cmdChargeReset);
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "NORMAL 🌍 | THERMAL ON | FAST CHARGE", Toast.LENGTH_SHORT).show();
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
