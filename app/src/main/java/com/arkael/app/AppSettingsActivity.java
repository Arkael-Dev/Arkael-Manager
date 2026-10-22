package com.arkael.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/** Language, donation link, update check. */
public class AppSettingsActivity extends AppCompatActivity {
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

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
        h.setText(R.string.ax_settings);
        h.setTextColor(Color.WHITE);
        h.setTextSize(22);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        h.setPadding(0, 0, 0, dp(14));
        col.addView(h);

        col.addView(row("🌐", getString(R.string.ax_language), LocaleHelper.label(this, LocaleHelper.current()), v -> pickLanguage()));
        col.addView(row("❤", getString(R.string.ax_donate), getString(R.string.ax_donate_sub),
                v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Notifier.DONATE_URL)))));
        col.addView(row("⬇", getString(R.string.ax_check_update), getString(R.string.ax_version, AppVersion.name("?")),
                v -> UpdateNotifier.checkInApp(this, true)));
        col.addView(row("⌥", "GitHub", Notifier.REPO_URL,
                v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Notifier.REPO_URL)))));

        setContentView(sv);
    }

    private View row(String icon, String title, String sub, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#141A22"));
        g.setCornerRadius(dp(16));
        r.setBackground(g);
        r.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        r.setLayoutParams(lp);

        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(20);
        ic.setTextColor(Color.parseColor("#AEC6FF"));
        ic.setGravity(android.view.Gravity.CENTER);
        r.addView(ic, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView a = new TextView(this);
        a.setText(title);
        a.setTextColor(Color.WHITE);
        a.setTextSize(15);
        a.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(Color.parseColor("#8B95A3"));
        s.setTextSize(12);
        t.addView(a); t.addView(s);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = dp(12);
        r.addView(t, tl);
        r.setOnClickListener(l);
        return r;
    }

    private void pickLanguage() {
        final String[][] L = LocaleHelper.LANGS;
        String[] names = new String[L.length + 1];
        names[0] = getString(R.string.ax_lang_system);
        int checked = 0;
        String cur = LocaleHelper.current();
        for (int i = 0; i < L.length; i++) {
            names[i + 1] = L[i][1];
            if (L[i][0].equals(cur)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.ax_language)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    d.dismiss();
                    LocaleHelper.set(which == 0 ? "" : L[which - 1][0]);   // recreates the screens by itself
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
