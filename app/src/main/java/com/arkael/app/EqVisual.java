package com.arkael.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.View;

/**
 * Visualizer ala Cava: 64 pita frekuensi (skala log), serangan cepat, jatuh dengan gravitasi,
 * haluskan antar-tetangga (efek "monstercat"), topi puncak yang jatuh pelan.
 * Enam gaya gambar dan delapan tema warna. Sumber data: EqVizSource (live) atau simulasi.
 */
final class EqVisual extends View {
    static final int S_BARS = 0, S_MIRROR = 1, S_WAVE = 2, S_CIRCLE = 3, S_DOTS = 4, S_BLOCKS = 5, S_COUNT = 6;
    static final int M_OFF = 0, M_LIVE = 1, M_SIM = 2;
    static final int B = 64;

    /** Palet tiap tema. Indeks RAINBOW dianimasikan lewat HSV. */
    static final int[][] THEMES = {
            {0xFF00E5FF, 0xFF3DFFA2},
            {0xFFFF512F, 0xFFF09819, 0xFFFFD200},
            {0xFF00F5A0, 0xFF00D9F5, 0xFF7B2FF7},
            {0xFFFF0080, 0xFFFF8C00, 0xFFFFE600},
            {0xFF00C6FF, 0xFF0072FF},
            {0xFFB721FF, 0xFF21D4FD},
            {0xFFFF0040, 0xFFFFE600, 0xFF00FF80, 0xFF00C8FF, 0xFF8A2BFF},
            {0xFFFFFFFF, 0xFF7A8594}
    };
    static final String[] THEME_NAMES = {"Neon", "Sunset", "Aurora", "Fire", "Ocean", "Violet", "Rainbow", "Mono"};
    static final int RAINBOW = 6;

    private final float[] target = new float[B];
    private final float[] val = new float[B], vel = new float[B], pk = new float[B], pv = new float[B], sm = new float[B];
    private final float[] hsv = new float[3];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path path = new Path(), fillPath = new Path();
    private final AudioManager am;
    private final float dd;

    private int style = S_BARS, theme = 0, mode = M_OFF;
    private long last = 0, musicCheck = 0;
    private boolean music;
    private float time;
    private Shader waveShader;

