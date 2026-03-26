// Untuk CustomScriptTileService.java
package com.zixine.engine;
import android.service.quicksettings.TileService;

public class CustomScriptTileService extends TileService {
    @Override
    public void onClick() {
        String p = getSharedPreferences("Zixine", MODE_PRIVATE).getString("path", "");
        if (!p.isEmpty()) {
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", "sh " + p}); } catch(Exception e){}
        }
    }
}
