package com.arkael.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Game Launcher, gaming-phone style (landscape): dark navy theme with neon cards in a swipeable carousel
 * (centre game big, neighbours tilted), live battery/CPU/GPU on top, SETELAN GAME | MAINKAN | TAMBAH GAME at the bottom.
 * Mainkan = apply the game's saved profile, switch the Game Panel on, then launch with animation + sound.
 * Remembers how long each game was played and shows it on its card.
 */
public class GameLauncherActivity extends Activity {
    private static final String PREFS = "gl_prefs";
    private static final String K_MANUAL = "manual", K_HIDDEN = "hidden", K_MUTE = "mute";

    private static final int RED = Color.parseColor("#FF1E3C");
    private static final int PANEL = Color.parseColor("#14141A");
    private static final int SUB = Color.parseColor("#8E8E9A");
    private static final int GREEN = Color.parseColor("#3DFFA2");
    private static final int NEON_PINK = Color.parseColor("#FF3D9A"), NEON_VIOLET = Color.parseColor("#8A5CFF"), NEON_BLUE = Color.parseColor("#3D8BFF");

    private static final int I_BAT = 0, I_CPU = 1, I_GPU = 2, I_SPK = 3, I_MUTE = 4, I_PLUS = 5, I_GEAR = 6, I_BOLT = 7;

    // =================================================================== custom drawing

    /** Rectangle with chamfered (slanted) corners. */
    static void buildPath(Path p, RectF r, float cut, boolean[] c) {
        float l = r.left, t = r.top, rr = r.right, b = r.bottom;
        p.reset();
        p.moveTo(l + (c[0] ? cut : 0), t);
        p.lineTo(rr - (c[1] ? cut : 0), t);
        if (c[1]) p.lineTo(rr, t + cut);
        p.lineTo(rr, b - (c[2] ? cut : 0));
        if (c[2]) p.lineTo(rr - cut, b);
        p.lineTo(l + (c[3] ? cut : 0), b);
        if (c[3]) p.lineTo(l, b - cut);
        p.lineTo(l, t + (c[0] ? cut : 0));
        if (c[0]) p.lineTo(l + cut, t);
        p.close();
    }

    static final class Cut extends Drawable {
        private final Paint f = new Paint(Paint.ANTI_ALIAS_FLAG), s = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float cut, sw;
        private final boolean[] c;
        private final Path path = new Path();

        Cut(int fill, int stroke, float sw, float cut, boolean tl, boolean tr, boolean br, boolean bl) {
            this.cut = cut; this.sw = sw; this.c = new boolean[]{tl, tr, br, bl};
            f.setColor(fill);
            s.setStyle(Paint.Style.STROKE); s.setColor(stroke); s.setStrokeWidth(sw); s.setStrokeJoin(Paint.Join.MITER);
        }
        @Override public void draw(Canvas cv) {
            RectF r = new RectF(getBounds());
            r.inset(sw / 2f, sw / 2f);
            buildPath(path, r, cut, c);
            cv.drawPath(path, f);
            if (sw > 0) cv.drawPath(path, s);
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter cf) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** All the little icons, drawn as vectors on a 24x24 grid. */
    static void drawIco(Canvas cv, int type, float cx, float cy, float size, int color, float level, Paint p) {
        cv.save();
        cv.translate(cx - size / 2f, cy - size / 2f);
        float sc = size / 24f;
        cv.scale(sc, sc);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (type) {
            case I_BAT: {
                cv.drawRoundRect(2.5f, 7f, 19f, 17f, 2f, 2f, p);
                p.setStyle(Paint.Style.FILL);
                cv.drawRect(20.5f, 10.5f, 22.5f, 13.5f, p);
                cv.drawRect(5f, 9.5f, 5f + 11f * Math.max(0.05f, Math.min(1f, level)), 14.5f, p);
                break;
            }
            case I_CPU: {
                cv.drawRoundRect(6f, 6f, 18f, 18f, 1.5f, 1.5f, p);
                cv.drawRect(9.5f, 9.5f, 14.5f, 14.5f, p);
                float[] k = {9f, 12f, 15f};
                for (float v : k) {
                    cv.drawLine(v, 2.5f, v, 6f, p);
                    cv.drawLine(v, 18f, v, 21.5f, p);
                    cv.drawLine(2.5f, v, 6f, v, p);
                    cv.drawLine(18f, v, 21.5f, v, p);
                }
                break;
            }
            case I_GPU: {
                cv.drawRoundRect(2f, 6f, 22f, 17f, 2f, 2f, p);
                cv.drawCircle(9f, 11.5f, 3.2f, p);
                cv.drawLine(14.5f, 9f, 19f, 9f, p);
                cv.drawLine(14.5f, 12f, 19f, 12f, p);
                cv.drawLine(14.5f, 14.5f, 17f, 14.5f, p);
                cv.drawLine(5f, 20.5f, 19f, 20.5f, p);
                break;
            }
            case I_SPK:
            case I_MUTE: {
                Path sp = new Path();
                sp.moveTo(3f, 9f); sp.lineTo(7f, 9f); sp.lineTo(12f, 5f); sp.lineTo(12f, 19f); sp.lineTo(7f, 15f); sp.lineTo(3f, 15f); sp.close();
                cv.drawPath(sp, p);
                if (type == I_SPK) {
                    cv.drawArc(new RectF(8f, 8f, 16f, 16f), -45f, 90f, false, p);
                    cv.drawArc(new RectF(4f, 4f, 20f, 20f), -45f, 90f, false, p);
                } else {
                    cv.drawLine(15.5f, 9f, 21f, 15f, p);
                    cv.drawLine(21f, 9f, 15.5f, 15f, p);
                }
                break;
            }
            case I_PLUS: {
                cv.drawLine(12f, 5f, 12f, 19f, p);
                cv.drawLine(5f, 12f, 19f, 12f, p);
                break;
            }
            case I_GEAR: {
                cv.drawCircle(12f, 12f, 3f, p);
                cv.drawCircle(12f, 12f, 6.8f, p);
                for (int k = 0; k < 8; k++) {
                    double a = Math.toRadians(k * 45);
                    cv.drawLine(12f + (float) Math.cos(a) * 6.8f, 12f + (float) Math.sin(a) * 6.8f,
                            12f + (float) Math.cos(a) * 10f, 12f + (float) Math.sin(a) * 10f, p);
                }
                break;
            }
            case I_BOLT: {
                p.setStyle(Paint.Style.FILL);
                Path b = new Path();
                b.moveTo(13f, 2f); b.lineTo(4f, 14f); b.lineTo(11f, 14f); b.lineTo(10f, 22f); b.lineTo(20f, 9.5f); b.lineTo(12.5f, 9.5f); b.close();
                cv.drawPath(b, p);
                break;
            }
        }
        cv.restore();
    }

