package com.zixine.engine;
import android.service.quicksettings.TileService;
import java.io.DataOutputStream;

public class CustomScriptTileService extends TileService {
    @Override
    public void onClick() {
        String p = getSharedPreferences("Zixine", MODE_PRIVATE).getString("path", "");
        if (!p.isEmpty()) {
            try {
                java.lang.Process pr = Runtime.getRuntime().exec("su");
                DataOutputStream o = new DataOutputStream(pr.getOutputStream());
                o.writeBytes("sh " + p + "\nexit\n"); o.flush();
            } catch (Exception ignored) {}
        }
    }
}
