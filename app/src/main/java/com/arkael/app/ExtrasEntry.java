package com.arkael.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Called once from MainActivity.onCreate: adds the "Anime & Donghua" and "Settings" cards to the Tools tab,
 * starts the hourly worker, asks for the notification permission and checks GitHub for a new release.
 */
final class ExtrasEntry {
    private ExtrasEntry() {}

    private static int dp(Activity a, float v) { return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f); }

    static void attach(final Activity a) {
        try { Notifier.ensureChannels(a); } catch (Throwable ignored) {}
        try { HourlyWorker.schedule(a); } catch (Throwable ignored) {}
        try { askNotifPermission(a); } catch (Throwable ignored) {}
        try { UpdateNotifier.checkInApp(a, false); } catch (Throwable ignored) {}
        try {
            View tools = a.findViewById(R.id.tools_content);
            if (!(tools instanceof LinearLayout)) return;
            LinearLayout col = (LinearLayout) tools;

            card(a, col, R.drawable.ic_play_circle, R.drawable.bg_ic_red, "#FF4444",
                    a.getString(R.string.ax_card_anime), a.getString(R.string.ax_card_anime_sub), AnimeHubActivity.class);
            card(a, col, R.drawable.ic_cpu, R.drawable.bg_ic_cyan, "#00E5FF",
                    a.getString(R.string.ax_settings), a.getString(R.string.ax_card_settings_sub), AppSettingsActivity.class);
        } catch (Exception ignored) {
            // never break the main screen because of the extra cards
        }
    }

    private static void askNotifPermission(Activity a) {
        if (Build.VERSION.SDK_INT < 33) return;
        if (a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences sp = a.getSharedPreferences("ax_update", Activity.MODE_PRIVATE);
        long last = sp.getLong("perm_asked", 0);
        if (System.currentTimeMillis() - last < 24L * 3600 * 1000) return;   // at most once a day
        sp.edit().putLong("perm_asked", System.currentTimeMillis()).apply();
        a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7101);
    }

    private static void card(final Activity a, LinearLayout col, int icon, int iconBg, String tint,
                             String title, String sub, final Class<?> target) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(dp(a, 66));
        card.setBackgroundResource(R.drawable.bg_card_rounded);
        card.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10));
        card.setTag("card");

        ImageView ic = new ImageView(a);
        ic.setImageResource(icon);
        ic.setBackgroundResource(iconBg);
        ic.setPadding(dp(a, 10), dp(a, 10), dp(a, 10), dp(a, 10));
        ic.setColorFilter(Color.parseColor(tint));
        card.addView(ic, new LinearLayout.LayoutParams(dp(a, 42), dp(a, 42)));

        LinearLayout txt = new LinearLayout(a);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(a);
        t1.setText(title.toUpperCase(java.util.Locale.getDefault()));
        t1.setTextColor(Color.WHITE);
        t1.setTextSize(14);
        t1.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView t2 = new TextView(a);
        t2.setText(sub);
        t2.setTextColor(Color.parseColor("#8B95A3"));
        t2.setTextSize(11);
        txt.addView(t1);
        txt.addView(t2);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(a, 14);
        card.addView(txt, tlp);

        ImageView ch = new ImageView(a);
        ch.setImageResource(R.drawable.ic_chevron);
        ch.setColorFilter(Color.parseColor("#556070"));
        card.addView(ch, new LinearLayout.LayoutParams(dp(a, 20), dp(a, 20)));

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.startActivity(new Intent(a, target)); }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(a, 12);
        col.addView(card, clp);
    }
}
