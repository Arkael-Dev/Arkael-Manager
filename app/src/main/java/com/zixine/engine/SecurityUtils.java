package com.zixine.engine;

import android.content.Context;

public class SecurityUtils {

    public static String generateHash(String input) {
        if (input == null) return "";
        return String.valueOf(input.hashCode());
    }

    public static boolean isSystemVerified(Context context) {
        // Cek Kernel Zixine
        try {
            String version = System.getProperty("os.version");
            if (version != null && version.toLowerCase().contains("zixine")) {
                return true;
            }
        } catch (Exception e) { /* Abaikan jika akses kernel dilarang */ }

        // Cek Bypass Passkey
        try {
            String savedHash = context.getSharedPreferences("ZixineSecurePrefs", Context.MODE_PRIVATE)
                    .getString("secured_pass_hash", "");
            String realHash = generateHash(BuildConfig.SECRET_PASSKEY);
            return !savedHash.isEmpty() && savedHash.equals(realHash);
        } catch (Exception e) {
            return false;
        }
    }
}
