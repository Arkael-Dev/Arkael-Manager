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

/** Adds a "Game Mode" card at the bottom of the Tools tab without touching the layout XML. */
final class GameModeEntry {
    private GameModeEntry() {}

    private static int dp(Activity a, float v) { return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f); }

    static void attach(final Activity a) {
        try {
            View tools = a.findViewById(R.id.tools_content);
            if (!(tools instanceof LinearLayout)) return;
            LinearLayout col = (LinearLayout) tools;

            TextView sec = new TextView(a);
            sec.setText("GAME");
            sec.setTextColor(Color.parseColor("#00E5FF"));
            sec.setTextSize(11);
            sec.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            sec.setLetterSpacing(0.12f);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(a, 18); slp.bottomMargin = dp(a, 6);
            col.addView(sec, slp);

            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setMinimumHeight(dp(a, 66));
            card.setBackgroundResource(R.drawable.bg_card_rounded);
            card.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10));
            card.setTag("card");

            ImageView ic = new ImageView(a);
            ic.setImageResource(R.drawable.ic_cpu);
            ic.setBackgroundResource(R.drawable.bg_ic_cyan);
            ic.setPadding(dp(a, 10), dp(a, 10), dp(a, 10), dp(a, 10));
            ic.setColorFilter(Color.parseColor("#00E5FF"));
            card.addView(ic, new LinearLayout.LayoutParams(dp(a, 42), dp(a, 42)));

            LinearLayout txt = new LinearLayout(a);
            txt.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(a);
            t1.setText("GAME MODE");
            t1.setTextColor(Color.WHITE);
            t1.setTextSize(14);
            t1.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView t2 = new TextView(a);
            t2.setText("Live performance overlay");
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
                @Override public void onClick(View v) { a.startActivity(new Intent(a, GameModeActivity.class)); }
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = dp(a, 12);
            col.addView(card, clp);
        } catch (Exception ignored) {
            // never break the main screen because of the extra card
        }
    }

}
