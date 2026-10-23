# Jalankan dari ~/Arkael-Manager :  python3 apply_yt_library.py
# Pasang: langganan channel tanpa login Google, playlist, mode putar/ulang + putar otomatis,
#         tombol Berikutnya, dan filter waktu/urutan di pencarian.
# Syarat: apply_yt_extras.py (v10) sudah dijalankan lebih dulu.
import os
B = 'app/src/main/java/com/arkael/app/'
PL, SE, MF = B + 'YoutubePlayerActivity.java', B + 'YoutubeSearchActivity.java', 'app/src/main/AndroidManifest.xml'
ok, bad = [], []

def rd(f): return open(f, encoding='utf8').read()
def wr(f, s): open(f, 'w', encoding='utf8').write(s)

def patch(path, tag, steps):
    """steps = [(nama, old, new)]. Semua-atau-tidak-sama-sekali per file."""
    if not os.path.exists(path):
        bad.append('%s tidak ditemukan' % path); return
    s = rd(path)
    if tag in s:
        ok.append('%s (sudah terpasang)' % os.path.basename(path)); return
    done, errs = [], []
    for name, old, new in steps:
        if s.count(old) != 1:
            errs.append('%s: anchor ditemukan %dx (harusnya 1x)' % (name, s.count(old))); continue
        s = s.replace(old, new, 1); done.append(name)
    if errs:
        bad.extend('%s -> %s' % (os.path.basename(path), e) for e in errs)
        return
    wr(path, s); ok.append('%s (%s)' % (os.path.basename(path), ', '.join(done)))

