package com.arkael.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
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
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

/**
 * Search YouTube and show results. Video pages are fetched directly through NewPipeExtractor
 * (no YouTube app, no WebView, no ads) — see NewPipeExtractorDownloader and YoutubePlayerActivity.
 */
public class YoutubeSearchActivity extends Activity {
    private static final class Row {
        String url, title, uploader, duration, thumb;
    }

    private final List<Row> items = new ArrayList<Row>();
    private BaseAdapter adapter;
    private TextView status;
    private ProgressBar spinner;
    private EditText query;
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
        h.setText("YouTube");
        h.setTextColor(Color.WHITE);
        h.setTextSize(24);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        col.addView(h);
        TextView sub = new TextView(this);
        sub.setText("No ads, no account. Streams are pulled directly, same way NewPipe works. Open a video, then leave the app to keep it floating.");
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
        query.setHint("Search YouTube");
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

        spinner = new ProgressBar(this);
        LinearLayout.LayoutParams spl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spl.gravity = Gravity.CENTER;
        spl.topMargin = dp(24);
        spinner.setVisibility(View.GONE);
        col.addView(spinner, spl);

        status = new TextView(this);
        status.setText("Type something and search.");
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
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int i, long id) {
                Row r = items.get(i);
                Intent in = new Intent(YoutubeSearchActivity.this, YoutubePlayerActivity.class);
                in.putExtra("url", r.url);
                in.putExtra("title", r.title);
                startActivity(in);
            }
        });
        col.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(col);
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
        return r;
    }

    private void doSearch() {
        final String q = query.getText().toString().trim();
        if (q.isEmpty()) return;
        final int gen = ++searchGen;
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
                    SearchInfo info = SearchInfo.getInfo(yt, yt.getSearchQHFactory().fromQuery(q));
                    for (InfoItem ii : info.getRelatedItems()) {
                        if (!(ii instanceof StreamInfoItem)) continue;
                        StreamInfoItem si = (StreamInfoItem) ii;
                        Row row = new Row();
                        row.url = si.getUrl();
                        row.title = si.getName();
                        row.uploader = si.getUploaderName();
                        long secs = si.getDuration();
                        row.duration = secs > 0 ? fmt(secs) : null;
                        row.thumb = bestThumb(si);
                        result.add(row);
                    }
                } catch (Exception e) {
                    err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
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
                        status.setText(result.isEmpty() ? "No results" : "");
                    }
                });
            }
        }).start();
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
