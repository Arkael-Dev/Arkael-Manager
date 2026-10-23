package com.arkael.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Layar equalizer: EQ 10-band, preset, bass, surround 3D, loudness. Semua view dibuat lewat kode (tanpa XML). */
public class EqualizerActivity extends Activity {
    private static final int BG = Color.parseColor("#0B0D12");
    private static final int PANEL = Color.parseColor("#141821");
    private static final int CYAN = Color.parseColor("#00E5FF");
    private static final int SUB = Color.parseColor("#8B95A3");
    private static final int GREEN = Color.parseColor("#3DFFA2");
    private static final int RED = Color.parseColor("#FF5C6C");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private EqConfig cfg;
    private EqGraph graph;
    private Switch master;
    private TextView statusTv, hintTv;
    private View dot;
    private SeekBar sBass, sSpatial, sLoud;
    private TextView vBass, vSpatial, vLoud;
    private LinearLayout chipRow, methodRow;
    private boolean binding;
    private float d;

    private final Runnable pushRun = new Runnable() {
        @Override public void run() { EqService.push(EqualizerActivity.this); }
    };

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            refreshStatus();
            ui.postDelayed(this, 1000);
        }
    };

    private int dp(float v) { return (int) (v * d + 0.5f); }

    // ------------------------------------------------------------------ grafik EQ

    static final class EqGraph extends View {
        interface Listener { void onBand(int i, float db); void onDone(); }

        private final float[] g = new float[EqConfig.N];
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG),
                line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
                node = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path(), area = new Path();
        private int sel = -1;
        private Listener lis;
        private final float dd;

        EqGraph(Context c) {
            super(c);
            dd = c.getResources().getDisplayMetrics().density;
            grid.setStrokeWidth(dd);
            txt.setTextSize(10 * dd);
            txt.setColor(SUB);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2.6f * dd);
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setColor(CYAN);
            fill.setStyle(Paint.Style.FILL);
        }

        void setListener(Listener l) { lis = l; }

        void setGains(float[] src) {
            System.arraycopy(src, 0, g, 0, EqConfig.N);
            invalidate();
        }

        private float left() { return 30 * dd; }
        private float right() { return getWidth() - 10 * dd; }
        private float top() { return 16 * dd; }
        private float bottom() { return getHeight() - 24 * dd; }
        private float yOf(float db) {
            float t = (EqConfig.MAX_DB - db) / (2 * EqConfig.MAX_DB);
            return top() + t * (bottom() - top());
        }
        private float xOf(int i) { return left() + (i + 0.5f) * (right() - left()) / EqConfig.N; }

        @Override protected void onDraw(Canvas c) {
            float l = left(), r = right(), t = top(), b = bottom();
            for (int db = -12; db <= 12; db += 6) {
                float y = yOf(db);
                grid.setColor(db == 0 ? Color.parseColor("#3A4452") : Color.parseColor("#1E2530"));
                c.drawLine(l, y, r, y, grid);
                txt.setTextAlign(Paint.Align.RIGHT);
                txt.setColor(SUB);
                c.drawText((db > 0 ? "+" : "") + db, l - 5 * dd, y + 3.5f * dd, txt);
            }
            txt.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < EqConfig.N; i++) {
                grid.setColor(Color.parseColor("#161C25"));
                c.drawLine(xOf(i), t, xOf(i), b, grid);
                txt.setColor(i == sel ? CYAN : SUB);
                c.drawText(EqConfig.FLABEL[i], xOf(i), getHeight() - 7 * dd, txt);
            }
            path.reset();
            path.moveTo(l, yOf(g[0]));
            path.lineTo(xOf(0), yOf(g[0]));
            for (int i = 0; i < EqConfig.N - 1; i++) {
                float x0 = xOf(i), x1 = xOf(i + 1), y0 = yOf(g[i]), y1 = yOf(g[i + 1]), mx = (x0 + x1) / 2f;
                path.cubicTo(mx, y0, mx, y1, x1, y1);
            }
            path.lineTo(r, yOf(g[EqConfig.N - 1]));
            float zy = yOf(0);
            area.set(path);
            area.lineTo(r, zy);
            area.lineTo(l, zy);
            area.close();
            fill.setShader(new LinearGradient(0, t, 0, b, Color.parseColor("#4400E5FF"), Color.parseColor("#0800E5FF"), Shader.TileMode.CLAMP));
            c.drawPath(area, fill);
            c.drawPath(path, line);
            for (int i = 0; i < EqConfig.N; i++) {
                float x = xOf(i), y = yOf(g[i]);
                node.setColor(Color.parseColor("#0B0D12"));
                c.drawCircle(x, y, (i == sel ? 9 : 6) * dd, node);
                node.setColor(i == sel ? Color.WHITE : CYAN);
                node.setStyle(Paint.Style.STROKE);
                node.setStrokeWidth(2 * dd);
                c.drawCircle(x, y, (i == sel ? 9 : 6) * dd, node);
                node.setStyle(Paint.Style.FILL);
                if (i == sel) {
                    txt.setColor(Color.WHITE);
                    String s = String.format(Locale.US, "%+.1f dB", g[i]);
                    c.drawText(s, Math.max(l + 24 * dd, Math.min(r - 24 * dd, x)), Math.max(t + 2 * dd, y - 14 * dd), txt);
                }
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    sel = nearest(e.getX());
                    drag(e.getY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    drag(e.getY());
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    sel = -1;
                    invalidate();
                    if (lis != null) lis.onDone();
                    return true;
            }
            return super.onTouchEvent(e);
        }

        private int nearest(float x) {
            int i = Math.round((x - left()) / ((right() - left()) / EqConfig.N) - 0.5f);
            return Math.max(0, Math.min(EqConfig.N - 1, i));
        }

        private void drag(float y) {
            if (sel < 0) return;
            float t = (y - top()) / (bottom() - top());
            float db = EqConfig.MAX_DB - t * 2 * EqConfig.MAX_DB;
            db = Math.round(db * 2f) / 2f;
            db = EqConfig.clamp(db, -EqConfig.MAX_DB, EqConfig.MAX_DB);
            if (db != g[sel]) {
                g[sel] = db;
                if (lis != null) lis.onBand(sel, db);
            }
            invalidate();
        }
    }

    // ------------------------------------------------------------------ onCreate

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        cfg = EqConfig.load(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(28));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);

        // --- judul + saklar utama
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = text("EQUALIZER", 20, Color.WHITE, true);
        t1.setLetterSpacing(0.12f);
        TextView t2 = text("Mesin audio sistem · butuh root", 11, SUB, false);
        titles.addView(t1);
        titles.addView(t2);
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        master = new Switch(this);
        master.setChecked(cfg.enabled);
        top.addView(master);
        col.addView(top);

        // --- status
        LinearLayout st = card();
        st.setOrientation(LinearLayout.HORIZONTAL);
        st.setGravity(Gravity.CENTER_VERTICAL);
        dot = new View(this);
        setDot(SUB);
        st.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        statusTv = text("", 12, Color.WHITE, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.leftMargin = dp(10);
        st.addView(statusTv, slp);
        TextView diag = pill("Diagnostik", false);
        diag.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showDiag(); } });
        st.addView(diag);
        col.addView(st, lp(12));

        // --- grafik
        LinearLayout gc = card();
        gc.setPadding(dp(8), dp(10), dp(8), dp(4));
        graph = new EqGraph(this);
        graph.setGains(cfg.gain);
        graph.setListener(new EqGraph.Listener() {
            @Override public void onBand(int i, float db) {
                cfg.gain[i] = db;
                boolean was = !EqConfig.CUSTOM.equals(cfg.preset);
                cfg.preset = EqConfig.CUSTOM;
                persist(false);
                if (was) renderChips();
            }
            @Override public void onDone() { persist(true); }
        });
        gc.addView(graph, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        TextView gh = text("Geser titik naik/turun untuk mengatur tiap frekuensi", 10, SUB, false);
        gh.setGravity(Gravity.CENTER);
        gc.addView(gh);
        col.addView(gc, lp(12));

        // --- preset
        col.addView(label("PRESET"), lp(16));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        hs.addView(chipRow);
        col.addView(hs, lp(6));

        // --- slider
        col.addView(label("EFEK"), lp(16));
        LinearLayout sc = card();
        sc.setOrientation(LinearLayout.VERTICAL);
        sBass = new SeekBar(this);
        sSpatial = new SeekBar(this);
        sLoud = new SeekBar(this);
        vBass = addSlider(sc, "Bass", sBass);
        vSpatial = addSlider(sc, "Surround 3D (mirip Atmos)", sSpatial);
        vLoud = addSlider(sc, "Loudness / volume ekstra", sLoud);
        col.addView(sc, lp(6));

        // --- opsi
        col.addView(label("OPSI"), lp(16));
        LinearLayout oc = card();
        oc.setOrientation(LinearLayout.VERTICAL);
        Switch safe = new Switch(this);
        safe.setText("Anti-pecah (preamp otomatis + limiter)");
        safe.setTextColor(Color.WHITE);
        safe.setTextSize(13);
        safe.setChecked(cfg.safe);
        safe.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                if (binding) return;
                cfg.safe = on;
                persist(true);
            }
        });
        oc.addView(safe);
        TextView mt = text("Metode pemasangan", 13, Color.WHITE, false);
        mt.setPadding(0, dp(14), 0, dp(6));
        oc.addView(mt);
        methodRow = new LinearLayout(this);
        oc.addView(methodRow);
        hintTv = text("", 10, SUB, false);
        hintTv.setPadding(0, dp(8), 0, 0);
        oc.addView(hintTv);
        col.addView(oc, lp(6));

        TextView reset = pill("Reset ke Flat", false);
        reset.setGravity(Gravity.CENTER);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                cfg.applyPreset(EqConfig.PRESETS[0]);
                bindAll();
                persist(true);
            }
        });
        col.addView(reset, lp(16));

        TextView foot = text("Ini bukan Dolby Atmos asli (berlisensi, tidak bisa dipasang dari app). Yang dipakai: EQ 10-band + limiter, "
                + "BassBoost, Virtualizer (surround virtual), dan LoudnessEnhancer bawaan Android, dipasang oleh proses root "
                + "supaya berlaku ke semua suara di HP.", 10, SUB, false);
        foot.setPadding(0, dp(14), 0, 0);
        col.addView(foot);

        master.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                if (binding) return;
                cfg.enabled = on;
                cfg.save(EqualizerActivity.this);
                if (on && Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
                }
                if (on) EqService.push(EqualizerActivity.this);
                else stopEq();
                refreshStatus();
            }
        });

        SeekBar.OnSeekBarChangeListener sl = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (!user || binding) return;
                if (s == sBass) cfg.bass = p * 10;
                else if (s == sSpatial) cfg.spatial = p * 10;
                else cfg.loud = p * 10;
                cfg.preset = EqConfig.CUSTOM;
                updateValueLabels();
                persist(false);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { renderChips(); persist(true); }
        };
        sBass.setOnSeekBarChangeListener(sl);
        sSpatial.setOnSeekBarChangeListener(sl);
        sLoud.setOnSeekBarChangeListener(sl);

        bindAll();
    }

    @Override protected void onResume() {
        super.onResume();
        if (cfg.enabled && !EqService.isRunning()) EqService.push(this);
        ui.post(poll);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(poll);
        ui.removeCallbacks(pushRun);
        cfg.save(this);
        super.onPause();
    }

    // ------------------------------------------------------------------ logika

    private void stopEq() {
        try {
            android.content.Intent i = new android.content.Intent(this, EqService.class).setAction(EqService.ACTION_STOP);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        } catch (Exception e) {
            EqService.addLog("stop: " + EqEngine.why(e));
        }
    }

    /** Simpan setelan; kirim ke mesin (langsung bila now, kalau tidak ditunda sedikit saat menggeser). */
    private void persist(boolean now) {
        cfg.save(this);
        ui.removeCallbacks(pushRun);
        if (!cfg.enabled) return;
        if (now) ui.post(pushRun); else ui.postDelayed(pushRun, 150);
    }

    private void bindAll() {
        binding = true;
        graph.setGains(cfg.gain);
        sBass.setProgress(cfg.bass / 10);
        sSpatial.setProgress(cfg.spatial / 10);
        sLoud.setProgress(cfg.loud / 10);
        updateValueLabels();
        renderChips();
        renderMethod();
        binding = false;
    }

    private void updateValueLabels() {
        vBass.setText(cfg.bass / 10 + "%");
        vSpatial.setText(cfg.spatial / 10 + "%");
        vLoud.setText(String.format(Locale.US, "+%.1f dB", cfg.loud / 100f));
    }

    private void renderChips() {
        chipRow.removeAllViews();
        List<String> names = new ArrayList<String>();
        for (EqConfig.Preset p : EqConfig.PRESETS) names.add(p.name);
        if (EqConfig.CUSTOM.equals(cfg.preset)) names.add(EqConfig.CUSTOM);
        for (final String n : names) {
            TextView c = pill(n, n.equals(cfg.preset));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    EqConfig.Preset p = EqConfig.find(n);
                    if (p == null) return;
                    cfg.applyPreset(p);
                    bindAll();
                    persist(true);
                }
            });
            chipRow.addView(c, lp);
        }
    }

    private void renderMethod() {
        methodRow.removeAllViews();
        String[] n = {"Otomatis", "Global", "Per-sesi"};
        for (int i = 0; i < n.length; i++) {
            final int m = i;
            TextView c = pill(n[i], cfg.method == i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cfg.method = m;
                    renderMethod();
                    persist(true);
                }
            });
            methodRow.addView(c, lp);
        }
        hintTv.setText(cfg.method == EqConfig.M_AUTO ? "Otomatis: coba global (semua suara) dulu, kalau gagal pasang per aplikasi yang sedang bunyi."
                : cfg.method == EqConfig.M_GLOBAL ? "Global: satu efek di output mix. Kalau status hijau tapi suara tidak berubah, coba Per-sesi."
                : "Per-sesi: efek dipasang ke tiap aplikasi yang sedang memutar suara (dideteksi lewat root).");
    }

    private void refreshStatus() {
        if (!cfg.enabled) { statusTv.setText("Mati"); setDot(SUB); return; }
        String s = EqService.status();
        if ("Mati".equals(s)) { statusTv.setText("Memulai…"); setDot(CYAN); return; }
        statusTv.setText(s);
        setDot(s.contains("GAGAL") ? RED : GREEN);
    }

    private void showDiag() {
        StringBuilder sb = new StringBuilder();
        sb.append("Perangkat : ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android   : ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Status    : ").append(EqService.status()).append("\n");
        sb.append("Setelan   : ").append(cfg.toJson()).append("\n\n");
        sb.append(EqService.log());
        final String all = sb.toString();
        TextView tv = new TextView(this);
        tv.setText(all);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(Color.WHITE);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(16), dp(12), dp(16), dp(12));
        ScrollView s = new ScrollView(this);
        s.addView(tv);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Diagnostik equalizer")
                .setView(s)
                .setPositiveButton("Salin", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface di, int w) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("eq", all));
                        Toast.makeText(EqualizerActivity.this, "Tersalin", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Tutup", null)
                .show();
    }

    // ------------------------------------------------------------------ helper tampilan

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 11, CYAN, true);
        t.setLetterSpacing(0.12f);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(PANEL);
        g.setCornerRadius(dp(14));
        g.setStroke(dp(1), Color.parseColor("#1F2630"));
        c.setBackground(g);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        return c;
    }

    private LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    private TextView pill(String s, boolean on) {
        TextView t = text(s, 12, on ? Color.BLACK : Color.WHITE, true);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(on ? CYAN : Color.parseColor("#1B212C"));
        if (!on) g.setStroke(dp(1), Color.parseColor("#2A3342"));
        t.setBackground(g);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setSingleLine(true);
        return t;
    }

    private void setDot(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        dot.setBackground(g);
    }

    private TextView addSlider(LinearLayout parent, String name, SeekBar sb) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = text(name, 13, Color.WHITE, false);
        row.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text("", 12, CYAN, true);
        row.addView(v);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(parent.getChildCount() == 0 ? 0 : 10);
        parent.addView(row, rp);
        sb.setMax(100);
        parent.addView(sb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return v;
    }
}
