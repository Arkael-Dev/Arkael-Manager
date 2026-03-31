package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class ExtremeTileService extends TileService {

    private final String GMS_PACKS = "com.google.android.gms com.android.vending com.google.android.gsf";
    // Whitelist penting agar sistem dan aplikasi ini tidak mati
    private final String WHITELIST = "com.zcqptx.dcwihze|com.termux|android|com.android.systemui|com.miui.home|com.zixine.engine|com.android.settings|com.miui.securitycenter";

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
            Toast.makeText(getApplicationContext(), "EXTREME: Akses Ditolak! Belum Verifikasi.", Toast.LENGTH_SHORT).show(); 
            return;
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        Toast.makeText(getApplicationContext(), active ? "ZIXINE EXTREME: ON (APPS SUSPENDED)" : "ZIXINE EXTREME: OFF (NORMAL)", Toast.LENGTH_SHORT).show();
        
        String cmd;
        if (active) {
            // MODE ON: Suspend aplikasi pihak ke-3 yang BUKAN game dan BUKAN di whitelist
            // Cara kerjanya:
            // 1. Ambil semua aplikasi pihak ke-3
            // 2. Cek apakah itu 'game'. Jika BUKAN, lanjutkan.
            // 3. Cek whitelist. Jika BUKAN whitelist, suspend!
            cmd = "for pkg in $(pm list packages -3 | cut -f 2 -d ':'); do " +
                  "  if ! dumpsys package $pkg | grep -q 'appCategory=game'; then " +
                  "    if ! echo $pkg | grep -qE '(" + WHITELIST + ")'; then " +
                  "      pm suspend $pkg; " +
                  "    fi; " +
                  "  fi; " +
                  "done; " +
                  "for p in " + GMS_PACKS + "; do pm suspend $p; done; " +
                  "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0;";
        } else {
            // MODE OFF: Unsuspend SEMUA aplikasi yang tersuspend
            cmd = "for pkg in $(pm list packages -3 | cut -f 2 -d ':'); do pm unsuspend $pkg; done; " +
                  "for p in " + GMS_PACKS + "; do pm unsuspend $p; done; " +
                  "settings put system min_refresh_rate 60.0; settings put system peak_refresh_rate 60.0;";
        }
        
        new Thread(() -> {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); } catch (Exception e) {}
        }).start();

        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
