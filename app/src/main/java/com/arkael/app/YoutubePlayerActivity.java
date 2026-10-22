package com.arkael.app;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.Comparator;
import java.util.List;

/**
 * Plays one YouTube video. The stream URL is fetched directly through NewPipeExtractor (the same
 * technique NewPipe/LibreTube use), so playback goes straight to Google's CDN with no ad break
 * ever inserted — there's no ad slot because there's no official player in the loop.
 *
 * Supports Android's native Picture-in-Picture: leaving the app (home button or switching apps)
 * while a video is playing shrinks it into a small movable window that keeps playing on top of
 * whatever else you open; tapping the shrink button does the same on demand.
 */
public class YoutubePlayerActivity extends AppCompatActivity {
    private static final String ACTION_MEDIA_CONTROL = "com.arkael.app.ytpip.control";
    private static final String EXTRA_CONTROL = "control";
    private static final int CONTROL_PLAY = 1, CONTROL_PAUSE = 2;

    private PlayerView playerView;
    private ExoPlayer player;
    private FrameLayout chromeOverlay;   // header + status, hidden while in PiP
    private TextView title, status;
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
        getWindow().setNavigationBarColor(Color.BLACK);

        videoUrl = getIntent().getStringExtra("url");
        videoTitle = getIntent().getStringExtra("title");
        if (videoUrl == null) { finish(); return; }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        root.addView(playerView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        chromeOverlay = new FrameLayout(this);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(28), dp(12), dp(10));
        header.setBackgroundColor(Color.parseColor("#B0000000"));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextColor(Color.WHITE);
        back.setTextSize(20);
        back.setPadding(dp(8), dp(4), dp(16), dp(4));
        back.setOnClickListener(v -> finish());
        header.addView(back);

        title = new TextView(this);
        title.setText(videoTitle == null ? "" : videoTitle);
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView shrink = new TextView(this);
        shrink.setText("⤓");
        shrink.setTextColor(Color.WHITE);
        shrink.setTextSize(20);
        shrink.setPadding(dp(16), dp(4), dp(8), dp(4));
        shrink.setOnClickListener(v -> enterPip());
        header.addView(shrink);

        chromeOverlay.addView(header, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(chromeOverlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        spinner = new ProgressBar(this);
        FrameLayout.LayoutParams spl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spl.gravity = Gravity.CENTER;
        root.addView(spinner, spl);

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#FF6B6B"));
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(24), 0, dp(24), 0);
        FrameLayout.LayoutParams stl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stl.gravity = Gravity.CENTER;
        root.addView(status, stl);

        setContentView(root);
        load();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void load() {
        new Thread(() -> {
            String err = null;
            MediaItem item = null;
            try {
                StreamingService yt = NewPipe.getService("YouTube");
                StreamInfo info = StreamInfo.getInfo(yt, videoUrl);
                item = pickStream(info);
                if (item == null) err = "No playable stream found for this video";
            } catch (Exception e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final MediaItem fi = item;
            final String ferr = err;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                spinner.setVisibility(View.GONE);
                if (ferr != null) { status.setText(ferr); return; }
                startPlayback(fi);
            });
        }).start();
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

    private void startPlayback(MediaItem item) {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setMediaItem(item);
        player.setPlayWhenReady(true);
        player.prepare();
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(@NonNull PlaybackException error) {
                status.setText("Playback failed: " + error.getMessage());
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
        chromeOverlay.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        playerView.setUseController(!isInPictureInPictureMode);
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
        if (player != null) { player.release(); player = null; }
    }
}
