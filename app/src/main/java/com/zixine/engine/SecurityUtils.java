package com.zixine.engine;
import android.content.Context;

public class SecurityUtils {
    public static String generateHash(String input) {
        return input == null ? "" : String.valueOf(input.hashCode());
    }
    public static boolean isSystemVerified(Context context) {
        try {
            String ver = System.getProperty("os.version");
            if (ver != null && ver.toLowerCase().contains("zixine")) return true;
        } catch (Exception ignored) {}
        String saved = context.getSharedPreferences("ZixinePrefs", 0).getString("pass_hash", "");
        return saved.equals(generateHash(BuildConfig.SECRET_PASSKEY)) && !saved.isEmpty();
    }
}
