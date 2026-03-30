package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class ExtremeTileService extends TileService {

    private final String GMS_PACKS = "com.google.android.gms com.android.vending com.google.android.gsf";
    
    // Semua yang ada di daftar ini TIDAK AKAN di-suspend
    private final String GAMES = "com.dts.freefireth com.dts.freefiremax com.mobile.legends com.tencent.ig com.pubg.imobile com.miHoYo.GenshinImpact com.hoYoverse.hkrpg";
    private final String WHITELIST = "com.zcqptx.dcwihze com.termux android com.android.systemui com.miui.home com.zixine.engine com.android.settings com.miui.securitycenter";

    @Override
    public void onClick() {
        SharedPreferences p = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        String kernelInfo = System.getProperty("os.version").toLowerCase();
        boolean isZixine = kernelInfo.contains("zixine");
        boolean isBypassed = p.getBoolean("isBypassed", false);

        if (!isZixine && !isBypassed) {
            Toast.makeText(this, "ZIXINE: ACCESS LOCKED!", Toast.LENGTH_SHORT).show(); 
            return;
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        // Menggabungkan whitelist dan games, dan mengubah spasinya menjadi format regex "|"
        // Contoh: com.termux|com.mobile.legends
        String ignoreRegex = (WHITELIST + " " + GAMES).trim().replace(" ", "|");

        String cmd;
        if (active) {
            // ON: Suspend semua aplikasi pihak ke-3 KECUALI yang di whitelist, suspend GMS, matikan zram
            cmd = "pm list packages -3 | cut -f 2 -d ':' | grep -vE '" + ignoreRegex + "' | xargs -n 1 pm suspend; " +
                  "for p in " + GMS_PACKS + "; do pm suspend $p; done; " +
                  "swapoff -a; settings put system min_refresh_rate 120.0;";
        } else {
            // OFF: Unsuspend semua aplikasi pihak ke-3, unsuspend GMS, nyalakan zram
            cmd = "pm list packages -3 | cut -f 2 -d ':' | grep -vE '" + ignoreRegex + "' | xargs -n 1 pm unsuspend; " +
                  "for p in " + GMS_PACKS + "; do pm unsuspend $p; done; " +
                  "swapon -a; settings put system min_refresh_rate 60.0;";
        }
        
        new Thread(() -> {
            try { 
                Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); 
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