# =====================================================================  PEMUTAR
player_methods = r'''    // ---------------------------------------------------------------- antrean / langganan / playlist

    private int exMode() { return getSharedPreferences(PREFS, MODE_PRIVATE).getInt("ex_mode", 1); }

    private String exModeLabel() {
        int m = exMode();
        return m == 0 ? "Berhenti" : m == 2 ? "Ulang 1 video" : m == 3 ? "Ulang playlist" : "Otomatis";
    }

    private void exRefreshChips() {
        if (chipMode == null) return;
        chipMode.setText("🔁  " + exModeLabel() + (exQueueUrls.isEmpty() ? "" : "  (" + (exQueueIdx + 1) + "/" + exQueueUrls.size() + ")"));
        styleChip(chipMode, exMode() != 1 || !exQueueUrls.isEmpty());
    }

    private void exReadQueue(Intent i) {
        if (i != null && i.hasExtra("queue_urls")) {
            String[] u = i.getStringArrayExtra("queue_urls");
            String[] t = i.getStringArrayExtra("queue_titles");
            exQueueUrls.clear();
            exQueueTitles.clear();
            if (u != null) {
                for (int k = 0; k < u.length; k++) {
                    exQueueUrls.add(u[k]);
                    exQueueTitles.add(t != null && k < t.length && t[k] != null ? t[k] : "");
                }
            }
            exQueueName = i.getStringExtra("queue_name");
            String cur = i.getStringExtra("url");
            int idx = i.getIntExtra("queue_index", -1);
            if (idx < 0 || idx >= exQueueUrls.size() || !exQueueUrls.get(idx).equals(cur)) idx = exQueueUrls.indexOf(cur);
            exQueueIdx = idx;
            if (idx < 0) { exQueueUrls.clear(); exQueueTitles.clear(); }
        } else if (!exInternal) {
            exQueueUrls.clear();
            exQueueTitles.clear();
            exQueueIdx = -1;
            exQueueName = null;
        }
        exRefreshChips();
    }

    /** Ulang 1 video ditangani player; mode lain ditangani exOnEnded(). */
    private void exApplyRepeat() {
        try {
            if (controller != null) controller.setRepeatMode(exMode() == 2 ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        } catch (Exception ignored) { }
    }

    private void exSwitch(String url, String title) {
        exInternal = true;
        try {
            Intent it = new Intent();
            it.putExtra("url", url);
            it.putExtra("title", title);
            onNewIntent(it);
        } finally {
            exInternal = false;
        }
    }

    private void exOnEnded() {
        int mode = exMode();
        if (mode == 0 || mode == 2) return;
        exGoNext(false);
    }

    /** Video berikutnya: antrean (playlist) kalau ada, kalau tidak video terkait pertama yang belum ditonton. */
    private boolean exGoNext(boolean manual) {
        if (!exQueueUrls.isEmpty() && exQueueIdx >= 0) {
            int n = exQueueIdx + 1;
            if (n >= exQueueUrls.size()) {
                if (exMode() == 3) n = 0;
                else {
                    if (manual) Toast.makeText(this, "Sudah di akhir " + (exQueueName == null || exQueueName.isEmpty() ? "antrean" : exQueueName), Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            exQueueIdx = n;
            exSwitch(exQueueUrls.get(n), exQueueTitles.get(n));
            return true;
        }
        if (curInfo != null && curInfo.getRelatedItems() != null) {
            java.util.Set<String> seen = new java.util.HashSet<String>();
            seen.add(videoUrl);
            java.util.List<YtStore.Entry> hist = YtStore.history(this);
            for (int k = 0; k < hist.size() && k < 30; k++) seen.add(hist.get(k).url);
            InfoItem fallback = null;
            for (InfoItem ii : curInfo.getRelatedItems()) {
                if (!(ii instanceof StreamInfoItem) || ii.getUrl() == null || ii.getUrl().equals(videoUrl)) continue;
                if (fallback == null) fallback = ii;
                if (seen.contains(ii.getUrl())) continue;
                exSwitch(ii.getUrl(), ii.getName());
                return true;
            }
            if (fallback != null) { exSwitch(fallback.getUrl(), fallback.getName()); return true; }
        }
        if (manual) Toast.makeText(this, "Tidak ada video berikutnya", Toast.LENGTH_SHORT).show();
        return false;
    }

    private void showModeDialog() {
        String[] names = {"Berhenti setelah selesai", "Putar otomatis (playlist / video berikutnya)", "Ulangi video ini", "Ulangi playlist (balik ke awal)"};
        new AlertDialog.Builder(this)
                .setTitle("Mode putar")
                .setSingleChoiceItems(names, exMode(), (d, which) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("ex_mode", which).apply();
                    exApplyRepeat();
                    exRefreshChips();
                    d.dismiss();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // ---- langganan (lokal, tanpa akun Google)

    private String exChannelId() {
        return curInfo == null ? null : YtLibrary.channelIdFromUrl(curInfo.getUploaderUrl());
    }

    private void exRefreshSub() {
        if (btnSub == null) return;
        String id = exChannelId();
        if (id == null) { btnSub.setVisibility(View.GONE); return; }
        boolean on = YtLibrary.isSubscribed(this, id);
        btnSub.setText(on ? "Berhenti Berlangganan" : "Langganan");
        btnSub.setBackground(round(on ? C_ON : C_ACCENT, 20));
        btnSub.setTextColor(on ? Color.WHITE : C_BG);
        btnSub.setVisibility(View.VISIBLE);
    }

    private void exToggleSub() {
        String id = exChannelId();
        if (id == null || curInfo == null) return;
        YtLibrary.Sub s = new YtLibrary.Sub();
        s.id = id;
        s.name = curInfo.getUploaderName() == null ? "" : curInfo.getUploaderName();
        s.avatar = bestImage(curInfo.getUploaderAvatars());
        boolean on = YtLibrary.toggleSub(this, s);
        exRefreshSub();
        Toast.makeText(this, on ? "Berlangganan: " + s.name : "Berhenti berlangganan", Toast.LENGTH_SHORT).show();
    }

    private void exOpenChannel() {
        String id = exChannelId();
        if (id == null) return;
        Intent i = new Intent(this, YoutubeLibraryActivity.class);
        i.putExtra("ch", id);
        i.putExtra("chname", curInfo.getUploaderName());
        startActivity(i);
    }

    // ---- playlist

    private YtStore.Entry exEntry() {
        if (curEntry != null) return curEntry;
        YtStore.Entry e = new YtStore.Entry();
        e.url = videoUrl;
        e.title = videoTitle == null ? "" : videoTitle;
        e.uploader = "";
        return e;
    }

    private void exAddToPlaylistDialog() {
        final java.util.List<YtLibrary.Pl> pls = YtLibrary.playlists(this);
        final String[] names = new String[pls.size() + 1];
        names[0] = "➕  Playlist baru…";
        for (int i = 0; i < pls.size(); i++) names[i + 1] = pls.get(i).name + "  (" + pls.get(i).videos.size() + ")";
        new AlertDialog.Builder(this)
                .setTitle("Simpan ke playlist")
                .setItems(names, (d, which) -> {
                    if (which == 0) exNewPlaylistThenAdd();
                    else exAddTo(pls.get(which - 1));
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void exAddTo(YtLibrary.Pl pl) {
        boolean added = YtLibrary.addToPlaylist(this, pl.id, exEntry());
        Toast.makeText(this, added ? "Disimpan ke " + pl.name : "Sudah ada di " + pl.name, Toast.LENGTH_SHORT).show();
    }

    private void exNewPlaylistThenAdd() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setSingleLine(true);
        et.setHint("Nama playlist");
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Playlist baru")
                .setView(box)
                .setPositiveButton("Buat & simpan", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) return;
                    exAddTo(YtLibrary.createPlaylist(this, n));
                })
                .setNegativeButton("Batal", null)
                .show();
    }

'''

