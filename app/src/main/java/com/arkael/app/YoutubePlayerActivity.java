package com.arkael.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
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
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Plays one YouTube video. The stream URL is fetched directly through NewPipeExtractor (the same
 * technique NewPipe/LibreTube use), so playback goes straight to Google's CDN with no ad break.
 * No account / login is involved.
 *
 * The ExoPlayer itself lives in YoutubeAudioService; this screen controls it through a
 * MediaController. That is what lets "audio mode" (headphone button) keep playing in the
 * background with a play/pause notification.
 *
 * Layout follows the YouTube watch page: player (16:9), title, views, likes, chips (Suka, Audio,
 * Kualitas, Bagikan, Salin link), channel row and a swipeable row of related videos.
 * Landscape = fullscreen. Picture-in-Picture is still supported.
 */
public class YoutubePlayerActivity extends AppCompatActivity {
    private static final String TAG = "ArkaelYT";
    private static final String ACTION_MEDIA_CONTROL = "com.arkael.app.ytpip.control";
    private static final String EXTRA_CONTROL = "control";
    private static final int CONTROL_PLAY = 1, CONTROL_PAUSE = 2;
    private static final int REQ_NOTIF = 77;
    private static final String PREFS = "ark_yt";
    private static final String K_QUALITY = "quality_h";
    private static final String K_AUDIOLANG = "audio_lang";

    private static final int C_BG = Color.parseColor("#0A0D12");
    private static final int C_CARD = Color.parseColor("#141A22");
    private static final int C_ON = Color.parseColor("#2A3A5C");
    private static final int C_SUB = Color.parseColor("#8B95A3");
    private static final int C_ACCENT = Color.parseColor("#AEC6FF");

    private PlayerView playerView;
    private TextView subText;
    private StreamInfo curInfo;               // last extracted video, used by the download picker
    private static final class SubOpt { String label, url; }
    private static final class Cue { long start, end; String text; }
    private final List<SubOpt> subOpts = new ArrayList<SubOpt>();
    private List<Cue> subCues;            // null = subtitles off
    private int subSel = -1;
    private int subGen = 0;
    private final android.os.Handler subHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable subTick = new Runnable() {
        @Override public void run() {
            if (subCues == null || subText == null) return;
            long pos = player == null ? 0 : player.getCurrentPosition();
            String txt = null;
            for (int i = 0; i < subCues.size(); i++) {
                Cue c = subCues.get(i);
                if (c.start > pos) break;
                if (pos < c.end) txt = c.text;
            }
            if (txt == null || txt.isEmpty()) subText.setVisibility(View.GONE);
            else { subText.setText(txt); subText.setVisibility(View.VISIBLE); }
            subHandler.postDelayed(this, 200);
        }
    };
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private Player player;                 // == controller once it is connected
    private Player.Listener playerListener;
    private FrameLayout playerFrame;
    private LinearLayout header;
    private ScrollView scroll;
    private TextView title, status, meta, likes, channelName, channelSubs, audioBadge;
    private TextView chipLike, chipAudio, chipQuality, btnAudioHdr;
    private ImageView avatar;
    private LinearLayout relatedRow;
    private TextView relatedLabel;
    private ProgressBar spinner;
    private String videoUrl, videoTitle;
    private BroadcastReceiver pipReceiver;
    private boolean inPip;
    // description + comments (read-only, straight from YouTube)
    private LinearLayout descCard, commentsBox;
    private TextView descBody, descMore, commentsLabel, commentsMore;
    private boolean descExpanded;
    private CommentsInfo curComments;
    private Page commentsPage;
    private boolean commentsLoading;
    private int commentsShown;
    private boolean headerShown = true;    // top buttons (back, quality, audio, fullscreen, PiP)
    private final Runnable hideHeaderRun = () -> {
        if (player != null && player.isPlaying() && !this.audioMode) { headerShown = false; applyHeaderVisibility(); }
    };

    private MediaItem pendingItem;         // extracted, waiting for the controller to connect
    private boolean attachedExisting;      // opened from the notification: keep what is playing
    private boolean audioMode;             // video track off, keeps playing in the background
    private int manualHeight;              // 0 = automatic quality
    private boolean liked;
    private YtStore.Entry curEntry;

    // ---- extras: posisi terakhir, SponsorBlock, Return YouTube Dislike, DeArrow
    private final android.os.Handler exHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private java.util.List<YtExtras.Seg> exSegs = new java.util.ArrayList<YtExtras.Seg>();
    private final java.util.Set<Integer> exSkipped = new java.util.HashSet<Integer>();
    private int exTickN;
    private final java.util.List<String> exQueueUrls = new java.util.ArrayList<String>();
    private final java.util.List<String> exQueueTitles = new java.util.ArrayList<String>();
    private int exQueueIdx = -1;
    private String exQueueName;
    private boolean exInternal, exEndedHandled;
    private TextView btnSub, chipMode;
    private final Runnable exTick = new Runnable() {
        @Override public void run() {
            try { exTickBody(); } catch (Exception ignored) { }
            exHandler.postDelayed(this, 500);
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        NewPipeBootstrap.ensureInit();
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(C_BG);

        readIntent(getIntent());
        boolean resume = getIntent().getBooleanExtra("resume", false);
        if (videoUrl == null && !resume) { finish(); return; }
        manualHeight = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(K_QUALITY, 0);

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
        playerView.setControllerShowTimeoutMs(3000);
        // The top buttons follow the playback controls: they appear when the video is tapped
        // (and once when playback starts) and fade away together with the controls.
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) vis -> {
            if (vis == View.VISIBLE) showHeader();
            else { headerShown = false; applyHeaderVisibility(); }
        });
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerFrame.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        subText = new TextView(this);          // captions: our own overlay, so a bad caption file can never break playback
        subText.setTextColor(Color.WHITE);
        subText.setTextSize(15);
        subText.setGravity(Gravity.CENTER);
        subText.setShadowLayer(4f, 0f, 0f, Color.BLACK);
        subText.setPadding(dp(8), dp(3), dp(8), dp(3));
        subText.setBackgroundColor(Color.argb(150, 0, 0, 0));
        subText.setVisibility(View.GONE);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        slp.bottomMargin = dp(44);
        slp.leftMargin = dp(24);
        slp.rightMargin = dp(24);
        playerFrame.addView(subText, slp);

