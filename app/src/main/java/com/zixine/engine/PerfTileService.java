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
            // A. Aim Lengket & UI Responsif
            String cmdBoost = "setprop touch.pressure.scale 0.001; setprop touch.size.scale 0.001; setprop debug.touch.filter 0; " +
                              "setprop persist.sys.composition.type gpu; setprop debug.hwui.renderer opengl; setprop debug.cpurenderer false; " +
                              "settings put system pointer_speed 7; setprop windowsmgr.max_events_per_sec 1000; setprop view.touch_slop 2; " +
                              "settings put secure long_press_timeout 150; ";

            // B. Instan UI (Animasi 0)
            String cmdAnimOff = "settings put global window_animation_scale 0.0; " +
                                "settings put global transition_animation_scale 0.0; " +
                                "settings put global animator_duration_scale 0.0; ";

            // C. Anti-Screen Dimming
            String cmdAntiDrop = "resetprop ro.vendor.display.framework_thermal_dimming false; " +
                                 "resetprop ro.vendor.fps.switch.thermal false; " +
                                 "resetprop ro.vendor.thermal.dimming.enable false; ";

            // D. Bypass Charging 17.5W
            String cmdCharge = "for path in " + CHARGE_PATHS + "; do if [ -f \"$path\" ]; then chmod 666 \"$path\"; echo 3500000 > \"$path\"; fi; done; ";
            
            // E. Network (Ping Booster) & RAM Tweaks
            String cmdNetRam = "sysctl -w net.ipv4.tcp_congestion_control=bbr; " +
                               "echo 3 > /proc/sys/vm/drop_caches; echo 0 > /proc/sys/vm/swappiness; ";

            // F. I/O Storage Read-Ahead 4096KB
            String cmdIO = "for q in /sys/block/*/queue/read_ahead_kb; do echo 4096 > \"$q\"; done; ";

            // G. Audio Latency Tweak (Footstep Cepat)
            String cmdAudio = "resetprop audio.deep_buffer.media false; resetprop af.fast_track_multiplier 1; ";

            // H. Mode Turnamen (DND Aktif, FSTRIM) -> Tanpa mengubah kecerahan layar
            String cmdExtreme = "settings put global zen_mode 2; " + 
                                "fstrim -v /data; fstrim -v /cache; ";
            
            exec(cmdBoost + cmdAnimOff + cmdAntiDrop + cmdCharge + cmdNetRam + cmdIO + cmdAudio + cmdExtreme);
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "GOD MODE 🔥 | ALL TWEAKS INJECTED", Toast.LENGTH_SHORT).show();
            
        } else {
            // Restore A & B (Normal UI & Aim)
            String cmdNormal = "setprop touch.pressure.scale 1.0; setprop touch.size.scale 1.0; setprop debug.touch.filter 1; " +
                               "setprop persist.sys.composition.type c2d; setprop debug.hwui.renderer default; setprop debug.cpurenderer false; " +
                               "settings put system pointer_speed 3; setprop windowsmgr.max_events_per_sec 90; setprop view.touch_slop 8; " +
                               "settings put secure long_press_timeout 400; " +
                               "settings put global window_animation_scale 1.0; " +
                               "settings put global transition_animation_scale 1.0; " +
                               "settings put global animator_duration_scale 1.0; ";

            // Restore C & D (Redup Layar & Fast Charge)
            String cmdRestore2 = "resetprop ro.vendor.display.framework_thermal_dimming true; " +
                                 "resetprop ro.vendor.fps.switch.thermal true; " +
                                 "resetprop ro.vendor.thermal.dimming.enable true; " +
                                 "for path in " + CHARGE_PATHS + "; do if [ -f \"$path\" ]; then chmod 666 \"$path\"; echo 6000000 > \"$path\"; fi; done; ";
            
            // Restore E & F (Network, RAM, IO)
            String cmdRestore3 = "sysctl -w net.ipv4.tcp_congestion_control=cubic; echo 100 > /proc/sys/vm/swappiness; " +
                                 "for q in /sys/block/*/queue/read_ahead_kb; do echo 128 > \"$q\"; done; ";

            // Restore G (Audio Normal)
            String cmdAudioRestore = "resetprop audio.deep_buffer.media true; resetprop af.fast_track_multiplier 2; ";

            // Restore H (Matikan DND)
            String cmdExtremeRestore = "settings put global zen_mode 0; ";
            
            exec(cmdNormal + cmdRestore2 + cmdRestore3 + cmdAudioRestore + cmdExtremeRestore);
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "NORMAL 🌍 | TWEAKS REMOVED", Toast.LENGTH_SHORT).show();
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