    EqVisual(Context c) {
        super(c);
        dd = c.getResources().getDisplayMetrics().density;
        am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    void setStyle(int s) { style = s; invalidate(); }
    int getStyle() { return style; }
    void setTheme(int t) { theme = Math.max(0, Math.min(THEMES.length - 1, t)); waveShader = null; invalidate(); }
    int getTheme() { return theme; }
    void setMode(int m) { mode = m; invalidate(); }

    /** Dipanggil dari thread Visualizer: 64 nilai 0..1. */
    void feed(float[] b) {
        synchronized (target) {
            System.arraycopy(b, 0, target, 0, Math.min(B, b.length));
        }
        postInvalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { waveShader = null; }

    // ------------------------------------------------------------------ fisika

    private void step(float dt) {
        float[] tg = new float[B];
        if (mode == M_LIVE) {
            synchronized (target) { System.arraycopy(target, 0, tg, 0, B); }
        } else if (mode == M_SIM && music) {
            float beat = (float) Math.pow(Math.max(0, Math.sin(time * 6.2832f * 2.1f)), 5);
            float beat2 = (float) Math.pow(Math.max(0, Math.sin(time * 6.2832f * 1.05f + 1.2f)), 8);
            for (int i = 0; i < B; i++) {
                float f = i / (float) B;
                float body = 0.20f + 0.30f * Math.abs((float) Math.sin(time * 2.6f + i * 0.55f)) * (1f - 0.5f * f);
                float v = body + (1f - f) * (0.55f * beat + 0.25f * beat2) + 0.12f * (float) Math.sin(time * 9f + i * 1.7f);
                tg[i] = Math.max(0f, Math.min(1f, v * (0.65f + 0.35f * (1f - f))));
            }
        }
        for (int i = 0; i < B; i++) {
            float t = tg[i];
            if (t >= val[i]) {
                val[i] += (t - val[i]) * Math.min(1f, 26f * dt);
                vel[i] = 0;
            } else {
                vel[i] += 5.2f * dt;
                val[i] -= vel[i] * dt;
                if (val[i] < t) val[i] = t;
            }
            if (val[i] < 0) val[i] = 0;
            if (val[i] >= pk[i]) { pk[i] = val[i]; pv[i] = 0; }
            else { pv[i] += 1.6f * dt; pk[i] -= pv[i] * dt; if (pk[i] < val[i]) pk[i] = val[i]; }
        }
        System.arraycopy(val, 0, sm, 0, B);
        for (int i = 1; i < B; i++) sm[i] = Math.max(sm[i], sm[i - 1] * 0.80f);
        for (int i = B - 2; i >= 0; i--) sm[i] = Math.max(sm[i], sm[i + 1] * 0.80f);
    }

    private float band(float[] a, int i, int n) {
        int s = (int) (i * B / (float) n), e = Math.max(s + 1, (int) ((i + 1) * B / (float) n));
        float m = 0;
        for (int k = s; k < e && k < B; k++) if (a[k] > m) m = a[k];
        return m;
    }

    // ------------------------------------------------------------------ warna

    private int colorAt(float t) {
        t = Math.max(0f, Math.min(1f, t));
        if (theme == RAINBOW) {
            hsv[0] = (t * 300f + time * 45f) % 360f;
            hsv[1] = 0.85f; hsv[2] = 1f;
            return Color.HSVToColor(hsv);
        }
        int[] pal = THEMES[theme];
        float x = t * (pal.length - 1);
        int i = Math.min(pal.length - 2, (int) x);
        return mix(pal[i], pal[i + 1], x - i);
    }

    private static int mix(int a, int b, float t) {
        return Color.argb(255,
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private int alpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }

    // ------------------------------------------------------------------ gambar

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        time += dt;
        if (now - musicCheck > 400) {
            musicCheck = now;
            try { music = am != null && am.isMusicActive(); } catch (Throwable t) { music = false; }
        }
        step(dt);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        switch (style) {
            case S_MIRROR: drawMirror(c, w, h); break;
            case S_WAVE: drawWave(c, w, h); break;
            case S_CIRCLE: drawCircle(c, w, h); break;
            case S_DOTS: drawLed(c, w, h, 32, 12, true); break;
            case S_BLOCKS: drawLed(c, w, h, 22, 14, false); break;
            default: drawBars(c, w, h); break;
        }
        float mx = 0;
        for (int i = 0; i < B; i++) if (sm[i] > mx) mx = sm[i];
        boolean busy = mx > 0.004f || (mode == M_SIM && music) || theme == RAINBOW && mx > 0.0005f;
        if (busy) postInvalidateOnAnimation(); else postInvalidateDelayed(160);
    }

    private void drawBars(Canvas c, int w, int h) {
        int n = 48;
        float padX = 6 * dd, base = h * 0.80f, top = 8 * dd;
        float slot = (w - 2 * padX) / n, bw = slot * 0.68f;
        for (int i = 0; i < n; i++) {
            float v = band(sm, i, n), pkv = band(pk, i, n);
            float bh = Math.max(2 * dd, v * (base - top));
            float x = padX + i * slot + (slot - bw) / 2f;
            int col = mix(colorAt(i / (float) (n - 1)), Color.WHITE, v * 0.22f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            rf.set(x, base - bh, x + bw, base);
            c.drawRoundRect(rf, Math.min(bw / 2f, 4 * dd), Math.min(bw / 2f, 4 * dd), p);
            p.setColor(alpha(col, 46));
            rf.set(x, base + 2 * dd, x + bw, base + 2 * dd + bh * 0.28f);
            c.drawRoundRect(rf, Math.min(bw / 2f, 4 * dd), Math.min(bw / 2f, 4 * dd), p);
            if (pkv > v + 0.02f) {
                float py = base - Math.max(2 * dd, pkv * (base - top)) - 4 * dd;
                p.setColor(alpha(Color.WHITE, 200));
                rf.set(x, py, x + bw, py + 2.4f * dd);
                c.drawRoundRect(rf, dd, dd, p);
            }
        }
    }

    private void drawMirror(Canvas c, int w, int h) {
        int n = 56;
        float padX = 6 * dd, cy = h / 2f, amp = h * 0.46f;
        float slot = (w - 2 * padX) / n, bw = slot * 0.62f;
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            float v = band(sm, i, n);
            float bh = Math.max(1.6f * dd, v * amp);
            float x = padX + i * slot + (slot - bw) / 2f;
            int col = mix(colorAt(i / (float) (n - 1)), Color.WHITE, v * 0.2f);
            p.setColor(col);
            rf.set(x, cy - bh, x + bw, cy + bh);
            c.drawRoundRect(rf, bw / 2f, bw / 2f, p);
        }
    }

    private void drawWave(Canvas c, int w, int h) {
        int n = 40;
        float padX = 6 * dd, cy = h * 0.5f, amp = h * 0.42f;
        if (waveShader == null) {
            int[] pal = theme == RAINBOW ? THEMES[RAINBOW] : THEMES[theme];
            int[] cs = new int[pal.length];
            System.arraycopy(pal, 0, cs, 0, pal.length);
            if (cs.length == 1) cs = new int[]{cs[0], cs[0]};
            waveShader = new LinearGradient(padX, 0, w - padX, 0, cs, null, Shader.TileMode.CLAMP);
        }
        for (int side = 0; side < 2; side++) {
            float sgn = side == 0 ? -1f : 1f, k = side == 0 ? 1f : 0.7f;
            path.reset();
            float px = padX, py = cy + sgn * band(sm, 0, n) * amp * k;
            path.moveTo(px, py);
            for (int i = 1; i < n; i++) {
                float x = padX + i * (w - 2 * padX) / (n - 1f);
                float y = cy + sgn * band(sm, i, n) * amp * k;
                path.quadTo(px, py, (px + x) / 2f, (py + y) / 2f);
                px = x; py = y;
            }
            path.lineTo(px, py);
            fillPath.set(path);
            fillPath.lineTo(w - padX, cy);
            fillPath.lineTo(padX, cy);
            fillPath.close();
            p.setShader(waveShader);
            p.setStyle(Paint.Style.FILL);
            p.setAlpha(side == 0 ? 70 : 38);
            c.drawPath(fillPath, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(8 * dd);
            p.setAlpha(side == 0 ? 50 : 26);
            c.drawPath(path, p);
            p.setStrokeWidth(2.8f * dd);
            p.setAlpha(side == 0 ? 255 : 150);
            c.drawPath(path, p);
            p.setShader(null);
            p.setAlpha(255);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dd);
        p.setColor(alpha(Color.WHITE, 40));
        c.drawLine(padX, cy, w - padX, cy, p);
    }

    private void drawCircle(Canvas c, int w, int h) {
        int n = 64;
        float cx = w / 2f, cy = h / 2f, m = Math.min(w, h);
        float r0 = m * 0.26f, maxLen = m * 0.21f, rot = time * 0.25f;
        float bass = 0;
        for (int i = 0; i < 7; i++) bass += sm[i];
        bass /= 7f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(alpha(colorAt(0.2f), 40));
        c.drawCircle(cx, cy, r0 * (0.88f + 0.22f * bass), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f * dd);
        p.setColor(alpha(colorAt(0.5f), 150));
        c.drawCircle(cx, cy, r0 * (0.96f + 0.08f * bass), p);
        p.setStrokeWidth(3.2f * dd);
        for (int i = 0; i < n; i++) {
            int idx = i < n / 2 ? i : n - 1 - i;
            float v = band(sm, idx, n / 2);
            float a = (float) (i / (float) n * 2 * Math.PI - Math.PI / 2 + rot);
            float len = 3 * dd + v * maxLen;
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            p.setColor(mix(colorAt(idx / (n / 2f - 1f)), Color.WHITE, v * 0.2f));
            c.drawLine(cx + cs * r0, cy + sn * r0, cx + cs * (r0 + len), cy + sn * (r0 + len), p);
        }
    }

    private void drawLed(Canvas c, int w, int h, int cols, int rows, boolean round) {
        float padX = 8 * dd, padY = 6 * dd;
        float cw = (w - 2 * padX) / cols, ch = (h - 2 * padY) / rows;
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < cols; i++) {
            float v = band(sm, i, cols), pkv = band(pk, i, cols);
            int lit = Math.round(v * rows), pr = Math.min(rows - 1, Math.round(pkv * rows));
            for (int r = 0; r < rows; r++) {
                float x = padX + i * cw, y = h - padY - (r + 1) * ch;
                int col = colorAt(r / (float) (rows - 1));
                boolean on = r < lit;
                boolean cap = !on && r == pr - 1 && pr > lit + 0;
                p.setColor(on ? col : cap ? alpha(Color.WHITE, 210) : alpha(col, 24));
                if (round) {
                    c.drawCircle(x + cw / 2f, y + ch / 2f, Math.min(cw, ch) * 0.32f, p);
                } else {
                    rf.set(x + cw * 0.1f, y + ch * 0.14f, x + cw * 0.9f, y + ch * 0.86f);
                    c.drawRoundRect(rf, 2 * dd, 2 * dd, p);
                }
            }
        }
    }
}
