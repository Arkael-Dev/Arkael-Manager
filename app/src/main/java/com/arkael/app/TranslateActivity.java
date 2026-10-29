package com.arkael.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Ikon "Translate": cek izin overlay lalu nyalakan TranslateService. */
public class TranslateActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Aktifkan 'Tampilkan di atas aplikasi lain' untuk Arkael", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } else {
            startService(new Intent(this, TranslateService.class));
            Toast.makeText(this, "Translate aktif. Buka game, tap tombol biru 文A", Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
