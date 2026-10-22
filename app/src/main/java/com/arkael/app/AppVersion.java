package com.arkael.app;

import android.app.Application;

/** Version shown inside the app: always the real versionName of the installed APK (so a new release changes it by itself). */
final class AppVersion {
    private AppVersion() {}

    static String name(String fallback) {
        try {
            Application a = (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (a != null) {
                String v = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
                if (v != null && !v.isEmpty()) return v;
            }
        } catch (Throwable ignored) { }
        return fallback;
    }
}
