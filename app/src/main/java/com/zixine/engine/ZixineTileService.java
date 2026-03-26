package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import java.io.DataOutputStream;

public class ZixineTileService extends TileService {

    private String[] targets = {
        "com.android.vending", "com.google.android.gms", "com.google.android.youtube", 
        "com.google.android.gm", "com.android.chrome", "com.miui.analytics", 
        "com.xiaomi.joyose", "com.miui.msa.global", "com.whatsapp"
    };

    // Cek status saat panel ditarik
    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        // Kamu bisa kembangkan logika cek status di sini, 
        // tapi untuk sekarang kita buat default Inactive.
        tile.setState(Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onClick() {
        Tile tile = getQsTile();
        if (tile.getState() == Tile.STATE_INACTIVE) {
            // AKTIFKAN BRUTAL MODE
            runBrutal(true);
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel("ZIXINE: ON 🔥");
        } else {
            // MATIKAN (NORMAL MODE)
            runBrutal(false);
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("ZIXINE: OFF 🌍");
        }
        tile.updateTile();
    }

    private void runBrutal(boolean enable) {
        StringBuilder sb = new StringBuilder();
        if (enable) {
            sb.append("pm disable com.miui.powerkeeper/.statemachine.PowerStateMachineService; ");
            for (String app : targets) {
                sb.append("am force-stop ").append(app).append("; ");
                sb.append("pm suspend ").append(app).append("; ");
            }
        } else {
            sb.append("pm enable com.miui.powerkeeper/.statemachine.PowerStateMachineService; ");
            for (String app : targets) {
                sb.append("pm unsuspend ").append(app).append("; ");
            }
        }
        
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(sb.toString() + "\nexit\n");
            os.flush();
        } catch (Exception ignored) {}
    }
}
