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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
 * Game Launcher, gaming-phone style (landscape): angular red/black theme, hexagon game icons,
 * hand-drawn vector icons, animated background. Game list on the left, big preview in the middle,
 * MAINKAN button on the right, "Setelan Game" tab at the bottom, live battery/CPU/GPU on top.
 * Mainkan = apply the game's saved profile, switch the Game Panel on, then launch with animation + sound.
 */
public class GameLauncherActivity extends Activity {
    private static final String PREFS = "gl_prefs";
    private static final String K_MANUAL = "manual", K_HIDDEN = "hidden", K_MUTE = "mute";

    private static final int RED = Color.parseColor("#FF1E3C");
    private static final int PANEL = Color.parseColor("#14141A");
    private static final int SUB = Color.parseColor("#8E8E9A");
    private static final int GREEN = Color.parseColor("#3DFFA2");

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

    /** The big MAINKAN button: slanted edge, red gradient, pulsing glow and a shine that sweeps across. */
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
            txt.setColor(Color.WHITE); txt.setTextSize(22 * d);
            txt.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)); txt.setLetterSpacing(0.14f);
            sub.setColor(Color.parseColor("#FFD3DA")); sub.setTextSize(11 * d);
            shine.setColor(Color.WHITE); shine.setAlpha(60);
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            buildPath(path, new RectF(2 * d, 2 * d, w + 4 * d, h - 2 * d), 18 * d, new boolean[]{true, false, false, true});
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
            drawIco(cv, I_BOLT, 38 * d, h / 2f, 26 * d, Color.WHITE, 1f, ico);
            cv.drawText("MAINKAN", 62 * d, h * 0.5f, txt);
            cv.drawText("Profil otomatis aktif", 64 * d, h * 0.5f + 17 * d, sub);
        }
    }

    /** Container clipped to a slanted-corner shape, with fill, border and an optional scan line. */
    static final class CutFrame extends FrameLayout {
        private final Path path = new Path();
        private final Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG), sp = new Paint(Paint.ANTI_ALIAS_FLAG), scan = new Paint();
        private final float cut, sw, d;
        private final boolean[] c;
        private final boolean scanOn;
        private float phase;
        private ValueAnimator va;

        CutFrame(Context ctx, int fill, int stroke, float sw, float cut, boolean tl, boolean tr, boolean br, boolean bl, boolean scanOn) {
            super(ctx);
            d = ctx.getResources().getDisplayMetrics().density;
            this.cut = cut; this.sw = sw; this.c = new boolean[]{tl, tr, br, bl}; this.scanOn = scanOn;
            fp.setColor(fill);
            sp.setStyle(Paint.Style.STROKE); sp.setColor(stroke); sp.setStrokeWidth(sw);
            setWillNotDraw(false);
            scan.setShader(new LinearGradient(0, -46 * d, 0, 0, Color.TRANSPARENT, Color.argb(120, 255, 30, 60), Shader.TileMode.CLAMP));
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            buildPath(path, new RectF(sw / 2, sw / 2, w - sw / 2, h - sw / 2), cut, c);
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (scanOn) {
                va = ValueAnimator.ofFloat(0f, 1f);
                va.setDuration(4200);
                va.setRepeatCount(ValueAnimator.INFINITE);
                va.setInterpolator(new LinearInterpolator());
                va.addUpdateListener(a -> { phase = (Float) a.getAnimatedValue(); invalidate(); });
                va.start();
            }
        }
        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (va != null) { va.cancel(); va = null; }
        }
        @Override protected void dispatchDraw(Canvas cv) {
            cv.save();
            cv.clipPath(path);
            cv.drawPath(path, fp);
            super.dispatchDraw(cv);
            if (scanOn) {
                float y = phase * (getHeight() + 46 * d);
                cv.save();
                cv.translate(0, y);
                cv.drawRect(0, -46 * d, getWidth(), 0, scan);
                cv.restore();
            }
            cv.restore();
            cv.drawPath(path, sp);
        }
    }

    /** Animated dark background: diagonal stripes, a breathing red glow and a slow light sweep. */
    static final class BgView extends View {
        private final Paint bg = new Paint(), stripe = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(), sweep = new Paint();
        private final Path band = new Path();
        private final float d;
        private float phase;
        private ValueAnimator va;

        BgView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            stripe.setColor(Color.argb(16, 255, 30, 60));
            stripe.setStrokeWidth(2 * d);
            sweep.setColor(Color.argb(14, 255, 30, 60));
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            bg.setShader(new LinearGradient(0, 0, 0, h, Color.parseColor("#14141A"), Color.parseColor("#07070A"), Shader.TileMode.CLAMP));
            glow.setShader(new RadialGradient(w * 0.88f, h * 1.05f, h * 1.15f, Color.argb(255, 255, 30, 60), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(7000);
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
            for (float x = -h; x < w; x += 34 * d) cv.drawLine(x, h, x + h * 0.58f, 0, stripe);
            glow.setAlpha((int) (46 + 26 * Math.sin(phase * 2 * Math.PI)));
            cv.drawRect(0, 0, w, h, glow);
            float x = (phase * 1.5f - 0.25f) * w, bw = w * 0.12f, skew = h * 0.58f;
            band.reset();
            band.moveTo(x + skew, 0); band.lineTo(x + skew + bw, 0); band.lineTo(x + bw, h); band.lineTo(x, h); band.close();
            cv.drawPath(band, sweep);
        }
    }

    // =================================================================== activity

    private static final class Game {
        String pkg, name;
        Drawable icon;
    }

    private final List<Game> games = new ArrayList<Game>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private PackageManager pm;
    private SharedPreferences sp;

    private LinearLayout listCol, body;
    private FrameLayout root, overlay;
    private LinearLayout pvContent;
    private ImageView pvBack;
    private HexIcon pvIcon, ovIcon, ovRing, ovRing2;
    private Ico muteIco, icBat, icCpu, icGpu;
    private TextView pvName, pvSub, emptyView, stBat, stCpu, stGpu, ovText;
    private SoundPool pool;
    private int sOpen, sLaunch, sSelect, sTab, sel = 0;
    private boolean muted, launching, introDone, pollOn;
    private long prevIdle = -1, prevTotal = -1;

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
        col.setPadding(0, dp(4), 0, 0);

        // ---------------- top bar
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(4), dp(14), dp(4));

        View mark = new View(this);
        mark.setBackground(new Cut(RED, RED, 0, dp(4), false, true, false, true));
        top.addView(mark, new LinearLayout.LayoutParams(dp(7), dp(24)));
        TextView title = new TextView(this);
        title.setText("GAME LAUNCHER");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        title.setLetterSpacing(0.16f);
        title.setShadowLayer(14, 0, 0, RED);
        title.setPadding(dp(10), 0, dp(22), 0);
        top.addView(title);

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
        top.addView(iconBtn(new Ico(this, I_PLUS, Color.WHITE), v -> showAddDialog()));
        col.addView(top, new LinearLayout.LayoutParams(-1, dp(52)));

        // ---------------- body: list | preview | play
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);

        ScrollView listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(false);
        listCol = new LinearLayout(this);
        listCol.setOrientation(LinearLayout.VERTICAL);
        listCol.setPadding(dp(8), dp(6), dp(4), dp(60));
        listScroll.addView(listCol, new ViewGroup.LayoutParams(-1, -2));
        body.addView(listScroll, new LinearLayout.LayoutParams(0, -1, 3f));

        CutFrame previewBox = new CutFrame(this, Color.parseColor("#E00C0C10"), Color.parseColor("#B0FF1E3C"),
                dp(1.6f), dp(26), false, true, false, true, true);
        pvBack = new ImageView(this);
        pvBack.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pvBack.setScaleX(3.4f);
        pvBack.setScaleY(3.4f);
        pvBack.setAlpha(0.28f);
        previewBox.addView(pvBack, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#10000000"), Color.parseColor("#F007070A")}));
        previewBox.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        pvContent = new LinearLayout(this);
        pvContent.setOrientation(LinearLayout.VERTICAL);
        pvContent.setGravity(Gravity.CENTER_HORIZONTAL);
        pvContent.setPadding(dp(20), dp(14), dp(20), dp(16));
        pvIcon = new HexIcon(this, null, RED, dp(2.2f), true);
        pvContent.addView(pvIcon, new LinearLayout.LayoutParams(dp(150), dp(150)));
        pvContent.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        pvName = new TextView(this);
        pvName.setTextColor(Color.WHITE);
        pvName.setTextSize(26);
        pvName.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        pvName.setLetterSpacing(0.06f);
        pvName.setShadowLayer(18, 0, 0, RED);
        pvName.setSingleLine(true);
        pvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pvName.setGravity(Gravity.CENTER);
        pvContent.addView(pvName, new LinearLayout.LayoutParams(-1, -2));
        pvSub = new TextView(this);
        pvSub.setTextColor(Color.parseColor("#C4C4CE"));
        pvSub.setTextSize(11);
        pvSub.setGravity(Gravity.CENTER);
        pvSub.setPadding(0, dp(4), 0, 0);
        pvSub.setMaxLines(2);
        pvContent.addView(pvSub, new LinearLayout.LayoutParams(-1, -2));
        previewBox.addView(pvContent, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams pvl = new LinearLayout.LayoutParams(0, -1, 6f);
        pvl.topMargin = dp(8);
        pvl.bottomMargin = dp(58);
        pvl.leftMargin = dp(8);
        pvl.rightMargin = dp(16);
        body.addView(previewBox, pvl);

        FrameLayout rightCol = new FrameLayout(this);
        PlayBtn play = new PlayBtn(this);
        play.setOnClickListener(v -> { if (!games.isEmpty()) launch(games.get(sel)); });
        FrameLayout.LayoutParams ppl = new FrameLayout.LayoutParams(-1, dp(78));
        ppl.gravity = Gravity.CENTER_VERTICAL;
        rightCol.addView(play, ppl);
        body.addView(rightCol, new LinearLayout.LayoutParams(0, -1, 3f));

        col.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // ---------------- bottom tab
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.HORIZONTAL);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(40), dp(9), dp(40), dp(11));
        tab.setBackground(new Cut(Color.parseColor("#F0181820"), Color.parseColor("#99FF1E3C"), dp(1.4f), dp(14), true, true, false, false));
        tab.addView(new Ico(this, I_GEAR, RED), new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView tt = new TextView(this);
        tt.setText("SETELAN GAME");
        tt.setTextColor(Color.WHITE);
        tt.setTextSize(13);
        tt.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        tt.setLetterSpacing(0.14f);
        tt.setPadding(dp(10), 0, 0, 0);
        tab.addView(tt);
        tab.setOnClickListener(v -> { if (!games.isEmpty()) { playFx(sTab, 0.9f); openProfile(games.get(sel).pkg); } });
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2);
        tl.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        root.addView(tab, tl);

        emptyView = new TextView(this);
        emptyView.setText("Belum ada game terdeteksi.\nTekan + untuk menambah game secara manual.");
        emptyView.setTextColor(SUB);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));

        TextView brand = new TextView(this);
        brand.setText("ARKAEL GAME LAUNCHER");
        brand.setTextColor(Color.parseColor("#88FF1E3C"));
        brand.setTextSize(9);
        brand.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));
        brand.setLetterSpacing(0.22f);
        FrameLayout.LayoutParams brl = new FrameLayout.LayoutParams(-2, -2);
        brl.gravity = Gravity.BOTTOM | Gravity.END;
        brl.rightMargin = dp(20);
        brl.bottomMargin = dp(14);
        root.addView(brand, brl);

        buildOverlay();
        setContentView(root);

        // intro animation
        top.setAlpha(0f);
        top.setTranslationY(-dp(20));
        top.animate().alpha(1f).translationY(0).setDuration(420).start();
        previewBox.setAlpha(0f);
        previewBox.setScaleX(0.94f);
        previewBox.setScaleY(0.94f);
        previewBox.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(150).setDuration(480).start();
        play.setAlpha(0f);
        play.setTranslationX(dp(80));
        play.animate().alpha(1f).translationX(0).setStartDelay(300).setDuration(460).start();
        tab.setTranslationY(dp(70));
        tab.animate().translationY(0).setStartDelay(250).setDuration(420).start();

        initSound();
        loadGames();
    }

    // ---------------------------------------------------------------- UI pieces

    private View chip(Ico ico, TextView t, String text) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(10), dp(4), dp(14), dp(4));
        c.setBackground(new Cut(Color.parseColor("#CC15151B"), Color.parseColor("#55FF1E3C"), dp(1), dp(6), false, true, false, true));
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
        f.setBackground(new Cut(Color.parseColor("#CC15151B"), Color.parseColor("#99FF1E3C"), dp(1.2f), dp(8), false, true, false, true));
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(dp(20), dp(20));
        il.gravity = Gravity.CENTER;
        f.addView(ico, il);
        f.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(36));
        lp.leftMargin = dp(8);
        f.setLayoutParams(lp);
        return f;
    }

    private void renderList() {
        listCol.removeAllViews();
        for (int i = 0; i < games.size(); i++) {
            final int idx = i;
            final Game g = games.get(i);
            boolean on = i == sel;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(7), dp(12), dp(7));
            row.setBackground(on
                    ? new Cut(Color.parseColor("#33FF1E3C"), Color.parseColor("#99FF1E3C"), dp(1), dp(12), false, true, false, true)
                    : new ColorDrawable(Color.TRANSPARENT));
            row.addView(new HexIcon(this, g.icon, on ? RED : Color.parseColor("#3C3C48"), dp(1.6f), false),
                    new LinearLayout.LayoutParams(dp(46), dp(50)));
            TextView n = new TextView(this);
            n.setText(g.name);
            n.setTextColor(on ? Color.WHITE : Color.parseColor("#9A9AA6"));
            n.setTextSize(on ? 16 : 14);
            n.setTypeface(Typeface.create(Typeface.DEFAULT, on ? Typeface.BOLD_ITALIC : Typeface.NORMAL));
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
            nl.leftMargin = dp(12);
            row.addView(n, nl);
            if (on) {
                View bar = new View(this);
                bar.setBackground(new Cut(RED, RED, 0, dp(3), true, false, true, false));
                row.addView(bar, new LinearLayout.LayoutParams(dp(4), dp(26)));
            }
            row.setOnClickListener(v -> select(idx));
            row.setOnLongClickListener(v -> { confirmHide(g); return true; });
            if (!introDone) {
                row.setAlpha(0f);
                row.setTranslationX(-dp(36));
                row.animate().alpha(1f).translationX(0).setStartDelay(Math.min(i, 10) * 55L + 200).setDuration(340).start();
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.bottomMargin = dp(4);
            listCol.addView(row, rl);
        }
        introDone = true;
    }

    private void select(int i) {
        if (i == sel || i < 0 || i >= games.size()) return;
        sel = i;
        playFx(sSelect, 0.8f);
        renderList();
        updatePreview(true);
    }

    private void updatePreview(boolean anim) {
        if (games.isEmpty()) return;
        if (sel >= games.size()) sel = 0;
        Game g = games.get(sel);
        pvBack.setImageDrawable(g.icon);
        pvIcon.set(g.icon, RED);
        pvName.setText(g.name);
        pvSub.setText(profileSummary(g.pkg));
        if (anim) {
            pvContent.setAlpha(0f);
            pvContent.setTranslationX(dp(28));
            pvContent.animate().alpha(1f).translationX(0).setDuration(260).start();
            pvBack.setAlpha(0f);
            pvBack.animate().alpha(0.28f).setDuration(320).start();
        }
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

    // ---------------------------------------------------------------- live stats

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!pollOn) return;
            new Thread(() -> {
                final int bat = battery();
                final int cpu = cpuLoad();
                final int gpu = gpuLoad();
                runOnUiThread(() -> {
                    stBat.setText((bat < 0 ? "--" : String.valueOf(bat)) + "%");
                    icBat.level = bat < 0 ? 1f : bat / 100f;
                    icBat.set(I_BAT, bat >= 0 && bat < 20 ? RED : GREEN);
                    stCpu.setText("CPU " + (cpu < 0 ? "--" : cpu + "%"));
                    stGpu.setText("GPU " + (gpu < 0 ? "--" : gpu + "%"));
                });
            }).start();
            main.postDelayed(this, 2000);
        }
    };

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

    private int gpuLoad() {
        String[] paths = {
                "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
                "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
                "/sys/kernel/gpu/gpu_busy",
                "/sys/class/devfreq/gpufreq/load"};
        for (String p : paths) {
            int v = firstInt(readFile(p));
            if (v >= 0) return Math.min(100, v);
        }
        return -1;
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
                body.setVisibility(none ? View.INVISIBLE : View.VISIBLE);
                emptyView.setVisibility(none ? View.VISIBLE : View.GONE);
                renderList();
                updatePreview(false);
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
