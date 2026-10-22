package com.arkael.app;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/** Per-app language: follows the system by default, can be overridden from Settings. */
final class LocaleHelper {
    private LocaleHelper() {}

    /** {tag, native name}. Strings for the new screens exist for these; other system languages fall back to English. */
    static final String[][] LANGS = {
            {"en", "English"}, {"id", "Bahasa Indonesia"}, {"ms", "Bahasa Melayu"}, {"es", "Español"},
            {"pt", "Português"}, {"fr", "Français"}, {"de", "Deutsch"}, {"it", "Italiano"},
            {"ru", "Русский"}, {"tr", "Türkçe"}, {"ar", "العربية"}, {"hi", "हिन्दी"},
            {"zh", "中文"}, {"ja", "日本語"}, {"ko", "한국어"}, {"vi", "Tiếng Việt"}, {"th", "ไทย"}};

    /** "" = follow system. */
    static String current() {
        LocaleListCompat l = AppCompatDelegate.getApplicationLocales();
        if (l.isEmpty()) return "";
        Locale loc = l.get(0);
        String lang = loc == null ? "" : loc.getLanguage();
        return "in".equals(lang) ? "id" : lang;   // old Java code for Indonesian
    }

    static void set(String tag) {
        AppCompatDelegate.setApplicationLocales(tag == null || tag.isEmpty()
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(tag));
    }

    static String label(Context c, String tag) {
        if (tag == null || tag.isEmpty()) return c.getString(R.string.ax_lang_system);
        for (String[] l : LANGS) if (l[0].equals(tag)) return l[1];
        return tag;
    }
}
