package com.arkael.app;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Terjemahan untuk teks yang dulu tertulis langsung (bahasa Indonesia) di kode Java.
 * Pemakaian: Tr.s("Batal"). Bahasa Indonesia = teks aslinya; bahasa lain dicari di TrData;
 * bahasa yang belum ada terjemahannya memakai English.
 */
final class Tr {
    private static Map<String, Integer> idx;

    static String s(String text) {
        if (text == null || text.isEmpty()) return text;
        try {
            String lang = lang();
            if (lang.equals("in")) return text;
            int a = 0, b = text.length();
            while (a < b && Character.isWhitespace(text.charAt(a))) a++;
            while (b > a && Character.isWhitespace(text.charAt(b - 1))) b--;
            if (a == b) return text;
            Integer i = index().get(text.substring(a, b));
            if (i == null) return text;
            String v = pick(lang, i);
            if (v == null || v.isEmpty()) return text;
            return text.substring(0, a) + v + text.substring(b);
        } catch (Throwable t) {
            return text;
        }
    }

    private static String pick(String lang, int i) {
        String[] arr = TrData.get(lang);
        String v = (arr != null && i < arr.length) ? arr[i] : null;
        if ((v == null || v.isEmpty()) && !lang.equals("en")) {
            String[] en = TrData.get("en");
            v = (en != null && i < en.length) ? en[i] : null;
        }
        return v;
    }

    private static synchronized Map<String, Integer> index() {
        if (idx == null) {
            Map<String, Integer> m = new HashMap<>();
            String[] k = TrData.K;
            for (int i = 0; i < k.length; i++) m.put(k[i].trim(), i);
            idx = m;
        }
        return idx;
    }

    /** Kode bahasa yang dipakai sekarang: pilihan di Pengaturan app, kalau kosong ikut bahasa sistem. */
    static String lang() {
        Locale loc = null;
        try {
            LocaleListCompat l = AppCompatDelegate.getApplicationLocales();
            if (l != null && !l.isEmpty()) loc = l.get(0);
        } catch (Throwable ignored) {
        }
        if (loc == null) loc = Locale.getDefault();
        String c = loc.getLanguage();
        if (c == null) return "en";
        c = c.toLowerCase(Locale.ROOT);
        if (c.equals("id")) return "in";
        if (c.equals("iw")) return "he";
        return c;
    }
}
