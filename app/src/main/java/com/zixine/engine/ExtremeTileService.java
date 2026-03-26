package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import java.io.DataOutputStream;

public class ExtremeTileService extends TileService {
    private String EXCLUDES = "com.zcqptx.dcwihze|com.termux|android|com.android.systemui|com.miui.home|com.zixine.engine|com.android.settings|com.dts.freefireth|com.dts.freefiremax|com.mobile.legends|com.tencent.ig|com.pubg.imobile|com.miHoYo.GenshinImpact|com.hoYoverse.hkrpg";

    @Override
    public void onClick() {
        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);

        if (active) {
            // EXTREME ON: Filter app, Suspend satu-satu secara berurutan agar tidak ada yang terlewat
            String cmd = "PKGS=$(pm list packages -e | cut -d ':' -f2 | grep -Ev '" + EXCLUDES + "'); " +
                         "for p in $PKGS; do pm suspend --user 0 $p; done; " +
                         "am kill-all; " +
                         "pm disable com.miui.powerkeeper/.statemachine.PowerStateMachineService;";
            exec(cmd);
            t.setState(Tile.STATE_ACTIVE);
            Toast.makeText(this, "SYSTEM: SEALED 🛡️", 0).show();
        } else {
            // EXTREME OFF: Bangunkan SEMUA
            exec("PKGS=$(pm list packages -u | cut -d ':' -f2); for p in $PKGS; do pm unsuspend --user 0 $p; done; pm enable com.miui.powerkeeper/.statemachine.PowerStateMachineService;");
            t.setState(Tile.STATE_INACTIVE);
            Toast.makeText(this, "SYSTEM: NORMAL 🌍", 0).show();
        }
        t.updateTile();
    }
    private void exec(String c) {
        try {
            java.lang.Process p = Runtime.getRuntime().exec("su");
            DataOutputStream o = new DataOutputStream(p.getOutputStream());
            o.writeBytes(c + "\nexit\n"); o.flush();
        } catch (Exception e) {}
    }
}
