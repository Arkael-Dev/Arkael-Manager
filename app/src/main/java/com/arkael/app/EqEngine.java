package com.arkael.app;

import android.annotation.SuppressLint;
import android.media.audiofx.BassBoost;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.os.Build;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mesin efek audio. Dipakai di dua tempat dengan kode yang sama:
 *  - di proses root (EqRootDaemon)  -> bisa memasang efek di output mix global (session 0)
 *  - di dalam app (EqService)       -> cadangan kalau daemon root tidak jalan
 *
 * Dua jalur pemasangan:
 *  GLOBAL     : satu rantai efek di session 0 (output mix) -> semua suara HP.
 *  PER-SESI   : cari session id yang sedang memutar suara (dumpsys), pasang rantai efek di tiap sesi.
 * Rantai efek: EQ 10-band (DynamicsProcessing + limiter, atau Equalizer bawaan kalau tidak ada),
 * BassBoost, Virtualizer (surround virtual), LoudnessEnhancer.
 */
final class EqEngine {

    interface Env {
        void log(String s);
        /** Jalankan perintah shell sebagai root, kembalikan outputnya ("" kalau gagal). */
        String sh(String cmd);
    }

    private static final int PRIO = 1000;
    private static final int MAX_SESSIONS = 32;
    /** Batas atas tiap band DynamicsProcessing (Hz) untuk pusat 31..16k. */
    private static final float[] CUT = {44, 88, 177, 355, 710, 1420, 2840, 5680, 11360, 22000};

    private final Env env;
    private final Map<Integer, Chain> chains = new HashMap<Integer, Chain>();
    private final Set<Integer> explicit = new HashSet<Integer>();
    private final Set<Integer> failed = new HashSet<Integer>();
    private final Map<Integer, Integer> missing = new HashMap<Integer, Integer>();
    private final Set<String> seen = new HashSet<String>();
    private EqConfig cfg = new EqConfig();
    private boolean sessionMode;
    private int lastFound;

    EqEngine(Env env) { this.env = env; }

    // ------------------------------------------------------------------ API

    /** Terapkan setelan. Hasil diawali "OK " atau "GAGAL ". */
    synchronized String apply(EqConfig c) {
        cfg = c;
        if (!c.enabled) {
            releaseAll();
            sessionMode = false;
            return "OK Mati";
        }
        sessionMode = false;
        if (c.method != EqConfig.M_SESSION) {
            Chain g = chains.get(0);
            if (g == null) {
                g = build(0);
                if (g != null) chains.put(0, g);
            }
            if (g != null) {
                g.apply(c);
                for (Iterator<Map.Entry<Integer, Chain>> it = chains.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Integer, Chain> e = it.next();
                    if (e.getKey() != 0) { e.getValue().release(); it.remove(); }
                }
                return "OK Global · " + g.describe();
            }
            if (c.method == EqConfig.M_GLOBAL) return "GAGAL Global (session 0) tidak bisa dibuat. Lihat Diagnostik.";
            env.log("Global gagal, pindah ke per-sesi");
        }
        Chain g0 = chains.remove(0);
        if (g0 != null) g0.release();
        sessionMode = true;
        rescan();
        for (Chain ch : chains.values()) ch.apply(c);
        if (!chains.isEmpty()) return "OK Per-sesi · " + chains.size() + " sesi";
        if (lastFound > 0) return "GAGAL Ada " + lastFound + " sesi audio tapi efek tidak bisa dipasang. Lihat Diagnostik.";
        return "OK Per-sesi · menunggu ada suara diputar";
    }

    /** Dipanggil berkala (tiap ±3 detik). Hanya bekerja di mode per-sesi. */
    synchronized void tick() {
        if (sessionMode && cfg.enabled) {
            rescan();
            for (Chain ch : chains.values()) ch.apply(cfg);
        }
    }

    synchronized void addSession(int id) {
        if (id <= 0) return;
        explicit.add(id);
        if (sessionMode && cfg.enabled && !chains.containsKey(id)) {
            Chain ch = build(id);
            if (ch != null) { ch.apply(cfg); chains.put(id, ch); }
        }
    }

    synchronized void removeSession(int id) {
        explicit.remove(id);
        Chain ch = chains.remove(id);
        if (ch != null) ch.release();
    }

    synchronized void releaseAll() {
        for (Chain ch : chains.values()) ch.release();
        chains.clear();
        missing.clear();
        failed.clear();
    }

