package com.arkael.app;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;
import java.util.Locale;

/**
 * Plays one YouTube video. The stream URL is fetched directly through NewPipeExtractor (the same
 * technique NewPipe/LibreTube use), so playback goes straight to Google's CDN with no ad break
 * ever inserted. No account / login is involved.
 *
 * Layout follows the YouTube watch page: player on top (16:9), then title, views, likes, share
 * chips, channel row and a swipeable row of related videos. Landscape = fullscreen player.
 *
 * Supports Android's native Picture-in-Picture: leaving the app (home button or switching apps)
 * while a video is playing shrinks it into a small movable window that keeps playing on top of
 * whatever else you open; tapping the shrink button does the same on demand.
 */
public class YoutubePlayerActivity extends AppCompatActivity {
    private static final String TAG = "ArkaelYT";
    private static final String ACTION_MEDIA_CONTROL = "com.arkael.app.ytpip.control";
    private static final String EXTRA_CONTROL = "control";
    private static final int CONTROL_PLAY = 1, CONTROL_PAUSE = 2;

    private static final int C_BG = Color.parseColor("#0A0D12");
    private static final int C_CARD = Color.parseColor("#141A22");
    private static final int C_SUB = Color.parseColor("#8B95A3");
    private static final int C_ACCENT = Color.parseColor("#AEC6FF");

    private PlayerView playerView;
    private ExoPlayer player;
    private FrameLayout playerFrame;
    private LinearLayout header;          // back / fullscreen / shrink buttons over the player
    private ScrollView scroll;            // everything under the player, hidden in landscape / PiP
    private TextView title, status, meta, likes, channelName, channelSubs;
    private ImageView avatar;
    private LinearLayout relatedRow;
    private TextView relatedLabel;
    private ProgressBar spinner;
    private String videoUrl, videoTitle;
    private BroadcastReceiver pipReceiver;
    private boolean inPip;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        NewPipeBootstrap.ensureInit();
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(C_BG);

