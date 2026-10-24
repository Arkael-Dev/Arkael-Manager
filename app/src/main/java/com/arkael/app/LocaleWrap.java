package com.arkael.app;

import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/** Membuat Activity biasa (bukan AppCompatActivity) ikut bahasa pilihan pengguna. Dipanggil dari attachBaseContext. */
final class LocaleWrap {
    static Context wrap(Context base) {
        try {
            LocaleListCompat l = AppCompatDelegate.getApplicationLocales();
            if (l == null || l.isEmpty()) return base;
            Locale loc = l.get(0);
            if (loc == null) return base;
            Configuration c = new Configuration(base.getResources().getConfiguration());
            c.setLocale(loc);
            return base.createConfigurationContext(c);
        } catch (Throwable t) {
            return base;
        }
    }
}
