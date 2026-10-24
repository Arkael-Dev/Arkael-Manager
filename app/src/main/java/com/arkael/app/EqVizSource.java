package com.arkael.app;

import android.media.audiofx.Visualizer;

/** Mengambil spektrum FFT dari output mix (session 0) dan mengubahnya jadi 64 pita log 0..1. Butuh izin RECORD_AUDIO. */
final class EqVizSource implements Visualizer.OnDataCaptureListener {
    private final EqVisual sink;
    private Visualizer vz;
    private float agc = 0.3f;
    private final float[] out = new float[EqVisual.B];
    private float[] mag = new float[0];
    private int magBins = -1;
    String error = "";

    EqVizSource(EqVisual sink) { this.sink = sink; }

    boolean start() {
        stop();
        try {
            vz = new Visualizer(0);
            vz.setEnabled(false);
            int[] r = Visualizer.getCaptureSizeRange();
            vz.setCaptureSize(Math.min(r[1], 2048));
            vz.setDataCaptureListener(this, Visualizer.getMaxCaptureRate(), false, true);
            vz.setEnabled(true);
            return true;
        } catch (Throwable t) {
            error = EqEngine.why(t);
            stop();
            return false;
        }
    }

    void stop() {
        Visualizer v = vz;
        vz = null;
        if (v == null) return;
        try { v.setEnabled(false); } catch (Throwable ignored) { }
        try { v.setDataCaptureListener(null, 0, false, false); } catch (Throwable ignored) { }
        try { v.release(); } catch (Throwable ignored) { }
    }

    @Override public void onWaveFormDataCapture(Visualizer v, byte[] w, int sr) { }

    @Override public void onFftDataCapture(Visualizer v, byte[] f, int srMilli) {
        int n = f.length, bins = n / 2;
        if (bins < 8) return;
        if (magBins != bins) { mag = new float[bins + 1]; magBins = bins; }
        for (int k = 1; k < bins; k++) {
            float re = f[2 * k], im = f[2 * k + 1];
            mag[k] = (float) Math.sqrt(re * re + im * im);
        }
        mag[0] = mag[1];
        mag[bins] = mag[bins - 1];
        float sr = srMilli / 1000f;
        if (sr < 8000f) sr = 44100f;
        float binHz = sr / n;
        float lo = 40f, hi = Math.min(16000f, sr / 2f * 0.95f);
        float ratio = hi / lo, frameMax = 0;
        for (int b = 0; b < EqVisual.B; b++) {
            float f0 = (float) (lo * Math.pow(ratio, b / (double) EqVisual.B));
            float f1 = (float) (lo * Math.pow(ratio, (b + 1) / (double) EqVisual.B));
            int k0 = Math.max(1, Math.round(f0 / binHz)), k1 = Math.min(bins, Math.round(f1 / binHz));
            float m;
            if (k1 - k0 <= 1) {
                float fc = (float) Math.sqrt(f0 * f1) / binHz;
                int i0 = Math.max(0, Math.min(bins - 1, (int) fc));
                float fr = fc - i0;
                m = mag[i0] * (1 - fr) + mag[i0 + 1] * fr;
            } else {
                m = 0;
                for (int k = k0; k < k1; k++) if (mag[k] > m) m = mag[k];
            }
            float val = (float) (Math.log10(1 + m) / Math.log10(182));
            val *= 0.75f + 0.65f * b / EqVisual.B;
            out[b] = val;
            if (val > frameMax) frameMax = val;
        }
        if (frameMax < 0.04f) {
            for (int b = 0; b < EqVisual.B; b++) out[b] = 0;
        } else {
            agc = frameMax > agc ? frameMax : Math.max(0.25f, agc * 0.997f);
            for (int b = 0; b < EqVisual.B; b++) {
                float x = Math.min(1f, out[b] / agc);
                out[b] = (float) Math.pow(x, 1.3);
            }
        }
        sink.feed(out);
    }
}
