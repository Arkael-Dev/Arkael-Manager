package com.arkael.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Riwayat sesi game: durasi, FPS rata-rata, suhu baterai tertinggi, baterai terpakai. */
public class SessionLogActivity extends Activity {

    private static final int BG = Color.parseColor("#0A0D12");
    private static final int CARD = Color.parseColor("#141A22");
    private static final int MUTED = Color.parseColor("#8B95A3");
    private static final int ACCENT = Color.parseColor("#00E5FF");
    private final java.util.Map<String, String> names = new java.util.HashMap<String, String>();

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LocaleWrap.wrap(base));
    }

    private boolean indo() {
        String l = Locale.getDefault().getLanguage();
        return "in".equals(l) || "id".equals(l);
    }

    private String tr(String en, String id) { return indo() ? id : en; }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        android.view.Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(BG);
        col.setPadding(dp(16), dp(24), dp(16), dp(16));

        TextView h = txt(tr("Game sessions", "Riwayat sesi game"), Color.WHITE, 24, true);
        col.addView(h);
        TextView sub = txt(tr("Recorded by Auto game mode. A session must last at least a minute.",
                "Dicatat oleh Auto game mode. Sesi minimal 1 menit."), MUTED, 12, false);
        sub.setPadding(0, dp(4), 0, dp(12));
        col.addView(sub);

        List<SessionStats.Entry> list = SessionStats.list(this);
        SessionStats.Totals t = SessionStats.totals(this);

        // ---- ringkasan
        LinearLayout sum = new LinearLayout(this);
        sum.setOrientation(LinearLayout.VERTICAL);
        sum.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(CARD);
        sg.setCornerRadius(dp(14));
        sum.setBackground(sg);
        sum.addView(txt(tr("LAST 7 DAYS", "7 HARI TERAKHIR"), ACCENT, 11, true));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(8), 0, 0);
        row.addView(stat(SessionStats.dur(t.weekSec), tr("playtime", "waktu main")), weight(1f));
        row.addView(stat(String.valueOf(t.weekSessions), tr("sessions", "sesi")), weight(1f));
        row.addView(stat(t.bestFps > 0 ? t.bestFps + " FPS" : "—", tr("best avg", "avg terbaik")), weight(1f));
        row.addView(stat(t.hottest > 0 ? String.format(Locale.US, "%.1f\u00B0C", t.hottest) : "—", tr("hottest", "terpanas")), weight(1f));
        sum.addView(row);
        if (!t.topPkg.isEmpty()) {
            TextView top = txt(tr("Most played: ", "Paling sering: ") + name(t.topPkg) + " · " + SessionStats.dur(t.topSec), MUTED, 11, false);
            top.setPadding(0, dp(10), 0, 0);
            sum.addView(top);
        }
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.bottomMargin = dp(12);
        col.addView(sum, slp);

        // ---- daftar
        if (list.isEmpty()) {
            TextView e = txt(tr("No sessions yet.\nTick your games in Games & profiles, turn Auto game mode on and play for at least a minute.",
                    "Belum ada sesi.\nCentang game di Games & profiles, nyalakan Auto game mode lalu main minimal 1 menit."), MUTED, 13, false);
            e.setPadding(0, dp(8), 0, 0);
            col.addView(e);
        } else {
            ScrollView sv = new ScrollView(this);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            for (SessionStats.Entry e : list) box.addView(card(e));
            sv.addView(box);
            col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        if (!list.isEmpty()) {
            TextView clear = txt(tr("Clear history", "Hapus riwayat"), MUTED, 12, true);
            clear.setGravity(Gravity.CENTER);
            clear.setPadding(0, dp(12), 0, dp(6));
            clear.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { confirmClear(); }
            });
            col.addView(clear);
        }

        setContentView(col);
    }

    private LinearLayout.LayoutParams weight(float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
        return lp;
    }

    private LinearLayout stat(String big, String small) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(txt(big, Color.WHITE, 15, true));
        c.addView(txt(small, MUTED, 10, false));
        return c;
    }

    private LinearLayout card(SessionStats.Entry e) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(14));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = txt(name(e.pkg), Color.WHITE, 14, true);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(txt(when(e.t), MUTED, 11, false));
        c.addView(top);

        StringBuilder sb = new StringBuilder(dur(e.sec));
        if (e.fps > 0) sb.append(" · ").append(e.fps).append(" FPS");
        if (e.temp > 0) sb.append(" · ").append(String.format(Locale.US, "%.1f\u00B0C", e.temp));
        if (e.bat != 0) sb.append(" · ").append(e.bat).append("%");
        TextView l = txt(sb.toString(), ACCENT, 12, false);
        l.setPadding(0, dp(3), 0, 0);
        c.addView(l);
        return c;
    }

    private TextView txt(String s, int color, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private String dur(long sec) {
        if (sec <= 0) return tr("unknown", "tidak tahu");
        return SessionStats.dur(sec);
    }

    private String name(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "?";
        String c = names.get(pkg);
        if (c != null) return c;
        String n = pkg;
        try {
            PackageManager pm = getPackageManager();
            n = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable ignored) {}
        names.put(pkg, n);
        return n;
    }

    private String when(long t) {
        Calendar now = Calendar.getInstance(), then = Calendar.getInstance();
        then.setTimeInMillis(t);
        String hm = DateFormat.getTimeFormat(this).format(then.getTime());
        if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR))
            return tr("today ", "hari ini ") + hm;
        return DateFormat.format("dd MMM, ", t) + hm;
    }

    private void confirmClear() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(tr("Clear history?", "Hapus riwayat?"))
                .setMessage(tr("All recorded game sessions are deleted. This cannot be undone.",
                        "Semua catatan sesi game dihapus. Tidak bisa dibatalkan."))
                .setPositiveButton(tr("Delete", "Hapus"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        SessionStats.clear(SessionLogActivity.this);
                        Toast.makeText(SessionLogActivity.this, tr("History cleared", "Riwayat dihapus"), Toast.LENGTH_SHORT).show();
                        recreate();
                    }
                })
                .setNegativeButton(tr("Cancel", "Batal"), null)
                .show();
    }
}
