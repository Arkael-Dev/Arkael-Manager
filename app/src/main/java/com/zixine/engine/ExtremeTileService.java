package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class ExtremeTileService extends TileService {

    private final String GMS_PACKS = "com.google.android.gms com.android.vending com.google.android.gsf";
    
    // ABSOLUTE SAFEGUARD (Mencakup SELURUH Merek Android & Komponen Vital Sistem)
    private final String SYSTEM_SAFEGUARD = 
            "com.zcqptx.dcwihze com.termux android com.android.systemui com.zixine.engine com.android.settings " +
            "com.google.android.webview com.google.android.packageinstaller com.google.android.permissioncontroller " +
            "com.miui.home com.miui.securitycenter com.miui.fsgs " +
            "com.transsion.XOSLauncher com.transsion.hilauncher " +
            "com.bbk.launcher2 com.vivo.launcher " +
            "com.oppo.launcher com.coloros.launcher com.oplus.launcher com.realme.launcher " +
            "com.sec.android.app.launcher com.samsung.android.honeyboard com.samsung.android.incallui " +
            "com.google.android.apps.nexuslauncher com.asus.launcher " +
            "com.motorola.launcher3 com.huawei.android.launcher com.hihonor.android.launcher com.nothing.launcher " +
            "launcher inputmethod keyboard dialer contacts clock messaging mms telecom telephony camera gallery photos " +
            "systemui settings webview permission installer bluetooth nfc wifi wlan network biometric fingerprint faceid faceunlock incallui gesture security battery power";
            
    // DAFTAR GAME MANUAL SUPER LENGKAP (Pisahkan dengan spasi)
    private final String GAMES = 
            // Free Fire (Biasa & Max)
            "com.dts.freefireth com.dts.freefiremax " +
            // PUBG (Global, BGMI, KR, VN, TW)
            "com.tencent.ig com.pubg.imobile com.pubg.krmobile com.vng.pubgmobile com.rekoo.pubgm " +
            // Mobile Legends
            "com.mobile.legends " +
            // Roblox
            "com.roblox.client " +
            // Genshin Impact & Honkai
            "com.miHoYo.GenshinImpact com.hoYoverse.hkrpg " +
            // Call of Duty Mobile (Global & Garena)
            "com.activision.callofduty.shooter com.garena.game.codm " +
            // Sausage Man
            "com.GlobalSoFunny.Sausage " +
            // TAMBAHAN GAME BARU (Wuthering Waves, Fortnite, Rainbow Six, Blood Strike)
            "com.kurogame.wutheringwaves com.epicgames.fortnite com.ubisoft.rainbowsixmobile com.netease.bloodstrike " +
            // Game Populer Lainnya
            "jp.konami.pesam com.riotgames.league.wildrift com.mojang.minecraftpe " +
            "com.supercell.clashofclans com.supercell.clashroyale com.ea.game.fifa14_row " +
            // JANGAN LUPA: Tambahkan spasi di akhir sebelum tanda kutip tutup kalau kamu mau masukin file teksmu di bawah
            "[ISI_DARI_GAMELIST_TXT_YANG_SANGAT_PANJANG]";

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
        Toast.makeText(getApplicationContext(), active ? "ZIXINE EXTREME: ON (PROTECTED GAMES)" : "ZIXINE EXTREME: OFF", Toast.LENGTH_SHORT).show();
        
        String ignoreRegex = (SYSTEM_SAFEGUARD + " " + GAMES).trim().replace(" ", "|");

        String cmd;
        if (active) {
            // MODE ON: Suspend aplikasi pihak ke-3 KECUALI yang ada di ignoreRegex
            cmd = "pm list packages -3 | cut -f 2 -d ':' | grep -vE '" + ignoreRegex + "' | xargs -n 1 pm suspend; " +
                  "for p in " + GMS_PACKS + "; do pm suspend $p; done; " +
                  "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0;";
        } else {
            // MODE OFF: Bangunkan (unsuspend) semuanya kembali
            cmd = "pm list packages -3 | cut -f 2 -d ':' | grep -vE '" + ignoreRegex + "' | xargs -n 1 pm unsuspend; " +
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