    static final class Ico extends View {
        int type, color;
        float level = 1f;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Ico(Context c, int type, int color) { super(c); this.type = type; this.color = color; }
        void set(int type, int color) { this.type = type; this.color = color; invalidate(); }
        @Override protected void onDraw(Canvas cv) {
            drawIco(cv, type, getWidth() / 2f, getHeight() / 2f, Math.min(getWidth(), getHeight()), color, level, p);
        }
    }

    static void hexPath(Path p, float cx, float cy, float r, float rotDeg) {
        p.reset();
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(60 * k - 90 + rotDeg);
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            if (k == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
    }

    /** Game icon cropped into a hexagon with a coloured edge. spin = rotating dashed outer ring. */
    static final class HexIcon extends View {
        private Drawable d;
        private int color;
        private final float sw;
        private final boolean spin;
        private float rot;
        private ValueAnimator va;
        private DashPathEffect dash;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path hex = new Path();

        HexIcon(Context c, Drawable d, int color, float sw, boolean spin) {
            super(c); this.d = d; this.color = color; this.sw = sw; this.spin = spin;
        }
        void set(Drawable d, int color) { this.d = d; this.color = color; invalidate(); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float r = Math.min(w, h) / 2f;
            dash = new DashPathEffect(new float[]{r * 0.45f, r * 0.22f}, 0);
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (spin) {
                va = ValueAnimator.ofFloat(0f, 360f);
                va.setDuration(9000);
                va.setRepeatCount(ValueAnimator.INFINITE);
                va.setInterpolator(new LinearInterpolator());
                va.addUpdateListener(a -> { rot = (Float) a.getAnimatedValue(); invalidate(); });
                va.start();
            }
        }
        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (va != null) { va.cancel(); va = null; }
        }
        @Override protected void onDraw(Canvas cv) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, R = Math.min(cx, cy) - sw;
            if (spin) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(sw * 0.6f);
                p.setColor(color);
                p.setPathEffect(dash);
                hexPath(hex, cx, cy, R, rot);
                cv.drawPath(hex, p);
                p.setPathEffect(null);
            }
            float ri = spin ? R * 0.8f : R;
            if (d != null) {
                hexPath(hex, cx, cy, ri, 0);
                cv.save();
                cv.clipPath(hex);
                d.setBounds((int) (cx - ri), (int) (cy - ri), (int) (cx + ri), (int) (cy + ri));
                d.draw(cv);
                cv.restore();
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setColor(color);
            hexPath(hex, cx, cy, ri, 0);
            cv.drawPath(hex, p);
        }
    }

    /** The big MAINKAN button: slanted corners, red gradient, pulsing glow and a shine that sweeps across. */
    static final class PlayBtn extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
                txt = new Paint(Paint.ANTI_ALIAS_FLAG), sub = new Paint(Paint.ANTI_ALIAS_FLAG),
                shine = new Paint(Paint.ANTI_ALIAS_FLAG), ico = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path(), band = new Path();
        private final float d;
        private float phase, pulse;
        private ValueAnimator va;

