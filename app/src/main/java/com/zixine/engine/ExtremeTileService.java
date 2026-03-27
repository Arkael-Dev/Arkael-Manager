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
            String cmd = "PKGS=$(pm list packages -e | cut -d ':' -f2); for p in $PKGS; do " +
                         "MATCH=false; for w in " + WHITELIST + " " + GAMES + "; do [ \"$p\" == \"$w\" ] && MATCH=true && break; done; " +
                         "[ \"$MATCH\" == \"false\" ] && pm disable-user --user 0 $p && am force-stop $p; " +
                         "done; pm disable com.miui.powerkeeper/.statemachine.PowerStateMachineService;";
            exec(cmd);
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "EXTREME: SEALED 🛡️", 0).show();
        } else {
            exec("PKGS=$(pm list packages -d | cut -d ':' -f2); for p in $PKGS; do pm enable $p & done; pm enable com.miui.powerkeeper/.statemachine.PowerStateMachineService;");
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "EXTREME: NORMAL 🌍", 0).show();
        }
        t.updateTile();
    }
    private void exec(String c) { try { java.lang.Process p = Runtime.getRuntime().exec("su"); java.io.DataOutputStream o = new java.io.DataOutputStream(p.getOutputStream()); o.writeBytes(c + "\nexit\n"); o.flush(); } catch (Exception e) {} }
}
