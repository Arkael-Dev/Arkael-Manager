package com.arkael.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Picker for the three anime / donghua sites; each opens in the ad-free in-app browser. */
public class AnimeHubActivity extends AppCompatActivity {
    private LinearLayout histBox;
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0A0D12"));
        getWindow().setNavigationBarColor(Color.parseColor("#0A0D12"));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#0A0D12"));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(16), dp(16), dp(24));
        sv.addView(col);
        sv.setOnApplyWindowInsetsListener((v, ins) -> {
            v.setPadding(0, ins.getSystemWindowInsetTop(), 0, ins.getSystemWindowInsetBottom());
            return ins;
        });

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextColor(Color.WHITE);
        back.setTextSize(24);
        back.setPadding(0, 0, dp(16), dp(8));
        back.setOnClickListener(v -> finish());
        col.addView(back);

        TextView h = new TextView(this);
        h.setText(R.string.ax_anime_title);
        h.setTextColor(Color.WHITE);
        h.setTextSize(22);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        col.addView(h);

        TextView sub = new TextView(this);
        sub.setText(R.string.ax_anime_sub);
        sub.setTextColor(Color.parseColor("#8B95A3"));
        sub.setTextSize(13);
        sub.setPadding(0, dp(4), 0, dp(16));
        col.addView(sub);

        histBox = new LinearLayout(this);
        histBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(histBox);

        col.addView(site("ANICHIN", getString(R.string.ax_site_donghua), "https://anichin.care/", "#FF7043"));
        col.addView(site("ANIMEXIN", getString(R.string.ax_site_donghua2), "https://animexin.dev/", "#AB47BC"));
        col.addView(site("SAMEHADAKU", getString(R.string.ax_site_anime), "https://samehadaku.care/", "#29B6F6"));

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderHistory();
    }

    // ------------------------------------------------------------ riwayat tontonan
    private void renderHistory() {
        histBox.removeAllViews();
        final java.util.List<WatchHistory.Item> items = WatchHistory.getAll(this);
        if (items.isEmpty()) return;

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, dp(8));
        TextView ht = new TextView(this);
        ht.setText(R.string.ax_history_title);
        ht.setTextColor(Color.WHITE);
        ht.setTextSize(16);
        ht.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView clr = new TextView(this);
        clr.setText(R.string.ax_history_clear);
        clr.setTextColor(Color.parseColor("#8B95A3"));
        clr.setTextSize(12);
        clr.setPadding(dp(8), dp(6), 0, dp(6));
        clr.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(R.string.ax_history_clear_q)
                .setPositiveButton(android.R.string.ok, (d, w) -> { WatchHistory.clear(this); renderHistory(); })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        head.addView(clr);
        histBox.addView(head);

        int n = Math.min(items.size(), 10);
        for (int i = 0; i < n; i++) histBox.addView(historyRow(items.get(i)));

        View gap = new View(this);
        histBox.addView(gap, new LinearLayout.LayoutParams(-1, dp(8)));
    }

    private View historyRow(final WatchHistory.Item it) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(round(Color.parseColor("#141A22"), 14));
        row.setPadding(dp(14), dp(10), dp(6), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        TextView play = new TextView(this);
        play.setText("▶");
        play.setTextColor(Color.parseColor("#29B6F6"));
        play.setTextSize(18);
        play.setPadding(0, 0, dp(12), 0);
        row.addView(play);

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText(it.title);
        t1.setTextColor(Color.WHITE);
        t1.setTextSize(14);
        t1.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t1.setSingleLine(true);
        t1.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView t2 = new TextView(this);
        String when = android.text.format.DateUtils.getRelativeTimeSpanString(
                it.ts, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString();
        t2.setText(getString(R.string.ax_history_episode, it.ep)
                + (it.site.isEmpty() ? "" : "  ·  " + it.site) + "  ·  " + when);
        t2.setTextColor(Color.parseColor("#8B95A3"));
        t2.setTextSize(12);
        t2.setSingleLine(true);
        txt.addView(t1);
        txt.addView(t2);
        row.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView x = new TextView(this);
        x.setText("✕");
        x.setTextColor(Color.parseColor("#8B95A3"));
        x.setTextSize(16);
        x.setGravity(Gravity.CENTER);
        x.setOnClickListener(v -> { WatchHistory.remove(this, it.key); renderHistory(); });
        row.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));

        row.setOnClickListener(v -> {
            Intent i = new Intent(this, BrowserActivity.class);
            i.putExtra(BrowserActivity.EXTRA_URL, it.url);
            i.putExtra(BrowserActivity.EXTRA_TITLE, it.site.isEmpty() ? it.title : it.site);
            startActivity(i);
        });
        return row;
    }

    private View site(String name, String desc, String url, String accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(Color.parseColor("#141A22"), 16));
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        TextView t1 = new TextView(this);
        t1.setText(name);
        t1.setTextColor(Color.parseColor(accent));
        t1.setTextSize(16);
        t1.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView t2 = new TextView(this);
        t2.setText(desc);
        t2.setTextColor(Color.WHITE);
        t2.setTextSize(13);
        t2.setPadding(0, dp(2), 0, 0);
        TextView t3 = new TextView(this);
        t3.setText("🛡  " + getString(R.string.ax_no_ads));
        t3.setTextColor(Color.parseColor("#8B95A3"));
        t3.setTextSize(11);
        t3.setPadding(0, dp(6), 0, 0);
        card.addView(t1); card.addView(t2); card.addView(t3);

        card.setOnClickListener(v -> {
            Intent i = new Intent(this, BrowserActivity.class);
            i.putExtra(BrowserActivity.EXTRA_URL, url);
            i.putExtra(BrowserActivity.EXTRA_TITLE, name);
            startActivity(i);
        });
        return card;
    }
}
