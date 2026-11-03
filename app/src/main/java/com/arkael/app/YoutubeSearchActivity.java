package com.arkael.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

/**
 * Search YouTube and show results. Video pages are fetched directly through NewPipeExtractor
 * (no YouTube app, no WebView, no ads) — see NewPipeExtractorDownloader and YoutubePlayerActivity.
 */
public class YoutubeSearchActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LocaleWrap.wrap(base));
    }

    private static final String TAG = "ArkaelYT";
    private static final class Row {
        long durSecs;      // 0 = tidak diketahui
        long posMs;        // titik lanjut tonton (0 = belum ditonton)
        String url, title, uploader, duration, thumb;
    }

    private final List<Row> items = new ArrayList<Row>();
    private BaseAdapter adapter;
    private TextView status;
    private ProgressBar spinner;
    private EditText query;
    private int mode = 3;            // 0 = search results, 1 = Riwayat (history), 2 = Disukai (likes), 3 = Beranda (home feed)
    private TextView tabHome, tabHistory, tabLikes, clearBtn;
    private TextView tabSubs, tabLists, chipTime, chipSort;
    private int fTime = 0;     // 0 semua waktu, 1 hari ini, 2 minggu ini, 3 bulan ini, 4 tahun ini
    private int fSort = 0;     // 0 relevansi, 1 terbaru
    private static final String[] F_TIME = {Tr.s("Semua waktu"), "Hari ini", "Minggu ini", "Bulan ini", "Tahun ini"};
    private static final long[] F_MS = {0L, 86400000L, 7 * 86400000L, 30 * 86400000L, 365 * 86400000L};
    private int lastHistSize = -1;   // untuk refresh cepat (lihat onResume)
    private long lastResumeMs;
    private int searchGen = 0;   // bumped on every new search so a slow old search can't overwrite a newer result

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        NewPipeBootstrap.ensureInit();
        getWindow().setStatusBarColor(Color.parseColor("#0A0D12"));
        getWindow().setNavigationBarColor(Color.parseColor("#0A0D12"));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Color.parseColor("#0A0D12"));
        col.setPadding(dp(16), dp(24), dp(16), 0);

        TextView h = new TextView(this);
        h.setText("Arkael YouTube");
        h.setTextColor(Color.WHITE);
        h.setTextSize(24);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        col.addView(h);
        TextView sub = new TextView(this);
        sub.setText("Tanpa iklan, tanpa akun. Tap untuk memutar · tahan lama untuk simpan ke playlist. Keluar dari pemutar untuk mode layar kecil.");
        sub.setTextColor(Color.parseColor("#8B95A3"));
        sub.setTextSize(12);
        sub.setPadding(0, dp(4), 0, dp(12));
        col.addView(sub);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(Color.parseColor("#141A22"));
        sg.setCornerRadius(dp(12));
        searchRow.setBackground(sg);
        searchRow.setPadding(dp(14), 0, dp(6), 0);

        query = new EditText(this);
        query.setHint("Cari video, channel, atau lagu");
        query.setHintTextColor(Color.parseColor("#556070"));
        query.setTextColor(Color.WHITE);
        query.setBackground(null);
        query.setSingleLine(true);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(); return true; }
                return false;
            }
        });
        searchRow.addView(query, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView go = new TextView(this);
        go.setText("GO");
        go.setTextColor(Color.parseColor("#0A0D12"));
        go.setTextSize(12);
        go.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        go.setPadding(dp(14), dp(9), dp(14), dp(9));
        GradientDrawable gg = new GradientDrawable();
        gg.setColor(Color.parseColor("#FF4444"));
        gg.setCornerRadius(dp(8));
        go.setBackground(gg);
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doSearch(); }
        });
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gl.leftMargin = dp(8);
        searchRow.addView(go, gl);
        col.addView(searchRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout fRow = new LinearLayout(this);
        fRow.setOrientation(LinearLayout.HORIZONTAL);
        fRow.setPadding(0, dp(10), 0, 0);
        chipTime = filterChip();
        chipSort = filterChip();
        chipTime.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTimeDialog(); }
        });
        chipSort.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSortDialog(); }
        });
        fRow.addView(chipTime);
        fRow.addView(chipSort);
        col.addView(fRow);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(0, dp(10), 0, 0);
        tabHome = tab("Beranda", 3);
        tabHistory = tab(Tr.s("Riwayat"), 1);
        tabLikes = tab("Disukai", 2);
        tabSubs = tab("Langganan", 4);
        tabLists = tab("Playlist", 5);
        tabs.addView(tabHome);
        tabs.addView(tabSubs);
        tabs.addView(tabLists);
        tabs.addView(tabHistory);
        tabs.addView(tabLikes);
        View grow = new View(this);
        tabs.addView(grow, new LinearLayout.LayoutParams(0, 1, 1f));
        clearBtn = new TextView(this);
        clearBtn.setText(Tr.s("Hapus riwayat"));
        clearBtn.setTextColor(Color.parseColor("#FF6B6B"));
        clearBtn.setTextSize(12);
        clearBtn.setPadding(dp(8), dp(8), dp(4), dp(8));
        clearBtn.setVisibility(View.GONE);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClearHistory(); }
        });
        tabs.addView(clearBtn);
        HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabsScroll.addView(tabs);
        col.addView(tabsScroll);
        refreshFilterChips();

        HorizontalScrollView cats = new HorizontalScrollView(this);
        cats.setHorizontalScrollBarEnabled(false);
        LinearLayout catRow = new LinearLayout(this);
        catRow.setOrientation(LinearLayout.HORIZONTAL);
        catRow.setPadding(0, dp(10), 0, 0);
        String[][] cat = {{Tr.s("🎵 Musik"), "musik terbaru"}, {"🎮 Game", "gameplay game terbaru"}, {"🎌 Anime", "anime sub indo"},
                {"🐉 Donghua", "donghua sub indo"}, {"📰 Berita", "berita hari ini"}, {"💻 Teknologi", "review teknologi"},
                {"🎬 Film", "film terbaru"}};
        for (final String[] c : cat) catRow.addView(catChip(c[0], c[1]));
        cats.addView(catRow);
        col.addView(cats);

        spinner = new ProgressBar(this);
        LinearLayout.LayoutParams spl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spl.gravity = Gravity.CENTER;
        spl.topMargin = dp(24);
        spinner.setVisibility(View.GONE);
        col.addView(spinner, spl);

        status = new TextView(this);
        status.setText("");
        status.setTextColor(Color.parseColor("#8B95A3"));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(24), 0, 0);
        col.addView(status);

        ListView lv = new ListView(this);
        lv.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        lv.setDividerHeight(dp(8));
        lv.setClipToPadding(false);
        lv.setPadding(0, dp(10), 0, dp(16));
        adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) { return row(items.get(i)); }
        };
        lv.setAdapter(adapter);
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int i, long id) {
                Row r = items.get(i);
                if (r.url == null) return false;
                showRowMenu(r);
                return true;
            }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                Row r = items.get(i);
                if (r.url == null) { openLibrary(1); return; }
                Intent in = new Intent(YoutubeSearchActivity.this, YoutubePlayerActivity.class);
                in.putExtra("url", r.url);
                in.putExtra("title", r.title);
                if (mode == 2 || mode == 4) putQueue(in, i);
                startActivity(in);
            }
        });
        col.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(col);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh cepat: tab Riwayat/Disukai selalu dari data lokal (instan). Beranda cuma
        // diambil ulang kalau daftar tontonan berubah atau sudah lewat 5 menit, jadi balik
        // dari pemutar tidak bikin spinner muter lagi.
        long now = System.currentTimeMillis();
        boolean stale = now - lastResumeMs > 5 * 60_000L;
        lastResumeMs = now;
        int hs = YtStore.history(this).size();
        boolean changed = hs != lastHistSize;
        lastHistSize = hs;
        if (mode == 4) showSubs();
        else if (mode == 5) openLibrary(0);
        else if (mode == 1 || mode == 2) showStored(mode);
        else if (mode == 3 && (items.isEmpty() || changed || stale) && spinner.getVisibility() != View.VISIBLE) showHome();
        else if (mode == 0 && changed) adapter.notifyDataSetChanged();   // progress bar hasil pencarian ikut segar
    }

    private TextView tab(String label, final int m) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (m == 3) showHome();
                else if (m == 4) showSubs();
                else if (m == 5) openLibrary(0);
                else showStored(m);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void updateTabs() {
        styleTab(tabHome, mode == 3);
        styleTab(tabHistory, mode == 1);
        styleTab(tabLikes, mode == 2);
        styleTab(tabSubs, mode == 4);
        styleTab(tabLists, false);
        clearBtn.setVisibility(mode == 1 && !items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void styleTab(TextView t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(on ? "#2A3A5C" : "#141A22"));
        g.setCornerRadius(dp(16));
        t.setBackground(g);
        t.setTextColor(Color.parseColor(on ? "#FFFFFF" : "#8B95A3"));
    }

    private static String mainWord(String q) {
        int i = q.indexOf(' ');
        return i < 0 ? q : q.substring(0, i);
    }

    private TextView catChip(String label, final String q) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(12);
        t.setTextColor(Color.parseColor("#C9D3E3"));
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#141A22"));
        g.setCornerRadius(dp(16));
        t.setBackground(g);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { query.setText(q); doSearch(); }
        });
        t.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                // long-press = cari kata itu saja, tanpa embel-embel "terbaru"
                query.setText(mainWord(q));
                doSearch();
                return true;
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void addItems(List<Row> out, java.util.Set<String> seen, List<? extends InfoItem> src, int max) {
        int n = 0;
        if (src == null) return;
        for (InfoItem ii : src) {
            if (n >= max) break;
            if (!(ii instanceof StreamInfoItem)) continue;
            StreamInfoItem si = (StreamInfoItem) ii;
            if (si.getUrl() == null || !seen.add(si.getUrl())) continue;
            Row row = new Row();
            row.url = si.getUrl();
            row.title = si.getName();
            row.uploader = si.getUploaderName();
            long secs = si.getDuration();
            row.duration = secs > 0 ? fmt(secs) : null;
            row.durSecs = secs > 0 ? secs : 0;
            row.posMs = YtExtras.getPosition(YoutubeSearchActivity.this, si.getUrl());
            row.thumb = bestThumb(si);
            out.add(row);
            n++;
        }
    }

    /** Home feed, no typing: videos related to what was watched last, then a few popular searches. */
    private void showHome() {
        mode = 3;
        final int gen = ++searchGen;
        updateTabs();
        items.clear();
        adapter.notifyDataSetChanged();
        status.setText("");
        spinner.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Row> result = new ArrayList<Row>();
                final java.util.HashSet<String> seen = new java.util.HashSet<String>();
                try {
                    final StreamingService yt = NewPipe.getService("YouTube");
                    try {
                        List<YtStore.Entry> h = YtStore.history(YoutubeSearchActivity.this);
                        if (!h.isEmpty()) {
                            StreamInfo si = StreamInfo.getInfo(yt, h.get(0).url);
                            addItems(result, seen, si.getRelatedItems(), 14);
                            postHome(gen, result, false);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "home: related failed", e);
                    }
                    String[] qs = {"populer hari ini", "musik terbaru", "gameplay terbaru"};
                    for (String q : qs) {
                        try {
                            SearchInfo info = SearchInfo.getInfo(yt, yt.getSearchQHFactory().fromQuery(q));
                            addItems(result, seen, info.getRelatedItems(), 8);
                            postHome(gen, result, false);
                        } catch (Exception e) {
                            Log.w(TAG, "home: search failed: " + q, e);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "home failed", e);
                }
                postHome(gen, result, true);
            }
        }).start();
    }

    private void postHome(final int gen, List<Row> src, final boolean done) {
        final List<Row> copy = new ArrayList<Row>(src);
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (gen != searchGen) return;
                items.clear();
                items.addAll(copy);
                adapter.notifyDataSetChanged();
                if (!copy.isEmpty()) spinner.setVisibility(View.GONE);
                if (done) {
                    spinner.setVisibility(View.GONE);
                    status.setText(copy.isEmpty() ? Tr.s("Gagal memuat beranda. Cek koneksi, lalu tap Beranda lagi.") : "");
                }
            }
        });
    }

    /** Shows the locally stored history (1) or liked videos (2) in the list. */
    private void showStored(int m) {
        mode = m;
        searchGen++;   // drop any search still running
        spinner.setVisibility(View.GONE);
        List<YtStore.Entry> src = m == 2 ? YtStore.likes(this) : YtStore.history(this);
        items.clear();
        for (YtStore.Entry e : src) {
            Row r = new Row();
            r.url = e.url;
            r.title = e.title == null ? "" : e.title;
            r.uploader = e.uploader == null ? "" : e.uploader;
            r.duration = e.duration > 0 ? fmt(e.duration) : null;
            r.durSecs = e.duration;
            r.posMs = YtExtras.getPosition(this, e.url);
            r.thumb = e.thumb;
            items.add(r);
        }
        adapter.notifyDataSetChanged();
        status.setText(items.isEmpty()
                ? (m == 2 ? Tr.s("Belum ada video yang disukai.") : Tr.s("Belum ada riwayat tontonan. Cari video di atas."))
                : "");
        updateTabs();
    }

    private void confirmClearHistory() {
        new android.app.AlertDialog.Builder(this)
                .setMessage(Tr.s("Hapus semua riwayat tontonan?"))
                .setPositiveButton(Tr.s("Hapus"), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        YtStore.clearHistory(YoutubeSearchActivity.this);
                        showStored(1);
                    }
                })
                .setNegativeButton(Tr.s("Batal"), null)
                .show();
    }

    private View row(final Row it) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(8), dp(8), dp(12), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#141A22"));
        g.setCornerRadius(dp(12));
        r.setBackground(g);

        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(Color.parseColor("#0A0D12"));
        tg.setCornerRadius(dp(8));
        thumb.setBackground(tg);
        thumb.setClipToOutline(true);
        if (it.thumb != null) Glide.with(this).load(it.thumb).into(thumb);
        r.addView(thumb, new LinearLayout.LayoutParams(dp(120), dp(68)));

        // garis merah di bawah thumbnail = seberapa jauh video ini sudah ditonton
        if (it.posMs > 0 && it.durSecs > 0) {
            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.addView(thumb, new LinearLayout.LayoutParams(dp(120), dp(68)));
            View bar = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#33FFFFFF"));
            bg.setCornerRadius(dp(2));
            bar.setBackground(bg);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
            blp.topMargin = dp(3);
            wrap.addView(bar, blp);
            float frac = Math.min(1f, it.posMs / (it.durSecs * 1000f));
            View fill = new View(this);
            GradientDrawable fg = new GradientDrawable();
            fg.setColor(Color.parseColor("#FF4444"));
            fg.setCornerRadius(dp(2));
            fill.setBackground(fg);
            int fw = Math.max(dp(3), (int) (dp(120) * frac));
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(fw, dp(3));
            flp.topMargin = -dp(3);
            wrap.addView(fill, flp);
            r.addView(wrap, new LinearLayout.LayoutParams(dp(120), dp(74)));
        } else {
            r.addView(thumb, new LinearLayout.LayoutParams(dp(120), dp(68)));
        }

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView n = new TextView(this);
        n.setText(it.title);
        n.setTextColor(Color.WHITE);
        n.setTextSize(13);
        n.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        n.setMaxLines(2);
        TextView s = new TextView(this);
        s.setText(it.uploader + (it.duration != null ? "  ·  " + it.duration : ""));
        s.setTextColor(Color.parseColor("#8B95A3"));
        s.setTextSize(11);
        s.setSingleLine(true);
        s.setPadding(0, dp(4), 0, 0);
        t.addView(n);
        t.addView(s);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = dp(10);
        r.addView(t, tl);

        // tombol ✕: buang dari daftar (riwayat saja) — pengganti swipe-to-delete
        if (mode == 1 && it.url != null) {
            TextView x = new TextView(this);
            x.setText("✕");
            x.setTextColor(Color.parseColor("#556070"));
            x.setTextSize(16);
            x.setPadding(dp(10), dp(10), dp(4), dp(10));
            x.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    YtStore.removeHistory(YoutubeSearchActivity.this, it.url);
                    if (mode == 1) showStored(1);
                }
            });
            r.addView(x);
        }
        return r;
    }

    /** Tekan lama sebuah video: simpan ke playlist, buka channel, atau salin link. */
    private void showRowMenu(final Row r) {
        final String[] opts = {"▶  Putar sekarang", "＋  Simpan ke playlist", "📋  Salin link", "⏱  Mulai dari awal"};
        new android.app.AlertDialog.Builder(this)
                .setTitle(r.title)
                .setItems(opts, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        if (w == 0) {
                            Intent in = new Intent(YoutubeSearchActivity.this, YoutubePlayerActivity.class);
                            in.putExtra("url", r.url);
                            in.putExtra("title", r.title);
                            startActivity(in);
                        } else if (w == 1) {
                            saveToPlaylist(r);
                        } else if (w == 2) {
                            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("link", r.url));
                            Toast.makeText(YoutubeSearchActivity.this, "Link disalin", Toast.LENGTH_SHORT).show();
                        } else {
                            YtExtras.clearPosition(YoutubeSearchActivity.this, r.url);
                            Toast.makeText(YoutubeSearchActivity.this, "Posisi lanjut dihapus", Toast.LENGTH_SHORT).show();
                            adapter.notifyDataSetChanged();
                        }
                    }
                })
                .show();
    }

    private void saveToPlaylist(final Row r) {
        final java.util.List<YtLibrary.Pl> pls = YtLibrary.playlists(this);
        final String[] names = new String[pls.size() + 1];
        names[0] = "＋  Playlist baru…";
        for (int i = 0; i < pls.size(); i++) names[i + 1] = pls.get(i).name;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Simpan ke playlist")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        if (w == 0) {
                            final android.widget.EditText et = new android.widget.EditText(YoutubeSearchActivity.this);
                            et.setHint("Nama playlist");
                            new android.app.AlertDialog.Builder(YoutubeSearchActivity.this)
                                    .setTitle("Playlist baru")
                                    .setView(et)
                                    .setPositiveButton("Buat", new android.content.DialogInterface.OnClickListener() {
                                        @Override public void onClick(android.content.DialogInterface d2, int w2) {
                                            String nm = et.getText().toString().trim();
                                            if (nm.isEmpty()) return;
                                            YtLibrary.Pl p = YtLibrary.createPlaylist(YoutubeSearchActivity.this, nm);
                                            addRowTo(p.id, r);
                                        }
                                    })
                                    .setNegativeButton(Tr.s("Batal"), null)
                                    .show();
                        } else {
                            addRowTo(pls.get(w - 1).id, r);
                        }
                    }
                })
                .show();
    }

    private void addRowTo(String plId, Row r) {
        YtStore.Entry e = new YtStore.Entry();
        e.url = r.url;
        e.title = r.title;
        e.uploader = r.uploader;
        e.thumb = r.thumb;
        e.duration = r.durSecs;
        boolean added = YtLibrary.addToPlaylist(this, plId, e);
        Toast.makeText(this, added ? "Disimpan ke playlist" : "Sudah ada di playlist itu", Toast.LENGTH_SHORT).show();
    }

    private void doSearch() {
        final String q = query.getText().toString().trim();
        if (q.isEmpty()) return;
        final int gen = ++searchGen;
        mode = 0;
        updateTabs();
        items.clear();
        adapter.notifyDataSetChanged();
        status.setText("");
        spinner.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Row> result = new ArrayList<Row>();
                String err = null;
                try {
                    StreamingService yt = NewPipe.getService("YouTube");
                    for (InfoItem ii : searchItems(yt, q)) {
                        if (!(ii instanceof StreamInfoItem)) continue;
                        StreamInfoItem si = (StreamInfoItem) ii;
                        Row row = new Row();
                        row.url = si.getUrl();
                        row.title = si.getName();
                        row.uploader = si.getUploaderName();
                        long secs = si.getDuration();
                        row.duration = secs > 0 ? fmt(secs) : null;
                        row.durSecs = secs > 0 ? secs : 0;
                        row.posMs = YtExtras.getPosition(YoutubeSearchActivity.this, si.getUrl());
                        row.thumb = bestThumb(si);
                        result.add(row);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "search failed for \"" + q + "\"", e);
                    err = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "(no message)" : e.getMessage());
                }
                final String ferr = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (gen != searchGen) return;   // a newer search already replaced this one
                        spinner.setVisibility(View.GONE);
                        if (ferr != null) {
                            status.setText("Search failed: " + ferr);
                            Toast.makeText(YoutubeSearchActivity.this, "Search failed", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        items.clear();
                        items.addAll(result);
                        adapter.notifyDataSetChanged();
                        status.setText(result.isEmpty() ? (fTime != 0 ? Tr.s("Tidak ada video dalam rentang waktu ini. Coba waktu yang lebih luas.") : "No results") : "");
                    }
                });
            }
        }).start();
    }

    // ---------------------------------------------------------------- langganan, playlist, filter

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
            status.setText(Tr.s("Belum ada langganan.\nBuka video lalu tap Langganan di bawah judul.\nTanpa akun Google."));
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
                        m.uploader = Tr.s("Lihat atau berhenti berlangganan channel");
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
                        status.setText(feed.isEmpty() ? Tr.s("Gagal memuat video langganan. Cek koneksi, lalu tap Langganan lagi.") : "");
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
                .setNegativeButton(Tr.s("Batal"), null)
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
                .setNegativeButton(Tr.s("Batal"), null)
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

    private static String bestThumb(StreamInfoItem si) {
        try {
            if (si.getThumbnails() != null && !si.getThumbnails().isEmpty())
                return si.getThumbnails().get(si.getThumbnails().size() - 1).getUrl();
        } catch (Exception ignored) {}
        return null;
    }

    private static String fmt(long secs) {
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }
}
