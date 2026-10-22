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

        col.addView(site("ANICHIN", getString(R.string.ax_site_donghua), "https://anichin.care/", "#FF7043"));
        col.addView(site("ANIMEXIN", getString(R.string.ax_site_donghua2), "https://animexin.dev/", "#AB47BC"));
        col.addView(site("SAMEHADAKU", getString(R.string.ax_site_anime), "https://samehadaku.care/", "#29B6F6"));

        setContentView(sv);
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
