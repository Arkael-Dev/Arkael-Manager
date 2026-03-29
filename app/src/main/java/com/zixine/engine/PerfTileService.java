package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class PerfTileService extends TileService {

    @Override
    public void onClick() {
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        if (active) {
            // A. TOUCH & RENDER DEWA (Responsif 1000Hz, HW Accel, Lock 120Hz)
            String cmdBoost = "setprop touch.pressure.scale 0.001; setprop touch.size.scale 0.001; " +
                              "setprop debug.touch.filter 0; resetprop ro.min.fling_velocity 8000; resetprop ro.max.fling_velocity 12000; " +
                              "settings put system pointer_speed 7; setprop windowsmgr.max_events_per_sec 1000; setprop view.touch_slop 2; " +
                              "setprop persist.sys.composition.type gpu; setprop debug.hwui.renderer opengl; setprop debug.cpurenderer false; " +
                              "setprop debug.sf.hw 1; setprop debug.egl.hw 1; setprop video.accelerate.hw 1; " +
                              "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0; ";

            // B. INSTAN UI (Hapus Semua Animasi)
            String cmdAnimOff = "settings put global window_animation_scale 0.0; " +
                                "settings put global transition_animation_scale 0.0; " +
                                "settings put global animator_duration_scale 0.0; ";

            // C. ANTI-SCREEN DIMMING (Bunuh Pemantau Layar & HWC)
            String cmdAntiDrop = "resetprop ro.vendor.display.framework_thermal_dimming false; " +
                                 "resetprop ro.vendor.display.hwc_thermal_dimming false; " +
                                 "resetprop ro.vendor.fps.switch.thermal false; " +
                                 "resetprop ro.vendor.thermal.dimming.enable false; ";
            
            // D. NETWORK & RAW MEMORY (BBR Ping Booster, Hapus Cache, MATIKAN ZRAM)
            String cmdNetRam = "sysctl -w net.ipv4.tcp_congestion_control=bbr; " +
                               "echo 3 > /proc/sys/vm/drop_caches; echo 0 > /proc/sys/vm/swappiness; " +
                               "swapoff -a; "; // <== ZRAM OFF! Performa Murni 100%

            // E. I/O STORAGE & AUDIO LATENCY (Footstep Instan & Fast Read)
            String cmdIO = "for q in /sys/block/*/queue/read_ahead_kb; do echo 4096 > \"$q\"; done; " +
                           "resetprop audio.deep_buffer.media false; resetprop af.fast_track_multiplier 1; ";

            // F. MODE TURNAMEN (Bypass DND, Bius Thermal QCOM, FSTRIM Storage)
            String cmdExtreme = "cmd notification set_dnd on; " +
                                "killall -STOP thermald; killall -STOP thermal-engine; " + 
                                "fstrim -v /data; fstrim -v /cache; ";
            
            exec(cmdBoost + cmdAnimOff + cmdAntiDrop + cmdNetRam + cmdIO + cmdExtreme);
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "GOD MODE 🔥 | ZRAM OFF | LIMITS DESTROYED", Toast.LENGTH_SHORT).show();
            
        } else {
            // Restore A (Kembalikan Touch, 60Hz-120Hz Auto, UI Normal)
            String cmdNormal = "setprop touch.pressure.scale 1; setprop touch.size.scale 1; " +
                               "setprop debug.touch.filter 1; resetprop ro.min.fling_velocity 50; resetprop ro.max.fling_velocity 8000; " +
                               "settings put system pointer_speed 3; setprop windowsmgr.max_events_per_sec 90; setprop view.touch_slop 8; " +
                               "setprop persist.sys.composition.type c2d; setprop debug.hwui.renderer default; setprop debug.cpurenderer false; " +
                               "setprop debug.sf.hw 0; setprop debug.egl.hw 0; setprop video.accelerate.hw 0; " +
                               "settings put system min_refresh_rate 60.0; settings put system peak_refresh_rate 120.0; " +
                               "settings put global window_animation_scale 1.0; " +
                               "settings put global transition_animation_scale 1.0; " +
                               "settings put global animator_duration_scale 1.0; ";

            // Restore C (Aktifkan Lagi Sensor Redup Layar)
            String cmdRestore2 = "resetprop ro.vendor.display.framework_thermal_dimming true; " +
                                 "resetprop ro.vendor.display.hwc_thermal_dimming true; " +
                                 "resetprop ro.vendor.fps.switch.thermal true; " +
                                 "resetprop ro.vendor.thermal.dimming.enable true; ";
            
            // Restore D & E (Nyalakan ZRAM, Kembalikan I/O Audio, Cubic Ping)
            String cmdRestore3 = "sysctl -w net.ipv4.tcp_congestion_control=cubic; echo 100 > /proc/sys/vm/swappiness; " +
                                 "swapon -a; " + // <== ZRAM ON! (Biar Harian Aman)
                                 "for q in /sys/block/*/queue/read_ahead_kb; do echo 128 > \"$q\"; done; " +
                                 "resetprop audio.deep_buffer.media true; resetprop af.fast_track_multiplier 2; ";

            // Restore F (Matikan DND, Bangunkan Pemantau Thermal)
            String cmdExtremeRestore = "cmd notification set_dnd off; " +
                                       "killall -CONT thermald; killall -CONT thermal-engine; ";
            
            exec(cmdNormal + cmdRestore2 + cmdRestore3 + cmdExtremeRestore);
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "NORMAL 🌍 | SYSTEM RESTORED", Toast.LENGTH_SHORT).show();
        }
        t.updateTile();
    }

    // Eksekusi KSU-Friendly & Stabil
    private void exec(String c) { 
        try { 
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", c});
            p.waitFor();
        } catch (Exception ignored) {} 
    }
}
