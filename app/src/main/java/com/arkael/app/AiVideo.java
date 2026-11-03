package com.arkael.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * make_video: merangkai gambar / kartu teks menjadi MP4 (H.264) dengan durasi bebas.
 * Juga bisa memotong video yang sudah ada tanpa re-encode (trim).
 * Semua di perangkat: MediaCodec + MediaMuxer, tanpa ffmpeg dan tanpa dependensi baru.
 */
final class AiVideo {

    static final int FPS = 24;
    static final int MAX_SECONDS = 120;
    private static final int BITRATE = 4000000;

    /** Satu bagian video: gambar (img) atau kartu teks (text). */
    static final class Clip {
        final File img;
        final String text;
        Clip(File f) { img = f; text = null; }
        Clip(String t) { img = null; text = t; }
    }

    private AiVideo() {}

    static int even(int v) { return (v & 1) == 0 ? v : v - 1; }

    /** "480p" / "720p" / "1080p" / "portrait" / "landscape" / "square" / "WxH" -> {w,h}. */
    static int[] parseSize(String s) {
        if (s == null) s = "";
        s = s.trim().toLowerCase();
        if (s.matches("\\d{3,4}x\\d{3,4}")) {
            int w = Integer.parseInt(s.substring(0, s.indexOf('x')));
            int h = Integer.parseInt(s.substring(s.indexOf('x') + 1));
            return new int[]{even(Math.max(16, w)), even(Math.max(16, h))};
        }
        switch (s) {
            case "480p":      return new int[]{even(854), 480};
            case "1080p":     return new int[]{even(1920), 1080};
            case "square":    return new int[]{720, 720};
            case "landscape": return new int[]{1280, 720};
            default:          return new int[]{720, 1280};   // portrait
        }
    }

    static int clampSeconds(int s) { return s <= 0 ? 10 : Math.min(s, MAX_SECONDS); }

