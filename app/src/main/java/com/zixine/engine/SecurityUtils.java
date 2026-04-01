package com.zixine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.MessageDigest;

public class SecurityUtils {

    public static String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    public static boolean isSystemVerified(Context context) {
        // PERBAIKAN FATAL ERROR: Cek apakah kernel null sebelum di-lowercase
        String osVer = System.getProperty("os.version");
        boolean isZixine = (osVer != null) && osVer.toLowerCase().contains("zixine");
        
        if (isZixine) return true;

        SharedPreferences prefs = context.getSharedPreferences("ZixineSecurePrefs", Context.MODE_PRIVATE);
        String savedHash = prefs.getString("secured_pass_hash", "");
        String realHash = generateHash(BuildConfig.SECRET_PASSKEY);

        return savedHash.equals(realHash) && !savedHash.isEmpty();
    }
}
