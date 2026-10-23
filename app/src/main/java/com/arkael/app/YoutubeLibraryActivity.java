package com.arkael.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Perpustakaan YouTube: playlist + channel yang dilanggani (semua lokal, tanpa akun Google).
 * Dari sini playlist / video channel bisa diputar berurutan: video berikutnya jalan sendiri.
 */
public class YoutubeLibraryActivity extends Activity {
    private static final int V_PLS = 0, V_CHS = 1, V_PL = 2, V_CH = 3;
    private static final int C_BG = Color.parseColor("#0A0D12");
    private static final int C_CARD = Color.parseColor("#141A22");
    private static final int C_SUB = Color.parseColor("#8B95A3");
    private static final int C_ACCENT = Color.parseColor("#AEC6FF");

    private static final class Row {
        int type;                 // 0 video, 1 playlist, 2 channel, 3 tombol aksi
        String id, url, title, sub, thumb;
    }

    private final List<Row> items = new ArrayList<Row>();
    private BaseAdapter adapter;
    private TextView status, header, tabPl, tabCh;
    private LinearLayout actionRow;
    private ProgressBar spinner;
    private int view = V_PLS;
    private String curId = "", curName = "";
    private boolean fromPlayer;     // channel dibuka dari pemutar: back = tutup
    private int gen;

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
        getWindow().setStatusBarColor(C_BG);
        getWindow().setNavigationBarColor(C_BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(C_BG);
        col.setPadding(dp(16), dp(24), dp(16), 0);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextColor(Color.WHITE);
        back.setTextSize(24);
        back.setPadding(0, 0, dp(14), dp(4));
        back.setOnClickListener(v -> onBackPressed());
        top.addView(back);
        header = new TextView(this);
        header.setTextColor(Color.WHITE);
        header.setTextSize(22);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.setSingleLine(true);
        top.addView(header, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(top);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(12), 0, 0);
        tabPl = tab("Playlist", V_PLS);
        tabCh = tab("Langganan", V_CHS);
        tabs.addView(tabPl);
        tabs.addView(tabCh);
        col.addView(tabs);

        actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(12), 0, 0);
        actionRow.addView(actionBtn("▶  Putar semua", false));
        actionRow.addView(actionBtn("🔀  Acak", true));
        actionRow.setVisibility(View.GONE);
        col.addView(actionRow);

        spinner = new ProgressBar(this);
        LinearLayout.LayoutParams spl = new LinearLayout.LayoutParams(-2, -2);
        spl.gravity = Gravity.CENTER;
        spl.topMargin = dp(24);
        spinner.setVisibility(View.GONE);
        col.addView(spinner, spl);

