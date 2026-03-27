package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import java.io.DataOutputStream;

public class ExtremeTileService extends TileService {
    private final String BLACKLIST = "com.facebook.katana com.facebook.orca com.instagram.android com.ss.android.ugc.trill com.zhiliaoapp.musically com.whatsapp com.whatsapp.w4b com.twitter.android com.shopee.id com.tokopedia.tkpd com.lazada.android com.google.android.youtube com.google.android.apps.docs com.google.android.apps.photos com.google.android.gm com.netflix.mediaclient com.spotify.music";
    private final String GAMES = "com.dts.freefireth com.dts.freefiremax com.mobile.legends com.tencent.ig com.pubg.imobile com.miHoYo.GenshinImpact com.hoYoverse.hkrpg";
    private final String WHITELIST = "com.zcqptx.dcwihze com.termux android com.android.systemui com.miui.home com.zixine.engine com.android.settings com.miui.securitycenter";

    @Override
    public void onClick() {
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        if (active) {
            String cmd = "for p in " + BLACKLIST + "; do pm suspend --user 0 $p; done; " +
                         "PKGS=$(pm list packages -3 | cut -d ':' -f2); for p in $PKGS; do " +
                         "MATCH=false; for w in " + WHITELIST + " " + GAMES + " " + BLACKLIST + "; do [ \"$p\" == \"$w\" ] && MATCH=true && break; done; " +
                         "[ \"$MATCH\" == \"false\" ] && pm suspend --user 0 $p; done;";
            exec(cmd);
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "EXTREME: ON 🛡️", Toast.LENGTH_SHORT).show();
        } else {
            exec("PKGS=$(pm list packages -u | cut -d ':' -f2); for p in $PKGS; do pm unsuspend --user 0 $p & done;");
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "EXTREME: OFF 🌍", Toast.LENGTH_SHORT).show();
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