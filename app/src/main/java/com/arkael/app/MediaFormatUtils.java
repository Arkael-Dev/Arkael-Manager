package com.arkael.app;

import android.media.MediaFormat;

/**
 * Stride dan slice-height buffer input encoder. Encoder H.264 di banyak perangkat
 * (Qualcomm/Exynos/MediaTek) minta baris lebih lebar dari lebar gambar dan jumlah
 * baris lebih banyak dari tinggi gambar. Kalau gambar dijejalkan selebar w saja,
 * hasilnya video bergaris miring / warna pecah. Nilai asli ada di format input.
 */
final class MediaFormatUtils {

    private MediaFormatUtils() {}

    static int keyStride(MediaFormat f) {
        if (f == null) return -1;
        try {
            int v = f.getInteger(MediaFormat.KEY_STRIDE);
            if (v > 0) return v;
        } catch (Exception ignore) {}
        return -1;
    }

    static int keySliceHeight(MediaFormat f) {
        if (f == null) return -1;
        try {
            int v = f.getInteger(MediaFormat.KEY_SLICE_HEIGHT);
            if (v > 0) return v;
        } catch (Exception ignore) {}
        return -1;
    }

    /** Stride final: KEY_STRIDE kalau ada dan masuk akal, kalau tidak fallback. */
    static int stride(MediaFormat f, int w, int fallback) {
        int v = keyStride(f);
        if (v >= w && v <= 8192) return v & ~1;
        return fallback;
    }

    /** Tinggi slice final: KEY_SLICE_HEIGHT kalau ada dan cukup, kalau tidak fallback. */
    static int sliceHeight(MediaFormat f, int h, int fallback) {
        int v = keySliceHeight(f);
        if (v >= h && v <= 8192) return v;
        return fallback;
    }
}
