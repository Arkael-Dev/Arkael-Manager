package com.arkael.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Captures the phone's internal playback (game sound) with AudioPlaybackCapture and encodes it to AAC.
 * Lives only while a recording runs (its own foreground service of type mediaProjection, which Android
 * requires). The Recorder calls begin()/end() once per video segment.
 */
@android.annotation.TargetApi(29)
public class AudioCapService extends Service {
    private static final String TAG = "ArkaelAudio";
    private static final String CHANNEL = "arkael_audio";
    private static final int RATE = 48000;

    private static volatile CountDownLatch consent;
    private static volatile int cRes;
    private static volatile Intent cData;
    private static volatile AudioCapService inst;
    private static volatile CountDownLatch ready;

    private MediaProjection proj;
    private volatile boolean capturing;
    private Thread capThread;

    // ---------- called by Recorder (worker thread) ----------

    /** Grants permissions with root, asks consent, starts the service. True when audio capture is ready. */
    static boolean open(Context ctx) {
        if (Build.VERSION.SDK_INT < 29) return false;     // playback capture needs Android 10+
        try {
            String p = ctx.getPackageName();
            RootShell.run("pm grant " + p + " android.permission.RECORD_AUDIO 2>/dev/null; appops set " + p + " PROJECT_MEDIA allow 2>/dev/null");
            consent = new CountDownLatch(1);
            cRes = 0; cData = null;
            ctx.startActivity(new Intent(ctx, AudioConsentActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            if (!consent.await(30, TimeUnit.SECONDS) || cData == null || cRes != android.app.Activity.RESULT_OK) return false;
            ready = new CountDownLatch(1);
            Intent si = new Intent(ctx, AudioCapService.class).putExtra("res", cRes).putExtra("data", cData);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(si); else ctx.startService(si);
            return ready.await(8, TimeUnit.SECONDS) && inst != null && inst.proj != null;
        } catch (Exception e) {
            Log.w(TAG, "open failed", e);
            return false;
        }
    }

    static void close(Context ctx) {
        try { ctx.stopService(new Intent(ctx, AudioCapService.class)); } catch (Exception ignored) {}
    }

    static void consentResult(Context c, int res, Intent data) {
        cRes = res; cData = data;
        CountDownLatch l = consent;
        if (l != null) l.countDown();
    }

    private static Thread active;
    private static volatile boolean stopFlag;

    /** Starts capturing into an .m4a (AAC) file. Returns at once. */
    static boolean begin(final String outFile) {
        final AudioCapService s = inst;
        if (s == null || s.proj == null) return false;
        stopFlag = false;
        active = new Thread(new Runnable() { @Override public void run() { s.capture(outFile); } }, "ark-audio");
        active.start();
        return true;
    }

    /** Stops the capture and waits until the m4a is closed. */
    static void end() {
        stopFlag = true;
        Thread t = active;
        if (t != null) { try { t.join(4000); } catch (InterruptedException ignored) {} }
        active = null;
    }

    // ---------- service ----------

    @Override public int onStartCommand(Intent i, int flags, int id) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm != null)
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Recording audio", NotificationManager.IMPORTANCE_LOW));
            Notification n = new NotificationCompat.Builder(this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_cpu).setContentTitle("Arkael Manager")
                    .setContentText("Capturing game audio").setOngoing(true).build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(4711, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(4711, n);
            if (i != null && i.hasExtra("data")) {
                MediaProjectionManager m = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                proj = m.getMediaProjection(i.getIntExtra("res", 0), (Intent) i.getParcelableExtra("data"));
            }
            inst = this;
        } catch (Exception e) {
            Log.w(TAG, "service start failed", e);
        }
        CountDownLatch l = ready;
        if (l != null) l.countDown();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        stopFlag = true;
        try { if (proj != null) proj.stop(); } catch (Exception ignored) {}
        proj = null;
        inst = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    // ---------- capture + AAC encode ----------

    private void capture(String outFile) {
        AudioRecord rec = null;
        MediaCodec codec = null;
        MediaMuxer mux = null;
        boolean muxStarted = false;
        try {
            AudioPlaybackCaptureConfiguration cfg = new AudioPlaybackCaptureConfiguration.Builder(proj)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build();
            int minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            rec = new AudioRecord.Builder().setAudioFormat(fmt).setAudioPlaybackCaptureConfig(cfg)
                    .setBufferSizeInBytes(Math.max(minBuf * 4, 32768)).build();
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("AudioRecord not initialised");

            MediaFormat mf = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 2);
            mf.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mf.setInteger(MediaFormat.KEY_BIT_RATE, 160000);
            mf.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            codec.configure(mf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            mux = new MediaMuxer(outFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            byte[] pcm = new byte[8192];
            long samples = 0;
            int track = -1;
            boolean eos = false;
            rec.startRecording();
            while (!eos) {
                int n = 0;
                if (!stopFlag) n = rec.read(pcm, 0, pcm.length);
                int in = codec.dequeueInputBuffer(10000);
                if (in >= 0) {
                    ByteBuffer ib = codec.getInputBuffer(in);
                    ib.clear();
                    if (stopFlag || n < 0) {
                        codec.queueInputBuffer(in, 0, 0, samples * 1000000L / RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else if (n > 0) {
                        ib.put(pcm, 0, n);
                        codec.queueInputBuffer(in, 0, n, samples * 1000000L / RATE, 0);
                        samples += n / 4;                    // 16 bit stereo = 4 bytes per frame
                    } else {
                        codec.queueInputBuffer(in, 0, 0, samples * 1000000L / RATE, 0);
                    }
                }
                while (true) {
                    int o = codec.dequeueOutputBuffer(bi, 0);
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = mux.addTrack(codec.getOutputFormat());
                        mux.start();
                        muxStarted = true;
                    } else if (o >= 0) {
                        ByteBuffer ob = codec.getOutputBuffer(o);
                        if ((bi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bi.size = 0;
                        if (bi.size > 0 && muxStarted) {
                            ob.position(bi.offset);
                            ob.limit(bi.offset + bi.size);
                            mux.writeSampleData(track, ob, bi);
                        }
                        codec.releaseOutputBuffer(o, false);
                        if ((bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) { eos = true; break; }
                    } else break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "capture failed", e);
        } finally {
            try { if (rec != null) { rec.stop(); rec.release(); } } catch (Exception ignored) {}
            try { if (codec != null) { codec.stop(); codec.release(); } } catch (Exception ignored) {}
            try { if (mux != null) { if (muxStarted) mux.stop(); mux.release(); } } catch (Exception ignored) {}
        }
    }
}