patch(PL, 'showModeDialog', [
    ('field', "    private int exTickN;\n",
     "    private int exTickN;\n"
     "    private final java.util.List<String> exQueueUrls = new java.util.ArrayList<String>();\n"
     "    private final java.util.List<String> exQueueTitles = new java.util.ArrayList<String>();\n"
     "    private int exQueueIdx = -1;\n"
     "    private String exQueueName;\n"
     "    private boolean exInternal, exEndedHandled;\n"
     "    private TextView btnSub, chipMode;\n"),
    ('readIntent', '        videoTitle = i == null ? null : i.getStringExtra("title");\n    }\n',
     '        videoTitle = i == null ? null : i.getStringExtra("title");\n        exReadQueue(i);\n    }\n'),
    ('tombol-langganan',
     "        chRow.addView(chText, ctl);\n        col.addView(chRow);\n",
     "        chRow.addView(chText, ctl);\n"
     "        btnSub = new TextView(this);\n"
     "        btnSub.setTextSize(13);\n"
     "        btnSub.setTypeface(Typeface.DEFAULT, Typeface.BOLD);\n"
     "        btnSub.setGravity(Gravity.CENTER);\n"
     "        btnSub.setPadding(dp(18), dp(10), dp(18), dp(10));\n"
     "        btnSub.setVisibility(View.GONE);\n"
     "        btnSub.setOnClickListener(v -> exToggleSub());\n"
     "        chRow.addView(btnSub);\n"
     "        chText.setOnClickListener(v -> exOpenChannel());\n"
     "        col.addView(chRow);\n"),
    ('chips',
     '        chips.addView(chip("🧩  Extras", v -> showExtrasDialog()));\n',
     '        chips.addView(chip("🧩  Extras", v -> showExtrasDialog()));\n'
     '        chipMode = chip("🔁  Otomatis", v -> showModeDialog());\n'
     '        chips.addView(chipMode);\n'
     '        chips.addView(chip("⏭  Berikutnya", v -> exGoNext(true)));\n'
     '        chips.addView(chip("➕  Playlist", v -> exAddToPlaylistDialog()));\n'
     '        exRefreshChips();\n'),
    ('ulang',
     '        if (exResume > 0) Toast.makeText(this, "Lanjut dari " + fmtDuration(exResume / 1000), Toast.LENGTH_SHORT).show();\n',
     '        if (exResume > 0) Toast.makeText(this, "Lanjut dari " + fmtDuration(exResume / 1000), Toast.LENGTH_SHORT).show();\n'
     '        exApplyRepeat();\n'),
    ('selesai',
     '        if (st == Player.STATE_ENDED) { YtExtras.clearPosition(this, videoUrl); return; }\n',
     '        if (st == Player.STATE_ENDED) {\n'
     '            YtExtras.clearPosition(this, videoUrl);\n'
     '            if (!exEndedHandled) { exEndedHandled = true; exOnEnded(); }\n'
     '            return;\n'
     '        }\n'),
    ('reset',
     "        exSegs = new java.util.ArrayList<YtExtras.Seg>();\n        exSkipped.clear();\n    }\n",
     "        exSegs = new java.util.ArrayList<YtExtras.Seg>();\n        exSkipped.clear();\n"
     "        exEndedHandled = false;\n"
     "        if (btnSub != null) btnSub.setVisibility(View.GONE);\n    }\n"),
    ('populate',
     "        loadExtras(videoUrl);\n    }\n",
     "        loadExtras(videoUrl);\n        exRefreshSub();\n    }\n"),
    ('methods', "    private void enterPip() {\n", player_methods + "    private void enterPip() {\n"),
])