        readIntent(getIntent());
        if (videoUrl == null) { finish(); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        // Works both with and without edge-to-edge: pad by whatever the system bars take.
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            v.setPadding(0, ins.getSystemWindowInsetTop(), 0, ins.getSystemWindowInsetBottom());
            return ins;
        });

        // ---- player (16:9) with overlay buttons ----
        playerFrame = new FrameLayout(this);
        playerFrame.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerFrame.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(4), dp(4), dp(4));
        header.addView(headerBtn("←", v -> finish()));
        View grow = new View(this);
        header.addView(grow, new LinearLayout.LayoutParams(0, 1, 1f));
        header.addView(headerBtn("⛶", v -> toggleFullscreen()));
        header.addView(headerBtn("⤓", v -> enterPip()));
        playerFrame.addView(header, new FrameLayout.LayoutParams(-1, -2));

        spinner = new ProgressBar(this);
        FrameLayout.LayoutParams spl = new FrameLayout.LayoutParams(-2, -2);
        spl.gravity = Gravity.CENTER;
        playerFrame.addView(spinner, spl);

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#FF6B6B"));
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(24), 0, dp(24), 0);
        FrameLayout.LayoutParams stl = new FrameLayout.LayoutParams(-1, -2);
        stl.gravity = Gravity.CENTER;
        playerFrame.addView(status, stl);

        root.addView(playerFrame, new LinearLayout.LayoutParams(-1, portraitPlayerHeight()));

        // ---- info area under the player ----
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(C_BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(14), 0, dp(24));

        title = new TextView(this);
        title.setText(videoTitle == null ? "" : videoTitle);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(dp(14), 0, dp(14), 0);
        col.addView(title);

        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.setPadding(dp(14), dp(6), dp(14), 0);
        meta = new TextView(this);
        meta.setTextColor(C_SUB);
        meta.setTextSize(13);
        metaRow.addView(meta, new LinearLayout.LayoutParams(0, -2, 1f));
        likes = new TextView(this);
        likes.setTextColor(Color.WHITE);
        likes.setTextSize(14);
        likes.setVisibility(View.GONE);
        metaRow.addView(likes);
        col.addView(metaRow);

        // action chips
        HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(14), dp(14), dp(14), 0);
        chips.addView(chip("↗  Bagikan", v -> shareLink()));
        chips.addView(chip("⧉  Salin link", v -> copyLink()));
        chipsScroll.addView(chips);
        col.addView(chipsScroll);

        // channel row
        LinearLayout chRow = new LinearLayout(this);
        chRow.setOrientation(LinearLayout.HORIZONTAL);
        chRow.setGravity(Gravity.CENTER_VERTICAL);
        chRow.setPadding(dp(14), dp(18), dp(14), dp(6));
        avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(oval(C_CARD));
        avatar.setClipToOutline(true);
        chRow.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout chText = new LinearLayout(this);
        chText.setOrientation(LinearLayout.VERTICAL);
        channelName = new TextView(this);
        channelName.setTextColor(Color.WHITE);
        channelName.setTextSize(15);
        channelName.setSingleLine(true);
        channelSubs = new TextView(this);
        channelSubs.setTextColor(C_SUB);
        channelSubs.setTextSize(12);
        chText.addView(channelName);
        chText.addView(channelSubs);
        LinearLayout.LayoutParams ctl = new LinearLayout.LayoutParams(0, -2, 1f);
        ctl.leftMargin = dp(12);
        chRow.addView(chText, ctl);
        col.addView(chRow);

        // related videos (swipe sideways)
        relatedLabel = new TextView(this);
        relatedLabel.setText("Video terkait");
        relatedLabel.setTextColor(Color.WHITE);
        relatedLabel.setTextSize(15);
        relatedLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        relatedLabel.setPadding(dp(14), dp(18), dp(14), dp(10));
        relatedLabel.setVisibility(View.GONE);
        col.addView(relatedLabel);
        HorizontalScrollView relScroll = new HorizontalScrollView(this);
        relScroll.setHorizontalScrollBarEnabled(false);
        relatedRow = new LinearLayout(this);
        relatedRow.setOrientation(LinearLayout.HORIZONTAL);
        relatedRow.setPadding(dp(14), 0, dp(14), 0);
        relScroll.addView(relatedRow);
        col.addView(relScroll);

        scroll.addView(col, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        applyLayout();
        load();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // A related video was tapped (launchMode=singleTask delivers it here): switch in place.
        setIntent(intent);
        readIntent(intent);
        if (videoUrl == null) return;
        releasePlayer();
        title.setText(videoTitle == null ? "" : videoTitle);
        meta.setText("");
        likes.setVisibility(View.GONE);
        channelName.setText("");
        channelSubs.setText("");
        avatar.setImageDrawable(null);
        relatedRow.removeAllViews();
        relatedLabel.setVisibility(View.GONE);
        status.setText("");
        scroll.scrollTo(0, 0);
        load();
    }

    private void readIntent(Intent i) {
        videoUrl = i == null ? null : i.getStringExtra("url");
        videoTitle = i == null ? null : i.getStringExtra("title");
    }

    // ---------------------------------------------------------------- small view helpers

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    private GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView headerBtn(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(20);
        t.setGravity(Gravity.CENTER);
        t.setBackground(oval(Color.parseColor("#66000000")));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.leftMargin = dp(6);
        lp.rightMargin = dp(2);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView chip(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(C_ACCENT);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        t.setBackground(round(C_CARD, 20));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(10);
        t.setLayoutParams(lp);
        return t;
    }

    private static String bestImage(List<Image> imgs) {
        try {
            if (imgs != null && !imgs.isEmpty()) return imgs.get(imgs.size() - 1).getUrl();
        } catch (Exception ignored) {}
        return null;
    }

    /** 3800 -> "3,8 rb", 230000 -> "230 rb", 1250000 -> "1,3 jt" (Indonesian style). */
    private static String compact(long n) {
        if (n < 1000) return String.valueOf(n);
        double v; String unit;
        if (n < 1_000_000L) { v = n / 1000.0; unit = " rb"; }
        else if (n < 1_000_000_000L) { v = n / 1_000_000.0; unit = " jt"; }
        else { v = n / 1_000_000_000.0; unit = " M"; }
        String s = v >= 100 ? String.format(Locale.US, "%.0f", v) : String.format(Locale.US, "%.1f", v);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s.replace('.', ',') + unit;
    }

    private static String fmtDuration(long secs) {
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }

    // ---------------------------------------------------------------- layout modes

    private int portraitPlayerHeight() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return Math.min(dm.widthPixels, dm.heightPixels) * 9 / 16;
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /** Portrait: 16:9 player + info below. Landscape or PiP: the player takes the whole screen. */
    @SuppressWarnings("deprecation")
    private void applyLayout() {
        if (playerFrame == null) return;
        boolean full = inPip || isLandscape();
        scroll.setVisibility(full ? View.GONE : View.VISIBLE);
        header.setVisibility(inPip ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) playerFrame.getLayoutParams();
        lp.height = full ? ViewGroup.LayoutParams.MATCH_PARENT : portraitPlayerHeight();
        lp.weight = 0;
        playerFrame.setLayoutParams(lp);
        int flags = View.SYSTEM_UI_FLAG_VISIBLE;
        if (isLandscape() && !inPip) {
            flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyLayout();
    }

    private void toggleFullscreen() {
        setRequestedOrientation(isLandscape()
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        // hand control back to the sensor shortly after, so auto-rotate keeps working
        playerFrame.postDelayed(() -> setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED), 1500);
    }

    // ---------------------------------------------------------------- share / copy

    private void shareLink() {
        Intent s = new Intent(Intent.ACTION_SEND);
        s.setType("text/plain");
        s.putExtra(Intent.EXTRA_TEXT, (title.getText() == null ? "" : title.getText() + "\n") + videoUrl);
        startActivity(Intent.createChooser(s, "Bagikan"));
    }

    private void copyLink() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("YouTube", videoUrl));
        Toast.makeText(this, "Link disalin", Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- loading

    private void load() {
        spinner.setVisibility(View.VISIBLE);
        final String url = videoUrl;
        new Thread(() -> {
            String err = null;
            MediaItem item = null;
            StreamInfo info = null;
            try {
                Log.i(TAG, "extracting: " + url);
                StreamingService yt = NewPipe.getService("YouTube");
                info = StreamInfo.getInfo(yt, url);
                Log.i(TAG, "extracted ok: hls=" + notEmpty(info.getHlsUrl()) + " dash=" + notEmpty(info.getDashMpdUrl())
                        + " videoStreams=" + (info.getVideoStreams() == null ? -1 : info.getVideoStreams().size())
                        + " videoOnlyStreams=" + (info.getVideoOnlyStreams() == null ? -1 : info.getVideoOnlyStreams().size()));
                item = pickStream(info);
                Log.i(TAG, "picked media item: " + (item == null ? "null" : item.localConfiguration.uri));
                if (item == null) err = "No playable stream found for this video";
            } catch (Exception e) {
                Log.e(TAG, "extraction failed for " + url, e);
                err = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "(no message)" : e.getMessage());
            }
            final MediaItem fi = item;
            final String ferr = err;
            final StreamInfo finfo = info;
            runOnUiThread(() -> {
                if (isFinishing() || !url.equals(videoUrl)) return;   // closed, or another video was picked meanwhile
                spinner.setVisibility(View.GONE);
                if (finfo != null) {
                    try { populate(finfo); } catch (Exception e) { Log.e(TAG, "populate failed", e); }
                }
                if (ferr != null) { status.setText(ferr); return; }
                startPlayback(fi);
            });
        }).start();
    }

    private void populate(StreamInfo info) {
        if (info.getName() != null && !info.getName().isEmpty()) title.setText(info.getName());

        StringBuilder m = new StringBuilder();
        if (info.getViewCount() >= 0) m.append(compact(info.getViewCount())).append(" tampilan");
        String date = info.getTextualUploadDate();
        if (date != null && !date.isEmpty()) m.append(m.length() > 0 ? "  •  " : "").append(date);
        meta.setText(m.toString());

        if (info.getLikeCount() >= 0) {
            likes.setText("👍 " + compact(info.getLikeCount()));
            likes.setVisibility(View.VISIBLE);
        }

        channelName.setText(info.getUploaderName() == null ? "" : info.getUploaderName());
        channelSubs.setText(info.getUploaderSubscriberCount() >= 0 ? compact(info.getUploaderSubscriberCount()) + " pelanggan" : "");
        String av = bestImage(info.getUploaderAvatars());
        if (av != null) Glide.with(this).load(av).into(avatar);

        relatedRow.removeAllViews();
        int added = 0;
        List<InfoItem> rel = info.getRelatedItems();
        if (rel != null) {
            for (InfoItem ii : rel) {
                if (!(ii instanceof StreamInfoItem)) continue;
                relatedRow.addView(relatedCard((StreamInfoItem) ii));
                if (++added >= 15) break;
            }
        }
        relatedLabel.setVisibility(added > 0 ? View.VISIBLE : View.GONE);
    }

    private View relatedCard(final StreamInfoItem si) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(260), -2);
        cl.rightMargin = dp(12);
        card.setLayoutParams(cl);

        FrameLayout thumbBox = new FrameLayout(this);
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(round(C_CARD, 12));
        thumb.setClipToOutline(true);
        String tu = bestImage(si.getThumbnails());
        if (tu != null) Glide.with(this).load(tu).into(thumb);
        thumbBox.addView(thumb, new FrameLayout.LayoutParams(-1, -1));
        if (si.getDuration() > 0) {
            TextView d = new TextView(this);
            d.setText(fmtDuration(si.getDuration()));
            d.setTextColor(Color.WHITE);
            d.setTextSize(11);
            d.setPadding(dp(6), dp(2), dp(6), dp(2));
            d.setBackground(round(Color.parseColor("#B3000000"), 6));
            FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-2, -2);
            dl.gravity = Gravity.BOTTOM | Gravity.END;
            dl.setMargins(0, 0, dp(8), dp(8));
            thumbBox.addView(d, dl);
        }
        card.addView(thumbBox, new LinearLayout.LayoutParams(dp(260), dp(146)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        ImageView av = new ImageView(this);
        av.setScaleType(ImageView.ScaleType.CENTER_CROP);
        av.setBackground(oval(C_CARD));
        av.setClipToOutline(true);
        String au = bestImage(si.getUploaderAvatars());
        if (au != null) Glide.with(this).load(au).into(av);
        row.addView(av, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView n = new TextView(this);
        n.setText(si.getName());
        n.setTextColor(Color.WHITE);
        n.setTextSize(14);
        n.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        n.setMaxLines(2);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView s = new TextView(this);
        String sub = si.getUploaderName() == null ? "" : si.getUploaderName();
        if (si.getViewCount() >= 0) sub += (sub.isEmpty() ? "" : "  •  ") + compact(si.getViewCount()) + " tampilan";
        s.setText(sub);
        s.setTextColor(C_SUB);
        s.setTextSize(11);
        s.setMaxLines(2);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(n);
        t.addView(s);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = dp(10);
        row.addView(t, tl);
        card.addView(row);

        card.setOnClickListener(v -> {
            Intent in = new Intent(this, YoutubePlayerActivity.class);
            in.putExtra("url", si.getUrl());
            in.putExtra("title", si.getName());
            startActivity(in);
        });
        return card;
    }

    /** Prefers an adaptive manifest (HLS, then DASH) so the player can switch quality; falls back
     *  to a single muxed (video+audio) file, which is what most short/live videos only offer. */
    private static MediaItem pickStream(StreamInfo info) {
        if (notEmpty(info.getHlsUrl())) return MediaItem.fromUri(Uri.parse(info.getHlsUrl()));
        if (notEmpty(info.getDashMpdUrl())) return MediaItem.fromUri(Uri.parse(info.getDashMpdUrl()));
        List<VideoStream> muxed = info.getVideoStreams();
        VideoStream best = null;
        if (muxed != null) {
            for (VideoStream v : muxed) {
                if (v.isVideoOnly()) continue;
                if (best == null || resOf(v.getResolution()) > resOf(best.getResolution())) best = v;
            }
        }
        if (best != null) return MediaItem.fromUri(Uri.parse(best.getUrl()));
        // last resort: video-only stream with no audio track, better than nothing
        List<VideoStream> vo = info.getVideoOnlyStreams();
        if (vo != null && !vo.isEmpty()) return MediaItem.fromUri(Uri.parse(vo.get(0).getUrl()));
        return null;
    }

    private static boolean notEmpty(String s) { return s != null && !s.isEmpty(); }

    private static int resOf(String r) {
        if (r == null) return 0;
        try { return Integer.parseInt(r.replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }

    private void releasePlayer() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }

    private void startPlayback(MediaItem item) {
        releasePlayer();
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setMediaItem(item);
        player.setPlayWhenReady(true);
        player.prepare();
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "ExoPlayer error, code=" + error.errorCode + " (" + error.getErrorCodeName() + ")", error);
                Throwable c = error.getCause();
                while (c != null) { Log.e(TAG, "  caused by: " + c, c); c = c.getCause(); }
                status.setText("Playback failed [" + error.getErrorCodeName() + "]: " + error.getMessage());
                status.setVisibility(View.VISIBLE);
            }
            @Override public void onIsPlayingChanged(boolean isPlaying) { updatePipParams(); }
            @Override public void onVideoSizeChanged(@NonNull VideoSize size) { updatePipParams(); }
        });
    }

    // ---------------------------------------------------------------- Picture-in-Picture

    private void enterPip() {
        if (Build.VERSION.SDK_INT < 26) {
            Toast.makeText(this, "Floating mini-player needs Android 8.0+", Toast.LENGTH_SHORT).show();
            return;
        }
        if (player == null) return;
        try {
            enterPictureInPictureMode(pipParams());
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't enter mini-player", Toast.LENGTH_SHORT).show();
        }
    }

    private android.app.PictureInPictureParams pipParams() {
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder();
        if (player != null) {
            VideoSize vs = player.getVideoSize();
            if (vs.width > 0 && vs.height > 0) {
                float ratio = (float) vs.width / (float) vs.height;
                ratio = Math.max(0.42f, Math.min(2.38f, ratio));   // PiP's allowed aspect range
                b.setAspectRatio(new Rational(Math.round(ratio * 100), 100));
            }
        }
        if (Build.VERSION.SDK_INT >= 26) b.setActions(java.util.Collections.singletonList(playPauseAction()));
        return b.build();
    }

    private RemoteAction playPauseAction() {
        boolean playing = player != null && player.isPlaying();
        Icon ic = Icon.createWithResource(this, playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        Intent intent = new Intent(ACTION_MEDIA_CONTROL).setPackage(getPackageName())
                .putExtra(EXTRA_CONTROL, playing ? CONTROL_PAUSE : CONTROL_PLAY);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getBroadcast(this, playing ? CONTROL_PAUSE : CONTROL_PLAY, intent, flags);
        return new RemoteAction(ic, playing ? "Pause" : "Play", playing ? "Pause" : "Play", pi);
    }

    private void updatePipParams() {
        if (Build.VERSION.SDK_INT >= 26 && inPip) setPictureInPictureParams(pipParams());
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Leaving via home / recents / another app while something is playing: follow it into PiP
        // instead of just pausing in the background, so it keeps playing as a floating window.
        if (Build.VERSION.SDK_INT >= 26 && player != null && player.isPlaying()) enterPip();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        inPip = isInPictureInPictureMode;
        playerView.setUseController(!isInPictureInPictureMode);
        applyLayout();
        if (isInPictureInPictureMode) {
            if (pipReceiver == null) {
                pipReceiver = new BroadcastReceiver() {
                    @Override public void onReceive(Context c, Intent i) {
                        if (player == null) return;
                        int control = i.getIntExtra(EXTRA_CONTROL, 0);
                        if (control == CONTROL_PLAY) player.play();
                        else if (control == CONTROL_PAUSE) player.pause();
                        updatePipParams();
                    }
                };
                IntentFilter f = new IntentFilter(ACTION_MEDIA_CONTROL);
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(pipReceiver, f, Context.RECEIVER_NOT_EXPORTED);
                else registerReceiver(pipReceiver, f);
            }
        } else if (pipReceiver != null) {
            try { unregisterReceiver(pipReceiver); } catch (Exception ignored) {}
            pipReceiver = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Not in PiP and backgrounded (e.g. screen off, or PiP unsupported below API 26): stop cleanly
        // rather than keep decoding video no one can see.
        if (!inPip && player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pipReceiver != null) { try { unregisterReceiver(pipReceiver); } catch (Exception ignored) {} }
        releasePlayer();
    }
}