        status = new TextView(this);
        status.setTextColor(C_SUB);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(24), 0, 0);
        col.addView(status);

        ListView lv = new ListView(this);
        lv.setDivider(new ColorDrawable(Color.TRANSPARENT));
        lv.setDividerHeight(dp(8));
        lv.setClipToPadding(false);
        lv.setPadding(0, dp(12), 0, dp(16));
        adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) { return rowView(items.get(i)); }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int i, long id) { onRowClick(i); }
        });
        col.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(col);

        Intent in = getIntent();
        String ch = in.getStringExtra("ch");
        String pl = in.getStringExtra("pl");
        if (ch != null) {
            view = V_CH;
            curId = ch;
            curName = in.getStringExtra("chname") == null ? "" : in.getStringExtra("chname");
            fromPlayer = true;
        } else if (pl != null) {
            view = V_PL;
            curId = pl;
        } else {
            view = in.getIntExtra("tab", 0) == 1 ? V_CHS : V_PLS;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view == V_PLS) showPlaylists();
        else if (view == V_CHS) showChannels();
        else if (view == V_PL) showPlaylist(curId);
        else if (view == V_CH && items.isEmpty() && spinner.getVisibility() != View.VISIBLE) showChannel(curId, curName);
    }

    @Override
    public void onBackPressed() {
        if (view == V_PL) showPlaylists();
        else if (view == V_CH && !fromPlayer) showChannels();
        else super.onBackPressed();
    }

    // ---------------------------------------------------------------- tabs / buttons

    private TextView tab(String label, final int v) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        t.setOnClickListener(x -> { if (v == V_PLS) showPlaylists(); else showChannels(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView actionBtn(String label, final boolean shuffle) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setTextColor(shuffle ? C_ACCENT : C_BG);
        t.setBackground(round(shuffle ? C_CARD : C_ACCENT, 20));
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        t.setOnClickListener(x -> play(shuffle, -1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void styleTab(TextView t, boolean on) {
        t.setBackground(round(on ? Color.parseColor("#2A3A5C") : C_CARD, 16));
        t.setTextColor(on ? Color.WHITE : C_SUB);
    }

    private void applyView(String title) {
        header.setText(title);
        styleTab(tabPl, view == V_PLS || view == V_PL);
        styleTab(tabCh, view == V_CHS || view == V_CH);
        actionRow.setVisibility(view == V_PL || view == V_CH ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- screens

    private void showPlaylists() {
        view = V_PLS;
        gen++;
        applyView("Playlist");
        spinner.setVisibility(View.GONE);
        items.clear();
        Row n = new Row();
        n.type = 3;
        n.title = "➕  Playlist baru";
        items.add(n);
        for (YtLibrary.Pl p : YtLibrary.playlists(this)) {
            Row r = new Row();
            r.type = 1;
            r.id = p.id;
            r.title = p.name;
            r.sub = p.videos.size() + " video";
            r.thumb = p.videos.isEmpty() ? null : p.videos.get(0).thumb;
            items.add(r);
        }
        adapter.notifyDataSetChanged();
        status.setText(items.size() == 1 ? "Belum ada playlist.\nBuka video lalu tap ➕ Playlist." : "");
    }

    private void showPlaylist(String id) {
        YtLibrary.Pl p = YtLibrary.playlist(this, id);
        if (p == null) { showPlaylists(); return; }
        view = V_PL;
        curId = id;
        curName = p.name;
        gen++;
        applyView(p.name);
        spinner.setVisibility(View.GONE);
        items.clear();
        for (YtStore.Entry e : p.videos) {
            Row r = new Row();
            r.type = 0;
            r.url = e.url;
            r.title = e.title;
            r.sub = (e.uploader == null ? "" : e.uploader) + (e.duration > 0 ? "  ·  " + fmt(e.duration) : "");
            r.thumb = e.thumb;
            items.add(r);
        }
        adapter.notifyDataSetChanged();
        status.setText(items.isEmpty() ? "Playlist kosong.\nBuka video lalu tap ➕ Playlist." : "");
    }

    private void showChannels() {
        view = V_CHS;
        gen++;
        applyView("Langganan");
        spinner.setVisibility(View.GONE);
        items.clear();
        for (YtLibrary.Sub s : YtLibrary.subs(this)) {
            Row r = new Row();
            r.type = 2;
            r.id = s.id;
            r.title = s.name;
            r.thumb = s.avatar;
            items.add(r);
        }
        adapter.notifyDataSetChanged();
        status.setText(items.isEmpty() ? "Belum ada langganan.\nBuka video lalu tap Langganan di bawah judul.\nTanpa akun Google." : "");
    }

    private void showChannel(final String id, final String name) {
        view = V_CH;
        curId = id;
        curName = name == null ? "" : name;
        final int g = ++gen;
        applyView(curName.isEmpty() ? "Channel" : curName);
        items.clear();
        adapter.notifyDataSetChanged();
        status.setText("");
        spinner.setVisibility(View.VISIBLE);
        new Thread(() -> {
            final List<YtLibrary.FeedItem> f = YtLibrary.fetchChannel(id, name);
            runOnUiThread(() -> {
                if (g != gen) return;
                spinner.setVisibility(View.GONE);
                items.clear();
                for (YtLibrary.FeedItem x : f) {
                    Row r = new Row();
                    r.type = 0;
                    r.url = x.url;
                    r.title = x.title;
                    r.sub = x.published > 0 ? DateUtils.getRelativeTimeSpanString(x.published,
                            System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() : "";
                    r.thumb = x.thumb;
                    items.add(r);
                }
                adapter.notifyDataSetChanged();
                status.setText(f.isEmpty() ? "Tidak bisa memuat video channel ini. Cek koneksi." : "");
            });
        }).start();
    }

    // ---------------------------------------------------------------- actions

    private void onRowClick(int i) {
        Row r = items.get(i);
        if (r.type == 3) newPlaylistDialog();
        else if (r.type == 1) showPlaylist(r.id);
        else if (r.type == 2) showChannel(r.id, r.title);
        else play(false, i);
    }

    /** Putar daftar yang sedang tampil sebagai antrean. clicked = posisi baris (atau -1 = dari awal). */
    private void play(boolean shuffle, int clicked) {
        List<Row> v = new ArrayList<Row>();
        int idx = 0;
        for (int k = 0; k < items.size(); k++) {
            Row r = items.get(k);
            if (r.type != 0) continue;
            if (k == clicked) idx = v.size();
            v.add(r);
        }
        if (v.isEmpty()) return;
        if (shuffle) { Collections.shuffle(v); idx = 0; }
        String[] urls = new String[v.size()];
        String[] titles = new String[v.size()];
        for (int k = 0; k < v.size(); k++) {
            urls[k] = v.get(k).url;
            titles[k] = v.get(k).title == null ? "" : v.get(k).title;
        }
        Intent in = new Intent(this, YoutubePlayerActivity.class);
        in.putExtra("url", urls[idx]);
        in.putExtra("title", titles[idx]);
        in.putExtra("queue_urls", urls);
        in.putExtra("queue_titles", titles);
        in.putExtra("queue_index", idx);
        in.putExtra("queue_name", curName);
        startActivity(in);
    }

    private void newPlaylistDialog() {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setHint("Nama playlist");
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Playlist baru")
                .setView(box)
                .setPositiveButton("Buat", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) return;
                    YtLibrary.createPlaylist(this, n);
                    showPlaylists();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // ---------------------------------------------------------------- row view

    private View rowView(final Row r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(10), dp(8));
        row.setBackground(round(C_CARD, 12));

        if (r.type == 3) {
            TextView t = new TextView(this);
            t.setText(r.title);
            t.setTextColor(C_ACCENT);
            t.setTextSize(14);
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(10), 0, dp(10));
            row.addView(t, new LinearLayout.LayoutParams(-1, -2));
            return row;
        }

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setClipToOutline(true);
        boolean isChannel = r.type == 2;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_BG);
        if (isChannel) bg.setShape(GradientDrawable.OVAL); else bg.setCornerRadius(dp(8));
        img.setBackground(bg);
        if (r.thumb != null) Glide.with(this).load(r.thumb).into(img);
        row.addView(img, isChannel ? new LinearLayout.LayoutParams(dp(48), dp(48)) : new LinearLayout.LayoutParams(dp(120), dp(68)));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView n = new TextView(this);
        n.setText(r.title == null ? "" : r.title);
        n.setTextColor(Color.WHITE);
        n.setTextSize(isChannel ? 15 : 13);
        n.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        n.setMaxLines(2);
        txt.addView(n);
        if (r.sub != null && !r.sub.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(r.sub);
            s.setTextColor(C_SUB);
            s.setTextSize(11);
            s.setSingleLine(true);
            s.setPadding(0, dp(4), 0, 0);
            txt.addView(s);
        }
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = dp(10);
        row.addView(txt, tl);

        if (r.type == 0 && view == V_PL) {
            row.addView(trailing("✕", v -> {
                YtLibrary.removeFromPlaylist(this, curId, r.url);
                showPlaylist(curId);
            }));
        } else if (r.type == 1) {
            row.addView(trailing("🗑", v -> new AlertDialog.Builder(this)
                    .setMessage("Hapus playlist \"" + r.title + "\"?")
                    .setPositiveButton("Hapus", (d, w) -> { YtLibrary.deletePlaylist(this, r.id); showPlaylists(); })
                    .setNegativeButton("Batal", null)
                    .show()));
        } else if (r.type == 2) {
            TextView b = new TextView(this);
            b.setText("Berhenti");
            b.setTextColor(Color.WHITE);
            b.setTextSize(12);
            b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            b.setBackground(round(Color.parseColor("#2A3A5C"), 16));
            b.setPadding(dp(14), dp(8), dp(14), dp(8));
            b.setOnClickListener(v -> { YtLibrary.removeSub(this, r.id); showChannels(); });
            row.addView(b);
        }
        return row;
    }

    private TextView trailing(String s, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(C_SUB);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(l);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return t;
    }

    private static String fmt(long secs) {
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }
}
