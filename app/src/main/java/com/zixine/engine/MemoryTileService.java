package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class MemoryTileService extends TileService {

    @Override
    public void onClick() {
        // Cek Keamanan
        if (!SecurityUtils.isSystemVerified(getApplicationContext())) {
            Toast.makeText(getApplicationContext(), "AKSES DITOLAK! Kernel/Passkey Invalid.", Toast.LENGTH_SHORT).show(); 
            Tile t = getQsTile(); t.setState(Tile.STATE_INACTIVE); t.updateTile(); return; 
        }

        Tile t = getQsTile();
        boolean active = (t.getState() == Tile.STATE_INACTIVE);
        
        Toast.makeText(getApplicationContext(), active ? "ZIXINE MEMORY: ZRAM OFF & RAM DIBERSIHKAN" : "ZIXINE MEMORY: ZRAM ON (NORMAL)", Toast.LENGTH_SHORT).show();

        String cmd;
        if (active) {
            // MODE ON: Bersihkan cache RAM (Drop Caches) lalu matikan ZRAM agar memori murni
            cmd = "sync; echo 3 > /proc/sys/vm/drop_caches;";
        } else {
            // MODE OFF: Nyalakan ZRAM kembali seperti standar pabrik
            cmd = "
        }
        
        new Thread(() -> { 
            try { Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); } catch (Exception e) {} 
        }).start();
        
        t.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE); 
        t.updateTile();
    }
}
