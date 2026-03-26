// Untuk ZixineTileService.java
package com.zixine.engine;
import android.service.quicksettings.*;

public class ZixineTileService extends TileService {
    @Override
    public void onClick() {
        Tile t = getQsTile();
        if (t.getState() == Tile.STATE_INACTIVE) {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", "pm disable-user --user 0 com.android.vending"}); } catch(Exception e){}
            t.setState(Tile.STATE_ACTIVE);
        } else {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", "pm enable com.android.vending"}); } catch(Exception e){}
            t.setState(Tile.STATE_INACTIVE);
        }
        t.updateTile();
    }
}