# =====================================================================  PENCARIAN
search_methods = r'''    // ---------------------------------------------------------------- langganan, playlist, filter

    private void openLibrary(int tab) {
        Intent in = new Intent(this, YoutubeLibraryActivity.class);
        in.putExtra("tab", tab);
        startActivity(in);
    }

    /** Feed langganan: video terbaru dari semua channel yang dilanggani (RSS publik, tanpa akun). */
    private void showSubs() {
        mode = 4;
        final int gen = ++searchGen;
        updateTabs();
        items.clear();
        adapter.notifyDataSetChanged();
        status.setText("");
        final int n = YtLibrary.subs(this).size();
        if (n == 0) {
            spinner.setVisibility(View.GONE);
            status.setText("Belum ada langganan.\nBuka video lalu tap Langganan di bawah judul.\nTanpa akun Google.");
            return;
        }
        spinner.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                final List<YtLibrary.FeedItem> feed = YtLibrary.fetchFeed(YoutubeSearchActivity.this);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (gen != searchGen) return;
                        spinner.setVisibility(View.GONE);
                        items.clear();
                        Row m = new Row();
                        m.url = null;
                        m.title = "👥  Kelola langganan (" + n + ")";
                        m.uploader = "Lihat atau berhenti berlangganan channel";
                        items.add(m);
                        for (YtLibrary.FeedItem f : feed) {
                            Row r = new Row();
                            r.url = f.url;
                            r.title = f.title;
                            String when = f.published > 0 ? android.text.format.DateUtils.getRelativeTimeSpanString(
                                    f.published, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString() : "";
                            r.uploader = f.uploader + (when.isEmpty() ? "" : "  ·  " + when);
                            r.thumb = f.thumb;
                            items.add(r);
                        }
                        adapter.notifyDataSetChanged();
                        status.setText(feed.isEmpty() ? "Gagal memuat video langganan. Cek koneksi, lalu tap Langganan lagi." : "");
                    }
                });
            }
        }).start();
    }

    /** Antrean dari daftar yang sedang tampil: video berikutnya diputar otomatis. */
    private void putQueue(Intent in, int clicked) {
        List<String> u = new ArrayList<String>();
        List<String> t = new ArrayList<String>();
        int idx = 0;
        for (int k = 0; k < items.size(); k++) {
            Row r = items.get(k);
            if (r.url == null) continue;
            if (k == clicked) idx = u.size();
            u.add(r.url);
            t.add(r.title == null ? "" : r.title);
        }
        in.putExtra("queue_urls", u.toArray(new String[0]));
        in.putExtra("queue_titles", t.toArray(new String[0]));
        in.putExtra("queue_index", idx);
        in.putExtra("queue_name", mode == 4 ? "Langganan" : "Disukai");
    }

    private TextView filterChip() {
        TextView t = new TextView(this);
        t.setTextSize(12);
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void refreshFilterChips() {
        chipTime.setText("🕒  " + F_TIME[fTime]);
        chipSort.setText("↕  " + (fSort == 0 ? "Relevansi" : "Terbaru"));
        styleTab(chipTime, fTime != 0);
        styleTab(chipSort, fSort != 0);
    }

    private void reSearch() {
        if (mode == 0 && !query.getText().toString().trim().isEmpty()) doSearch();
    }

    private void showTimeDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Waktu unggah")
                .setSingleChoiceItems(F_TIME, fTime, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        fTime = which;
                        refreshFilterChips();
                        d.dismiss();
                        reSearch();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showSortDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Urutkan")
                .setSingleChoiceItems(new String[]{"Relevansi", "Terbaru"}, fSort, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        fSort = which;
                        refreshFilterChips();
                        d.dismiss();
                        reSearch();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private static long uploadMs(StreamInfoItem si) {
        try {
            org.schabi.newpipe.extractor.localization.DateWrapper d = si.getUploadDate();
            if (d != null) return d.offsetDateTime().toInstant().toEpochMilli();
        } catch (Throwable ignored) { }
        return 0;
    }

    /**
     * Hasil pencarian. Dengan filter waktu / urutan "Terbaru" diambil beberapa halaman sekaligus lalu
     * disaring dan diurutkan di app (YouTube lewat NewPipe tidak menyediakan filter tanggal).
     */
    private List<InfoItem> searchItems(StreamingService yt, String q) throws Exception {
        SearchQueryHandler qh = yt.getSearchQHFactory().fromQuery(q);
        SearchInfo info = SearchInfo.getInfo(yt, qh);
        List<InfoItem> out = new ArrayList<InfoItem>(info.getRelatedItems());
        if (fTime == 0 && fSort == 0) return out;

        Page page = info.getNextPage();
        for (int p = 0; p < 4 && page != null; p++) {
            try {
                ListExtractor.InfoItemsPage<InfoItem> more = SearchInfo.getMoreItems(yt, qh, page);
                out.addAll(more.getItems());
                page = more.getNextPage();
            } catch (Exception e) {
                Log.w(TAG, "search: more pages failed", e);
                break;
            }
        }
        long now = System.currentTimeMillis();
        final java.util.Map<InfoItem, Long> when = new java.util.HashMap<InfoItem, Long>();
        List<InfoItem> res = new ArrayList<InfoItem>();
        for (InfoItem ii : out) {
            if (!(ii instanceof StreamInfoItem)) continue;
            long t = uploadMs((StreamInfoItem) ii);
            if (fTime != 0 && (t <= 0 || now - t > F_MS[fTime])) continue;
            when.put(ii, t);
            res.add(ii);
        }
        if (fSort == 1) {
            java.util.Collections.sort(res, new java.util.Comparator<InfoItem>() {
                @Override public int compare(InfoItem a, InfoItem b) { return Long.compare(when.get(b), when.get(a)); }
            });
        }
        return res;
    }

'''

