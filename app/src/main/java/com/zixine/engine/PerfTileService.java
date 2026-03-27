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

    private final String LIMIT_PATHS = "/sys/class/power_supply/battery/step_charging_enabled " +
                                       "/sys/class/power_supply/battery/thermal_limit";

    private final String THERMAL_SERVICES = "logd android.thermal-hal vendor.thermal-engine vendor.thermal_manager vendor.thermal-manager vendor.thermal-hal-2-0 vendor.thermal-symlinks thermal_mnt_hal_service thermal mi_thermald thermald thermalloadalgod thermalservice sec-thermal-1-0 debug_pid.sec-thermal-1-0 thermal-engine vendor.thermal-hal-1-0 vendor-thermal-1-0 thermal-hal joyose";

    @Override
    public void onClick() {
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        if (active) {
            // A. GPU Boost & Aim Lengket
            String cmdBoost = "setprop touch.pressure.scale 0.001; setprop persist.sys.composition.type gpu; setprop debug.cpurenderer true; " +
                              "settings put system pointer_speed 7; setprop windowsmgr.max_events_per_sec 300; setprop view.touch_slop 2; ";

            // B. Anti-Screen Dimming & Anti-FPS Drop (Berkat temuan dari Termux!)
            String cmdAntiDrop = "resetprop ro.vendor.display.framework_thermal_dimming false; " +
                                 "resetprop ro.vendor.fps.switch.thermal false; " +
                                 "resetprop ro.vendor.thermal.dimming.enable false; ";

            // C. Thermal Disabler
            String cmdThermalOff = "echo 0 > /proc/sys/kernel/sched_boost; echo N > /sys/module/msm_thermal/parameters/enabled; " +
                                   "echo 0 > /sys/module/msm_thermal/core_control/enabled; echo 0 > /sys/kernel/msm_thermal/enabled; " +
                                   "for s in " + THERMAL_SERVICES + "; do stop $s; setprop init.svc.$s stopped; done; ";

            // D. Charging Limit Bypass & Arus ±18 Watt
            String cmdCharge = "for limit in " + LIMIT_PATHS + "; do if [ -f \"$limit\" ]; then chmod 666 \"$limit\"; echo 0 > \"$limit\"; fi; done; " +
                               "for path in " + CHARGE_PATHS + "; do if [ -f \"$path\" ]; then chmod 666 \"$path\"; echo 3500000 > \"$path\"; fi; done; ";
            
            exec(cmdBoost + cmdAntiDrop + cmdThermalOff + cmdCharge);
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "PERF 🔥 | FPS LOCK | CHARGE BOOST", Toast.LENGTH_SHORT).show();
            
        } else {
            String cmdNormal = "setprop touch.pressure.scale 1.0; setprop persist.sys.composition.type c2d; setprop debug.cpurenderer false; " +
                               "settings put system pointer_speed 3; setprop windowsmgr.max_events_per_sec 90; setprop view.touch_slop 8; ";

            String cmdAntiDropReset = "resetprop ro.vendor.display.framework_thermal_dimming true; " +
                                      "resetprop ro.vendor.fps.switch.thermal true; " +
                                      "resetprop ro.vendor.thermal.dimming.enable true; ";

            String cmdThermalOn = "echo 1 > /proc/sys/kernel/sched_boost; echo Y > /sys/module/msm_thermal/parameters/enabled; " +
                                  "echo 1 > /sys/module/msm_thermal/core_control/enabled; echo 1 > /sys/kernel/msm_thermal/enabled; " +
                                  "for s in " + THERMAL_SERVICES + "; do start $s; done; ";

            String cmdChargeReset = "for limit in " + LIMIT_PATHS + "; do if [ -f \"$limit\" ]; then chmod 666 \"$limit\"; echo 1 > \"$limit\"; fi; done; " +
                                    "for path in " + CHARGE_PATHS + "; do if [ -f \"$path\" ]; then chmod 666 \"$path\"; echo 6000000 > \"$path\"; fi; done; ";
            
            exec(cmdNormal + cmdAntiDropReset + cmdThermalOn + cmdChargeReset);
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
