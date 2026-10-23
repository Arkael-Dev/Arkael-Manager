package com.arkael.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the ExoPlayer for the YouTube screen. Because the player lives here and not in the
 * Activity, audio mode keeps playing in the background, and Android shows the standard media
 * notification (play / pause) for it. The Activity talks to this player through a MediaController.
 */
public class YoutubeAudioService extends MediaSessionService {
    private MediaSession session;

    @Override
    public void onCreate() {
        super.onCreate();
        ExoPlayer player = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(), true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        // Tapping the notification re-opens the player screen and re-attaches to what is playing.
        Intent open = new Intent(this, YoutubePlayerActivity.class)
                .putExtra("resume", true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        session = new MediaSession.Builder(this, player)
                .setCallback(new Cb())
                .setSessionActivity(pi)
                .build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // App swiped away from recents: keep going only if something is actually playing.
        Player p = session == null ? null : session.getPlayer();
        if (p == null || !p.getPlayWhenReady() || p.getMediaItemCount() == 0) stopSelf();
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            session.getPlayer().release();
            session.release();
            session = null;
        }
        super.onDestroy();
    }

    /** A controller can't send a playback URI as-is; rebuild the item from its request metadata. */
    private static final class Cb implements MediaSession.Callback {
        @NonNull
        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(@NonNull MediaSession mediaSession,
                @NonNull MediaSession.ControllerInfo controller, @NonNull List<MediaItem> mediaItems) {
            List<MediaItem> out = new ArrayList<MediaItem>();
            for (MediaItem it : mediaItems) {
                Uri u = it.requestMetadata.mediaUri;
                if (u == null && it.localConfiguration != null) u = it.localConfiguration.uri;
                MediaItem.Builder nb = it.buildUpon();
                if (u != null) nb.setUri(u);
                Bundle ex = it.requestMetadata.extras;
                if (ex != null && ex.getStringArrayList("sub_url") != null) {
                    List<String> su = ex.getStringArrayList("sub_url"), sl = ex.getStringArrayList("sub_lang"),
                            sm = ex.getStringArrayList("sub_mime"), sb = ex.getStringArrayList("sub_label");
                    List<MediaItem.SubtitleConfiguration> subs = new ArrayList<MediaItem.SubtitleConfiguration>();
                    for (int i = 0; i < su.size(); i++) {
                        subs.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(su.get(i)))
                                .setMimeType(sm.get(i)).setLanguage(sl.get(i)).setLabel(sb.get(i)).setSelectionFlags(0).build());
                    }
                    nb.setSubtitleConfigurations(subs);
                }
                out.add(u == null ? it : nb.build());
            }
            return Futures.immediateFuture(out);
        }
    }
}