patch(SE, 'searchItems', [
    ('imports',
     "import org.schabi.newpipe.extractor.InfoItem;\n",
     "import org.schabi.newpipe.extractor.InfoItem;\n"
     "import org.schabi.newpipe.extractor.ListExtractor;\n"
     "import org.schabi.newpipe.extractor.Page;\n"
     "import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;\n"),
    ('field',
     "    private TextView tabHome, tabHistory, tabLikes, clearBtn;\n",
     "    private TextView tabHome, tabHistory, tabLikes, clearBtn;\n"
     "    private TextView tabSubs, tabLists, chipTime, chipSort;\n"
     "    private int fTime = 0;     // 0 semua waktu, 1 hari ini, 2 minggu ini, 3 bulan ini, 4 tahun ini\n"
     "    private int fSort = 0;     // 0 relevansi, 1 terbaru\n"
     "    private static final String[] F_TIME = {\"Semua waktu\", \"Hari ini\", \"Minggu ini\", \"Bulan ini\", \"Tahun ini\"};\n"
     "    private static final long[] F_MS = {0L, 86400000L, 7 * 86400000L, 30 * 86400000L, 365 * 86400000L};\n"),
    ('filter-chip',
     "        col.addView(searchRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));\n",
     "        col.addView(searchRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));\n"
     "\n"
     "        LinearLayout fRow = new LinearLayout(this);\n"
     "        fRow.setOrientation(LinearLayout.HORIZONTAL);\n"
     "        fRow.setPadding(0, dp(10), 0, 0);\n"
     "        chipTime = filterChip();\n"
     "        chipSort = filterChip();\n"
     "        chipTime.setOnClickListener(new View.OnClickListener() {\n"
     "            @Override public void onClick(View v) { showTimeDialog(); }\n"
     "        });\n"
     "        chipSort.setOnClickListener(new View.OnClickListener() {\n"
     "            @Override public void onClick(View v) { showSortDialog(); }\n"
     "        });\n"
     "        fRow.addView(chipTime);\n"
     "        fRow.addView(chipSort);\n"
     "        col.addView(fRow);\n"),
    ('tab-baru',
     '        tabLikes = tab("Disukai", 2);\n',
     '        tabLikes = tab("Disukai", 2);\n'
     '        tabSubs = tab("Langganan", 4);\n'
     '        tabLists = tab("Playlist", 5);\n'),
    ('tab-urutan',
     "        tabs.addView(tabHome);\n        tabs.addView(tabHistory);\n        tabs.addView(tabLikes);\n",
     "        tabs.addView(tabHome);\n        tabs.addView(tabSubs);\n        tabs.addView(tabLists);\n"
     "        tabs.addView(tabHistory);\n        tabs.addView(tabLikes);\n"),
    ('tab-scroll',
     "        col.addView(tabs);\n",
     "        HorizontalScrollView tabsScroll = new HorizontalScrollView(this);\n"
     "        tabsScroll.setHorizontalScrollBarEnabled(false);\n"
     "        tabsScroll.addView(tabs);\n"
     "        col.addView(tabsScroll);\n"
     "        refreshFilterChips();\n"),
    ('tab-klik',
     "@Override public void onClick(View v) { if (m == 3) showHome(); else showStored(m); }",
     "@Override public void onClick(View v) {\n"
     "                if (m == 3) showHome();\n"
     "                else if (m == 4) showSubs();\n"
     "                else if (m == 5) openLibrary(0);\n"
     "                else showStored(m);\n"
     "            }"),
    ('tab-style',
     "        styleTab(tabLikes, mode == 2);\n",
     "        styleTab(tabLikes, mode == 2);\n        styleTab(tabSubs, mode == 4);\n        styleTab(tabLists, false);\n"),
    ('onResume',
     "        if (mode == 1 || mode == 2) showStored(mode);\n",
     "        if (mode == 4) showSubs();\n        else if (mode == 1 || mode == 2) showStored(mode);\n"),
    ('klik-item',
     "                Row r = items.get(i);\n"
     "                Intent in = new Intent(YoutubeSearchActivity.this, YoutubePlayerActivity.class);\n"
     "                in.putExtra(\"url\", r.url);\n"
     "                in.putExtra(\"title\", r.title);\n"
     "                startActivity(in);\n",
     "                Row r = items.get(i);\n"
     "                if (r.url == null) { openLibrary(1); return; }\n"
     "                Intent in = new Intent(YoutubeSearchActivity.this, YoutubePlayerActivity.class);\n"
     "                in.putExtra(\"url\", r.url);\n"
     "                in.putExtra(\"title\", r.title);\n"
     "                if (mode == 2 || mode == 4) putQueue(in, i);\n"
     "                startActivity(in);\n"),
    ('cari',
     "                    StreamingService yt = NewPipe.getService(\"YouTube\");\n"
     "                    SearchInfo info = SearchInfo.getInfo(yt, yt.getSearchQHFactory().fromQuery(q));\n"
     "                    for (InfoItem ii : info.getRelatedItems()) {\n",
     "                    StreamingService yt = NewPipe.getService(\"YouTube\");\n"
     "                    for (InfoItem ii : searchItems(yt, q)) {\n"),
    ('kosong',
     'status.setText(result.isEmpty() ? "No results" : "");',
     'status.setText(result.isEmpty() ? (fTime != 0 ? "Tidak ada video dalam rentang waktu ini. Coba waktu yang lebih luas." : "No results") : "");'),
    ('methods', "    private static String bestThumb(StreamInfoItem si) {\n", search_methods + "    private static String bestThumb(StreamInfoItem si) {\n"),
])

# =====================================================================  MANIFEST
if not os.path.exists(MF):
    bad.append('AndroidManifest.xml tidak ditemukan')
else:
    m = rd(MF)
    if '.YoutubeLibraryActivity' in m:
        ok.append('Manifest (sudah terpasang)')
    elif '</application>' not in m:
        bad.append('Manifest: </application> tidak ketemu')
    else:
        j = m.rindex('</application>')
        ls = m.rfind('\n', 0, j) + 1
        m = m[:ls] + '        <activity android:name=".YoutubeLibraryActivity" android:exported="false" android:label="Library"/>\n' + m[ls:]
        wr(MF, m); ok.append('Manifest (YoutubeLibraryActivity)')

print('BERHASIL:'); [print('  +', x) for x in ok]
if bad:
    print('GAGAL:'); [print('  !', x) for x in bad]; raise SystemExit(1)
print('Selesai. YtLibrary.java + YoutubeLibraryActivity.java sudah ada dari zip.')