        audioBadge = new TextView(this);
        audioBadge.setText("🎧  Mode audio\nVideo dimatikan, tetap jalan di latar belakang");
        audioBadge.setTextColor(Color.WHITE);
        audioBadge.setTextSize(13);
        audioBadge.setGravity(Gravity.CENTER);
        audioBadge.setPadding(dp(16), dp(10), dp(16), dp(10));
        audioBadge.setBackground(round(Color.parseColor("#99000000"), 14));
        audioBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams abl = new FrameLayout.LayoutParams(-2, -2);
        abl.gravity = Gravity.CENTER;
        playerFrame.addView(audioBadge, abl);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(4), dp(4), dp(4));
        header.addView(headerBtn("←", v -> finish()));
        View grow = new View(this);
        header.addView(grow, new LinearLayout.LayoutParams(0, 1, 1f));
        header.addView(headerBtn("⚙", v -> showQualityDialog()));
        btnAudioHdr = headerBtn("🎧", v -> toggleAudio());
        header.addView(btnAudioHdr);
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
        chipLike = chip("👍  Suka", v -> toggleLike());
        chipAudio = chip("🎧  Audio", v -> toggleAudio());
        chipQuality = chip("⚙  Otomatis", v -> showQualityDialog());
        chips.addView(chipLike);
        chips.addView(chipAudio);
        chips.addView(chipQuality);
        chips.addView(chip("💬  Subtitle", v -> showSubtitleDialog()));
        chips.addView(chip(Tr.s("🔊  Suara"), v -> showAudioLangDialog()));
        chips.addView(chip("⬇  Download", v -> showDownloadDialog()));
        chips.addView(chip("🧩  Extras", v -> showExtrasDialog()));
        chipMode = chip("🔁  Otomatis", v -> showModeDialog());
        chips.addView(chipMode);
        chips.addView(chip("⏭  Berikutnya", v -> exGoNext(true)));
        chips.addView(chip("➕  Playlist", v -> exAddToPlaylistDialog()));
        exRefreshChips();
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
        btnSub = new TextView(this);
        btnSub.setTextSize(13);
        btnSub.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        btnSub.setGravity(Gravity.CENTER);
        btnSub.setPadding(dp(18), dp(10), dp(18), dp(10));
        btnSub.setVisibility(View.GONE);
        btnSub.setOnClickListener(v -> exToggleSub());
        chRow.addView(btnSub);
        chText.setOnClickListener(v -> exOpenChannel());
        col.addView(chRow);

        // description (tap to expand)
        descCard = new LinearLayout(this);
        descCard.setOrientation(LinearLayout.VERTICAL);
        descCard.setBackground(round(C_CARD, 14));
        descCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        descCard.setVisibility(View.GONE);
        TextView descTitle = new TextView(this);
        descTitle.setText("Deskripsi");
        descTitle.setTextColor(Color.WHITE);
        descTitle.setTextSize(14);
        descTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        descBody = new TextView(this);
        descBody.setTextColor(Color.parseColor("#DDE3EA"));
        descBody.setTextSize(13);
        descBody.setLinkTextColor(C_ACCENT);
        descBody.setPadding(0, dp(6), 0, 0);
        descBody.setMaxLines(3);
        descBody.setEllipsize(TextUtils.TruncateAt.END);
        descMore = new TextView(this);
        descMore.setText("Selengkapnya");
        descMore.setTextColor(C_ACCENT);
        descMore.setTextSize(12);
        descMore.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        descMore.setPadding(0, dp(8), 0, 0);
        descCard.addView(descTitle);
        descCard.addView(descBody);
        descCard.addView(descMore);
        descCard.setOnClickListener(v -> { if (!descExpanded) toggleDesc(); });
        descMore.setOnClickListener(v -> toggleDesc());
        LinearLayout.LayoutParams dcl = new LinearLayout.LayoutParams(-1, -2);
        dcl.setMargins(dp(14), dp(10), dp(14), 0);
        col.addView(descCard, dcl);

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

        // comments (read-only, from YouTube)
        commentsLabel = new TextView(this);
        commentsLabel.setText("Komentar");
        commentsLabel.setTextColor(Color.WHITE);
        commentsLabel.setTextSize(15);
        commentsLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        commentsLabel.setPadding(dp(14), dp(22), dp(14), dp(8));
        commentsLabel.setVisibility(View.GONE);
        col.addView(commentsLabel);
        commentsBox = new LinearLayout(this);
        commentsBox.setOrientation(LinearLayout.VERTICAL);
        commentsBox.setPadding(dp(14), 0, dp(14), 0);
        col.addView(commentsBox, new LinearLayout.LayoutParams(-1, -2));
        commentsMore = new TextView(this);
        commentsMore.setText("Muat komentar lainnya");
        commentsMore.setTextColor(C_ACCENT);
        commentsMore.setTextSize(13);
        commentsMore.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        commentsMore.setGravity(Gravity.CENTER);
        commentsMore.setPadding(dp(14), dp(14), dp(14), dp(14));
        commentsMore.setVisibility(View.GONE);
        commentsMore.setOnClickListener(v -> loadMoreComments());
        col.addView(commentsMore, new LinearLayout.LayoutParams(-1, -2));

        scroll.addView(col, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        applyLayout();
        refreshChips();
        connectController();
        if (videoUrl != null) {
            liked = YtStore.isLiked(this, videoUrl);
            refreshChips();
            load();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getStringExtra("url") == null) return;   // notification tap while already open: nothing to switch
        // A related / history video was picked (launchMode=singleTask delivers it here): switch in place.
        exSavePos();
        exReset();
        readIntent(intent);
        pendingItem = null;
        curEntry = null;
        title.setText(videoTitle == null ? "" : videoTitle);
        meta.setText("");
        likes.setVisibility(View.GONE);
        channelName.setText("");
        channelSubs.setText("");
        avatar.setImageDrawable(null);
        relatedRow.removeAllViews();
        relatedLabel.setVisibility(View.GONE);
        resetInfoExtras();
        status.setText("");
        scroll.scrollTo(0, 0);
        liked = YtStore.isLiked(this, videoUrl);
        refreshChips();
        load();
    }

    private void readIntent(Intent i) {
        videoUrl = i == null ? null : i.getStringExtra("url");
        videoTitle = i == null ? null : i.getStringExtra("title");
        exReadQueue(i);
    }

    // ---------------------------------------------------------------- player service connection

    private void connectController() {
        SessionToken token = new SessionToken(this, new ComponentName(this, YoutubeAudioService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
            } catch (Exception e) {
                Log.e(TAG, "player service connection failed", e);
                spinner.setVisibility(View.GONE);
                status.setText("Player service failed: " + e.getClass().getSimpleName());
                return;
            }
            if (isDestroyed() || isFinishing()) { controller.release(); controller = null; return; }
            onControllerReady();
        }, ContextCompat.getMainExecutor(this));
    }

    private void onControllerReady() {
        player = controller;
        playerView.setPlayer(controller);
        playerListener = new Player.Listener() {
            @Override public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "ExoPlayer error, code=" + error.errorCode + " (" + error.getErrorCodeName() + ")", error);
                Throwable c = error.getCause();
                while (c != null) { Log.e(TAG, "  caused by: " + c, c); c = c.getCause(); }
                status.setText("Playback failed [" + error.getErrorCodeName() + "]: " + error.getMessage());
                status.setVisibility(View.VISIBLE);
                showHeader();
            }
            @Override public void onPlaybackStateChanged(int state) {
                spinner.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
            }
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                updatePipParams();
                showHeader();   // playing: shows once, then hides itself; paused: stays visible
            }
            @Override public void onVideoSizeChanged(@NonNull VideoSize size) { updatePipParams(); }
        };
        controller.addListener(playerListener);
        exStart();

        if (videoUrl == null) {
            // Opened from the media notification: attach to whatever is already playing.
            MediaItem cur = controller.getCurrentMediaItem();
            if (cur == null || cur.mediaId == null || MediaItem.DEFAULT_MEDIA_ID.equals(cur.mediaId)) {
                finish();
                return;
            }
            attachedExisting = true;
            videoUrl = cur.mediaId;
            CharSequence t = cur.mediaMetadata.title;
            videoTitle = t == null ? null : t.toString();
            if (videoTitle != null) title.setText(videoTitle);
            audioMode = controller.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO);
            liked = YtStore.isLiked(this, videoUrl);
            refreshChips();
            refreshAudioUi();
            load();           // fills the info area only; playback is left untouched
            return;
        }
        tryStart();
    }

    private void tryStart() {
        if (controller == null || pendingItem == null) return;
        MediaItem it = pendingItem;
        pendingItem = null;
        startPlayback(it);
    }

    private void startPlayback(MediaItem item) {
        playerView.setPlayer(controller);
        long exResume = YtExtras.getPosition(this, videoUrl);
        controller.setMediaItem(item, exResume);
        if (exResume > 0) Toast.makeText(this, Tr.s("Lanjut dari ") + fmtDuration(exResume / 1000), Toast.LENGTH_SHORT).show();
        exApplyRepeat();
        applyTrackPrefs();
        controller.setPlayWhenReady(true);
        controller.prepare();
    }

    /** Pushes audio mode (video track off) and the chosen quality to the player. */
    private void applyTrackPrefs() {
        if (controller == null) return;
        TrackSelectionParameters.Builder b = controller.getTrackSelectionParameters().buildUpon();
        b.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioMode);
        if (manualHeight > 0) {
            b.setMinVideoSize(0, manualHeight).setMaxVideoSize(Integer.MAX_VALUE, manualHeight);
        } else {
            b.clearVideoSizeConstraints();
        }
        android.content.SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String audioLang = sp.getString(K_AUDIOLANG, "");
        if (!audioLang.isEmpty()) b.setPreferredAudioLanguage(audioLang);
        controller.setTrackSelectionParameters(b.build());
    }

    // ---------------------------------------------------------------- audio mode / quality / like

    private void toggleAudio() {
        audioMode = !audioMode;
        if (audioMode) ensureNotificationPermission();
        applyTrackPrefs();
        refreshAudioUi();
        refreshChips();
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private void refreshAudioUi() {
        audioBadge.setVisibility(audioMode ? View.VISIBLE : View.GONE);
        btnAudioHdr.setTextColor(audioMode ? C_ACCENT : Color.WHITE);
        applyHeaderVisibility();
    }

    private void showQualityDialog() {
        if (controller == null) return;
        TreeSet<Integer> heights = new TreeSet<Integer>(Collections.<Integer>reverseOrder());
        for (Tracks.Group g : controller.getCurrentTracks().getGroups()) {
            if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                int h = g.getTrackFormat(i).height;
                if (h > 0) heights.add(h);
            }
        }
        final List<Integer> values = new ArrayList<Integer>();
        values.add(0);
        values.addAll(heights);
        if (manualHeight > 0 && !values.contains(manualHeight)) values.add(1, manualHeight);
        String[] labels = new String[values.size()];
        int checked = 0;
        for (int i = 0; i < values.size(); i++) {
            labels[i] = values.get(i) == 0 ? "Otomatis" : values.get(i) + "p";
            if (values.get(i) == manualHeight) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Kualitas video")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    manualHeight = values.get(which);
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(K_QUALITY, manualHeight).apply();
                    applyTrackPrefs();
                    refreshChips();
                    d.dismiss();
                })
                .setNegativeButton(Tr.s("Batal"), null)
                .show();
    }

    // ---------------------------------------------------------------- subtitles / audio language

    private static String langName(String tag) {
        if (tag == null || tag.isEmpty() || "und".equals(tag)) return "";
        String n = Locale.forLanguageTag(tag).getDisplayLanguage();
        if (n == null || n.isEmpty()) return tag;
        return n.substring(0, 1).toUpperCase() + n.substring(1);
    }

    private static String trackLabel(androidx.media3.common.Format f, int index) {
        if (f.label != null && !f.label.isEmpty()) return f.label;
        String n = langName(f.language);
        return n.isEmpty() ? "Track " + (index + 1) : n;
    }

    private static final String[] TRANSLATE_TO = {"id", "en", "ja", "ko", "zh-Hans", "zh-Hant", "ar", "hi", "th",
            "vi", "ms", "es", "fr", "de", "pt", "ru", "tr", "it", "nl", "pl", "fil", "bn", "ur", "fa", "sv"};

    private static String capJson(String u, String tlang) {
        u = u.replaceAll("[&?]tlang=[^&]*", "");
        if (u.matches(".*[&?]fmt=[^&]*.*")) u = u.replaceAll("([&?])fmt=[^&]*", "$1fmt=json3");
        else u = u + (u.contains("?") ? "&" : "?") + "fmt=json3";
        return tlang == null ? u : u + "&tlang=" + tlang;
    }

    /** Every caption YouTube lists (manual first, then auto-generated) + machine translations of one track. */
    private void buildSubOptions(StreamInfo info) {
        subOpts.clear();
        subSel = -1;
        subGen++;
        subCues = null;
        subHandler.removeCallbacks(subTick);
        if (subText != null) subText.setVisibility(View.GONE);
        List<SubtitlesStream> all = info.getSubtitles();
        if (all == null) return;
        java.util.LinkedHashMap<String, SubtitlesStream> best = new java.util.LinkedHashMap<String, SubtitlesStream>();
        for (SubtitlesStream x : all) {
            if (!x.isUrl() || x.getContent() == null) continue;
            String key = x.getLanguageTag() + "|" + x.isAutoGenerated();
            if (!best.containsKey(key)) best.put(key, x);
        }
        SubtitlesStream base = null;
        List<String> have = new ArrayList<String>();
        for (int pass = 0; pass < 2; pass++) {
            for (SubtitlesStream x : best.values()) {
                if (x.isAutoGenerated() != (pass == 1)) continue;
                String name = langName(x.getLanguageTag());
                if (name.isEmpty()) name = x.getDisplayLanguageName();
                SubOpt o = new SubOpt();
                o.label = x.isAutoGenerated() ? name + " (otomatis)" : name;
                o.url = capJson(x.getContent(), null);
                subOpts.add(o);
                have.add(x.getLanguageTag());
                if (base == null) base = x;
            }
        }
        if (base == null) return;
        for (String code : TRANSLATE_TO) {
            if (have.contains(code)) continue;
            SubOpt o = new SubOpt();
            o.label = langName(code) + " (terjemahan otomatis)";
            o.url = capJson(base.getContent(), code);
            subOpts.add(o);
        }
    }

    private void showSubtitleDialog() {
        if (subOpts.isEmpty()) {
            Toast.makeText(this, Tr.s("Video ini tidak punya subtitle"), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[subOpts.size() + 1];
        labels[0] = "Mati";
        for (int i = 0; i < subOpts.size(); i++) labels[i + 1] = subOpts.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle("Subtitle")
                .setSingleChoiceItems(labels, subSel + 1, (d, which) -> {
                    d.dismiss();
                    if (which == 0) { stopSubtitles(); return; }
                    loadSubtitle(which - 1);
                })
                .setNegativeButton(Tr.s("Batal"), null)
                .show();
    }

    private void stopSubtitles() {
        subGen++;
        subSel = -1;
        subCues = null;
        subHandler.removeCallbacks(subTick);
        if (subText != null) subText.setVisibility(View.GONE);
    }

    private void loadSubtitle(final int idx) {
        final int gen = ++subGen;
        final SubOpt o = subOpts.get(idx);
        Toast.makeText(this, Tr.s("Memuat subtitle…"), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<Cue> cues = new ArrayList<Cue>();
            String err = null;
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(o.url).openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
                c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                int code = c.getResponseCode();
                if (code != 200) {
                    err = "HTTP " + code;
                } else {
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    java.io.InputStream in = c.getInputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0 && bo.size() < 4000000) bo.write(buf, 0, n);
                    in.close();
                    String body = bo.toString("UTF-8");
                    org.json.JSONArray ev = new org.json.JSONObject(body).optJSONArray("events");
                    if (ev == null) err = Tr.s("kosong");
                    else for (int i = 0; i < ev.length(); i++) {
                        org.json.JSONObject e = ev.getJSONObject(i);
                        org.json.JSONArray segs = e.optJSONArray("segs");
                        if (segs == null) continue;
                        StringBuilder sb = new StringBuilder();
                        for (int j = 0; j < segs.length(); j++) sb.append(segs.getJSONObject(j).optString("utf8", ""));
                        String t = sb.toString().replace('\u00a0', ' ').trim();
                        if (t.isEmpty()) continue;
                        Cue cu = new Cue();
                        cu.start = e.optLong("tStartMs", 0);
                        cu.end = cu.start + e.optLong("dDurationMs", 3000);
                        cu.text = t;
                        cues.add(cu);
                    }
                    if (err == null && cues.isEmpty()) err = Tr.s("tidak ada teks");
                }
                c.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "subtitle load failed", e);
                err = e.getClass().getSimpleName();
            }
            final String ferr = err;
            final List<Cue> fc = cues;
            runOnUiThread(() -> {
                if (isFinishing() || gen != subGen) return;
                if (ferr != null) {
                    Toast.makeText(this, Tr.s("Subtitle tidak bisa dimuat (") + ferr + Tr.s("). Coba bahasa lain."), Toast.LENGTH_LONG).show();
                    return;
                }
                java.util.Collections.sort(fc, (a, b) -> Long.compare(a.start, b.start));
                subCues = fc;
                subSel = idx;
                subHandler.removeCallbacks(subTick);
                subHandler.post(subTick);
            });
        }).start();
    }

    private void showAudioLangDialog() {
        if (controller == null) return;
        final List<Tracks.Group> gs = new ArrayList<Tracks.Group>();
        List<String> seen = new ArrayList<String>();
        for (Tracks.Group g : controller.getCurrentTracks().getGroups()) {
            if (g.getType() != C.TRACK_TYPE_AUDIO || g.length == 0) continue;
            androidx.media3.common.Format f = g.getTrackFormat(0);
            String key = (f.language == null ? "" : f.language) + "|" + (f.label == null ? "" : f.label);
            if (seen.contains(key)) continue;
            seen.add(key);
            gs.add(g);
        }
        if (gs.size() < 2) {
            Toast.makeText(this, Tr.s("Video ini cuma punya satu bahasa suara"), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[gs.size()];
        int checked = 0;
        for (int i = 0; i < gs.size(); i++) {
            labels[i] = trackLabel(gs.get(i).getTrackFormat(0), i);
            if (gs.get(i).isSelected()) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(Tr.s("Bahasa suara"))
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    Tracks.Group g = gs.get(which);
                    TrackSelectionParameters.Builder b = controller.getTrackSelectionParameters().buildUpon();
                    b.setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), 0));
                    controller.setTrackSelectionParameters(b.build());
                    String lang = g.getTrackFormat(0).language;
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(K_AUDIOLANG, lang == null ? "" : lang).apply();
                    d.dismiss();
                })
                .setNegativeButton(Tr.s("Batal"), null)
                .show();
    }

    // ---------------------------------------------------------------- download

    private static final class DlOpt { String label, kind, ext, video, audio, res; int h; }

    private static int hOf(String r) {
        if (r == null) return 0;
        int i = r.indexOf('p');
        try { return Integer.parseInt(r.substring(0, i < 0 ? r.length() : i).replaceAll("\\D", "")); } catch (Exception e) { return 0; }
    }

    private static boolean isAv1(String url) {   // AV1 inside mp4 cannot be joined on most phones: skip by itag
        try {
            String t = Uri.parse(url).getQueryParameter("itag");
            int i = t == null ? 0 : Integer.parseInt(t);
            return (i >= 394 && i <= 402) || i == 571 || (i >= 694 && i <= 702);
        } catch (Exception e) { return false; }
    }

    private static List<DlOpt> downloadOptions(StreamInfo info) {
        AudioStream m4a = null, webm = null;
        if (info.getAudioStreams() != null) {
            for (AudioStream a : info.getAudioStreams()) {
                String u = a.getContent();
                if (!a.isUrl() || u == null || u.toLowerCase(Locale.ROOT).contains("dubbed")) continue;   // keep the original language
                String x = a.getFormat() == null ? "" : a.getFormat().getSuffix();
                if ("m4a".equals(x) && (m4a == null || a.getAverageBitrate() > m4a.getAverageBitrate())) m4a = a;
                if ("webm".equals(x) && (webm == null || a.getAverageBitrate() > webm.getAverageBitrate())) webm = a;
            }
        }
        java.util.LinkedHashMap<String, VideoStream> mp4 = new java.util.LinkedHashMap<String, VideoStream>();
        java.util.LinkedHashMap<String, VideoStream> wb = new java.util.LinkedHashMap<String, VideoStream>();
        if (info.getVideoOnlyStreams() != null) {
            for (VideoStream v : info.getVideoOnlyStreams()) {
                String u = v.getContent(), r = v.getResolution();
                if (!v.isUrl() || u == null || r == null) continue;
                String x = v.getFormat() == null ? "" : v.getFormat().getSuffix();
                if ("mp4".equals(x)) { if (!isAv1(u) && !mp4.containsKey(r)) mp4.put(r, v); }
                else if ("webm".equals(x)) { if (!wb.containsKey(r)) wb.put(r, v); }
            }
        }
        List<DlOpt> vids = new ArrayList<DlOpt>();
        int maxMp4 = 0;
        if (m4a != null) {
            for (java.util.Map.Entry<String, VideoStream> e : mp4.entrySet()) {
                DlOpt o = new DlOpt();
                o.res = e.getKey(); o.h = hOf(o.res); o.label = o.res + " · MP4";
                o.kind = "mp4"; o.ext = "mp4"; o.video = e.getValue().getContent(); o.audio = m4a.getContent();
                vids.add(o);
                maxMp4 = Math.max(maxMp4, o.h);
            }
        }
        if (webm != null) {
            for (java.util.Map.Entry<String, VideoStream> e : wb.entrySet()) {
                if (hOf(e.getKey()) <= maxMp4) continue;   // only the sizes MP4 cannot offer (1440p, 4K...)
                DlOpt o = new DlOpt();
                o.res = e.getKey(); o.h = hOf(o.res); o.label = o.res + " · WebM";
                o.kind = "webm"; o.ext = "webm"; o.video = e.getValue().getContent(); o.audio = webm.getContent();
                vids.add(o);
            }
        }
        if (info.getVideoStreams() != null) {
            for (VideoStream v : info.getVideoStreams()) {
                String u = v.getContent(), r = v.getResolution();
                if (v.isVideoOnly() || !v.isUrl() || u == null || r == null || mp4.containsKey(r)) continue;
                String x = v.getFormat() == null ? "" : v.getFormat().getSuffix();
                if (!"mp4".equals(x)) continue;
                DlOpt o = new DlOpt();
                o.res = r; o.h = hOf(r); o.label = r + Tr.s(" · MP4 (langsung, ada suara)");
                o.kind = "direct"; o.ext = "mp4"; o.video = u;
                vids.add(o);
            }
        }
        java.util.Collections.sort(vids, (p, q) -> q.h - p.h);
        if (m4a != null) {
            DlOpt o = new DlOpt();
            o.res = "audio"; o.label = "🎵 Audio saja · M4A";
            o.kind = "audio"; o.ext = "m4a"; o.audio = m4a.getContent();
            vids.add(o);
        }
        return vids;
    }

    private void showDownloadDialog() {
        if (curInfo == null) {
            Toast.makeText(this, Tr.s("Video belum siap, tunggu sebentar"), Toast.LENGTH_SHORT).show();
            return;
        }
        final StreamInfo info = curInfo;
        final List<DlOpt> opts = downloadOptions(info);
        if (opts.isEmpty()) {
            Toast.makeText(this, Tr.s("Tidak ada file yang bisa diunduh untuk video ini"), Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[opts.size()];
        for (int i = 0; i < opts.size(); i++) labels[i] = opts.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle("Download")
                .setItems(labels, (d, which) -> {
                    DlOpt o = opts.get(which);
                    ensureNotificationPermission();
                    YoutubeDownloadService.start(this, info.getName() == null ? "video" : info.getName(),
                            o.kind, o.video, o.audio, o.ext, "audio".equals(o.kind) ? "" : o.res);
                    Toast.makeText(this, "Download dimulai, lihat di notifikasi", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(Tr.s("Batal"), null)
                .show();
    }

    private void toggleLike() {
        YtStore.Entry e = curEntry;
        if (e == null) {
            e = new YtStore.Entry();
            e.url = videoUrl;
            e.title = videoTitle == null ? "" : videoTitle;
            e.uploader = "";
        }
        liked = YtStore.toggleLike(this, e);
        refreshChips();
        Toast.makeText(this, liked ? "Disimpan ke Disukai" : Tr.s("Dihapus dari Disukai"), Toast.LENGTH_SHORT).show();
    }

    private void refreshChips() {
        if (chipLike == null) return;
        chipLike.setText(liked ? "👍  Disukai" : "👍  Suka");
        styleChip(chipLike, liked);
        chipAudio.setText(audioMode ? Tr.s("🎧  Audio: aktif") : "🎧  Audio");
        styleChip(chipAudio, audioMode);
        chipQuality.setText("⚙  " + (manualHeight > 0 ? manualHeight + "p" : "Otomatis"));
        styleChip(chipQuality, manualHeight > 0);
    }

    private void styleChip(TextView t, boolean on) {
        t.setBackground(round(on ? C_ON : C_CARD, 20));
        t.setTextColor(on ? Color.WHITE : C_ACCENT);
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
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        t.setBackground(oval(Color.parseColor("#66000000")));
        t.setOnClickListener(v -> { l.onClick(v); showHeader(); });
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
        applyHeaderVisibility();
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

    /** Header is visible only briefly; always visible in audio mode (nothing else to tap) and never in PiP. */
    private void applyHeaderVisibility() {
        if (header == null) return;
        boolean show = !inPip && (headerShown || audioMode);
        header.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showHeader() {
        headerShown = true;
        applyHeaderVisibility();
        playerFrame.removeCallbacks(hideHeaderRun);
        if (player != null && player.isPlaying()) playerFrame.postDelayed(hideHeaderRun, 3500);
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
                item = withMeta(pickStream(info), info, url);
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
                if (attachedExisting) { attachedExisting = false; return; }   // already playing, just keep it
                if (ferr != null) { status.setText(ferr); return; }
                pendingItem = fi;
                tryStart();
            });
        }).start();
    }

    /** Gives the item an id (the video URL), title / artist / artwork for the media notification. */
    private static MediaItem withMeta(MediaItem base, StreamInfo info, String url) {
        if (base == null || base.localConfiguration == null) return null;
        Uri uri = base.localConfiguration.uri;
        MediaMetadata.Builder md = new MediaMetadata.Builder()
                .setTitle(info.getName())
                .setArtist(info.getUploaderName());
        String th = bestImage(info.getThumbnails());
        if (th != null) md.setArtworkUri(Uri.parse(th));
        return new MediaItem.Builder()
                .setUri(uri)
                .setMediaId(url)
                .setMediaMetadata(md.build())
                .setRequestMetadata(new MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
                .build();
    }

    private static final String[] BULAN = {"Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"};

    /** "2026-05-25T06:09:13-07:00" -> "25 Mei 2026" (anything else is returned unchanged). */
    private static String prettyDate(String d) {
        if (d == null || !d.matches("^\\d{4}-\\d{2}-\\d{2}.*")) return d;
        try {
            int m = Integer.parseInt(d.substring(5, 7));
            return Integer.parseInt(d.substring(8, 10)) + " " + BULAN[m - 1] + " " + d.substring(0, 4);
        } catch (Exception e) { return d; }
    }

    private void populate(StreamInfo info) {
        buildSubOptions(info);
        curInfo = info;
        if (info.getName() != null && !info.getName().isEmpty()) title.setText(info.getName());

        StringBuilder m = new StringBuilder();
        if (info.getViewCount() >= 0) m.append(compact(info.getViewCount())).append(" tampilan");
        String date = prettyDate(info.getTextualUploadDate());
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

        // remember it locally (history), and keep the data the Suka button needs
        YtStore.Entry e = new YtStore.Entry();
        e.url = videoUrl;
        e.title = info.getName() == null ? "" : info.getName();
        e.uploader = info.getUploaderName() == null ? "" : info.getUploaderName();
        e.thumb = bestImage(info.getThumbnails());
        e.duration = Math.max(0, info.getDuration());
        curEntry = e;
        YtStore.addHistory(this, e);
        liked = YtStore.isLiked(this, videoUrl);
        refreshChips();

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

        showDescription(info.getDescription());
        loadComments(videoUrl);
        loadExtras(videoUrl);
        exRefreshSub();
    }

    // ---------------------------------------------------------------- description + comments

    private void resetInfoExtras() {
        descCard.setVisibility(View.GONE);
        descBody.setText("");
        descExpanded = false;
        commentsBox.removeAllViews();
        commentsLabel.setVisibility(View.GONE);
        commentsMore.setVisibility(View.GONE);
        curComments = null;
        commentsPage = null;
        commentsShown = 0;
        commentsLoading = false;
    }

    private static String plain(Description d) {
        if (d == null || d.getContent() == null) return "";
        String c = d.getContent();
        if (d.getType() == Description.HTML) {
            c = Html.fromHtml(c, Html.FROM_HTML_MODE_LEGACY).toString();
        }
        return c.trim();
    }

    private void showDescription(Description d) {
        String t = plain(d);
        if (t.isEmpty()) { descCard.setVisibility(View.GONE); return; }
        descExpanded = false;
        descBody.setText(t);
        descBody.setMaxLines(3);
        descBody.setEllipsize(TextUtils.TruncateAt.END);
        descBody.setMovementMethod(null);
        descMore.setText("Selengkapnya");
        descMore.setVisibility(t.length() > 120 || t.indexOf('\n') >= 0 ? View.VISIBLE : View.GONE);
        descCard.setVisibility(View.VISIBLE);
    }

    private void toggleDesc() {
        descExpanded = !descExpanded;
        if (descExpanded) {
            descBody.setMaxLines(Integer.MAX_VALUE);
            descBody.setEllipsize(null);
            Linkify.addLinks(descBody, Linkify.WEB_URLS);     // links work once expanded
            descBody.setMovementMethod(LinkMovementMethod.getInstance());
            descMore.setText(Tr.s("Tampilkan lebih sedikit"));
        } else {
            descBody.setMaxLines(3);
            descBody.setEllipsize(TextUtils.TruncateAt.END);
            descBody.setMovementMethod(null);
            descMore.setText("Selengkapnya");
        }
    }

    private void loadComments(final String url) {
        commentsLoading = true;
        new Thread(() -> {
            CommentsInfo ci = null;
            String err = null;
            try {
                ci = CommentsInfo.getInfo(url);
            } catch (Exception e) {
                Log.e(TAG, "comments failed for " + url, e);
                err = e.getClass().getSimpleName();
            }
            final CommentsInfo fci = ci;
            final String ferr = err;
            runOnUiThread(() -> {
                if (isFinishing() || !url.equals(videoUrl)) return;
                commentsLoading = false;
                commentsLabel.setVisibility(View.VISIBLE);
                if (fci == null || fci.isCommentsDisabled()) {
                    commentsLabel.setText(ferr != null ? Tr.s("Komentar tidak bisa dimuat") : Tr.s("Komentar dimatikan untuk video ini"));
                    return;
                }
                curComments = fci;
                commentsPage = fci.getNextPage();
                List<CommentsInfoItem> items = fci.getRelatedItems();
                if (items == null || items.isEmpty()) { commentsLabel.setText(Tr.s("Belum ada komentar")); return; }
                commentsLabel.setText("Komentar");
                addComments(items);
                commentsMore.setVisibility(fci.hasNextPage() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    private void loadMoreComments() {
        if (commentsLoading || curComments == null || commentsPage == null) return;
        commentsLoading = true;
        commentsMore.setText(Tr.s("Memuat…"));
        final String url = videoUrl;
        final CommentsInfo ci = curComments;
        final Page pg = commentsPage;
        new Thread(() -> {
            ListExtractor.InfoItemsPage<CommentsInfoItem> res = null;
            try {
                res = CommentsInfo.getMoreItems(NewPipe.getService("YouTube"), ci, pg);
            } catch (Exception e) {
                Log.e(TAG, "more comments failed", e);
            }
            final ListExtractor.InfoItemsPage<CommentsInfoItem> fres = res;
            runOnUiThread(() -> {
                if (isFinishing() || !url.equals(videoUrl)) return;
                commentsLoading = false;
                commentsMore.setText("Muat komentar lainnya");
                if (fres == null) { Toast.makeText(this, Tr.s("Gagal memuat komentar"), Toast.LENGTH_SHORT).show(); return; }
                commentsPage = fres.getNextPage();
                addComments(fres.getItems());
                commentsMore.setVisibility(fres.hasNextPage() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    private void addComments(List<CommentsInfoItem> items) {
        if (items == null) return;
        for (CommentsInfoItem c : items) {
            try { commentsBox.addView(commentRow(c)); commentsShown++; } catch (Exception e) { Log.e(TAG, "comment row", e); }
        }
    }

    private View commentRow(CommentsInfoItem c) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(8));

        ImageView av = new ImageView(this);
        av.setScaleType(ImageView.ScaleType.CENTER_CROP);
        av.setBackground(oval(C_CARD));
        av.setClipToOutline(true);
        String url = bestImage(c.getUploaderAvatars());
        if (url != null) Glide.with(this).load(url).into(av);
        row.addView(av, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.leftMargin = dp(10);
        row.addView(col, cl);

        TextView head = new TextView(this);
        StringBuilder h = new StringBuilder();
        if (c.isPinned()) h.append("📌 ");
        h.append(c.getUploaderName() == null ? "" : c.getUploaderName());
        String when = c.getTextualUploadDate();
        if (when != null && !when.isEmpty()) h.append("  •  ").append(when);
        head.setText(h.toString());
        head.setTextColor(C_SUB);
        head.setTextSize(12);
        head.setSingleLine(true);
        head.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(head);

        TextView body = new TextView(this);
        body.setText(plain(c.getCommentText()));
        body.setTextColor(Color.WHITE);
        body.setTextSize(13);
        body.setPadding(0, dp(2), 0, 0);
        col.addView(body);

        if (c.getLikeCount() > 0) {
            TextView lk = new TextView(this);
            lk.setText("👍 " + compact(c.getLikeCount()));
            lk.setTextColor(C_SUB);
            lk.setTextSize(11);
            lk.setPadding(0, dp(3), 0, 0);
            col.addView(lk);
        }
        return row;
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

    // ---------------------------------------------------------------- Picture-in-Picture

    // ---------------------------------------------------------------- extras

    private boolean exOn(String key) {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(key, true);
    }

    private void exStart() {
        exHandler.removeCallbacks(exTick);
        exHandler.postDelayed(exTick, 500);
    }

    private void exReset() {
        exSegs = new java.util.ArrayList<YtExtras.Seg>();
        exSkipped.clear();
        exEndedHandled = false;
        if (btnSub != null) btnSub.setVisibility(View.GONE);
    }

    /** True kalau yang sedang diputar memang video di layar ini (bukan video lama saat berganti). */
    private boolean exCurrent() {
        if (player == null || videoUrl == null) return false;
        MediaItem cur = player.getCurrentMediaItem();
        return cur != null && videoUrl.equals(cur.mediaId);
    }

    private void exTickBody() {
        if (!exCurrent()) return;
        int st = player.getPlaybackState();
        if (st == Player.STATE_ENDED) {
            YtExtras.clearPosition(this, videoUrl);
            if (!exEndedHandled) { exEndedHandled = true; exOnEnded(); }
            return;
        }
        long pos = player.getCurrentPosition();

        if (player.isPlaying() && !exSegs.isEmpty() && exOn("ex_sb")) {
            for (int i = 0; i < exSegs.size(); i++) {
                YtExtras.Seg g = exSegs.get(i);
                if (exSkipped.contains(i)) continue;                 // sekali saja per segmen: kalau diputar ulang, tidak dipaksa
                if (pos >= g.startMs && pos < g.endMs - 400) {
                    exSkipped.add(i);
                    long to = g.endMs;
                    long dur = player.getDuration();
                    if (dur > 0 && to > dur) to = dur;
                    player.seekTo(to);
                    Toast.makeText(this, "⏭ Dilewati: " + YtExtras.categoryLabel(g.category), Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }
        if (++exTickN % 10 == 0 && player.isPlaying()) exSavePos();   // tiap ~5 detik
    }

    private void exSavePos() {
        try {
            if (!exCurrent() || player.getPlaybackState() == Player.STATE_ENDED) return;
            YtExtras.savePosition(this, videoUrl, player.getCurrentPosition(), player.getDuration());
        } catch (Exception ignored) { }
    }

    private void loadExtras(final String url) {
        final String id = YtExtras.videoId(url);
        if (id == null) return;
        exReset();

        if (exOn("ex_sb")) new Thread(() -> {
            final java.util.List<YtExtras.Seg> segs = YtExtras.sponsorSegments(id);
            runOnUiThread(() -> { if (!isFinishing() && url.equals(videoUrl)) exSegs = segs; });
        }).start();

        if (exOn("ex_ryd")) new Thread(() -> {
            final long[] v = YtExtras.dislikes(id);
            runOnUiThread(() -> {
                if (isFinishing() || !url.equals(videoUrl) || v == null || v[1] < 0) return;
                long l = (curInfo != null && curInfo.getLikeCount() >= 0) ? curInfo.getLikeCount() : v[0];
                likes.setText("👍 " + (l >= 0 ? compact(l) : "?") + "   👎 " + compact(v[1]));
                likes.setVisibility(View.VISIBLE);
            });
        }).start();

        if (exOn("ex_dearrow")) new Thread(() -> {
            final String t = YtExtras.deArrowTitle(id);
            runOnUiThread(() -> {
                if (isFinishing() || !url.equals(videoUrl) || t == null || t.isEmpty()) return;
                title.setText(t);
            });
        }).start();
    }

    private void showExtrasDialog() {
        final String[] keys = {"ex_sb", "ex_ryd", "ex_dearrow"};
        String[] names = {"SponsorBlock (lewati sponsor)", "Return YouTube Dislike (jumlah dislike)", Tr.s("DeArrow (judul tidak clickbait)")};
        final boolean[] on = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) on[i] = exOn(keys[i]);
        new AlertDialog.Builder(this)
                .setTitle("Extras")
                .setMultiChoiceItems(names, on, (d, which, checked) ->
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(keys[which], checked).apply())
                .setPositiveButton("OK", (d, w) -> {
                    if (curInfo != null) {                                    // kembalikan tampilan asli kalau dimatikan
                        if (!exOn("ex_dearrow") && curInfo.getName() != null) title.setText(curInfo.getName());
                        if (!exOn("ex_ryd") && curInfo.getLikeCount() >= 0) likes.setText("👍 " + compact(curInfo.getLikeCount()));
                    }
                    if (videoUrl != null) loadExtras(videoUrl);
                })
                .setNegativeButton(Tr.s("Batal"), null)
                .show();
    }

    // ---------------------------------------------------------------- antrean / langganan / playlist

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
                    if (manual) Toast.makeText(this, Tr.s("Sudah di akhir ") + (exQueueName == null || exQueueName.isEmpty() ? "antrean" : exQueueName), Toast.LENGTH_SHORT).show();
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
        if (manual) Toast.makeText(this, Tr.s("Tidak ada video berikutnya"), Toast.LENGTH_SHORT).show();
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
                .setNegativeButton(Tr.s("Batal"), null)
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
                .setTitle(Tr.s("Simpan ke playlist"))
                .setItems(names, (d, which) -> {
                    if (which == 0) exNewPlaylistThenAdd();
                    else exAddTo(pls.get(which - 1));
                })
                .setNegativeButton(Tr.s("Batal"), null)
                .show();
    }

    private void exAddTo(YtLibrary.Pl pl) {
        boolean added = YtLibrary.addToPlaylist(this, pl.id, exEntry());
        Toast.makeText(this, added ? "Disimpan ke " + pl.name : Tr.s("Sudah ada di ") + pl.name, Toast.LENGTH_SHORT).show();
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
                .setPositiveButton(Tr.s("Buat & simpan"), (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) return;
                    exAddTo(YtLibrary.createPlaylist(this, n));
                })
                .setNegativeButton(Tr.s("Batal"), null)
                .show();
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < 26) {
            Toast.makeText(this, "Floating mini-player needs Android 8.0+", Toast.LENGTH_SHORT).show();
            return;
        }
        if (audioMode) {
            Toast.makeText(this, Tr.s("Matikan mode audio untuk memakai mini-player"), Toast.LENGTH_SHORT).show();
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
        // Leaving via home / recents / another app while a video is playing: follow it into PiP.
        // In audio mode there is nothing to show, so it just keeps playing in the background.
        if (Build.VERSION.SDK_INT >= 26 && !audioMode && player != null && player.isPlaying()) enterPip();
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
        // Backgrounded without PiP and not in audio mode (e.g. screen off): pause instead of
        // decoding video no one can see. Audio mode keeps playing on purpose.
        exSavePos();
        if (!inPip && !audioMode && player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subHandler.removeCallbacksAndMessages(null);
        exSavePos();
        exHandler.removeCallbacksAndMessages(null);
        if (pipReceiver != null) { try { unregisterReceiver(pipReceiver); } catch (Exception ignored) {} }
        if (playerFrame != null) playerFrame.removeCallbacks(hideHeaderRun);
        if (playerView != null) playerView.setPlayer(null);
        if (controller != null) {
            // Closing the screen in video mode ends playback; in audio mode the service keeps going.
            boolean stopAll = isFinishing() && !audioMode;
            if (playerListener != null) controller.removeListener(playerListener);
            if (stopAll) controller.pause();
            controller.release();
            controller = null;
            player = null;
            if (stopAll) stopService(new Intent(this, YoutubeAudioService.class));
        } else if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
    }
}
