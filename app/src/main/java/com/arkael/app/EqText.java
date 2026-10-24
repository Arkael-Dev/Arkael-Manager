package com.arkael.app;

import android.content.Context;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Menerjemahkan teks status mesin EQ (ditulis dalam bahasa Indonesia di EqEngine/EqService) ke bahasa yang dipilih pengguna. */
final class EqText {
    private static final Pattern SESSIONS = Pattern.compile("Per-sesi · (\\d+) sesi");

    static String loc(Context c, String s) {
        if (s == null) return "";
        if (s.equals("Mati")) return c.getString(R.string.ax_eq_off);
        if (s.equals("Memulai…")) return c.getString(R.string.ax_eq_starting);
        Matcher m = SESSIONS.matcher(s);
        if (m.find()) s = m.replaceFirst(c.getString(R.string.ax_eq_s_session) + " · " + c.getString(R.string.ax_eq_s_sessions, Integer.parseInt(m.group(1))));
        s = s.replace("Per-sesi", c.getString(R.string.ax_eq_s_session));
        s = s.replace("menunggu ada suara diputar", c.getString(R.string.ax_eq_s_waiting));
        s = s.replace("(bawaan)", "(" + c.getString(R.string.ax_eq_s_builtin) + ")");
        s = s.replace("tanpa EQ", c.getString(R.string.ax_eq_s_noeq));
        if (s.contains("GAGAL")) s = s.replace("GAGAL", c.getString(R.string.ax_eq_s_fail));
        return s;
    }
}