    static File videoDir(Context ctx) {
        File d = new File(ctx.getExternalFilesDir(null), "Movies");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Status muxer, dipakai bersama oleh loop frame dan loop EOS. */
    private static final class Mux {
        MediaMuxer m = null;
        int track = -1;
        int lastId = -1;
        boolean started = false;

        /** Buat muxer sekali saja, saat encoder pertama kali melaporkan format outputnya. */
        void ensure(MediaCodec codec, File out) throws Exception {
            if (m != null) return;
            m = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            track = m.addTrack(codec.getOutputFormat());
            m.start();
            started = true;
        }

        /** Tulis satu buffer output encoder ke muxer. */
        void write(MediaCodec codec, MediaCodec.BufferInfo info) {
            if (!started || m == null || track < 0 || lastId < 0 || info.size <= 0) return;
            ByteBuffer ob = codec.getOutputBuffer(lastId);
            if (ob == null) return;
            ob.position(info.offset);
            ob.limit(info.offset + info.size);
            try { m.writeSampleData(track, ob, info); } catch (Exception ignore) {}
        }

        void release() {
            try { if (started) m.stop(); } catch (Exception ignore) {}
            try { if (m != null) m.release(); } catch (Exception ignore) {}
        }
    }

    /**
     * Rangkaikan clip jadi MP4 (H.264). Durasi total "seconds", dibagi rata ke semua clip.
     * Mengembalikan path file hasil.
     */
    static String build(Context ctx, List<Clip> clips, int seconds, int w, int h) throws Exception {
        if (clips == null || clips.isEmpty()) throw new Exception("tidak ada gambar atau teks untuk video");
        seconds = clampSeconds(seconds);
        w = even(w);
        h = even(h);
        if (w < 16 || h < 16) throw new Exception("ukuran video tidak valid: " + w + "x" + h);

        final File out = new File(videoDir(ctx), "vid_" + System.currentTimeMillis() + ".mp4");
        final String mime = "video/avc";
        final Mux mux = new Mux();
        MediaCodec codec = null;

        try {
            MediaFormat fmt = MediaFormat.createVideoFormat(mime, w, h);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
            fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            codec = openEncoder(mime, fmt);
            final boolean planar = fmt.getInteger(MediaFormat.KEY_COLOR_FORMAT)
                    == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
            codec.start();

            final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            final int totalFrames = seconds * FPS;
            final int perClip = Math.max(1, (int) Math.ceil((double) totalFrames / clips.size()));
            final int[] argb = new int[w * h];
            final int[] yuv = new int[w * h * 3 / 2];
            final byte[] yuvBytes = new byte[yuv.length];

            // Render tiap clip sekali saja; frame statis tak perlu digambar ulang 24x per detik.
            int cachedIdx = -1;
            for (int f = 0; f < totalFrames; f++) {
                int idx = Math.min(clips.size() - 1, f / perClip);
                if (idx != cachedIdx) {
                    Bitmap frame = frameFor(clips.get(idx), w, h);
                    frame.getPixels(argb, 0, w, 0, 0, w, h);
                    frame.recycle();
                    cachedIdx = idx;
                    if (planar) argbToYuv420Planar(argb, yuv, w, h);
                    else argbToYuv420SemiPlanar(argb, yuv, w, h);
                    for (int k = 0; k < yuv.length; k++) yuvBytes[k] = (byte) yuv[k];
                }

                int in = codec.dequeueInputBuffer(10000);
                while (in < 0) {
                    pump(codec, mux, info, out);          // encoder sibuk: tulis output yang siap
                    in = codec.dequeueInputBuffer(10000);
                }
                ByteBuffer ib = codec.getInputBuffer(in);
                if (ib == null) throw new Exception("input buffer encoder null");
                ib.clear();
                ib.put(yuvBytes);
                codec.queueInputBuffer(in, 0, yuvBytes.length, f * 1000000L / FPS, 0);

                pump(codec, mux, info, out);
            }

            // Kirim sinyal akhir, lalu kuras output sampai encoder melapor EOS.
            int in = codec.dequeueInputBuffer(100000);
            if (in < 0) throw new Exception("encoder tidak menerima sinyal akhir");
            codec.queueInputBuffer(in, 0, 0, totalFrames * 1000000L / FPS,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);

            final long guard = System.currentTimeMillis() + 30000;
            while (true) {
                int id = codec.dequeueOutputBuffer(info, 10000);
                if (id == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { mux.ensure(codec, out); continue; }
                if (id == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (System.currentTimeMillis() > guard) throw new Exception("encoder tidak selesai");
                    continue;
                }
                if (id < 0) continue;
                mux.lastId = id;
                mux.write(codec, info);
                boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                codec.releaseOutputBuffer(id, false);
                if (eos) break;
            }
        } finally {
            try { if (codec != null) codec.stop(); } catch (Exception ignore) {}
            try { if (codec != null) codec.release(); } catch (Exception ignore) {}
            mux.release();
        }

        if (!out.isFile() || out.length() < 1024) throw new Exception("video gagal ditulis");
        return out.getAbsolutePath();
    }

    /** Ambil dan tulis semua output yang sudah siap; format baru memicu pembuatan muxer. */
    private static void pump(MediaCodec codec, Mux mux, MediaCodec.BufferInfo info, File out)
            throws Exception {
        while (true) {
            int id = codec.dequeueOutputBuffer(info, 0);
            if (id == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { mux.ensure(codec, out); continue; }
            if (id == MediaCodec.INFO_TRY_AGAIN_LATER) return;
            if (id < 0) continue;
            mux.lastId = id;
            mux.write(codec, info);
            codec.releaseOutputBuffer(id, false);
        }
    }

    /** Render satu clip ke bitmap ukuran video: gambar di-crop tengah, teks digambar di kartu gradasi. */
    static Bitmap frameFor(Clip c, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);

        if (c.img != null && c.img.isFile()) {
            Bitmap src = decode(c.img, w, h);
            if (src != null) {
                // center-crop: penuhi bingkai tanpa gepeng
                float s = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
                int dw = Math.round(src.getWidth() * s);
                int dh = Math.round(src.getHeight() * s);
                Rect dst = new Rect((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
                cv.drawBitmap(src, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
                src.recycle();
                return out;
            }
        }

        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, w, h,
                Color.parseColor("#0A0D12"), Color.parseColor("#12304A"), Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, bg);

        String text = (c.text == null || c.text.trim().isEmpty()) ? AiPersona.NAME : c.text.trim();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextSize(Math.max(22f, w / 18f));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        String[] lines = wrap(text, p, w * 0.86f);
        float lh = p.getTextSize() * 1.35f;
        float y = h / 2f - (lines.length - 1) * lh / 2f;
        for (String line : lines) {
            cv.drawText(line, w / 2f, y, p);
            y += lh;
        }

        Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
        mark.setColor(Color.parseColor("#00E5FF"));
        mark.setTextSize(p.getTextSize() * 0.42f);
        mark.setTextAlign(Paint.Align.CENTER);
        cv.drawText(AiPersona.NAME, w / 2f, h - mark.getTextSize(), mark);
        return out;
    }

    private static String[] wrap(String text, Paint p, float maxW) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String test = line.length() == 0 ? word : line + " " + word;
                if (line.length() > 0 && p.measureText(test) > maxW) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(test);
                }
            }
            if (line.length() > 0) out.add(line.toString());
        }
        if (out.isEmpty()) out.add("");
        return out.toArray(new String[0]);
    }

