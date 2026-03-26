package com.zixine.engine;
import android.service.quicksettings.*;
import java.io.DataOutputStream;

public class ZixineTileService extends TileService {
    @Override
    public void onClick() {
        Tile t = getQsTile();
        if (t.getState() == Tile.STATE_INACTIVE) {
            execRoot("pm disable com.miui.powerkeeper/.statemachine.PowerStateMachineService; pm disable-user --user 0 com.android.vending");
            t.setState(Tile.STATE_ACTIVE);
        } else {
            execRoot("pm enable com.miui.powerkeeper/.statemachine.PowerStateMachineService; pm enable com.android.vending");
            t.setState(Tile.STATE_INACTIVE);
        }
        t.updateTile();
    }
    private void execRoot(String c) {
        try {
            java.lang.Process p = Runtime.getRuntime().exec("su");
            DataOutputStream o = new DataOutputStream(p.getOutputStream());
            o.writeBytes(c + "\nexit\n"); o.flush();
        } catch (Exception ignored) {}
    }
}
