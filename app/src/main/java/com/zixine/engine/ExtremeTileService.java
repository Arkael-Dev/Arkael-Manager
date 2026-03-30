package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class ExtremeTileService extends TileService {

    private final String BLACKLIST = "com.facebook.katana com.facebook.orca com.instagram.android com.ss.android.ugc.trill com.zhiliaoapp.musically com.whatsapp com.whatsapp.w4b com.twitter.android com.shopee.id com.tokopedia.tkpd com.lazada.android com.google.android.youtube com.google.android.apps.docs com.google.android.apps.photos com.google.android.gm com.netflix.mediaclient com.spotify.music";
    private final String GMS_PACKS = "com.google.android.gms com.android.vending com.google.android.gsf";
    // WHITELIST & GAMES dipertahankan di memori untuk referensi filter masa depan jika dibutuhkan
    private final String GAMES = "com.dts.freefireth com.dts.freefiremax com.mobile.legends com.tencent.ig com.pubg.imobile com.miHoYo.GenshinImpact com.hoYoverse.hkrpg";
    private final String WHITELIST = "com.zcqptx.dcwihze com.termux android com.android.systemui com.miui.home com.zixine.engine com.android.settings com.miui.securitycenter";

    @Override
    public void onClick() {
        SharedPreferences p = getSharedPreferences("ZixinePrefs", Context.MODE_PRIVATE);
        boolean isZixine = System.getProperty("os.version").toLowerCase().contains("zixine");
        boolean isBypassed = p.getBoolean("isBypassed", false);

        if (!isZixine && !isBypassed) {
            Toast.makeText(this, "ACCESS LOCKED!", Toast.LENGTH_SHORT).show(); return;
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        // Logika: Suspend semua BLACKLIST dan GMS, lalu matikan ZRAM
        String cmd = active ? 
            "for app in " + BLACKLIST + " " + GMS_PACKS + "; do pm suspend $app; done; swapoff -a; settings put system min_refresh_rate 120.0;" : 
            "for app in " + BLACKLIST + " " + GMS_PACKS + "; do pm unsuspend $app; done; swapon -a; settings put system min_refresh_rate 60.0;";
        
        new Thread(() -> {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); } catch (Exception ignored) {}
        }).start();

        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
