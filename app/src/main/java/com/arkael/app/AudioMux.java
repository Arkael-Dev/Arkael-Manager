package com.arkael.app;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;

/** Puts the AAC track of an .m4a next to the video track of a screenrecord .mp4 (no re-encoding). */
final class AudioMux {
    private static final String TAG = "ArkaelMux";
    private AudioMux() {}

    /** True when out was written with video and audio. */
    static boolean join(String video, String audio, String out) {
        MediaExtractor v = new MediaExtractor(), a = new MediaExtractor();
        MediaMuxer m = null;
        boolean started = false;
        try {
            if (!new File(audio).exists() || new File(audio).length() < 2048) return false;
            v.setDataSource(video);
            a.setDataSource(audio);
            int vt = track(v, "video/"), at = track(a, "audio/");
            if (vt < 0 || at < 0) return false;
            v.selectTrack(vt);
            a.selectTrack(at);
            MediaFormat vf = v.getTrackFormat(vt), af = a.getTrackFormat(at);
            long vDur = vf.containsKey(MediaFormat.KEY_DURATION) ? vf.getLong(MediaFormat.KEY_DURATION) : Long.MAX_VALUE;
            m = new MediaMuxer(out, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int mv = m.addTrack(vf), ma = m.addTrack(af);
            m.start();
            started = true;
            copy(v, m, mv, Long.MAX_VALUE);
            copy(a, m, ma, vDur);                    // audio never runs past the video
            return true;
        } catch (Exception e) {
            Log.w(TAG, "mux failed", e);
            return false;
        } finally {
            try { if (m != null) { if (started) m.stop(); m.release(); } } catch (Exception ignored) {}
            v.release();
            a.release();
        }
    }

    private static int track(MediaExtractor x, String prefix) {
        for (int i = 0; i < x.getTrackCount(); i++) {
            String mime = x.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(prefix)) return i;
        }
        return -1;
    }

    private static void copy(MediaExtractor x, MediaMuxer m, int t, long maxUs) {
        ByteBuffer buf = ByteBuffer.allocate(4 * 1024 * 1024);
        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
        while (true) {
            buf.clear();
            int n = x.readSampleData(buf, 0);
            if (n < 0) break;
            long ts = x.getSampleTime();
            if (ts > maxUs) break;
            bi.set(0, n, ts, (x.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
            m.writeSampleData(t, buf, bi);
            x.advance();
        }
    }
}
