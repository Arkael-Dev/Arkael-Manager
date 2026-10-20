package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Screen recording with root "screenrecord". It runs in its own su process per segment (never in
 * RootShell, which would freeze the live stats), is cut into ~170 s files because screenrecord
 * stops at 180 s, and is stopped with SIGINT so every MP4 is closed properly. No game audio.
 */
final class Recorder {
    static final String DIR = "/sdcard/Movies/Arkael";
    static final String[] Q_NAMES = {"720p", "1080p", "Native"};
    private static final int[] Q_SHORT = {720, 1080, 0};      // short side in px, 0 = screen size
    private static final int[] Q_MBPS = {6, 10, 16};
    private static final int SEGMENT_SEC = 170;
    private static final long MIN_FREE = 300L * 1024 * 1024;

    private static volatile boolean rec, stopReq;
    private static volatile String error;
    private static Thread worker;

    private Recorder() {}

    static boolean isRecording() { return rec; }
    static String lastError() { return error; }

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("arkael_rec", Context.MODE_PRIVATE); }
    static int quality(Context c) { return Math.max(0, Math.min(Q_NAMES.length - 1, sp(c).getInt("q", 0))); }
    static void setQuality(Context c, int q) { sp(c).edit().putInt("q", q).apply(); }
    static boolean hideHandle(Context c) { return sp(c).getBoolean("hide", false); }
    static void setHideHandle(Context c, boolean v) { sp(c).edit().putBoolean("hide", v).apply(); }

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
        final Context app = ctx.getApplicationContext();
        worker = new Thread(new Runnable() {
            @Override public void run() {
                try { loop(app); }
                catch (Exception e) { error = "Recording failed: " + e.getMessage(); }
                finally {
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
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        int n = 0;
        while (!stopReq) {
            if (freeBytes() < MIN_FREE) { error = "Storage almost full, recording stopped"; break; }
            n++;
            String file = DIR + "/rec_" + ts + "_" + (n < 10 ? "0" : "") + n + ".mp4";
            long t0 = System.currentTimeMillis();
            String out = runSegment(ctx, file);
            RootShell.run("[ -s " + file + " ] && am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://" + file + " >/dev/null 2>&1");
            if (!stopReq && System.currentTimeMillis() - t0 < 3000) {
                String first = out.trim();
                int nl = first.indexOf('\n');
                if (nl > 0) first = first.substring(0, nl);
                error = "screenrecord failed" + (first.isEmpty() ? "" : ": " + first);
                break;
            }
        }
    }

    private static String runSegment(Context ctx, String file) {
        int q = quality(ctx);
        String cmd = "screenrecord --time-limit " + SEGMENT_SEC + " --bit-rate " + (Q_MBPS[q] * 1000000)
                + sizeArg(ctx, Q_SHORT[q]) + " " + file + " 2>&1";
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

    /** " --size WxH" in the screen's current orientation, or "" for the native size. */
    private static String sizeArg(Context ctx, int shortSide) {
        if (shortSide <= 0) return "";
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            int w = dm.widthPixels, h = dm.heightPixels;
            int s = Math.min(w, h);
            if (s <= shortSide) return "";
            float k = shortSide / (float) s;
            int ow = Math.round(w * k) & ~1, oh = Math.round(h * k) & ~1;
            return " --size " + ow + "x" + oh;
        } catch (Exception e) {
            return "";
        }
    }

    private static long freeBytes() {
        try { return new StatFs(Environment.getExternalStorageDirectory().getPath()).getAvailableBytes(); }
        catch (Exception e) { return Long.MAX_VALUE; }
    }
}