        PlayBtn(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            line.setStyle(Paint.Style.STROKE);
            txt.setColor(Color.WHITE); txt.setTextSize(19 * d); txt.setTextAlign(Paint.Align.CENTER);
            txt.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)); txt.setLetterSpacing(0.14f);
            sub.setColor(Color.parseColor("#FFD3DA")); sub.setTextSize(10 * d); sub.setTextAlign(Paint.Align.CENTER);
            shine.setColor(Color.WHITE); shine.setAlpha(60);
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            buildPath(path, new RectF(5 * d, 5 * d, w - 5 * d, h - 5 * d), 16 * d, new boolean[]{true, false, true, false});
            fill.setShader(new LinearGradient(0, 0, w, h, Color.parseColor("#FF2A48"), Color.parseColor("#A80020"), Shader.TileMode.CLAMP));
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(2600);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.setInterpolator(new LinearInterpolator());
            va.addUpdateListener(a -> {
                phase = (Float) a.getAnimatedValue();
                pulse = (float) (0.5 + 0.5 * Math.sin(phase * 2 * Math.PI));
                invalidate();
            });
            va.start();
        }
        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (va != null) { va.cancel(); va = null; }
        }
        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            line.setColor(RED); line.setAlpha((int) (70 + 120 * pulse)); line.setStrokeWidth(7 * d);
            cv.drawPath(path, line);                       // soft pulsing glow
            cv.drawPath(path, fill);
            cv.save();
            cv.clipPath(path);
            float x = (phase * 1.6f - 0.35f) * w, bw = w * 0.14f, skew = h * 0.55f;
            band.reset();
            band.moveTo(x + skew, 0); band.lineTo(x + skew + bw, 0); band.lineTo(x + bw, h); band.lineTo(x, h); band.close();
            cv.drawPath(band, shine);
            cv.restore();
            line.setColor(Color.parseColor("#FF7388")); line.setAlpha(255); line.setStrokeWidth(1.5f * d);
            cv.drawPath(path, line);
            float tw = txt.measureText("MAINKAN");
            float x0 = (w - (34 * d + tw)) / 2f;
            drawIco(cv, I_BOLT, x0 + 12 * d, h / 2f, 24 * d, Color.WHITE, 1f, ico);
            float cx = x0 + 34 * d + tw / 2f;
            cv.drawText("MAINKAN", cx, h * 0.5f, txt);
            cv.drawText("Profil otomatis aktif", cx, h * 0.5f + 15 * d, sub);
        }
    }

    /** One game card of the carousel: game art as backdrop, neon animated border, round icon, name and play time. */
    static final class GameCard extends View {
        int idx = -1;
        private Drawable icon;
        private String name = "", sub = "";
        private float focus, phase;
        private final float d;
        private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
                shadeP = new Paint(), dim = new Paint(), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint txt = new TextPaint(Paint.ANTI_ALIAS_FLAG), subP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path(), circ = new Path();
        private final Matrix mx = new Matrix();
        private LinearGradient neon;
        private ValueAnimator va;

        GameCard(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            base.setColor(Color.parseColor("#0B1024"));
            line.setStyle(Paint.Style.STROKE);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(2 * d);
            txt.setColor(Color.WHITE); txt.setTextSize(16 * d);
            txt.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            subP.setColor(Color.parseColor("#C9D2F0")); subP.setTextSize(10.5f * d);
        }

        void bind(Drawable ic, String n, String s) { icon = ic; name = n == null ? "" : n; sub = s == null ? "" : s; invalidate(); }
        void setFocus(float f) { if (Math.abs(f - focus) > 0.004f) { focus = f; invalidate(); } }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            buildPath(path, new RectF(6 * d, 6 * d, w - 6 * d, h - 6 * d), 16 * d, new boolean[]{true, false, true, false});
            neon = new LinearGradient(0, 0, w, 0, new int[]{NEON_PINK, NEON_VIOLET, NEON_BLUE}, null, Shader.TileMode.MIRROR);
            shadeP.setShader(new LinearGradient(0, 0, 0, h, Color.parseColor("#40050914"), Color.parseColor("#F0050914"), Shader.TileMode.CLAMP));
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(3600);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.setInterpolator(new LinearInterpolator());
            va.addUpdateListener(a -> {
                phase = (Float) a.getAnimatedValue();
                if (focus > 0.3f && getVisibility() == VISIBLE) invalidate();
            });
            va.start();
        }
        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (va != null) { va.cancel(); va = null; }
        }
        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            if (neon == null) return;
            mx.setTranslate(phase * 2f * w, 0);
            neon.setLocalMatrix(mx);
            line.setShader(neon);
            // soft neon glow outside the card
            line.setStrokeWidth(10 * d); line.setAlpha((int) (30 + 80 * focus)); cv.drawPath(path, line);
            line.setStrokeWidth(5 * d);  line.setAlpha((int) (50 + 100 * focus)); cv.drawPath(path, line);

            cv.save();
            cv.clipPath(path);
            cv.drawPath(path, base);
            if (icon != null) {
                float s = w * 1.15f, cx = w / 2f, cy = h * 0.40f;
                icon.setBounds((int) (cx - s / 2), (int) (cy - s / 2), (int) (cx + s / 2), (int) (cy + s / 2));
                icon.draw(cv);
            }
            cv.drawRect(0, 0, w, h, shadeP);
            if (focus < 1f) {
                dim.setColor(Color.argb((int) (120 * (1f - focus)), 5, 8, 22));
                cv.drawRect(0, 0, w, h, dim);
            }
            cv.restore();

            line.setStrokeWidth(2.2f * d); line.setAlpha((int) (170 + 85 * focus)); cv.drawPath(path, line);

            // round game icon + texts at the bottom
            float r = 21 * d, cxI = 16 * d + r + 6 * d, cyI = h - 14 * d - r - 6 * d;
            if (icon != null) {
                circ.reset();
                circ.addCircle(cxI, cyI, r, Path.Direction.CW);
                cv.save();
                cv.clipPath(circ);
                icon.setBounds((int) (cxI - r), (int) (cyI - r), (int) (cxI + r), (int) (cyI + r));
                icon.draw(cv);
                cv.restore();
            }
            ring.setColor(Color.WHITE); ring.setAlpha((int) (110 + 120 * focus));
            cv.drawCircle(cxI, cyI, r + 1.5f * d, ring);
            float tx = cxI + r + 11 * d, avail = w - tx - 16 * d;
            if (avail > 20 * d) {
                cv.drawText(TextUtils.ellipsize(name, txt, avail, TextUtils.TruncateAt.END).toString(), tx, cyI - 1 * d, txt);
                cv.drawText(TextUtils.ellipsize(sub, subP, avail, TextUtils.TruncateAt.END).toString(), tx, cyI + 15 * d, subP);
            }
        }
    }

    /** Holds the carousel cards, all the same size and centred; the activity moves and tilts them. */
    static final class Stage extends FrameLayout {
        int cw, ch, sp;
        Runnable onLaid;

        Stage(Context c) {
            super(c);
            setClipChildren(false);
            setClipToPadding(false);
        }
        @Override protected void onMeasure(int wms, int hms) {
            int w = MeasureSpec.getSize(wms), h = MeasureSpec.getSize(hms);
            cw = (int) Math.min(h * 0.92f * 1.7f, w * 0.40f);
            ch = (int) (cw / 1.7f);
            sp = (int) (cw * 0.97f);
            int a = MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), b = MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(a, b);
            setMeasuredDimension(w, h);
        }
        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int w = r - l, h = b - t, x = (w - cw) / 2, y = (h - ch) / 2;
            for (int i = 0; i < getChildCount(); i++) getChildAt(i).layout(x, y, x + cw, y + ch);
            if (changed && onLaid != null) onLaid.run();
        }
    }

    /** Dark navy background with a breathing neon glow and rotating HUD rings behind the cards. */
    static final class BgView extends View {
        private final Paint bg = new Paint(), glow = new Paint(), glow2 = new Paint(), ringP = new Paint(Paint.ANTI_ALIAS_FLAG), thin = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private float phase;
        private ValueAnimator va;
        private DashPathEffect dashA, dashB;

        BgView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            ringP.setStyle(Paint.Style.STROKE);
            thin.setStyle(Paint.Style.STROKE);
            thin.setStrokeWidth(1 * d);
            thin.setColor(Color.argb(40, 140, 120, 255));
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            bg.setShader(new LinearGradient(0, 0, 0, h, Color.parseColor("#0D1233"), Color.parseColor("#04050D"), Shader.TileMode.CLAMP));
            float cy = cy(h);
            glow.setShader(new RadialGradient(w / 2f, cy, Math.max(w, h) * 0.46f, Color.argb(255, 120, 60, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            glow2.setShader(new RadialGradient(w / 2f, h * 1.02f, w * 0.36f, Color.argb(255, 255, 30, 60), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            float r = h * 0.36f;
            dashA = new DashPathEffect(new float[]{r * 0.22f, r * 0.10f}, 0);
            dashB = new DashPathEffect(new float[]{r * 0.06f, r * 0.09f}, 0);
        }
        private float cy(int h) { return 56 * d + (h - 56 * d - 118 * d) / 2f; }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(14000);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.setInterpolator(new LinearInterpolator());
            va.addUpdateListener(a -> { phase = (Float) a.getAnimatedValue(); invalidate(); });
            va.start();
        }
        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (va != null) { va.cancel(); va = null; }
        }
        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            cv.drawRect(0, 0, w, h, bg);
            double br = Math.sin(phase * 2 * Math.PI * 2);
            glow.setAlpha((int) (70 + 26 * br));
            cv.drawRect(0, 0, w, h, glow);
            glow2.setAlpha((int) (60 + 20 * br));
            cv.drawRect(0, 0, w, h, glow2);
            float cx = w / 2f, cy = cy(h), r = h * 0.36f;
            cv.drawCircle(cx, cy, r * 1.32f, thin);
            ringP.setStrokeWidth(2.2f * d);
            ringP.setColor(Color.argb(110, 150, 110, 255));
            ringP.setPathEffect(dashA);
            cv.save(); cv.rotate(phase * 360f, cx, cy);
            cv.drawCircle(cx, cy, r, ringP);
            cv.restore();
            ringP.setStrokeWidth(3.5f * d);
            ringP.setColor(Color.argb(80, 70, 140, 255));
            ringP.setPathEffect(dashB);
            cv.save(); cv.rotate(-phase * 360f * 0.6f, cx, cy);
            cv.drawCircle(cx, cy, r * 1.16f, ringP);
            cv.restore();
            ringP.setPathEffect(null);
        }
    }

    // =================================================================== activity

    private static final float TILT = 26f;   // side-card tilt in degrees; flip the sign if it leans the wrong way

    private static final class Game {
        String pkg, name;
        Drawable icon;
    }

    private final List<Game> games = new ArrayList<Game>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SysProbe probe = new SysProbe();
    private PackageManager pm;
    private SharedPreferences sp;

    private FrameLayout root, overlay;
    private Stage stage;
    private final GameCard[] cards = new GameCard[7];
    private HexIcon ovIcon, ovRing, ovRing2;
    private Ico muteIco, icBat, icCpu, icGpu;
    private TextView summary, emptyView, stBat, stCpu, stGpu, ovText;
    private SoundPool pool;
    private int sOpen, sLaunch, sSelect, sTab, sel = 0;
    private float pos = 0f;
    private ValueAnimator posAnim;
    private boolean muted, launching, pollOn, settled;
    private volatile boolean polling;
    private long prevIdle = -1, prevTotal = -1;
    private String gpuPath;
    private int gpuTries;
    private Boolean rootOk;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        pm = getPackageManager();
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        muted = sp.getBoolean(K_MUTE, false);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        root = new FrameLayout(this);
        root.addView(new BgView(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(4), 0, dp(8));

        // ---------------- top bar: red tab + live stats + mute
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(4), dp(14), dp(4));

        TextView tab = new TextView(this);
        tab.setText("ARKAEL GAME LAUNCHER");
        tab.setTextColor(Color.WHITE);
        tab.setTextSize(12.5f);
        tab.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        tab.setLetterSpacing(0.12f);
        tab.setShadowLayer(14, 0, 0, RED);
        tab.setPadding(dp(24), dp(8), dp(28), dp(8));
        tab.setBackground(new Cut(Color.parseColor("#55FF1E3C"), Color.parseColor("#FFFF2D4A"), dp(1.4f), dp(10), true, false, true, false));
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(-2, -2);
        tabLp.rightMargin = dp(14);
        top.addView(tab, tabLp);

        icBat = new Ico(this, I_BAT, GREEN);
        icCpu = new Ico(this, I_CPU, RED);
        icGpu = new Ico(this, I_GPU, RED);
        stBat = new TextView(this); stCpu = new TextView(this); stGpu = new TextView(this);
        top.addView(chip(icBat, stBat, "--%"));
        top.addView(chip(icCpu, stCpu, "CPU --"));
        top.addView(chip(icGpu, stGpu, "GPU --"));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        muteIco = new Ico(this, muted ? I_MUTE : I_SPK, Color.WHITE);
        top.addView(iconBtn(muteIco, v -> toggleMute()));
        col.addView(top, new LinearLayout.LayoutParams(-1, dp(52)));

        // ---------------- carousel
        stage = new Stage(this);
        for (int i = 0; i < cards.length; i++) {
            cards[i] = new GameCard(this);
            cards[i].setCameraDistance(8000f * getResources().getDisplayMetrics().density);
            stage.addView(cards[i]);
        }
        stage.onLaid = this::applyPos;
        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (games.isEmpty() || stage.sp <= 0) return true;
                cancelAnim();
                pos = Math.max(-0.3f, Math.min(games.size() - 0.7f, pos + dx / stage.sp));
                applyPos();
                return true;
            }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (games.isEmpty() || stage.sp <= 0) return true;
                int step = Math.max(-2, Math.min(2, Math.round(-vx / stage.sp * 0.2f)));
                settled = true;
                animateTo(Math.round(pos) + step);
                return true;
            }
            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (games.isEmpty() || stage.sp <= 0) return true;
                int step = Math.round((e.getX() - stage.getWidth() / 2f) / stage.sp);
                if (step != 0) { settled = true; animateTo(Math.round(pos) + step); }
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                if (games.isEmpty() || stage.sp <= 0) return;
                int step = Math.round((e.getX() - stage.getWidth() / 2f) / stage.sp);
                if (step == 0 && sel < games.size()) confirmHide(games.get(sel));
            }
        });
        stage.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) settled = false;
            gd.onTouchEvent(e);
            int a = e.getActionMasked();
            if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && !settled && !games.isEmpty()) animateTo(Math.round(pos));
            return true;
        });
        col.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        summary = new TextView(this);
        summary.setTextColor(Color.parseColor("#A9B6E0"));
        summary.setTextSize(11.5f);
        summary.setGravity(Gravity.CENTER);
        summary.setSingleLine(true);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        summary.setPadding(dp(24), 0, dp(24), 0);
        col.addView(summary, new LinearLayout.LayoutParams(-1, dp(24)));

        // ---------------- bottom row: settings | MAINKAN | add
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.addView(sideBtn(I_GEAR, "SETELAN GAME", v -> {
            if (!games.isEmpty()) { playFx(sTab, 0.9f); openProfile(games.get(sel).pkg); }
        }));
        PlayBtn play = new PlayBtn(this);
        play.setOnClickListener(v -> { if (!games.isEmpty()) launch(games.get(sel)); });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(240), dp(60));
        pl.leftMargin = dp(14);
        pl.rightMargin = dp(14);
        row.addView(play, pl);
        row.addView(sideBtn(I_PLUS, "TAMBAH GAME", v -> showAddDialog()));
        col.addView(row, new LinearLayout.LayoutParams(-1, dp(66)));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        emptyView = new TextView(this);
        emptyView.setText("Belum ada game terdeteksi.\nTekan TAMBAH GAME untuk menambah game secara manual.");
        emptyView.setTextColor(SUB);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));

        buildOverlay();
        setContentView(root);

        // intro animation
        top.setAlpha(0f);
        top.setTranslationY(-dp(20));
        top.animate().alpha(1f).translationY(0).setDuration(420).start();
        stage.setAlpha(0f);
        stage.setScaleX(0.92f);
        stage.setScaleY(0.92f);
        stage.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(150).setDuration(520).start();
        row.setAlpha(0f);
        row.setTranslationY(dp(60));
        row.animate().alpha(1f).translationY(0).setStartDelay(300).setDuration(440).start();

        initSound();
        loadGames();
    }

    // ---------------------------------------------------------------- UI pieces

    private View chip(Ico ico, TextView t, String text) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(10), dp(4), dp(14), dp(4));
        c.setBackground(new Cut(Color.parseColor("#CC0C1230"), Color.parseColor("#66FF1E3C"), dp(1), dp(6), false, true, false, true));
        c.addView(ico, new LinearLayout.LayoutParams(dp(18), dp(18)));
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(12);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setPadding(dp(6), 0, 0, 0);
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    private View iconBtn(Ico ico, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(new Cut(Color.parseColor("#CC0C1230"), Color.parseColor("#99FF1E3C"), dp(1.2f), dp(8), false, true, false, true));
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(dp(20), dp(20));
        il.gravity = Gravity.CENTER;
        f.addView(ico, il);
        f.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(36));
        lp.leftMargin = dp(8);
        f.setLayoutParams(lp);
        return f;
    }

    /** Small blue-bordered button next to MAINKAN (like "Official site" / "My record" on gaming phones). */
    private View sideBtn(int icon, String text, View.OnClickListener l) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.HORIZONTAL);
        f.setGravity(Gravity.CENTER);
        f.setPadding(dp(14), dp(8), dp(16), dp(8));
        f.setBackground(new Cut(Color.parseColor("#CC0A1233"), Color.parseColor("#FF3D8BFF"), dp(1.3f), dp(9), true, false, true, false));
        f.addView(new Ico(this, icon, Color.parseColor("#7FB2FF")), new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(11);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        t.setLetterSpacing(0.1f);
        t.setPadding(dp(8), 0, 0, 0);
        f.addView(t);
        f.setOnClickListener(l);
        f.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(38)));
        return f;
    }

    // ---------------------------------------------------------------- carousel logic

    private void cancelAnim() {
        if (posAnim != null) { posAnim.cancel(); posAnim = null; }
    }

    private void animateTo(int target) {
        int n = games.size();
        if (n == 0) return;
        target = Math.max(0, Math.min(n - 1, target));
        cancelAnim();
        posAnim = ValueAnimator.ofFloat(pos, target);
        posAnim.setDuration(280);
        posAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        posAnim.addUpdateListener(a -> { pos = (Float) a.getAnimatedValue(); applyPos(); });
        posAnim.start();
    }

    /** Places, scales and tilts the 7 card slots for the current (fractional) scroll position. */
    private void applyPos() {
        int n = games.size();
        if (n == 0) {
            for (GameCard c : cards) { c.setVisibility(View.INVISIBLE); c.idx = -1; }
            return;
        }
        int base = Math.max(0, Math.min(n - 1, Math.round(pos)));
        for (int j = 0; j < cards.length; j++) {
            GameCard c = cards[j];
            int idx = base + (j - 3);
            if (idx < 0 || idx >= n) { c.setVisibility(View.INVISIBLE); c.idx = -1; continue; }
            float r = idx - pos, ar = Math.abs(r), sg = r < 0 ? -1f : 1f;
            float a1 = Math.min(ar, 1f), a2 = Math.max(0f, Math.min(ar - 1f, 2f));
            if (c.idx != idx) {
                Game g = games.get(idx);
                c.bind(g.icon, g.name, playText(g.pkg));
                c.idx = idx;
            }
            float sc = 1f - 0.2f * a1 - 0.06f * a2;
            float alpha = ar <= 1f ? 1f - 0.25f * ar : Math.max(0f, 0.75f - 0.4f * (ar - 1f));
            c.setVisibility(alpha <= 0.02f ? View.INVISIBLE : View.VISIBLE);
            c.setScaleX(sc);
            c.setScaleY(sc);
            c.setTranslationX(sg * (a1 * stage.sp + a2 * stage.sp * 0.7f));
            c.setAlpha(alpha);
            c.setRotationY(-sg * a1 * TILT);
            c.setTranslationZ(-ar * 10f);
            c.setFocus(1f - a1);
        }
        if (base != sel) {
            sel = base;
            playFx(sSelect, 0.8f);
            updateSummary();
        }
    }

    private void updateSummary() {
        if (games.isEmpty() || sel >= games.size()) { summary.setText(""); return; }
        summary.setText(profileSummary(games.get(sel).pkg));
    }

    // ---------------------------------------------------------------- play time record

    private static String fmtDur(long ms) {
        long m = ms / 60000L, h = m / 60L;
        if (m < 1) return "kurang dari 1 mnt";
        if (h < 1) return m + " mnt";
        return h + " j " + (m % 60L) + " mnt";
    }

    private String playText(String pkg) {
        long ms = sp.getLong("t_" + pkg, 0L);
        return ms <= 0 ? "Belum pernah dimainkan" : "Durasi main: " + fmtDur(ms);
    }

    /** Called when we come back to the launcher: adds the time spent in the game we started. */
    private void settleRun() {
        String pkg = sp.getString("run_pkg", null);
        long ts = sp.getLong("run_ts", 0L);
        if (pkg == null || ts <= 0) return;
        long el = System.currentTimeMillis() - ts;
        SharedPreferences.Editor ed = sp.edit().remove("run_pkg").remove("run_ts");
        if (el >= 15000L && el <= 12L * 3600000L) ed.putLong("t_" + pkg, sp.getLong("t_" + pkg, 0L) + el);
        ed.apply();
    }

    // ---------------------------------------------------------------- live stats

    private static final String GPU_CANDIDATES =
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage /sys/class/kgsl/kgsl-3d0/gpubusy " +
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load /sys/kernel/gpu/gpu_busy " +
            "/sys/devices/platform/*mali*/utilization /sys/devices/platform/*gpu*/utilization " +
            "/sys/class/misc/mali0/device/utilization /proc/mali/utilization " +
            "/sys/class/devfreq/*gpu*/load /sys/class/devfreq/*mali*/load /sys/class/devfreq/*kgsl*/load /sys/class/devfreq/*g3d*/load " +
            "/sys/kernel/ged/hal/gpu_utilization /sys/kernel/debug/ged/hal/gpu_utilization";

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!pollOn) return;
            if (!polling) {
                polling = true;
                new Thread(() -> {
                    try {
                        final int[] v = readStats();
                        runOnUiThread(() -> {
                            final int bat = v[0], cpu = v[1], gpu = v[2], gmhz = v[3];
                            stBat.setText((bat < 0 ? "--" : String.valueOf(bat)) + "%");
                            icBat.level = bat < 0 ? 1f : bat / 100f;
                            icBat.set(I_BAT, bat >= 0 && bat < 20 ? RED : GREEN);
                            stCpu.setText("CPU " + (cpu < 0 ? "--" : cpu + "%"));
                            if (gpu >= 0) stGpu.setText("GPU " + gpu + "%");
                            else if (gmhz > 0) stGpu.setText("GPU " + gmhz + " MHz");
                            else stGpu.setText("GPU --");
                        });
                    } finally {
                        polling = false;
                    }
                }).start();
            }
            main.postDelayed(this, 2000);
        }
    };

    /** {battery %, cpu %, gpu %, gpu MHz}. Uses the same SysProbe as Game Panel (root aware), then falls back. */
    private int[] readStats() {
        int bat = battery(), cpu = -1, gpu = -1, gmhz = -1;
        try {
            SysProbe.Snapshot s = probe.read(this);
            if (s != null) {
                cpu = s.cpuLoad;
                gpu = s.gpuLoad;
                gmhz = s.gpuMhz;
                if (bat < 0) bat = s.batPct;
            }
        } catch (Throwable ignored) {}
        if (cpu < 0) cpu = cpuLoad();
        if (gpu < 0) gpu = gpuLoadRoot();
        return new int[]{bat, cpu, gpu, gmhz};
    }

    /** Extra GPU load lookup through root: tries a wider list of sysfs nodes (Adreno, Mali, MediaTek) and remembers the one that works. */
    private int gpuLoadRoot() {
        try {
            if (rootOk == null) rootOk = RootShell.hasRoot();
            if (!rootOk) return -1;
            String out;
            if (gpuPath != null) {
                out = RootShell.run("echo \"" + gpuPath + "|$(cat " + gpuPath + " 2>/dev/null | head -n 1)\"");
            } else {
                if (gpuTries++ >= 4) return -1;
                out = RootShell.run("for f in " + GPU_CANDIDATES + "; do [ -r \"$f\" ] || continue; v=$(cat \"$f\" 2>/dev/null | head -n 1); "
                        + "[ -n \"$v\" ] && { echo \"$f|$v\"; break; }; done");
            }
            if (out == null) return -1;
            out = out.trim();
            int bar = out.indexOf('|');
            if (bar < 0) return -1;
            String path = out.substring(0, bar), val = out.substring(bar + 1).trim();
            int v;
            if (path.endsWith("gpubusy")) {
                String[] t = val.split("\\s+");
                long busy = Long.parseLong(t[0]), total = Long.parseLong(t[1]);
                v = total > 0 ? (int) (100L * busy / total) : 0;
            } else {
                v = firstInt(val);
            }
            if (v < 0) return -1;
            gpuPath = path;
            return Math.min(100, v);
        } catch (Throwable t) {
            return -1;
        }
    }

    private int battery() {
        try {
            Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return -1;
            int l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            return l < 0 ? -1 : l * 100 / Math.max(1, s);
        } catch (Throwable t) { return -1; }
    }

    private static String readFile(String path) {
        BufferedReader r = null;
        try {
            File f = new File(path);
            if (!f.canRead()) return null;
            r = new BufferedReader(new FileReader(f));
            String line = r.readLine();
            return line == null ? null : line.trim();
        } catch (Throwable t) { return null; }
        finally { if (r != null) try { r.close(); } catch (Throwable ignored) {} }
    }

    private static int firstInt(String s) {
        if (s == null) return -1;
        String d = s.replaceAll("[^0-9]", " ").trim();
        if (d.isEmpty()) return -1;
        try { return Integer.parseInt(d.split("\\s+")[0]); } catch (Throwable t) { return -1; }
    }

    /** Real load from /proc/stat when readable; otherwise average CPU clock vs its max clock. */
    private int cpuLoad() {
        String line = readFile("/proc/stat");
        if (line != null && line.startsWith("cpu")) {
            try {
                String[] p = line.split("\\s+");
                long total = 0;
                for (int i = 1; i < Math.min(p.length, 9); i++) total += Long.parseLong(p[i]);
                long idle = Long.parseLong(p[4]) + Long.parseLong(p[5]);
                int out = -1;
                if (prevTotal >= 0 && total > prevTotal) out = (int) (100L * ((total - prevTotal) - (idle - prevIdle)) / (total - prevTotal));
                prevTotal = total;
                prevIdle = idle;
                if (out >= 0) return Math.max(0, Math.min(100, out));
            } catch (Throwable ignored) {}
        }
        long cur = 0, max = 0;
        for (int c = 0; c < 16; c++) {
            String base = "/sys/devices/system/cpu/cpu" + c + "/cpufreq/";
            int a = firstInt(readFile(base + "scaling_cur_freq")), m = firstInt(readFile(base + "cpuinfo_max_freq"));
            if (a > 0 && m > 0) { cur += a; max += m; }
        }
        return max > 0 ? (int) (100 * cur / max) : -1;
    }

    // ---------------------------------------------------------------- sound

    private void initSound() {
        try {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(aa).build();
            sOpen = pool.load(this, R.raw.gl_open, 1);
            sLaunch = pool.load(this, R.raw.gl_launch, 1);
            sSelect = pool.load(this, R.raw.gl_select, 1);
            sTab = pool.load(this, R.raw.gl_tab, 1);
            pool.setOnLoadCompleteListener((p, id, status) -> {
                if (status == 0 && id == sOpen && !muted) p.play(id, 0.9f, 0.9f, 1, 0, 1f);
            });
        } catch (Throwable ignored) { pool = null; }
    }

    private void playFx(int id, float vol) {
        if (!muted && pool != null && id != 0) pool.play(id, vol, vol, 1, 0, 1f);
    }

    private void toggleMute() {
        muted = !muted;
        sp.edit().putBoolean(K_MUTE, muted).apply();
        muteIco.set(muted ? I_MUTE : I_SPK, Color.WHITE);
    }

    // ---------------------------------------------------------------- game list

    private Set<String> getSet(String k) { return new HashSet<String>(sp.getStringSet(k, new HashSet<String>())); }
    private void putSet(String k, Set<String> v) { sp.edit().putStringSet(k, v).apply(); }

    @SuppressWarnings("deprecation")
    private void loadGames() {
        new Thread(() -> {
            Set<String> manual = getSet(K_MANUAL), hidden = getSet(K_HIDDEN), seen = new HashSet<String>();
            List<Game> out = new ArrayList<Game>();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || hidden.contains(pkg) || !seen.add(pkg)) continue;
                ApplicationInfo ai = ri.activityInfo.applicationInfo;
                boolean isGame = (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
                if (Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME) isGame = true;
                if (!isGame && !manual.contains(pkg)) continue;
                Game g = new Game();
                g.pkg = pkg;
                g.name = String.valueOf(ri.loadLabel(pm));
                g.icon = ri.loadIcon(pm);
                out.add(g);
            }
            Collections.sort(out, new Comparator<Game>() {
                @Override public int compare(Game a, Game b) { return a.name.compareToIgnoreCase(b.name); }
            });
            final List<Game> res = out;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                String keep = (!games.isEmpty() && sel < games.size()) ? games.get(sel).pkg : null;
                games.clear();
                games.addAll(res);
                sel = 0;
                if (keep != null) for (int i = 0; i < games.size(); i++) if (games.get(i).pkg.equals(keep)) sel = i;
                boolean none = games.isEmpty();
                stage.setVisibility(none ? View.INVISIBLE : View.VISIBLE);
                emptyView.setVisibility(none ? View.VISIBLE : View.GONE);
                cancelAnim();
                for (GameCard c : cards) c.idx = -1;
                pos = sel;
                applyPos();
                updateSummary();
            });
        }).start();
    }

    private void showAddDialog() {
        new Thread(() -> {
            Set<String> have = new HashSet<String>();
            for (Game g : games) have.add(g.pkg);
            final List<String> pk = new ArrayList<String>(), nm = new ArrayList<String>();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> all = pm.queryIntentActivities(q, 0);
            Collections.sort(all, new ResolveInfo.DisplayNameComparator(pm));
            for (ResolveInfo ri : all) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName()) || have.contains(p) || pk.contains(p)) continue;
                pk.add(p);
                nm.add(String.valueOf(ri.loadLabel(pm)));
            }
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (pk.isEmpty()) { Toast.makeText(this, "Tidak ada aplikasi lain", Toast.LENGTH_SHORT).show(); return; }
                final boolean[] selected = new boolean[pk.size()];
                new AlertDialog.Builder(this)
                        .setTitle("Tambah game")
                        .setMultiChoiceItems(nm.toArray(new String[0]), selected, (d, i, c) -> selected[i] = c)
                        .setPositiveButton("Tambah", (d, w) -> {
                            Set<String> man = getSet(K_MANUAL), hid = getSet(K_HIDDEN);
                            for (int i = 0; i < selected.length; i++) if (selected[i]) { man.add(pk.get(i)); hid.remove(pk.get(i)); }
                            putSet(K_MANUAL, man);
                            putSet(K_HIDDEN, hid);
                            loadGames();
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            });
        }).start();
    }

    private void confirmHide(final Game g) {
        new AlertDialog.Builder(this)
                .setTitle(g.name)
                .setMessage("Sembunyikan dari Game Launcher? (Game tidak dihapus dari HP. Bisa ditambah lagi lewat tombol +)")
                .setPositiveButton("Sembunyikan", (d, w) -> {
                    Set<String> hid = getSet(K_HIDDEN), man = getSet(K_MANUAL);
                    hid.add(g.pkg);
                    man.remove(g.pkg);
                    putSet(K_HIDDEN, hid);
                    putSet(K_MANUAL, man);
                    loadGames();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    /** One-line summary of what Mainkan will turn on for this game (from the saved Game Mode profile). */
    private String profileSummary(String pkg) {
        try {
            GameProfiles.Profile p = GameProfiles.get(this, pkg);
            List<String> on = new ArrayList<String>();
            if (p.gov != null && !p.gov.isEmpty()) on.add("CPU " + p.gov);
            if (p.gpuMaxMhz > 0) on.add("GPU " + p.gpuMaxMhz + " MHz");
            if (p.dnd) on.add("Jangan ganggu");
            if (p.headsUp) on.add("Pop-up diblok");
            if (p.thermalRelax) on.add("Thermal longgar");
            if (p.cleanRam) on.add("Bersih RAM");
            if (on.isEmpty()) return "Belum ada boost aktif. Atur di Setelan Game.";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < on.size(); i++) { if (i > 0) sb.append("  •  "); sb.append(on.get(i)); }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private void buildOverlay() {
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.parseColor("#F507070A"));
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);

        ovRing = new HexIcon(this, null, RED, dp(3), false);
        ovRing2 = new HexIcon(this, null, Color.parseColor("#FF7388"), dp(2), false);
        for (HexIcon h : new HexIcon[]{ovRing, ovRing2}) {
            FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(dp(120), dp(120));
            rl.gravity = Gravity.CENTER;
            overlay.addView(h, rl);
        }
        ovIcon = new HexIcon(this, null, RED, dp(2.5f), true);
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(dp(130), dp(130));
        il.gravity = Gravity.CENTER;
        overlay.addView(ovIcon, il);

        ovText = new TextView(this);
        ovText.setTextColor(Color.WHITE);
        ovText.setTextSize(15);
        ovText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        ovText.setLetterSpacing(0.18f);
        ovText.setShadowLayer(16, 0, 0, RED);
        ovText.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-1, -2);
        tl.gravity = Gravity.CENTER;
        tl.topMargin = dp(120);
        overlay.addView(ovText, tl);

        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    // ---------------------------------------------------------------- launch / panel / settings

    private void launch(final Game g) {
        if (launching) return;
        launching = true;
        ovIcon.set(g.icon, RED);
        ovText.setText("MEMUAT  " + g.name.toUpperCase());
        overlay.setAlpha(0f);
        overlay.setVisibility(View.VISIBLE);
        overlay.animate().alpha(1f).setDuration(180).start();
        ovIcon.setScaleX(0.4f); ovIcon.setScaleY(0.4f); ovIcon.setAlpha(0f);
        ovIcon.animate().scaleX(1.05f).scaleY(1.05f).alpha(1f).setDuration(520).start();
        ovRing.setScaleX(0.8f); ovRing.setScaleY(0.8f); ovRing.setAlpha(1f);
        ovRing.animate().scaleX(3.4f).scaleY(3.4f).alpha(0f).setDuration(950).start();
        ovRing2.setScaleX(0.8f); ovRing2.setScaleY(0.8f); ovRing2.setAlpha(1f);
        ovRing2.animate().scaleX(3.4f).scaleY(3.4f).alpha(0f).setStartDelay(220).setDuration(950).start();
        ovText.setAlpha(0f);
        ovText.animate().alpha(1f).setStartDelay(250).setDuration(400).start();
        if (!muted && pool != null) pool.play(sLaunch, 1f, 1f, 1, 0, 1f);

        final long t0 = System.currentTimeMillis();
        new Thread(() -> {
            // Same thing Game Mode does when a game starts: the profile saved for this game.
            try { Booster.applyProfile(this, GameProfiles.get(this, g.pkg), false); } catch (Throwable ignored) {}
            long wait = 1100 - (System.currentTimeMillis() - t0);
            if (wait > 0) { try { Thread.sleep(wait); } catch (InterruptedException ignored) {} }
            runOnUiThread(() -> {
                Intent i = pm.getLaunchIntentForPackage(g.pkg);
                if (i == null) {
                    Toast.makeText(this, "Tidak bisa membuka " + g.name, Toast.LENGTH_SHORT).show();
                    hideOverlay();
                    return;
                }
                openGamePanel(g.pkg);   // while we're still in the foreground, so the service may start
                sp.edit().putString("run_pkg", g.pkg).putLong("run_ts", System.currentTimeMillis()).apply();
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i); } catch (Throwable e) {
                    Toast.makeText(this, "Gagal membuka " + g.name, Toast.LENGTH_SHORT).show();
                    hideOverlay();
                }
            });
        }).start();
    }

    /** Switch the Game Panel on: GameService is the foreground "game_mode" service that hosts it. */
    private void openGamePanel(String pkg) {
        try {
            Intent s = new Intent(this, GameService.class);
            s.putExtra("pkg", pkg);
            s.putExtra("package", pkg);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
        } catch (Throwable ignored) {}
    }

    private void hideOverlay() {
        launching = false;
        overlay.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        settleRun();
        if (!games.isEmpty()) {
            for (GameCard c : cards) c.idx = -1;   // refresh play time on the cards
            applyPos();
        }
        if (launching) main.postDelayed(this::hideOverlay, 400);   // came back from the game
        pollOn = true;
        main.removeCallbacks(poll);
        main.post(poll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollOn = false;
        main.removeCallbacks(poll);
    }

    /** Opens the existing per-game profile settings. Tries the likely screen names, else the game list. */
    private void openProfile(String pkg) {
        String[] cands = {"GameProfileActivity", "GameSettingsActivity", "GameProfileSettingsActivity", "ProfileActivity"};
        for (String c : cands) {
            try {
                Intent i = new Intent(this, Class.forName("com.arkael.app." + c));
                i.putExtra("pkg", pkg);
                i.putExtra("package", pkg);
                startActivity(i);
                return;
            } catch (Throwable ignored) {}
        }
        Intent i = new Intent(this, GameListActivity.class);
        i.putExtra("pkg", pkg);
        startActivity(i);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
        if (pool != null) { pool.release(); pool = null; }
    }
}
