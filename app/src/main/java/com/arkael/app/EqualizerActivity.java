package com.arkael.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Arkael Equalizer: EQ 10-band, visualizer ala Cava, preset (simpan/impor/ekspor/bagikan), bass, surround 3D, loudness. View dibuat lewat kode. */
public class EqualizerActivity extends Activity {
    private static final int BG = Color.parseColor("#0B0D12");
    private static final int PANEL = Color.parseColor("#141821");
    private static final int CYAN = Color.parseColor("#00E5FF");
    private static final int SUB = Color.parseColor("#8B95A3");
    private static final int GREEN = Color.parseColor("#3DFFA2");
    private static final int RED = Color.parseColor("#FF5C6C");

    private static final int REQ_NOTIF = 11, REQ_MIC = 12, REQ_IMPORT = 21, REQ_EXPORT = 22;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private EqConfig cfg;
    private EqGraph graph;
    private Switch master, vizSwitch;
    private TextView statusTv, hintTv, vizInfo;
    private View dot;
    private SeekBar sBass, sSpatial, sLoud;
    private TextView vBass, vSpatial, vLoud;
    private LinearLayout chipRow, methodRow, styleRow, themeRow;
    private EqVisual viz;
    private EqVizSource vizSrc;
    private SharedPreferences vp;
    private List<EqPresetStore.UserPreset> mine = new ArrayList<EqPresetStore.UserPreset>();
    private String pendingExport;
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
        vp = getSharedPreferences("ark_eq_viz", MODE_PRIVATE);
        mine = EqPresetStore.load(this);

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
        TextView t1 = text(getString(R.string.ax_eq_title).toUpperCase(Locale.getDefault()), 19, Color.WHITE, true);
        t1.setLetterSpacing(0.1f);
        TextView t2 = text(getString(R.string.ax_eq_subtitle), 11, SUB, false);
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
        TextView diag = pill(getString(R.string.ax_eq_diag), false);
        diag.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showDiag(); } });
        st.addView(diag);
        col.addView(st, lp(12));

        // --- visualizer
        LinearLayout vc = card();
        LinearLayout vh = new LinearLayout(this);
        vh.setGravity(Gravity.CENTER_VERTICAL);
        vh.addView(label(getString(R.string.ax_eq_viz)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        vizSwitch = new Switch(this);
        vizSwitch.setChecked(vp.getBoolean("on", true));
        vh.addView(vizSwitch);
        vc.addView(vh);
        viz = new EqVisual(this);
        viz.setStyle(vp.getInt("style", EqVisual.S_BARS));
        viz.setTheme(vp.getInt("theme", 0));
        viz.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int s = (viz.getStyle() + 1) % EqVisual.S_COUNT;
                viz.setStyle(s);
                vp.edit().putInt("style", s).apply();
                renderViz();
            }
        });
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160));
        vlp.topMargin = dp(8);
        vc.addView(viz, vlp);
        vizInfo = text("", 10, SUB, false);
        vizInfo.setPadding(0, dp(6), 0, dp(4));
        vizInfo.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { askMic(); } });
        vc.addView(vizInfo);
        HorizontalScrollView sh = new HorizontalScrollView(this);
        sh.setHorizontalScrollBarEnabled(false);
        styleRow = new LinearLayout(this);
        sh.addView(styleRow);
        vc.addView(sh);
        HorizontalScrollView th = new HorizontalScrollView(this);
        th.setHorizontalScrollBarEnabled(false);
        themeRow = new LinearLayout(this);
        themeRow.setGravity(Gravity.CENTER_VERTICAL);
        themeRow.setPadding(0, dp(10), 0, dp(2));
        th.addView(themeRow);
        vc.addView(th);
        col.addView(vc, lp(12));
        renderViz();
        vizSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                vp.edit().putBoolean("on", on).apply();
                startViz();
            }
        });

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
        TextView gh = text(getString(R.string.ax_eq_graph_hint), 10, SUB, false);
        gh.setGravity(Gravity.CENTER);
        gc.addView(gh);
        col.addView(gc, lp(12));

        // --- preset
        col.addView(label(getString(R.string.ax_eq_preset)), lp(16));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        hs.addView(chipRow);
        col.addView(hs, lp(6));

        HorizontalScrollView ah = new HorizontalScrollView(this);
        ah.setHorizontalScrollBarEnabled(false);
        LinearLayout actRow = new LinearLayout(this);
        ah.addView(actRow);
        addAction(actRow, R.string.ax_eq_p_save, new Runnable() { @Override public void run() { savePresetDialog(); } });
        addAction(actRow, R.string.ax_eq_p_import, new Runnable() { @Override public void run() { importDialog(); } });
        addAction(actRow, R.string.ax_eq_p_export, new Runnable() { @Override public void run() { chooseScope(false); } });
        addAction(actRow, R.string.ax_eq_p_share, new Runnable() { @Override public void run() { chooseScope(true); } });
        col.addView(ah, lp(8));

        // --- slider
        col.addView(label(getString(R.string.ax_eq_effects)), lp(16));
        LinearLayout sc = card();
        sc.setOrientation(LinearLayout.VERTICAL);
        sBass = new SeekBar(this);
        sSpatial = new SeekBar(this);
        sLoud = new SeekBar(this);
        vBass = addSlider(sc, getString(R.string.ax_eq_bass), sBass);
        vSpatial = addSlider(sc, getString(R.string.ax_eq_spatial), sSpatial);
        vLoud = addSlider(sc, getString(R.string.ax_eq_loud), sLoud);
        col.addView(sc, lp(6));

        // --- opsi
        col.addView(label(getString(R.string.ax_eq_options)), lp(16));
        LinearLayout oc = card();
        oc.setOrientation(LinearLayout.VERTICAL);
        Switch safe = new Switch(this);
        safe.setText(R.string.ax_eq_safe);
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
        TextView mt = text(getString(R.string.ax_eq_method), 13, Color.WHITE, false);
        mt.setPadding(0, dp(14), 0, dp(6));
        oc.addView(mt);
        methodRow = new LinearLayout(this);
        oc.addView(methodRow);
        hintTv = text("", 10, SUB, false);
        hintTv.setPadding(0, dp(8), 0, 0);
        oc.addView(hintTv);
        col.addView(oc, lp(6));

        TextView reset = pill(getString(R.string.ax_eq_reset), false);
        reset.setGravity(Gravity.CENTER);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                cfg.applyPreset(EqConfig.PRESETS[0]);
                bindAll();
                persist(true);
            }
        });
        col.addView(reset, lp(16));

        TextView foot = text(getString(R.string.ax_eq_footer), 10, SUB, false);
        foot.setPadding(0, dp(14), 0, 0);
        col.addView(foot);

        master.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                if (binding) return;
                cfg.enabled = on;
                cfg.save(EqualizerActivity.this);
                if (on && Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
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
        startViz();
    }

    @Override protected void onPause() {
        ui.removeCallbacks(poll);
        ui.removeCallbacks(pushRun);
        stopViz();
        cfg.save(this);
        super.onPause();
    }

    // ------------------------------------------------------------------ visualizer

    private boolean hasMic() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void askMic() {
        if (!hasMic() && Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }

    private void startViz() {
        stopViz();
        if (!vp.getBoolean("on", true)) {
            viz.setMode(EqVisual.M_OFF);
            vizInfo.setText(R.string.ax_eq_viz_off);
            return;
        }
        if (!hasMic()) {
            viz.setMode(EqVisual.M_SIM);
            vizInfo.setText(R.string.ax_eq_viz_sim);
            if (!vp.getBoolean("asked", false)) {
                vp.edit().putBoolean("asked", true).apply();
                askMic();
            }
            return;
        }
        vizSrc = new EqVizSource(viz);
        if (vizSrc.start()) {
            viz.setMode(EqVisual.M_LIVE);
            vizInfo.setText(R.string.ax_eq_viz_live);
        } else {
            viz.setMode(EqVisual.M_SIM);
            vizInfo.setText(getString(R.string.ax_eq_viz_fail, vizSrc.error));
            vizSrc = null;
        }
    }

    private void stopViz() {
        if (vizSrc != null) { vizSrc.stop(); vizSrc = null; }
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_MIC) startViz();
    }

    private void renderViz() {
        styleRow.removeAllViews();
        String[] names = {getString(R.string.ax_eq_vs_bars), getString(R.string.ax_eq_vs_mirror), getString(R.string.ax_eq_vs_wave),
                getString(R.string.ax_eq_vs_circle), getString(R.string.ax_eq_vs_dots), getString(R.string.ax_eq_vs_blocks)};
        for (int i = 0; i < names.length; i++) {
            final int s = i;
            TextView c = pill(names[i], viz.getStyle() == i);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            l.rightMargin = dp(8);
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    viz.setStyle(s);
                    vp.edit().putInt("style", s).apply();
                    renderViz();
                }
            });
            styleRow.addView(c, l);
        }
        themeRow.removeAllViews();
        for (int i = 0; i < EqVisual.THEMES.length; i++) {
            final int t = i;
            int[] pal = EqVisual.THEMES[i];
            int[] cs = pal.length == 1 ? new int[]{pal[0], pal[0]} : pal;
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, cs);
            g.setShape(GradientDrawable.OVAL);
            g.setStroke(dp(viz.getTheme() == i ? 3 : 1), viz.getTheme() == i ? Color.WHITE : Color.parseColor("#2A3342"));
            View sw = new View(this);
            sw.setBackground(g);
            sw.setContentDescription(EqVisual.THEME_NAMES[i]);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(32), dp(32));
            l.rightMargin = dp(10);
            sw.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    viz.setTheme(t);
                    vp.edit().putInt("theme", t).apply();
                    renderViz();
                }
            });
            themeRow.addView(sw, l);
        }
    }

    // ------------------------------------------------------------------ preset: simpan / impor / ekspor / bagikan

    private void addAction(LinearLayout row, int res, final Runnable r) {
        TextView c = pill(getString(res), false);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.rightMargin = dp(8);
        c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        row.addView(c, l);
    }

    private AlertDialog.Builder dlg() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    private void savePresetDialog() {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setHint(R.string.ax_eq_p_name_hint);
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(SUB);
        et.setPadding(dp(20), dp(14), dp(20), dp(14));
        if (!EqConfig.CUSTOM.equals(cfg.preset) && EqPresetStore.find(mine, cfg.preset) != null) et.setText(cfg.preset);
        dlg().setTitle(R.string.ax_eq_p_name_title).setView(et)
                .setPositiveButton(R.string.ax_eq_ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface di, int w) {
                        String n = EqPresetStore.cleanName(et.getText().toString());
                        if (n.isEmpty()) return;
                        if (EqPresetStore.isBuiltIn(n)) { Toast.makeText(EqualizerActivity.this, R.string.ax_eq_p_reserved, Toast.LENGTH_SHORT).show(); return; }
                        EqPresetStore.upsert(EqualizerActivity.this, EqPresetStore.fromConfig(n, cfg));
                        mine = EqPresetStore.load(EqualizerActivity.this);
                        cfg.preset = n;
                        cfg.save(EqualizerActivity.this);
                        renderChips();
                        Toast.makeText(EqualizerActivity.this, getString(R.string.ax_eq_p_saved, n), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.ax_eq_cancel, null).show();
    }

    private void importDialog() {
        String[] items = {getString(R.string.ax_eq_p_from_file), getString(R.string.ax_eq_p_from_clip)};
        dlg().setTitle(R.string.ax_eq_p_import_title).setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface di, int which) {
                if (which == 0) {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    try { startActivityForResult(i, REQ_IMPORT); } catch (Throwable t) { toastErr(); }
                } else {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    String s = "";
                    try {
                        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                            CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(EqualizerActivity.this);
                            if (cs != null) s = cs.toString();
                        }
                    } catch (Throwable ignored) { }
                    doImport(s);
                }
            }
        }).setNegativeButton(R.string.ax_eq_cancel, null).show();
    }

    private void doImport(String text) {
        int n = EqPresetStore.importText(this, text);
        mine = EqPresetStore.load(this);
        renderChips();
        Toast.makeText(this, n > 0 ? getString(R.string.ax_eq_p_imported, n) : getString(R.string.ax_eq_p_import_fail), Toast.LENGTH_LONG).show();
    }

    private void toastErr() { Toast.makeText(this, R.string.ax_eq_p_export_fail, Toast.LENGTH_SHORT).show(); }

    private List<EqPresetStore.UserPreset> currentAsList() {
        String n = EqConfig.CUSTOM.equals(cfg.preset) ? "Arkael Preset" : cfg.preset;
        List<EqPresetStore.UserPreset> l = new ArrayList<EqPresetStore.UserPreset>();
        l.add(EqPresetStore.fromConfig(n, cfg));
        return l;
    }

    /** Pilih apa yang diekspor/dibagikan: setelan sekarang atau semua preset tersimpan. */
    private void chooseScope(final boolean share) {
        if (mine.isEmpty()) { out(currentAsList(), share); return; }
        String[] items = {getString(R.string.ax_eq_p_scope_current), getString(R.string.ax_eq_p_scope_all, mine.size())};
        dlg().setTitle(share ? R.string.ax_eq_p_share : R.string.ax_eq_p_export).setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface di, int which) {
                out(which == 0 ? currentAsList() : new ArrayList<EqPresetStore.UserPreset>(mine), share);
            }
        }).setNegativeButton(R.string.ax_eq_cancel, null).show();
    }

    private void out(List<EqPresetStore.UserPreset> list, boolean share) {
        String json = EqPresetStore.export(list);
        if (share) {
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType("text/plain");
            s.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.ax_eq_p_share_subject));
            s.putExtra(Intent.EXTRA_TEXT, getString(R.string.ax_eq_p_share_text) + "\n\n" + json);
            try { startActivity(Intent.createChooser(s, getString(R.string.ax_eq_p_share))); } catch (Throwable t) { toastErr(); }
        } else {
            pendingExport = json;
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            String base = list.size() == 1 ? list.get(0).name.replaceAll("[^A-Za-z0-9_-]+", "_") : "presets";
            i.putExtra(Intent.EXTRA_TITLE, "arkael_eq_" + base + ".json");
            try { startActivityForResult(i, REQ_EXPORT); } catch (Throwable t) { toastErr(); }
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        if (req == REQ_IMPORT) {
            try {
                InputStream in = getContentResolver().openInputStream(u);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n, tot = 0;
                while (in != null && (n = in.read(buf)) > 0) {
                    tot += n;
                    if (tot > 512 * 1024) break;
                    bo.write(buf, 0, n);
                }
                if (in != null) in.close();
                doImport(bo.toString("UTF-8"));
            } catch (Throwable t) {
                toastErr();
            }
        } else if (req == REQ_EXPORT && pendingExport != null) {
            try {
                OutputStream os = getContentResolver().openOutputStream(u, "wt");
                if (os == null) throw new java.io.IOException();
                os.write(pendingExport.getBytes("UTF-8"));
                os.close();
                Toast.makeText(this, R.string.ax_eq_p_exported, Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                toastErr();
            }
            pendingExport = null;
        }
    }

    private void userPresetMenu(final String name) {
        final EqPresetStore.UserPreset u = EqPresetStore.find(mine, name);
        if (u == null) return;
        String[] items = {getString(R.string.ax_eq_p_share), getString(R.string.ax_eq_p_export), getString(R.string.ax_eq_p_delete)};
        dlg().setTitle(name).setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface di, int which) {
                List<EqPresetStore.UserPreset> one = new ArrayList<EqPresetStore.UserPreset>();
                one.add(u);
                if (which == 0) out(one, true);
                else if (which == 1) out(one, false);
                else {
                    dlg().setMessage(getString(R.string.ax_eq_p_confirm_delete, name))
                            .setPositiveButton(R.string.ax_eq_p_delete, new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d2, int w) {
                                    EqPresetStore.delete(EqualizerActivity.this, name);
                                    mine = EqPresetStore.load(EqualizerActivity.this);
                                    if (name.equals(cfg.preset)) cfg.preset = EqConfig.CUSTOM;
                                    renderChips();
                                    Toast.makeText(EqualizerActivity.this, R.string.ax_eq_p_deleted, Toast.LENGTH_SHORT).show();
                                }
                            }).setNegativeButton(R.string.ax_eq_cancel, null).show();
                }
            }
        }).show();
    }

    // ------------------------------------------------------------------ logika

    private void stopEq() {
        try {
            Intent i = new Intent(this, EqService.class).setAction(EqService.ACTION_STOP);
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
        for (EqPresetStore.UserPreset u : mine) names.add(u.name);
        if (EqConfig.CUSTOM.equals(cfg.preset)) names.add(EqConfig.CUSTOM);
        for (final String n : names) {
            final boolean user = EqPresetStore.find(mine, n) != null;
            TextView c = pill((user ? "★ " : "") + n, n.equals(cfg.preset));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    EqConfig.Preset p = user ? EqPresetStore.find(mine, n).toPreset() : EqConfig.find(n);
                    if (p == null) return;
                    cfg.applyPreset(p);
                    bindAll();
                    persist(true);
                }
            });
            if (user) {
                c.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) { userPresetMenu(n); return true; }
                });
            }
            chipRow.addView(c, lp);
        }
    }

    private void renderMethod() {
        methodRow.removeAllViews();
        String[] n = {getString(R.string.ax_eq_m_auto), getString(R.string.ax_eq_m_global), getString(R.string.ax_eq_m_session)};
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
        hintTv.setText(cfg.method == EqConfig.M_AUTO ? R.string.ax_eq_m_auto_hint
                : cfg.method == EqConfig.M_GLOBAL ? R.string.ax_eq_m_global_hint
                : R.string.ax_eq_m_session_hint);
    }

    private void refreshStatus() {
        if (!cfg.enabled) { statusTv.setText(R.string.ax_eq_off); setDot(SUB); return; }
        String s = EqService.status();
        if ("Mati".equals(s)) { statusTv.setText(R.string.ax_eq_starting); setDot(CYAN); return; }
        statusTv.setText(EqText.loc(this, s));
        setDot(s.contains("GAGAL") ? RED : GREEN);
    }

    private void showDiag() {
        StringBuilder sb = new StringBuilder();
        sb.append("Device  : ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Android : ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Status  : ").append(EqService.status()).append("\n");
        sb.append("Config  : ").append(cfg.toJson()).append("\n\n");
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
        dlg().setTitle(R.string.ax_eq_diag_title)
                .setView(s)
                .setPositiveButton(R.string.ax_eq_copy, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface di, int w) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("eq", all));
                        Toast.makeText(EqualizerActivity.this, R.string.ax_eq_copied, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.ax_eq_close, null)
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
