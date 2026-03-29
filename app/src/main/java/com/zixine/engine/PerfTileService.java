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
            // A. Aim & Touch Fix (Responsif 1000Hz, Anti-Palm Rejection error)
            String cmdBoost = "setprop debug.touch.filter 0; " +
                              "setprop persist.sys.composition.type gpu; setprop debug.hwui.renderer opengl; setprop debug.cpurenderer false; " +
                              "settings put system pointer_speed 7; setprop windowsmgr.max_events_per_sec 1000; setprop view.touch_slop 2; ";

            // B. Instan UI (Animasi 0)
            String cmdAnimOff = "settings put global window_animation_scale 0.0; " +
                                "settings put global transition_animation_scale 0.0; " +
                                "settings put global animator_duration_scale 0.0; ";

            // C. Anti-Screen Dimming
            String cmdAntiDrop = "resetprop ro.vendor.display.framework_thermal_dimming false; " +
                                 "resetprop ro.vendor.fps.switch.thermal false; " +
                                 "resetprop ro.vendor.thermal.dimming.enable false; ";
            
            // D. Network (Ping Booster) & RAM Tweaks
            String cmdNetRam = "sysctl -w net.ipv4.tcp_congestion_control=bbr; " +
                               "echo 3 > /proc/sys/vm/drop_caches; echo 0 > /proc/sys/vm/swappiness; ";

            // E. I/O Storage Read-Ahead 4096KB & Audio Latency
            String cmdIO = "for q in /sys/block/*/queue/read_ahead_kb; do echo 4096 > \"$q\"; done; " +
                           "resetprop audio.deep_buffer.media false; resetprop af.fast_track_multiplier 1; ";

            // F. Mode Turnamen (DND Jalur Belakang, Bius Trio Macan, FSTRIM)
            String cmdExtreme = "cmd notification set_dnd on; " +
                                "killall -STOP joyose; killall -STOP powerkeeper; killall -STOP mi_thermald; " + 
                                "fstrim -v /data; fstrim -v /cache; ";
            
            exec(cmdBoost + cmdAnimOff + cmdAntiDrop + cmdNetRam + cmdIO + cmdExtreme);
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "PERF 🔥 | DND ON | JOYOSE DISABLED", Toast.LENGTH_SHORT).show();
            
        } else {
            // Restore A & B (Normal UI & Aim)
            String cmdNormal = "setprop debug.touch.filter 1; " +
                               "setprop persist.sys.composition.type c2d; setprop debug.hwui.renderer default; setprop debug.cpurenderer false; " +
                               "settings put system pointer_speed 3; setprop windowsmgr.max_events_per_sec 90; setprop view.touch_slop 8; " +
                               "settings put global window_animation_scale 1.0; " +
                               "settings put global transition_animation_scale 1.0; " +
                               "settings put global animator_duration_scale 1.0; ";

            // Restore C (Redup Layar Aktif Lagi)
            String cmdRestore2 = "resetprop ro.vendor.display.framework_thermal_dimming true; " +
                                 "resetprop ro.vendor.fps.switch.thermal true; " +
                                 "resetprop ro.vendor.thermal.dimming.enable true; ";
            
            // Restore D & E (Network, RAM, IO, Audio)
            String cmdRestore3 = "sysctl -w net.ipv4.tcp_congestion_control=cubic; echo 100 > /proc/sys/vm/swappiness; " +
                                 "for q in /sys/block/*/queue/read_ahead_kb; do echo 128 > \"$q\"; done; " +
                                 "resetprop audio.deep_buffer.media true; resetprop af.fast_track_multiplier 2; ";

            // Restore F (Matikan DND, Bangunkan Trio Macan)
            String cmdExtremeRestore = "cmd notification set_dnd off; " +
                                       "killall -CONT joyose; killall -CONT powerkeeper; killall -CONT mi_thermald; ";
            
            exec(cmdNormal + cmdRestore2 + cmdRestore3 + cmdExtremeRestore);
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "NORMAL 🌍 | SYSTEM RESTORED", Toast.LENGTH_SHORT).show();
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
