package com.arkael.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Floating pieces for screen recording: a 3-2-1 countdown (tap it to cancel) and a small bar with a
 * blinking dot, the running time and a Stop button. Drag the bar anywhere; it remembers where you left it.
 */
final class RecOverlay {
    private final Context ctx;
    private final Handler main;
    private final Runnable onStop;
    private final WindowManager wm;
    private final SharedPreferences sp;

    private LinearLayout bar;
    private WindowManager.LayoutParams barLp;
    private TextView dot, timer, stopBtn;
    private TextView count;
    private Runnable countStep;
    private boolean ticking;

    RecOverlay(Context c, Handler main, Runnable onStop) {
        this.ctx = c;
        this.main = main;
        this.onStop = onStop;
        this.wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        this.sp = c.getSharedPreferences("arkael_recbar", Context.MODE_PRIVATE);
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics());
    }

    private WindowManager.LayoutParams lp(int w, int h) {
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_SECURE;   // screenrecord skips secure windows
        return new WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT);
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    // ------------------------------------------------------------ countdown

    boolean counting() { return count != null; }

    /** Shows sec..1 in the middle of the screen, then calls go (about 0.3 s after the number is gone). Tap = cancel. */
    void countdown(final int sec, final Runnable go, final Runnable cancel) {
        if (count != null || wm == null) return;
        final TextView t = new TextView(ctx);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 80);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(0xCC101419, 75));
        WindowManager.LayoutParams p = lp(dp(150), dp(150));
        p.gravity = Gravity.CENTER;
        final int[] left = {sec};
        countStep = new Runnable() {
            @Override public void run() {
                if (count == null) return;
                if (left[0] <= 0) {
                    removeCount();
                    main.postDelayed(go, 300);
                    return;
                }
                t.setText(String.valueOf(left[0]));
                left[0]--;
                main.postDelayed(this, 1000);
            }
        };
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                cancelCountdown();
                if (cancel != null) cancel.run();
            }
        });
        try { wm.addView(t, p); count = t; }
        catch (Exception e) { main.post(go); return; }     // cannot draw: just start
        countStep.run();
    }

    void cancelCountdown() {
        if (countStep != null) main.removeCallbacks(countStep);
        removeCount();
    }

    private void removeCount() {
        if (wm != null && count != null) { try { wm.removeView(count); } catch (Exception ignored) {} }
        count = null;
    }

    // ------------------------------------------------------------ recording bar

    void showBar() {
        if (bar != null || wm == null) return;
        final LinearLayout b = new LinearLayout(ctx);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(12), dp(6), dp(6), dp(6));
        b.setBackground(round(0xE61B222C, 24));

        dot = new TextView(ctx);
        dot.setText("●");
        dot.setTextColor(0xFFFF3B30);
        dot.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.addView(dot);

        timer = new TextView(ctx);
        timer.setText("00:00");
        timer.setTextColor(Color.WHITE);
        timer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        timer.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timer.setPadding(dp(8), 0, dp(10), 0);
        b.addView(timer);

        stopBtn = new TextView(ctx);
        stopBtn.setText("■");
        stopBtn.setTextColor(Color.WHITE);
        stopBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        stopBtn.setGravity(Gravity.CENTER);
        stopBtn.setBackground(round(0xFFFF3B30, 18));
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                stopBtn.setEnabled(false);
                stopBtn.setText("…");
                timer.setText("Saving");
                if (onStop != null) onStop.run();
            }
        });
        b.addView(stopBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        final WindowManager.LayoutParams p = lp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        p.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics m = ctx.getResources().getDisplayMetrics();
        p.x = Math.max(0, Math.min(m.widthPixels - dp(130), sp.getInt("x", dp(16))));
        p.y = Math.max(0, Math.min(m.heightPixels - dp(48), sp.getInt("y", dp(90))));

        b.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY; private int sx, sy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY(); sx = p.x; sy = p.y;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int maxX = Math.max(0, dm.widthPixels - Math.max(v.getWidth(), 1));
                        int maxY = Math.max(0, dm.heightPixels - Math.max(v.getHeight(), 1));
                        p.x = Math.max(0, Math.min(maxX, sx + (int) (e.getRawX() - downX)));
                        p.y = Math.max(0, Math.min(maxY, sy + (int) (e.getRawY() - downY)));
                        try { wm.updateViewLayout(bar, p); } catch (Exception ignored) {}
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        sp.edit().putInt("x", p.x).putInt("y", p.y).apply();
                        return true;
                }
                return true;
            }
        });

        try { wm.addView(b, p); bar = b; barLp = p; }
        catch (Exception ignored) { return; }
        ticking = true;
        main.post(tick);
    }

    void hideBar() {
        ticking = false;
        main.removeCallbacks(tick);
        if (wm != null && bar != null) { try { wm.removeView(bar); } catch (Exception ignored) {} }
        bar = null;
        barLp = null;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!ticking || bar == null) return;
            if (stopBtn != null && stopBtn.isEnabled()) {
                long s = Math.max(0, (System.currentTimeMillis() - Recorder.startedAt()) / 1000);
                String txt = s >= 3600
                        ? String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                        : String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
                timer.setText(txt);
            }
            dot.setAlpha(dot.getAlpha() < 0.5f ? 1f : 0.25f);
            main.postDelayed(this, 1000);
        }
    };
}
