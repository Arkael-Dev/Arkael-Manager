package com.arkael.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Downloads a YouTube video (any resolution) or just its audio.
 *
 * High resolutions come as separate video-only and audio-only files, so the service downloads both
 * and joins them with MediaMuxer (no re-encoding, so it is fast and lossless). The result is saved
 * to Movies/Arkael (video) or Music/Arkael (audio). Progress shows in a notification; it keeps
 * running when the app is closed.
 */
public class YoutubeDownloadService extends Service {
    private static final String TAG = "ArkaelYT";
    static final String ACTION_CANCEL = "com.arkael.app.ytdl.CANCEL";
    private static final String CHANNEL = "ark_ytdl";
    private static final int NID = 4242;
    private static final int CHUNK = 4 * 1024 * 1024;

    /** kind: "mp4" / "webm" = video-only + audio joined, "direct" = one file with sound, "audio" = audio only. */
    static void start(Context c, String title, String kind, String videoUrl, String audioUrl, String ext, String label) {
        Intent i = new Intent(c, YoutubeDownloadService.class)
                .putExtra("title", title).putExtra("kind", kind).putExtra("video", videoUrl)
                .putExtra("audio", audioUrl).putExtra("ext", ext).putExtra("label", label);
        ContextCompat.startForegroundService(c, i);
    }

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final AtomicInteger pending = new AtomicInteger();
    private volatile boolean cancel;
    private long lastNotify;
    private int doneId = NID + 1;

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (ACTION_CANCEL.equals(intent.getAction())) { cancel = true; return START_NOT_STICKY; }
        final String title = nz(intent.getStringExtra("title")), kind = nz(intent.getStringExtra("kind")),
                video = intent.getStringExtra("video"), audio = intent.getStringExtra("audio"),
                ext = nz(intent.getStringExtra("ext")), label = nz(intent.getStringExtra("label"));
        Notification n = notif(title, "Menyiapkan…", 0, true);
        if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NID, n);
        pending.incrementAndGet();
        pool.execute(new Runnable() {
            @Override public void run() {
                try { job(title, kind, video, audio, ext, label); }
                finally { if (pending.decrementAndGet() == 0) { stopForeground(false); stopSelf(); } }
            }
        });
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    // ------------------------------------------------------------------ one download

    private void job(String title, String kind, String videoUrl, String audioUrl, String ext, String label) {
        cancel = false;
        File dir = new File(getCacheDir(), "ytdl");
        dir.mkdirs();
        File tv = new File(dir, "v.tmp"), ta = new File(dir, "a.tmp"), tm = new File(dir, "out." + ext);
        String shown = title.length() > 40 ? title.substring(0, 40) + "…" : title;
        try {
            boolean needV = !"audio".equals(kind), needA = "mp4".equals(kind) || "webm".equals(kind) || "audio".equals(kind);
            long total = 0;
            if ("direct".equals(kind)) total = clen(videoUrl);
            else {
                if (needV) total += clen(videoUrl);
                if (needA) total += clen(audioUrl);
            }
            long[] prog = {0, total};
            File result;
            if ("direct".equals(kind)) {
                fetch(videoUrl, tm, prog, shown);
                result = tm;
            } else if ("audio".equals(kind)) {
                fetch(audioUrl, tm, prog, shown);
                result = tm;
            } else {
                fetch(videoUrl, tv, prog, shown);
                fetch(audioUrl, ta, prog, shown);
                postProgress(shown, Tr.s("Menggabungkan video dan suara…"), 100, true, true);
                mux(tv, ta, tm, "webm".equals(kind));
                result = tm;
            }
            if (cancel) throw new IOException("cancelled");
            boolean video = !"audio".equals(kind);
            String mime = video ? ("webm".equals(ext) ? "video/webm" : "video/mp4") : ("m4a".equals(ext) ? "audio/mp4" : "audio/webm");
            String name = safe(title) + (label.isEmpty() ? "" : " [" + label + "]") + "." + ext;
            Uri saved = save(result, name, mime, video);
            report(shown, Tr.s("Selesai · tersimpan di ") + (video ? "Movies" : "Music") + "/Arkael", saved, mime, false);
        } catch (Exception e) {
            if (cancel || "cancelled".equals(e.getMessage())) {
                report(shown, "Download dibatalkan", null, null, true);
            } else {
                Log.e(TAG, "download failed", e);
                report(shown, Tr.s("Gagal: ") + e.getMessage(), null, null, true);
            }
        } finally {
            tv.delete(); ta.delete(); tm.delete();
        }
    }

    // ------------------------------------------------------------------ network

    private static String ua(String u) {
        if (u.contains("c=IOS")) return "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)";
        if (u.contains("c=ANDROID_VR")) return "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip";
        if (u.contains("c=ANDROID")) return "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip";
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";
    }

    private static long clen(String url) {
        try {
            String v = Uri.parse(url).getQueryParameter("clen");
            return v == null ? 0 : Long.parseLong(v);
        } catch (Exception e) { return 0; }
    }

    private HttpURLConnection open(String url, long from, long to, boolean rangeParam) throws IOException {
        String u = url;
        if (rangeParam) u = url.replaceAll("[&?]range=[^&]*", "") + "&range=" + from + "-" + to;
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", ua(url));
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        if (!rangeParam) c.setRequestProperty("Range", "bytes=" + from + "-" + to);
        return c;
    }

    /** Downloads url into dest in 4 MB pieces (YouTube's servers slow down or refuse one huge request). */
    private void fetch(String url, File dest, long[] prog, String shown) throws IOException {
        long len = clen(url), pos = 0;
        boolean useParam = false;
        FileOutputStream fo = new FileOutputStream(dest);
        try {
            byte[] buf = new byte[64 * 1024];
            while (len <= 0 || pos < len) {
                if (cancel) throw new IOException("cancelled");
                long end = pos + CHUNK - 1;
                HttpURLConnection c = open(url, pos, end, useParam);
                int code = c.getResponseCode();
                if (!useParam && (code == 403 || code == 400 || code == 416)) {   // some links want ?range= instead of a header
                    c.disconnect();
                    useParam = true;
                    c = open(url, pos, end, true);
                    code = c.getResponseCode();
                }
                if (code != 200 && code != 206) { c.disconnect(); throw new IOException("HTTP " + code); }
                if (len <= 0) {
                    String cr = c.getHeaderField("Content-Range");
                    if (cr != null && cr.contains("/")) {
                        try { len = Long.parseLong(cr.substring(cr.indexOf('/') + 1).trim()); } catch (Exception ignored) {}
                    }
                }
                InputStream in = c.getInputStream();
                long got = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancel) { in.close(); c.disconnect(); throw new IOException("cancelled"); }
                    fo.write(buf, 0, n);
                    got += n;
                    prog[0] += n;
                    postProgress(shown, null, prog[1] > 0 ? (int) (prog[0] * 100 / prog[1]) : 0, prog[1] <= 0, false);
                }
                in.close();
                c.disconnect();
                if (got == 0) throw new IOException(Tr.s("data kosong"));
                pos += got;
                if (len <= 0 && got < CHUNK) break;   // size unknown: a short piece means the end
            }
        } finally {
            fo.close();
        }
    }

    // ------------------------------------------------------------------ join + save

    private static int pickTrack(MediaExtractor ex, String prefix) {
        for (int i = 0; i < ex.getTrackCount(); i++) {
            String m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (m != null && m.startsWith(prefix)) return i;
        }
        return -1;
    }

    private void mux(File v, File a, File out, boolean webm) throws IOException {
        MediaExtractor ev = new MediaExtractor(), ea = new MediaExtractor();
        MediaMuxer mm = null;
        boolean started = false;
        try {
            ev.setDataSource(v.getAbsolutePath());
            ea.setDataSource(a.getAbsolutePath());
            int vi = pickTrack(ev, "video/"), ai = pickTrack(ea, "audio/");
            if (vi < 0 || ai < 0) throw new IOException(Tr.s("track video/suara tidak ditemukan"));
            ev.selectTrack(vi);
            ea.selectTrack(ai);
            mm = new MediaMuxer(out.getAbsolutePath(), webm ? MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM : MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int vt = mm.addTrack(ev.getTrackFormat(vi)), at = mm.addTrack(ea.getTrackFormat(ai));
            mm.start();
            started = true;
            ByteBuffer buf = ByteBuffer.allocate(4 * 1024 * 1024);
            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            boolean vDone = false, aDone = false;
            while (!vDone || !aDone) {
                if (cancel) throw new IOException("cancelled");
                long vts = vDone ? -1 : ev.getSampleTime();
                long ats = aDone ? -1 : ea.getSampleTime();
                if (!vDone && vts < 0) vDone = true;
                if (!aDone && ats < 0) aDone = true;
                if (vDone && aDone) break;
                boolean useV = !vDone && (aDone || vts <= ats);
                MediaExtractor src = useV ? ev : ea;
                buf.clear();
                int size = src.readSampleData(buf, 0);
                if (size < 0) { if (useV) vDone = true; else aDone = true; continue; }
                bi.set(0, size, src.getSampleTime(),
                        (src.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                mm.writeSampleData(useV ? vt : at, buf, bi);
                src.advance();
            }
        } finally {
            try { if (mm != null) { if (started) mm.stop(); mm.release(); } } catch (Exception ignored) {}
            ev.release();
            ea.release();
        }
    }

    private static String safe(String s) {
        String t = s.replaceAll("[\\\\/:*?\"<>|\\n\\r]", "_").trim();
        if (t.isEmpty()) t = "video";
        return t.length() > 90 ? t.substring(0, 90) : t;
    }

    private Uri save(File f, String name, String mime, boolean video) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_MUSIC) + "/Arkael");
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri col = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            Uri u = getContentResolver().insert(col, cv);
            if (u == null) throw new IOException(Tr.s("tidak bisa membuat file di penyimpanan"));
            OutputStream os = getContentResolver().openOutputStream(u);
            if (os == null) throw new IOException(Tr.s("tidak bisa menulis file"));
            copy(f, os);
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(u, done, null, null);
            return u;
        }
        File d = getExternalFilesDir(video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_MUSIC);
        if (d == null) throw new IOException(Tr.s("penyimpanan tidak tersedia"));
        File o = new File(d, name);
        copy(f, new FileOutputStream(o));
        MediaScannerConnection.scanFile(this, new String[]{o.getAbsolutePath()}, new String[]{mime}, null);
        return Uri.fromFile(o);
    }

    private static void copy(File f, OutputStream os) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            byte[] b = new byte[128 * 1024];
            int n;
            while ((n = in.read(b)) > 0) os.write(b, 0, n);
        } finally {
            in.close();
            os.close();
        }
    }

    // ------------------------------------------------------------------ notifications

    private Notification notif(String title, String text, int pct, boolean indeterminate) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Download YouTube", NotificationManager.IMPORTANCE_LOW));
        }
        Intent ci = new Intent(this, YoutubeDownloadService.class).setAction(ACTION_CANCEL);
        PendingIntent cancelPi = PendingIntent.getService(this, 1, ci, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text != null ? text : (indeterminate ? "Mengunduh…" : pct + "%"))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, pct, indeterminate)
                .addAction(0, Tr.s("Batal"), cancelPi);
        return b.build();
    }

    private void postProgress(String title, String text, int pct, boolean indeterminate, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastNotify < 500) return;
        lastNotify = now;
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NID, notif(title, text, pct, indeterminate));
    }

    private void report(String title, String text, Uri uri, String mime, boolean failed) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(failed ? android.R.drawable.stat_notify_error : android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true);
        if (uri != null && mime != null) {
            Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            b.setContentIntent(PendingIntent.getActivity(this, doneId, view, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        nm.notify(doneId++, b.build());
    }
}
