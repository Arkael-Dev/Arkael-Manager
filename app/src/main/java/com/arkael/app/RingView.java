package com.arkael.app;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Small animated ring for the overlay (same look as the gauges on the main screen, but cheap to draw):
 * the arc sweeps to each new value, glows softly, and a light streak travels along it.
 * No blur filters and no software layer, so it costs almost nothing on top of a game.
 */
final class RingView extends View {
    private static final float START = 150f, TOTAL = 240f;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint under = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final ArgbEvaluator ev = new ArgbEvaluator();
    private ValueAnimator anim;
    private float shown, phase;
    private boolean valid;
    private String center = "--";
    private int base = 0xFF00E5FF;
    private boolean heat = true, inverted, animated = true;
    private int sizePx;

    RingView(Context c, int sizePx) {
        super(c);
        this.sizePx = Math.max(8, sizePx);
        track.setStyle(Paint.Style.STROKE); track.setStrokeCap(Paint.Cap.ROUND); track.setColor(0x2AFFFFFF);
        under.setStyle(Paint.Style.STROKE); under.setStrokeCap(Paint.Cap.ROUND);
        arc.setStyle(Paint.Style.STROKE); arc.setStrokeCap(Paint.Cap.ROUND);
        shine.setStyle(Paint.Style.STROKE); shine.setStrokeCap(Paint.Cap.ROUND);
        dot.setColor(Color.WHITE);
        txt.setColor(Color.WHITE); txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    void setSizePx(int px) {
        sizePx = Math.max(8, px);
        requestLayout();
        invalidate();
    }

    void setAnimated(boolean a) { animated = a; invalidate(); }

    /** 0..1 position of the travelling light streak and the glow breathing. */
    void setPhase(float p) { phase = p; invalidate(); }

    /** percent < 0 means "no data": the ring stays empty and shows the text only. */
    void set(float percent, String centerText, int baseColor, boolean heatColor, boolean inv) {
        center = centerText; base = baseColor; heat = heatColor; inverted = inv;
        valid = percent >= 0f;
        float to = valid ? Math.min(100f, percent) : 0f;
        if (anim != null) anim.cancel();
        if (!animated || Math.abs(to - shown) < 0.5f) { shown = to; invalidate(); return; }
        anim = ValueAnimator.ofFloat(shown, to);
        anim.setDuration(450);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { shown = (Float) a.getAnimatedValue(); invalidate(); }
        });
        anim.start();
    }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(sizePx, sizePx); }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) anim.cancel();
    }

    private int colorFor(float p) {
        if (!heat) return base;
        float load = inverted ? 100f - p : p;
        if (load < 65f) return base;
        if (load < 88f) return (Integer) ev.evaluate((load - 65f) / 23f, base, 0xFFFFA000);
        return (Integer) ev.evaluate((load - 88f) / 12f, 0xFFFFA000, 0xFFFF3D3D);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h), sw = size * 0.13f;
        float cx = w / 2f, cy = h / 2f;
        box.set(cx - size / 2f + sw, cy - size / 2f + sw, cx + size / 2f - sw, cy + size / 2f - sw);
        track.setStrokeWidth(sw);
        arc.setStrokeWidth(sw);
        c.drawArc(box, START, TOTAL, false, track);

        if (shown > 0.5f) {
            float sweep = TOTAL * shown / 100f;
            int col = colorFor(shown);
            float breath = animated ? 0.5f + 0.5f * (float) Math.sin(phase * 2.0 * Math.PI) : 0.5f;
            under.setStrokeWidth(sw * 1.7f);
            under.setColor(col);
            under.setAlpha((int) (35 + 45 * breath));
            c.drawArc(box, START, sweep, false, under);
            arc.setColor(col);
            c.drawArc(box, START, sweep, false, arc);
            if (animated && sweep > 24f) {
                float seg = Math.min(34f, sweep * 0.4f);
                shine.setStrokeWidth(sw * 0.5f);
                shine.setColor(Color.WHITE);
                shine.setAlpha(120);
                c.drawArc(box, START + (sweep - seg) * phase, seg, false, shine);
            }
            double ang = Math.toRadians(START + sweep);
            float r = box.width() / 2f;
            c.drawCircle(box.centerX() + (float) Math.cos(ang) * r, box.centerY() + (float) Math.sin(ang) * r, sw * 0.36f, dot);
        }
        float ts = size * (center.length() > 3 ? 0.25f : 0.31f);
        txt.setTextSize(ts);
        txt.setAlpha(valid ? 255 : 130);
        c.drawText(center, cx, cy + ts * 0.34f, txt);
    }
}
