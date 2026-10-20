package com.arkael.app;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Gauge melingkar: neon glow, animasi halus, berdenyut, warna otomatis (cyan -> oranye -> merah). */
public class GaugeView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lbl = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final ArgbEvaluator ev = new ArgbEvaluator();
    private ValueAnimator valueAnim, pulseAnim;
    private float shown = 0f, pulse = 0f;
    private String center = "--", label = "";
    private boolean autoColor = true;
    private int accent = 0xFF00E5FF;

    public GaugeView(Context c) { this(c, null); }
    public GaugeView(Context c, AttributeSet a) {
        super(c, a);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        track.setStyle(Paint.Style.STROKE); track.setStrokeCap(Paint.Cap.ROUND); track.setColor(0x22FFFFFF);
        arc.setStyle(Paint.Style.STROKE); arc.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE); glow.setStrokeCap(Paint.Cap.ROUND);
        txt.setColor(Color.WHITE); txt.setTextAlign(Paint.Align.CENTER);
        lbl.setColor(0xFF8B95A3); lbl.setTextAlign(Paint.Align.CENTER);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                txt.setTypeface(getResources().getFont(R.font.poppins_bold));
                lbl.setTypeface(getResources().getFont(R.font.poppins_medium));
            }
        } catch (Exception ignored) {}
    }

    /** percent 0..100, centerText mis. "62%", label mis. "CPU" */
    public void setValue(float percent, String centerText, String labelText) {
        center = centerText; label = labelText;
        float to = Math.max(0f, Math.min(100f, percent));
        if (valueAnim != null) valueAnim.cancel();
        valueAnim = ValueAnimator.ofFloat(shown, to);
        valueAnim.setDuration(700);
        valueAnim.setInterpolator(new DecelerateInterpolator());
        valueAnim.addUpdateListener(v -> { shown = (float) v.getAnimatedValue(); invalidate(); });
        valueAnim.start();
    }

    /** Matikan warna otomatis dan pakai warna tetap. */
    public void setAccent(int color) { autoColor = false; accent = color; invalidate(); }

    /** Untuk baterai: semakin kecil semakin merah. */
    private boolean invert = false;
    public void setInverted(boolean inv) { invert = inv; }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        pulseAnim = ValueAnimator.ofFloat(0f, 1f);
        pulseAnim.setDuration(1800);
        pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.addUpdateListener(v -> { pulse = (float) v.getAnimatedValue(); invalidate(); });
        pulseAnim.start();
    }
    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulseAnim != null) pulseAnim.cancel();
        if (valueAnim != null) valueAnim.cancel();
    }

    private int colorFor(float p) {
        if (!autoColor) return accent;
        float load = invert ? 100f - p : p;
        if (load < 60f) return 0xFF00E5FF;
        if (load < 85f) return (Integer) ev.evaluate((load - 60f) / 25f, 0xFF00E5FF, 0xFFFF6B35);
        return (Integer) ev.evaluate((load - 85f) / 15f, 0xFFFF6B35, 0xFFFF1744);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h) * 0.88f, sw = size * 0.10f;
        float cx = w / 2f, cy = h / 2f - size * 0.03f;
        box.set(cx - size / 2f + sw, cy - size / 2f + sw, cx + size / 2f - sw, cy + size / 2f - sw);
        track.setStrokeWidth(sw); arc.setStrokeWidth(sw);

        float sweep = 270f * shown / 100f;
        int col = colorFor(shown);
        c.drawArc(box, 135f, 270f, false, track);
        if (shown > 0.5f) {
            glow.setStrokeWidth(sw * 1.5f);
            glow.setColor(col);
            glow.setAlpha((int) (70 + 80 * pulse));
            glow.setMaskFilter(new BlurMaskFilter(sw * (0.9f + 0.5f * pulse), BlurMaskFilter.Blur.NORMAL));
            c.drawArc(box, 135f, sweep, false, glow);
            arc.setColor(col);
            c.drawArc(box, 135f, sweep, false, arc);
            double ang = Math.toRadians(135f + sweep);
            float rx = box.width() / 2f;
            dot.setColor(Color.WHITE);
            c.drawCircle(cx + (float) Math.cos(ang) * rx, (box.centerY()) + (float) Math.sin(ang) * rx, sw * 0.38f, dot);
        }
        txt.setTextSize(size * 0.22f);
        c.drawText(center, cx, cy + size * 0.06f, txt);
        lbl.setTextSize(size * 0.13f);
        c.drawText(label, cx, cy + size * 0.30f, lbl);
    }
}
