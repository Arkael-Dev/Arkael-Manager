package com.zixine.engine;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class ExtremeTileService extends TileService {
    private String GAMES = "com.dts.freefireth com.dts.freefiremax com.mobile.legends com.tencent.ig com.pubg.imobile com.miHoYo.GenshinImpact com.hoYoverse.hkrpg";
    private String WHITELIST = "com.zcqptx.dcwihze com.termux android com.android.systemui com.miui.home com.zixine.engine com.android.settings";

    @Override
    public void onClick() {
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        if (active) {
            // HANYA SCAN APLIKASI PIHAK KETIGA (-3)
            String cmd = "PKGS=$(pm list packages -3 | cut -d ':' -f2); for p in $PKGS; do " +
                         "MATCH=false; for w in " + WHITELIST + " " + GAMES + "; do [ \"$p\" == \"$w\" ] && MATCH=true && break; done; " +
                         "[ \"$MATCH\" == \"false\" ] && pm suspend --user 0 $p; " +
                         "done;";
            exec(cmd);
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "EXTREME: ON 🛡️", 0).show();
        } else {
            // UNSUSPEND SEMUA
            exec("PKGS=$(pm list packages -u | cut -d ':' -f2); for p in $PKGS; do pm unsuspend --user 0 $p & done;");
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "EXTREME: OFF 🌍", 0).show();
        }
        t.updateTile();
    }
    private void exec(String c) { try { java.lang.Process p = Runtime.getRuntime().exec("su"); java.io.DataOutputStream o = new java.io.DataOutputStream(p.getOutputStream()); o.writeBytes(c + "\nexit\n"); o.flush(); } catch (Exception e) {} }
}