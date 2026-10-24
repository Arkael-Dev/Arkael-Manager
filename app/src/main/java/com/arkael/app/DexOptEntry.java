package com.arkael.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Menambah kartu "Dex2oat Optimizer" ke tab Tools. Dipanggil dari MainActivity tepat setelah ExtrasEntry.attach(this). */
final class DexOptEntry {
    private DexOptEntry() {}

    private static int dp(Activity a, float v) { return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f); }

    static void attach(final Activity a) {
        try {
            View tools = a.findViewById(R.id.tools_content);
            if (!(tools instanceof LinearLayout)) return;
            LinearLayout col = (LinearLayout) tools;
            for (int i = 0; i < col.getChildCount(); i++) {
                if ("dexopt_card".equals(col.getChildAt(i).getTag(R.id.tools_content))) return;   // sudah ada
            }

            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setMinimumHeight(dp(a, 66));
            card.setBackgroundResource(R.drawable.bg_card_rounded);
            card.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10));
            card.setTag("card");
            card.setTag(R.id.tools_content, "dexopt_card");

            ImageView ic = new ImageView(a);
            ic.setImageResource(R.drawable.ic_cpu);
            ic.setBackgroundResource(R.drawable.bg_ic_red);
            ic.setPadding(dp(a, 10), dp(a, 10), dp(a, 10), dp(a, 10));
            ic.setColorFilter(Color.parseColor("#FFB300"));
            card.addView(ic, new LinearLayout.LayoutParams(dp(a, 42), dp(a, 42)));

            LinearLayout txt = new LinearLayout(a);
            txt.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(a);
            t1.setText(DexOptActivity.cardTitle().toUpperCase(java.util.Locale.getDefault()));
            t1.setTextColor(Color.WHITE);
            t1.setTextSize(14);
            t1.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView t2 = new TextView(a);
            t2.setText(DexOptActivity.cardSub());
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
                @Override public void onClick(View v) { a.startActivity(new Intent(a, DexOptActivity.class)); }
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = dp(a, 12);
            // sebelum kartu terakhir (Pengaturan tetap paling bawah)
            col.addView(card, Math.max(0, col.getChildCount() - 1), clp);
        } catch (Throwable ignored) {
            // jangan pernah merusak layar utama
        }
    }
}
