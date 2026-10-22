package com.arkael.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Tiny history graph: CPU load, temperature and FPS over the last minute. */
final class SparkView extends View {
    private static final int N = 60;
    private final float[][] d = new float[3][N];
    private final float[] lo = {0f, 30f, 0f};
    private final float[] hi = {100f, 90f, 120f};
    private final Paint[] p = new Paint[3];
    private final Paint grid = new Paint();
    private final Path path = new Path();
    private int w, h;
    private int count;

    SparkView(Context c, int accent, int widthPx, int heightPx) {
        super(c);
        w = widthPx; h = heightPx;
        int[] col = {accent, 0xFFFF6B35, 0xFF00E676};
        for (int i = 0; i < 3; i++) {
            p[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            p[i].setColor(col[i]);
            p[i].setStyle(Paint.Style.STROKE);
            for (int k = 0; k < N; k++) d[i][k] = -1f;
        }
        grid.setColor(0x33FFFFFF);
        grid.setStrokeWidth(1f);
        stroke();
    }

    private void stroke() {
        for (int i = 0; i < 3; i++) p[i].setStrokeWidth(Math.max(2f, h / 16f));
    }

    /** Resize without losing the history (used while the overlay is being scaled). */
    void setSizePx(int widthPx, int heightPx) {
        w = Math.max(1, widthPx); h = Math.max(1, heightPx);
        stroke();
        requestLayout();
        invalidate();
    }

    void push(float cpu, float temp, float fps) {
        float[] v = {cpu, temp, fps};
        for (int i = 0; i < 3; i++) {
            System.arraycopy(d[i], 1, d[i], 0, N - 1);
            d[i][N - 1] = v[i];
        }
        if (count < N) count++;
        invalidate();
    }

    @Override protected void onMeasure(int wm, int hm) {
        int mw = View.MeasureSpec.getMode(wm) == View.MeasureSpec.EXACTLY ? View.MeasureSpec.getSize(wm) : w;
        setMeasuredDimension(mw, h);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth() > 0 ? getWidth() : this.w;
        c.drawLine(0, h - 1, w, h - 1, grid);
        float step = w / (float) (N - 1);
        for (int s = 0; s < 3; s++) {
            path.reset();
            boolean pen = false;
            for (int i = N - count; i < N; i++) {
                float v = d[s][i];
                if (v < 0) { pen = false; continue; }
                float t = Math.max(0f, Math.min(1f, (v - lo[s]) / (hi[s] - lo[s])));
                float x = i * step, y = (h - 2) - t * (h - 4);
                if (!pen) { path.moveTo(x, y); pen = true; } else path.lineTo(x, y);
            }
            c.drawPath(path, p[s]);
        }
    }
}