    /** Decode hemat memori: inSampleSize dinaikkan sampai sisi terpanjang <= 2x target. */
    static Bitmap decode(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int want = Math.max(w, h) * 2;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > want) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
    }

    /**
     * Tidak semua encoder H.264 menerima COLOR_FormatYUV420Planar; banyak yang hanya
     * SemiPlanar (NV12). Coba planar dulu, lalu semiplanar, lalu flexible.
     * Hasil akhir ditentukan oleh configure() yang berhasil, bukan tebakan.
     */
    static MediaCodec openEncoder(String mime, MediaFormat fmt) throws Exception {
        int[] tries = {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
        };
        Exception last = null;
        for (int cf : tries) {
            MediaCodec c = null;
            try {
                fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, cf);
                c = MediaCodec.createEncoderByType(mime);
                c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                return c;
            } catch (Exception e) {
                last = e;
                try { if (c != null) c.release(); } catch (Exception ignore) {}
            }
        }
        throw new Exception("encoder H.264 tidak menerima format warna umum: "
                + (last == null ? "tidak diketahui" : last.getMessage()));
    }

    /** NV12: Y penuh, lalu U/V berselang-seling. */
    private static void argbToYuv420SemiPlanar(int[] argb, int[] yuv, int w, int h) {
        final int frames = w * h;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int c = argb[j * w + i];
                int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
                yuv[j * w + i] = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
            }
        }
        int uv = frames;
        for (int j = 0; j < h; j += 2) {
            for (int i = 0; i < w; i += 2) {
                int c = argb[j * w + i];
                int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
                yuv[uv++] = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                yuv[uv++] = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
            }
        }
    }

    private static void argbToYuv420Planar(int[] argb, int[] yuv, int w, int h) {
        final int frames = w * h;
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int c = argb[j * w + i];
                int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
                yuv[j * w + i] = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
            }
        }
        final int uOff = frames, vOff = frames + frames / 4, halfW = w / 2;
        for (int j = 0; j < h; j += 2) {
            for (int i = 0; i < w; i += 2) {
                int c = argb[j * w + i];
                int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
                yuv[uOff + (j / 2) * halfW + i / 2] = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                yuv[vOff + (j / 2) * halfW + i / 2] = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
            }
        }
    }

    /** Potong video tanpa re-encode. seconds = durasi hasil (detik). */
    static String trim(Context ctx, File in, int seconds) throws Exception {
        seconds = clampSeconds(seconds);
        MediaExtractor ex = new MediaExtractor();
        MediaMuxer mux = null;
        boolean started = false;
        try {
            ex.setDataSource(in.getAbsolutePath());
            int vt = -1;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                String mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) { vt = i; break; }
            }
            if (vt < 0) throw new Exception("file tidak punya track video");
            ex.selectTrack(vt);

            File out = new File(videoDir(ctx), "trim_" + System.currentTimeMillis() + ".mp4");
            mux = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int track = mux.addTrack(ex.getTrackFormat(vt));
            mux.start();
            started = true;

            long limit = seconds * 1000000L;
            // Buffer harus cukup untuk satu sample; ambil dari format, cadangan 4 MB.
            int cap = 4 << 20;
            try {
                int mis = ex.getTrackFormat(vt).getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                if (mis > cap) cap = mis;
            } catch (Exception ignore) {}
            ByteBuffer buf = ByteBuffer.allocate(cap);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                int size = ex.readSampleData(buf, 0);
                if (size < 0) break;
                long t = ex.getSampleTime();
                if (t > limit) break;
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = t;
                info.flags = ex.getSampleFlags();
                mux.writeSampleData(track, buf, info);
                ex.advance();
            }
            mux.stop();
            started = false;
            if (!out.isFile() || out.length() < 1024) throw new Exception("hasil potong gagal ditulis");
            return out.getAbsolutePath();
        } finally {
            try { if (mux != null && started) mux.stop(); } catch (Exception ignore) {}
            try { if (mux != null) mux.release(); } catch (Exception ignore) {}
            ex.release();
        }
    }
}
