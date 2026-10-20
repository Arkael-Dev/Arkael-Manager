package com.arkael.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

/**
 * Overlay transparan di atas banner (id header_banner):
 *  - gauge 3 busur digambar live (intro sweep, napas, comet orbit, ripple tengah)
 *  - kilau diagonal tiap beberapa detik
 *  - sudut banner dipotong rounded, titik "LIVE" berkedip
 * Berhenti otomatis saat tidak terlihat / animasi sistem dimatikan.
 */
public class BannerFxView extends View {
    private static final float IMG_W = 1440f, IMG_H = 600f, ASPECT = IMG_W / IMG_H;
    private static final float CX = 1130f, CY = 285f;           // pusat gauge pada PNG
    private static final float START = 135f, SWEEP = 270f;
    private static final float[] RAD  = {192f, 140f, 88f};
    private static final float[] STK  = {30f, 24f, 24f};
    private static final float[] FILL = {0.85f, 0.65f, 0.50f};
    private static final int[] COL = {0xFF00E5FF, 0xFFFF6B35, 0xFFB388FF};

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    private ValueAnimator clock;
    private ObjectAnimator livePulse;
    private View liveView;
    private float t = 0f;
    private long introStart = -1;
    private boolean animOk = true;
    private Shader shineShader;
    private float shineW = -1f;

    public BannerFxView(Context c) { this(c, null); }

    public BannerFxView(Context c, AttributeSet a) {
        super(c, a);
        track.setStyle(Paint.Style.STROKE); track.setStrokeCap(Paint.Cap.ROUND); track.setColor(0x20FFFFFF);
        arc.setStyle(Paint.Style.STROKE); arc.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE); glow.setStrokeCap(Paint.Cap.ROUND);
        ripple.setStyle(Paint.Style.STROKE); ripple.setColor(0xFF00E5FF);
        dot.setColor(Color.WHITE);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animOk = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
        roundParent();
        forceDefaultBanner();
        liveView = getRootView().findViewById(R.id.tv_live);
        updateRunning();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAll();
    }

    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        updateRunning();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateRunning();
    }

    /** Fitur Change Banner dihapus: apa pun yang tersimpan, tampilkan banner bawaan. */
    private void forceDefaultBanner() {
        View p = (View) getParent();
        if (p == null) return;
        View v = p.findViewById(R.id.header_banner);
        if (v instanceof ImageView) ((ImageView) v).setImageResource(R.drawable.header_bg);
    }

    /** Potong sudut banner (gambar + overlay) mengikuti card rounded. */
    private void roundParent() {
        if (!(getParent() instanceof ViewGroup)) return;
        final float r = dp(18);
        ViewGroup p = (ViewGroup) getParent();
        p.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        p.setClipToOutline(true);
    }

    private void updateRunning() {
        if (!isAttachedToWindow()) return;
        boolean show = isShown() && getWindowVisibility() == View.VISIBLE;
        if (show && animOk) {
            if (clock == null) {
                clock = ValueAnimator.ofFloat(0f, 1f);
                clock.setDuration(8000);
                clock.setInterpolator(new LinearInterpolator());
                clock.setRepeatCount(ValueAnimator.INFINITE);
                clock.addUpdateListener(v -> { t = (float) v.getAnimatedValue(); invalidate(); });
                clock.start();
            }
            if (introStart < 0) introStart = SystemClock.uptimeMillis();
            if (liveView != null && livePulse == null) {
                livePulse = ObjectAnimator.ofFloat(liveView, View.ALPHA, 1f, 0.4f);
                livePulse.setDuration(900);
                livePulse.setRepeatMode(ValueAnimator.REVERSE);
                livePulse.setRepeatCount(ValueAnimator.INFINITE);
                livePulse.start();
            }
        } else {
            stopAll();
            if (show) invalidate();   // animasi dimatikan: gambar statis
        }
    }

    private void stopAll() {
        if (clock != null) { clock.cancel(); clock = null; }
        if (livePulse != null) {
            livePulse.cancel(); livePulse = null;
            if (liveView != null) liveView.setAlpha(1f);
        }
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float t01 = animOk ? t : 0.25f;

        drawShine(c, w, h, t01);

        float s = Math.max(w / IMG_W, h / IMG_H);
        float cx = (w - IMG_W * s) / 2f + CX * s;
        float cy = (h - IMG_H * s) / 2f + CY * s;

        float ip = (!animOk || introStart < 0) ? 1f
                : Math.min(1f, (SystemClock.uptimeMillis() - introStart) / 1400f);
        float e = 1f - (1f - ip) * (1f - ip) * (1f - ip);

        for (int i = 0; i < 3; i++) {
            float r = RAD[i] * s, sw = STK[i] * s;
            box.set(cx - r, cy - r, cx + r, cy + r);
            track.setStrokeWidth(sw);
            c.drawArc(box, START, SWEEP, false, track);

            float breath = animOk ? 0.10f * (float) Math.sin(2 * Math.PI * (t01 * (i + 1) + i * 0.3f)) : 0f;
            float f = Math.max(0.02f, Math.min(1f, (FILL[i] + breath) * e));
            glow.setColor(COL[i]); glow.setAlpha(46); glow.setStrokeWidth(sw * 1.9f);
            c.drawArc(box, START, SWEEP * f, false, glow);
            arc.setColor(COL[i]); arc.setStrokeWidth(sw);
            c.drawArc(box, START, SWEEP * f, false, arc);
        }

        if (animOk) {
            // comet mengorbit di cincin cyan (4 dtk / putaran)
            float rc = RAD[0] * s, head = STK[0] * s * 0.32f;
            double th = 2 * Math.PI * ((t01 * 2f) % 1f);
            for (int k = 0; k < 14; k++) {
                double a = th - k * 0.05;
                float al = 1f - k / 14f;
                dot.setAlpha((int) (210 * al * al));
                c.drawCircle(cx + (float) Math.cos(a) * rc, cy + (float) Math.sin(a) * rc,
                        head * (1f - k * 0.045f), dot);
            }
            // ripple dari titik tengah (2 dtk)
            float pr = (t01 * 4f) % 1f;
            ripple.setStrokeWidth(2f * s);
            ripple.setAlpha((int) (90 * (1f - pr)));
            c.drawCircle(cx, cy, (10f + 70f * pr) * s, ripple);
        }

        float pulse = animOk ? 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * t01 * 4f) : 0f;
        dot.setAlpha(255);
        c.drawCircle(cx, cy, (8f + 3f * pulse) * s, dot);
    }

    /** Kilau diagonal yang lewat ~3,6 dtk lalu jeda ~4,4 dtk. */
    private void drawShine(Canvas c, float w, float h, float t01) {
        float p = t01 / 0.45f;
        if (p >= 1f) return;
        p = p * p * (3f - 2f * p);
        float bw = w * 0.22f;
        if (shineShader == null || shineW != bw) {
            shineW = bw;
            shineShader = new LinearGradient(0, 0, bw, 0, new int[]{0x00FFFFFF, 0x18FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            shine.setShader(shineShader);
        }
        float x = -bw * 1.5f + (w + bw * 3f) * p;
        c.save();
        c.clipRect(0, 0, w, h);
        c.translate(x, 0);
        c.skew(-0.35f, 0);
        c.drawRect(0, 0, bw, h, shine);
        c.restore();
    }
}