    /** null = bisa membuat efek di session 0, selain itu pesan error. */
    String probeGlobal() {
        Equalizer e = null;
        try {
            e = new Equalizer(PRIO, 0);
            return null;
        } catch (Throwable t) {
            return why(t);
        } finally {
            if (e != null) try { e.release(); } catch (Throwable ignored) { }
        }
    }

    // ------------------------------------------------------------------ per-sesi

    private void rescan() {
        String a = env.sh("dumpsys audio 2>/dev/null | grep AudioPlaybackConfiguration");
        String b = env.sh("dumpsys media.audio_flinger 2>/dev/null");
        Set<Integer> ids = parseSessions(a, b);
        ids.addAll(explicit);
        lastFound = ids.size();
        for (int id : ids) {
            missing.remove(id);
            if (chains.containsKey(id) || failed.contains(id) || chains.size() >= MAX_SESSIONS) continue;
            Chain ch = build(id);
            if (ch != null) chains.put(id, ch); else failed.add(id);
        }
        for (Iterator<Map.Entry<Integer, Chain>> it = chains.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Chain> e = it.next();
            int k = e.getKey();
            if (k == 0 || ids.contains(k)) continue;
            Integer m = missing.get(k);
            int n = m == null ? 1 : m + 1;
            if (n >= 3) { e.getValue().release(); it.remove(); missing.remove(k); } else missing.put(k, n);
        }
        failed.retainAll(ids);
    }

    static Set<Integer> parseSessions(String audioDump, String flingerDump) {
        Set<Integer> ids = new LinkedHashSet<Integer>();
        if (audioDump != null) {
            Matcher m = Pattern.compile("sessionId[:=\\s]+(\\d+)").matcher(audioDump);
            while (m.find()) addId(ids, m.group(1));
        }
        if (flingerDump != null) {
            int col = -1;
            for (String ln : flingerDump.split("\n")) {
                String t = ln.trim();
                if (t.isEmpty()) { col = -1; continue; }
                String[] p = t.split("\\s+");
                if (t.startsWith("Type") && t.contains("Session")) {
                    col = -1;
                    for (int i = 0; i < p.length; i++) if (p[i].equals("Session")) { col = i; break; }
                } else if (col >= 0 && col < p.length) {
                    addId(ids, p[col]);
                }
            }
        }
        return ids;
    }

