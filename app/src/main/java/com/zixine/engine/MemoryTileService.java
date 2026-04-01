package com.zixine.engine;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class MemoryTileService extends TileService {

    @Override
    public void onClick() {
        // Cek Keamanan Real-Time
        if (!SecurityUtils.isSystemVerified(getApplicationContext())) {
            Toast.makeText(getApplicationContext(), "AKSES DITOLAK! Kernel/Passkey Invalid.", Toast.LENGTH_SHORT).show(); 
            Tile t = getQsTile(); 
            t.setState(Tile.STATE_INACTIVE); 
            t.updateTile(); 
            return; 
        }

        // Tampilkan notifikasi pembersihan
        Toast.makeText(getApplicationContext(), "🧹 ZIXINE MEMORY: RAM DIBERSIHKAN!", Toast.LENGTH_SHORT).show();

        // MODE MURNI CLEAN RAM: Sinkronisasi data -> Drop Caches -> Bunuh aplikasi latar belakang.
        // TANPA menyentuh swap/zram sama sekali!
        String cmd = "sync; echo 3 > /proc/sys/vm/drop_caches; am kill-all;";
        
        new Thread(() -> { 
            try { 
                Runtime.getRuntime().exec(new String[]{"su", "-c", cmd}).waitFor(); 
            } catch (Exception e) {} 
        }).start();
        
        // Karena ini tombol "Pembersih Instan", kembalikan wujudnya ke tidak aktif (INACTIVE)
        // agar pengguna bisa mengkliknya lagi nanti tanpa harus menekan dua kali.
        Tile t = getQsTile();
        t.setState(Tile.STATE_INACTIVE); 
        t.updateTile();
    }
}
