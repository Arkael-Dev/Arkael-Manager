# Jalankan dari ~/Arkael-Manager :  python3 apply_watch_history.py
# Pasang: riwayat tontonan anime/donghua + bagian "Lanjutkan menonton" di menu Anime.
import os
D = 'app/src/main/java/com/arkael/app/'
BR, HUB = D + 'BrowserActivity.java', D + 'AnimeHubActivity.java'
ok, bad = [], []

def rd(f): return open(f, encoding='utf8').read()
def wr(f, s): open(f, 'w', encoding='utf8').write(s)

# ---------------- BrowserActivity: simpan riwayat ----------------
if not os.path.exists(BR):
    bad.append('BrowserActivity.java tidak ditemukan (jalankan dari root repo)')
else:
    s = rd(BR)
    if 'WatchHistory.record' in s:
        ok.append('BrowserActivity (sudah terpasang)')
    else:
        a1 = ("            @Override public void onPageFinished(WebView v, String url) {\n"
              "                bar.setVisibility(View.GONE);\n")
        a2 = "            @Override public void onProgressChanged(WebView v, int p) { bar.setProgress(p); }\n"
        if a1 in s and a2 in s:
            s = s.replace(a1, a1 + "                saveHistory(url, v.getTitle());\n", 1)
            s = s.replace(a2, a2 + "\n            @Override public void onReceivedTitle(WebView v, String title) {\n"
                                   "                saveHistory(v.getUrl(), title);\n            }\n", 1)
            helper = ("    private void saveHistory(String url, String title) {\n"
                      "        WatchHistory.record(this, url, title,\n"
                      "                getIntent().getStringExtra(EXTRA_TITLE), getIntent().getStringExtra(EXTRA_URL));\n"
                      "    }\n\n"
                      "    private boolean allowedHost(String host) {")
            if 'private boolean allowedHost(String host) {' in s:
                s = s.replace('    private boolean allowedHost(String host) {', helper, 1)
                wr(BR, s); ok.append('BrowserActivity')
            else:
                bad.append('BrowserActivity: anchor allowedHost tidak ketemu')
        else:
            bad.append('BrowserActivity: anchor onPageFinished/onProgressChanged tidak cocok (file sudah berubah?)')

# ---------------- AnimeHubActivity: daftar "Lanjutkan menonton" ----------------
if not os.path.exists(HUB):
    bad.append('AnimeHubActivity.java tidak ditemukan')
else:
    s = rd(HUB)
    if 'renderHistory' in s:
        ok.append('AnimeHubActivity (sudah terpasang)')
    else:
        a_sub = "        col.addView(sub);\n"
        a_set = "        setContentView(sv);\n    }\n"
        if a_sub in s and a_set in s:
            s = s.replace(a_sub, a_sub + "\n        histBox = new LinearLayout(this);\n"
                          "        histBox.setOrientation(LinearLayout.VERTICAL);\n"
                          "        col.addView(histBox);\n", 1)
            s = s.replace(a_set, "        setContentView(sv);\n    }\n\n"
                          "    @Override\n    protected void onResume() {\n"
                          "        super.onResume();\n        renderHistory();\n    }\n", 1)
            s = s.replace("public class AnimeHubActivity extends AppCompatActivity {\n",
                          "public class AnimeHubActivity extends AppCompatActivity {\n"
                          "    private LinearLayout histBox;\n", 1)
            methods = r'''
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
'''
            k = s.index("    private View site(")
            s = s[:k] + methods.lstrip('\n') + "\n" + s[k:]
            wr(HUB, s); ok.append('AnimeHubActivity')
        else:
            bad.append('AnimeHubActivity: anchor tidak cocok (file sudah berubah?)')

print('BERHASIL:'); [print('  +', x) for x in ok]
if bad:
    print('GAGAL:'); [print('  !', x) for x in bad]
else:
    print('Selesai. File baru WatchHistory.java + strings_history.xml sudah ada dari zip.')