    private static void addId(Set<Integer> ids, String s) {
        if (s == null || s.length() == 0 || s.length() > 9) return;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < '0' || s.charAt(i) > '9') return;
        int v = Integer.parseInt(s);
        if (v > 0) ids.add(v);
    }

    // ------------------------------------------------------------------ util

    private Chain build(int session) {
        Chain c = new Chain(session);
        if (!c.usable()) {
            logOnce("build" + session + c.errors, "Sesi " + session + " gagal: " + c.errors);
            c.release();
            return null;
        }
        return c;
    }

    private void logOnce(String key, String msg) {
        if (seen.add(key)) env.log(msg);
    }

    static String why(Throwable t) {
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }

    /** Interpolasi gain 10 titik pada skala log-frekuensi. */
    static float interp(float[] g, float hz) {
        if (hz <= EqConfig.FREQ[0]) return g[0];
        if (hz >= EqConfig.FREQ[EqConfig.N - 1]) return g[EqConfig.N - 1];
        for (int i = 0; i < EqConfig.N - 1; i++) {
            float a = EqConfig.FREQ[i], b = EqConfig.FREQ[i + 1];
            if (hz >= a && hz <= b) {
                float t = (float) (Math.log(hz / a) / Math.log(b / a));
                return g[i] + (g[i + 1] - g[i]) * t;
            }
        }
        return 0f;
    }

    // ------------------------------------------------------------------ rantai efek

    @SuppressLint("NewApi")
    private final class Chain {
        final int session;
        DynamicsProcessing dp;
        Equalizer eq;
        BassBoost bb;
        Virtualizer vz;
        LoudnessEnhancer le;
        boolean bbOk, vzOk;
        final StringBuilder errors = new StringBuilder();

        Chain(int s) {
            session = s;
            if (Build.VERSION.SDK_INT >= 28) {
                try {
                    DynamicsProcessing.Config.Builder b = new DynamicsProcessing.Config.Builder(
                            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                            true, EqConfig.N, false, 0, false, 0, true);
                    dp = new DynamicsProcessing(PRIO, s, b.build());
                    if (!dp.hasControl()) { err("DynamicsProcessing tanpa kontrol"); drop(dp); dp = null; }
                } catch (Throwable t) { err("DynamicsProcessing " + why(t)); dp = null; }
            }
            if (dp == null) {
                try { eq = new Equalizer(PRIO, s); } catch (Throwable t) { err("Equalizer " + why(t)); eq = null; }
            }
            try { bb = new BassBoost(PRIO, s); bbOk = bb.getStrengthSupported(); } catch (Throwable t) { err("BassBoost " + why(t)); bb = null; }
            try { vz = new Virtualizer(PRIO, s); vzOk = vz.getStrengthSupported(); } catch (Throwable t) { err("Virtualizer " + why(t)); vz = null; }
            try { le = new LoudnessEnhancer(s); } catch (Throwable t) { err("Loudness " + why(t)); le = null; }
        }

        private void err(String m) { if (errors.length() > 0) errors.append(" | "); errors.append(m); }

        private void drop(android.media.audiofx.AudioEffect fx) { try { fx.release(); } catch (Throwable ignored) { } }

        boolean usable() { return dp != null || eq != null; }

        String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append(dp != null ? "EQ 10-band + limiter" : eq != null ? "EQ " + bands() + "-band (bawaan)" : "tanpa EQ");
            if (bb != null && bbOk) sb.append(", Bass");
            if (vz != null && vzOk) sb.append(", 3D");
            if (le != null) sb.append(", Loudness");
            return sb.toString();
        }

        private int bands() { try { return eq.getNumberOfBands(); } catch (Throwable t) { return 0; } }

        void apply(EqConfig c) {
            float[] g = c.gain.clone();
            float bassAdd = (bb == null || !bbOk) ? c.bass / 1000f * 6f : 0f;
            g[0] += bassAdd; g[1] += bassAdd; g[2] += bassAdd * 0.6f; g[3] += bassAdd * 0.25f;
            float max = 0f;
            for (int i = 0; i < g.length; i++) {
                g[i] = EqConfig.clamp(g[i], -15f, 15f);
                if (g[i] > max) max = g[i];
            }
            try {
                if (dp != null) applyDp(c, g, max);
                else if (eq != null) applyEq(g);
            } catch (Throwable t) { logOnce("eq" + session + why(t), "EQ gagal di sesi " + session + ": " + why(t)); }
            try {
                if (bb != null && bbOk) {
                    if (c.bass > 0) bb.setStrength((short) c.bass);
                    bb.setEnabled(c.bass > 0);
                }
            } catch (Throwable t) { logOnce("bb" + session + why(t), "Bass gagal: " + why(t)); }
            try {
                if (vz != null && vzOk) {
                    if (c.spatial > 0) vz.setStrength((short) c.spatial);
                    vz.setEnabled(c.spatial > 0);
                }
            } catch (Throwable t) { logOnce("vz" + session + why(t), "3D gagal: " + why(t)); }
            try {
                if (le != null) {
                    int target = c.loud;
                    if (dp == null && c.safe) target -= (int) (0.6f * max * 100f);
                    le.setTargetGain(target);
                    le.setEnabled(target != 0);
                }
            } catch (Throwable t) { logOnce("le" + session + why(t), "Loudness gagal: " + why(t)); }
        }

        private void applyDp(EqConfig c, float[] g, float max) {
            DynamicsProcessing.Eq e = new DynamicsProcessing.Eq(true, true, EqConfig.N);
            for (int i = 0; i < EqConfig.N; i++) e.setBand(i, new DynamicsProcessing.EqBand(true, CUT[i], g[i]));
            dp.setPreEqAllChannelsTo(e);
            dp.setInputGainAllChannelsTo(c.safe ? -0.6f * max : 0f);
            dp.setLimiterAllChannelsTo(new DynamicsProcessing.Limiter(true, c.safe, 0, 1f, 60f, 10f, -1f, 0f));
            dp.setEnabled(true);
        }

        private void applyEq(float[] g) {
            short nb = eq.getNumberOfBands();
            short[] r = eq.getBandLevelRange();
            for (short b = 0; b < nb; b++) {
                float hz = eq.getCenterFreq(b) / 1000f;
                int lvl = Math.round(interp(g, hz) * 100f);
                if (lvl < r[0]) lvl = r[0];
                if (lvl > r[1]) lvl = r[1];
                eq.setBandLevel(b, (short) lvl);
            }
            eq.setEnabled(true);
        }

        void release() {
            for (android.media.audiofx.AudioEffect fx : new android.media.audiofx.AudioEffect[]{dp, eq, bb, vz, le}) {
                if (fx == null) continue;
                try { fx.setEnabled(false); } catch (Throwable ignored) { }
                try { fx.release(); } catch (Throwable ignored) { }
            }
            dp = null; eq = null; bb = null; vz = null; le = null;
        }
    }
}
