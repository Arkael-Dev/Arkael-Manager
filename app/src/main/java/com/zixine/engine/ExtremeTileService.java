package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class ExtremeTileService extends TileService {

    private final String GMS_PACKS = "com.google.android.gms com.android.vending com.google.android.gsf";
    private final String GAMES = "com.dts.freefireth com.dts.freefiremax com.mobile.legends com.tencent.ig com.pubg.imobile com.miHoYo.GenshinImpact com.hoYoverse.hkrpg";
    private final String WHITELIST = "com.zcqptx.dcwihze com.termux android com.android.systemui com.miui.home com.zixine.engine com.android.settings com.miui.securitycenter";

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE).edit().putBoolean("extreme_added", true).apply();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE).edit().putBoolean("extreme_added", false).apply();
    }

    @Override
    public void onClick() {
        SharedPreferences p = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        boolean isVerified = System.getProperty("os.version").toLowerCase().contains("zixine") || p.getBoolean("isBypassed", false);

        if (!isVerified) {
            Toast.makeText(getApplicationContext(), "EXTREME: Belum Verifikasi!", Toast.LENGTH_SHORT).show(); 
            return;
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        Toast.makeText(getApplicationContext(), active ? "EXTREME: AKTIF (ZRAM OFF)" : "EXTREME: NORMAL (ZRAM ON)", Toast.LENGTH_SHORT).show();
        
        String ignoreRegex = (WHITELIST + " " + GAMES).trim().replace(" ", "|");

        String cmd;
        if (active) {
            cmd = "pm list packages -3 | cut -f 2 -d ':' | grep -vE '" + ignoreRegex + "' | xargs -n 1 pm suspend; " +
                  "for p in " + GMS_PACKS + "; do pm suspend $p; done; " +
                  "swapoff -a; settings put system min_refresh_rate 120.0;";
        } else {
            // KEMBALIKAN: Unsuspend semua, nyalakan SWAP, dan kembalikan Hz ke 60 via 'put'
            cmd = "pm list packages -3 | cut -f 2 -d ':' | grep -vE '" + ignoreRegex + "' | xargs -n 1 pm unsuspend; " +
                  "for p in " + GMS_PACKS + "; do pm unsuspend $p; done; " +
                  "swapon -a; settings put system min_refresh_rate 60.0;";
        }
        
        new Thread(() -> {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); } catch (Exception e) {}
        }).start();

        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
