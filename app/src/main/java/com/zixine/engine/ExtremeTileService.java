package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class ExtremeTileService extends TileService {

    private final String GMS_PACKS = "com.google.android.gms com.android.vending com.google.android.gsf";
    
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
            
    private final String GAMES = 
            "com.dts.freefireth com.dts.freefiremax " +
            "com.tencent.ig com.pubg.imobile com.pubg.krmobile com.vng.pubgmobile com.rekoo.pubgm " +
            "com.mobile.legends com.roblox.client com.miHoYo.GenshinImpact com.hoYoverse.hkrpg " +
            "com.activision.callofduty.shooter com.garena.game.codm com.GlobalSoFunny.Sausage " +
            "com.kurogame.wutheringwaves com.epicgames.fortnite com.ubisoft.rainbowsixmobile com.netease.bloodstrike " +
            "jp.konami.pesam com.riotgames.league.wildrift com.mojang.minecraftpe " +
            "com.supercell.clashofclans com.supercell.clashroyale com.ea.game.fifa14_row " +
            "[ISI_DARI_GAMELIST_TXT_YANG_SANGAT_PANJANG_PASTE_DISINI_JANGAN_LUPA_SPASI_SEBELUMNYA]";

    @Override
    public void onClick() {
        if (!SecurityUtils.isSystemVerified(getApplicationContext())) {
            Toast.makeText(getApplicationContext(), "AKSES DITOLAK! Kernel/Passkey Invalid.", Toast.LENGTH_SHORT).show(); 
            Tile t = getQsTile(); t.setState(Tile.STATE_INACTIVE); t.updateTile(); return; 
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        Toast.makeText(getApplicationContext(), active ? "ZIXINE EXTREME: ON (PROTECTED GAMES)" : "ZIXINE EXTREME: OFF", Toast.LENGTH_SHORT).show();
        
        String ignoreRegex = (SYSTEM_SAFEGUARD + " " + GAMES).trim().replace(" ", "|");

        String cmd;
        if (active) {
            cmd = "pm list packages -3 | cut -f 2 -d ':' | grep -vE '" + ignoreRegex + "' | xargs -n 1 pm suspend; " +
                  "for p in " + GMS_PACKS + "; do pm suspend $p; done; " +
                  "settings put system min_refresh_rate 120.0; settings put system peak_refresh_rate 120.0;";
        } else {
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
