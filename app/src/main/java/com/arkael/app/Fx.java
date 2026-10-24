package com.arkael.app;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.os.PowerManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Penghemat animasi untuk HP kentang. Animasi tak terbatas (latar, tombol Mainkan, ikon hex) dipantau di sini:
 * dijeda saat layar tidak tampil, dan dimatikan otomatis di HP RAM kecil / mode hemat baterai.
 * Mode di SharedPreferences "ark_fx" kunci "mode": 0 otomatis (bawaan), 1 selalu nyala, 2 selalu mati.
 */
final class Fx {
    private static final List<WeakReference<ValueAnimator>> LIST = new ArrayList<>();
    private static Boolean low;
    private static boolean paused;

    static boolean reduce(Context c) {
        int mode = c.getSharedPreferences("ark_fx", Context.MODE_PRIVATE).getInt("mode", 0);
        if (mode == 1) return false;
        if (mode == 2) return true;
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isPowerSaveMode()) return true;
        } catch (Throwable ignored) {
        }
        if (low == null) {
            boolean l = false;
            try {
                ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                l = am.isLowRamDevice() || mi.totalMem <= 4L * 1024 * 1024 * 1024;
            } catch (Throwable ignored) {
            }
            low = l;
        }
        return low;
    }

    /** Panggil tepat setelah va.start(). */
    static synchronized void track(Context c, ValueAnimator va) {
        if (va == null) return;
        if (reduce(c)) {   // HP kentang: diam saja, tanpa gambar ulang 60x per detik
            va.cancel();
            return;
        }
        for (Iterator<WeakReference<ValueAnimator>> it = LIST.iterator(); it.hasNext(); ) {
            if (it.next().get() == null) it.remove();
        }
        LIST.add(new WeakReference<>(va));
        if (paused) va.pause();
    }

    static synchronized void pauseAll() {
        paused = true;
        for (WeakReference<ValueAnimator> r : LIST) {
            ValueAnimator v = r.get();
            if (v != null && v.isStarted() && !v.isPaused()) v.pause();
        }
    }

    static synchronized void resumeAll() {
        paused = false;
        for (WeakReference<ValueAnimator> r : LIST) {
            ValueAnimator v = r.get();
            if (v != null && v.isPaused()) v.resume();
        }
    }

    /** Jeda polling statistik: lebih jarang di HP kentang. */
    static long pollMs(Context c) {
        return reduce(c) ? 4000 : 2000;
    }
}
