package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Screen recording with root "screenrecord". It runs in its own su process per segment (never in
 * RootShell, which would freeze the live stats), is cut into ~170 s files because screenrecord
 * stops at 180 s, and is stopped with SIGINT so every MP4 is closed properly. No game audio.
 */
final class Recorder {
    static final String DIR = "/sdcard/Movies/Arkael";
    private static final String TAG = "ArkaelRec";
    static final String[] Q_NAMES = {"720p", "1080p", "Native"};
    private static final int[] Q_SHORT = {720, 1080, 0};      // short side in px, 0 = screen size
    private static final int[] Q_MBPS = {12, 20, 32};
    private static final int SEGMENT_SEC = 170;
    private static final long MIN_FREE = 300L * 1024 * 1024;

    private static volatile boolean rec, stopReq;
    private static volatile long startedAt;
    private static volatile String error;
    private static Thread worker;

    private Recorder() {}

    static boolean isRecording() { return rec; }
    static long startedAt() { return startedAt; }
    static String lastError() { return error; }

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("arkael_rec", Context.MODE_PRIVATE); }
    static int quality(Context c) { return Math.max(0, Math.min(Q_NAMES.length - 1, sp(c).getInt("q", 0))); }
    static void setQuality(Context c, int q) { sp(c).edit().putInt("q", q).apply(); }
    static boolean hideHandle(Context c) { return sp(c).getBoolean("hide", false); }
    static void setHideHandle(Context c, boolean v) { sp(c).edit().putBoolean("hide", v).apply(); }
    static boolean showBar(Context c) { return sp(c).getBoolean("bar", true); }
    static void setShowBar(Context c, boolean v) { sp(c).edit().putBoolean("bar", v).apply(); }
    static boolean showTouches(Context c) { return sp(c).getBoolean("touch", true); }
    static void setShowTouches(Context c, boolean v) { sp(c).edit().putBoolean("touch", v).apply(); }

    static String describe(Context c) {
        int q = quality(c);
        return "Now: " + Q_NAMES[q] + " · " + Q_MBPS[q] + " Mbps · new file about every 3 min, no game audio";
    }

    /** Starts recording and returns at once. onEnd runs (on a worker thread) when recording has ended for any reason. */
    static synchronized void start(final Context ctx, final Runnable onEnd) {
        if (rec) return;
        rec = true;
        stopReq = false;
        error = null;
        startedAt = System.currentTimeMillis();
        final Context app = ctx.getApplicationContext();
        worker = new Thread(new Runnable() {
            @Override public void run() {
                try { loop(app); }
                catch (Exception e) { error = "Recording failed: " + e.getMessage(); }
                finally {
                    try { restoreTouches(app); } catch (Exception ignored) {}
                    rec = false;
                    if (onEnd != null) { try { onEnd.run(); } catch (Exception ignored) {} }
                }
            }
        }, "ark-rec");
        worker.start();
    }

    /** Asks screenrecord to finish (SIGINT) and returns at once; onEnd given to start() reports the end. */
    static void stopAsync() {
        if (!rec) return;
        stopReq = true;
        final Thread w = worker;
        new Thread(new Runnable() {
            @Override public void run() {
                // repeat the signal: it can land between two segments
                for (int i = 0; i < 14 && w != null && w.isAlive(); i++) {
                    RootShell.run("kill -2 $(pidof screenrecord) 2>/dev/null");
                    try { w.join(500); } catch (InterruptedException ignored) {}
                }
            }
        }, "ark-rec-stop").start();
    }

    private static void loop(Context ctx) throws Exception {
        if (!RootShell.hasRoot()) { error = "Recording needs root"; return; }
        RootShell.run("mkdir -p " + DIR);
        if (showTouches(ctx)) enableTouches(ctx);
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        int wanted = quality(ctx);
        int q = wanted;
        int n = 0;
        while (!stopReq) {
            if (freeBytes() < MIN_FREE) { error = "Storage almost full, recording stopped"; break; }
            n++;
            String file = DIR + "/rec_" + ts + "_" + (n < 10 ? "0" : "") + n + ".mp4";
            long t0 = System.currentTimeMillis();
            String out = runSegment(ctx, file, q);
            RootShell.run("[ -s " + file + " ] && am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://" + file + " >/dev/null 2>&1");
            if (!stopReq && System.currentTimeMillis() - t0 < 3000) {
                RootShell.run("[ -s " + file + " ] || rm -f " + file);
                if (q > 0) {                         // encoder refused this size: step down one tier and retry
                    Log.w(TAG, "screenrecord failed at " + Q_NAMES[q] + ", falling back. Output: " + out.trim());
                    q--;
                    n--;
                    continue;
                }
                String first = out.trim();
                int nl = first.indexOf('\n');
                if (nl > 0) first = first.substring(0, nl);
                error = "screenrecord failed" + (first.isEmpty() ? "" : ": " + first);
                break;
            }
        }
        if (error == null && q != wanted) {
            error = "Device refused " + Q_NAMES[wanted] + ", recorded at " + Q_NAMES[q] + " instead. Saved in Movies/Arkael";
        }
    }

    /** Turns on Android's "Show touches" (white circles that follow taps and swipes, they end up in the video). */
    private static void enableTouches(Context c) {
        SharedPreferences s = sp(c);
        int prev = s.getInt("touch_prev", -1);        // left over from a session that ended badly: that is the real original
        if (prev < 0) prev = "1".equals(su("settings get system show_touches").trim()) ? 1 : 0;
        s.edit().putInt("touch_prev", prev).apply();
        su("settings put system show_touches 1");
    }

    private static void restoreTouches(Context c) {
        SharedPreferences s = sp(c);
        int prev = s.getInt("touch_prev", -1);
        if (prev < 0) return;
        su("settings put system show_touches " + prev);
        s.edit().remove("touch_prev").apply();
    }

    /** One su process per segment; returns everything screenrecord printed. */
    private static String su(String cmd) {
        StringBuilder sb = new StringBuilder();
        try {
            Process p = new ProcessBuilder("su").redirectErrorStream(true).start();
            OutputStream o = p.getOutputStream();
            o.write((cmd + "\nexit\n").getBytes("UTF-8"));
            o.flush();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            p.waitFor();
        } catch (Exception e) {
            sb.append(String.valueOf(e.getMessage()));
        }
        return sb.toString();
    }

    private static String runSegment(Context ctx, String file, int q) {
        String cmd = "screenrecord --time-limit " + SEGMENT_SEC + " --bit-rate " + (Q_MBPS[q] * 1000000)
                + sizeArg(ctx, Q_SHORT[q]) + " " + file + " 2>&1";
        Log.i(TAG, cmd);
        return su(cmd);
    }

    /**
     * Screen size in the CURRENT orientation. "wm size" (root) gives the real panel size; the app's own
     * DisplayMetrics only decide portrait vs landscape. Falls back to DisplayMetrics, or null if unknown.
     */
    private static int[] screenSize(Context ctx) {
        int pw = 0, ph = 0, ow = 0, oh = 0;
        try {
            Matcher m = Pattern.compile("(Physical|Override) size:\\s*(\\d+)x(\\d+)").matcher(su("wm size"));
            while (m.find()) {
                int a = Integer.parseInt(m.group(2)), b = Integer.parseInt(m.group(3));
                if ("Override".equals(m.group(1))) { ow = a; oh = b; } else { pw = a; ph = b; }
            }
        } catch (Exception ignored) {}
        int w = ow > 0 ? ow : pw, h = ow > 0 ? oh : ph;
        int mw = 0, mh = 0;
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            mw = dm.widthPixels; mh = dm.heightPixels;
        } catch (Exception ignored) {}
        if (w <= 0 || h <= 0) {
            if (mw <= 0 || mh <= 0) return null;
            return new int[]{mw, mh};
        }
        if (mw > 0 && mh > 0) {                       // match the current orientation
            boolean land = mw > mh;
            int lo = Math.min(w, h), hi = Math.max(w, h);
            w = land ? hi : lo;
            h = land ? lo : hi;
        }
        return new int[]{w, h};
    }

    /**
     * Always an explicit " --size WxH". Without it screenrecord falls back to 1280x720 on many devices,
     * which is why 1080p and Native used to come out as 720p.
     */
    private static String sizeArg(Context ctx, int shortSide) {
        int[] s = screenSize(ctx);
        if (s == null) return "";
        int w = s[0], h = s[1];
        int sh = Math.min(w, h);
        if (shortSide > 0 && sh > shortSide) {
            float k = shortSide / (float) sh;
            w = Math.round(w * k);
            h = Math.round(h * k);
        }
        w &= ~1; h &= ~1;
        return " --size " + w + "x" + h;
    }

    private static long freeBytes() {
        try { return new StatFs(Environment.getExternalStorageDirectory().getPath()).getAvailableBytes(); }
        catch (Exception e) { return Long.MAX_VALUE; }
    }
}
