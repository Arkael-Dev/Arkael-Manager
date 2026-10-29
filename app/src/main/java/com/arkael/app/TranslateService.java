package com.arkael.app;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.TextViewCompat;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Auto-translate layar game, MANUAL satu ketukan (tidak ada scan otomatis):
 * tap tombol -> screencap (root) -> OCR -> deteksi bahasa -> terjemah ke Indonesia -> kotak terjemahan
 * tampil di atas teks asli dan TETAP ada sampai kamu tap tombol lagi (tombol jadi tanda silang).
 * Tekan lama tombol = matikan fitur. Hanya membaca layar; tidak menyentuh file/memori game.
 * Log untuk diagnosa: logcat -s ArkTr
 */
public class TranslateService extends Service {
    static volatile boolean running = false;

    private static final String TAG = "ArkTr";
    private static final String TARGET = TranslateLanguage.INDONESIAN;
    private static final String LABEL = "\u6587A";
    private static final String CLOSE = "\u2715";
    private static final int MAX_BLOCKS = 30;

    private static class Item { Rect r; String t; }
    private static class Pending { Rect r; String src; Task<String> task; }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Map<String, Translator> translators = new ConcurrentHashMap<>();
    private final Set<String> ready = ConcurrentHashMap.newKeySet();
    private final List<TextRecognizer> recognizers = new ArrayList<>();
    private LanguageIdentifier langId;
    private WindowManager wm;
    private FrameLayout layer;
    private TextView btn;
    private boolean busy = false, showing = false;   // hanya diakses dari thread UI
    private volatile int shotW = 0;

    /** Pengaman: kalau proses macet, tombol dikembalikan supaya tidak terkunci. */
    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            busy = false;
            if (btn != null) btn.setText(showing ? CLOSE : LABEL);
            Toast.makeText(TranslateService.this, "Terlalu lama, coba lagi", Toast.LENGTH_SHORT).show();
        }
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        addRecognizer("latin", () -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS));
        addRecognizer("chinese", () -> TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build()));
        addRecognizer("japanese", () -> TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build()));
        addRecognizer("korean", () -> TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build()));
        try { langId = LanguageIdentification.getClient(); } catch (Throwable t) { Log.e(TAG, "langId", t); }
        addLayer();
        addButton();
        // Unduh model Inggris -> Indonesia sekarang, supaya tap pertama di game langsung cepat.
        new Thread(() -> prefetch("en"), "ArkTrPrefetch").start();
    }

    private void addRecognizer(String name, Callable<TextRecognizer> c) {
        try { recognizers.add(c.call()); } catch (Throwable t) { Log.e(TAG, "recognizer " + name, t); }
    }

    private int type() {
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    private void addLayer() {
        layer = new FrameLayout(this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.alpha = 0.8f;   // batas Android 12+ untuk overlay yang tembus sentuh
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        wm.addView(layer, lp);
    }

    private void addButton() {
        btn = new TextView(this);
        btn.setText(LABEL);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xCC1E88E5);
        btn.setBackground(bg);
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(dp(46), dp(46), type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(8); lp.y = dp(120);
        btn.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sx, sy; long t0; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX(); dy = e.getRawY(); sx = lp.x; sy = lp.y; t0 = System.currentTimeMillis(); moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - dx) > dp(8) || Math.abs(e.getRawY() - dy) > dp(8)) moved = true;
                        if (moved) {
                            lp.x = sx + (int) (e.getRawX() - dx);
                            lp.y = sy + (int) (e.getRawY() - dy);
                            wm.updateViewLayout(btn, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) return true;
                        if (System.currentTimeMillis() - t0 > 800) stopSelf(); else onTap();
                        return true;
                }
                return false;
            }
        });
        wm.addView(btn, lp);
    }

    /** Tap: kalau hasil sedang tampil -> tutup. Kalau tidak -> terjemahkan layar sekarang. */
    private void onTap() {
        if (busy) return;
        if (showing) {
            layer.removeAllViews();
            showing = false;
            btn.setText(LABEL);
            return;
        }
        busy = true;
        btn.setText("...");
        // Tombol tidak disembunyikan; posisinya dicatat supaya hurufnya tidak ikut diterjemahkan.
        int[] loc = new int[2];
        btn.getLocationOnScreen(loc);
        final Rect btnRect = new Rect(loc[0] - dp(8), loc[1] - dp(8), loc[0] + btn.getWidth() + dp(8), loc[1] + btn.getHeight() + dp(8));
        ui.postDelayed(watchdog, 120000);
        try {
            exec.execute(() -> scan(btnRect));
        } catch (Throwable t) {
            Log.e(TAG, "exec", t);
            finishScan(null);
        }
    }

    private void toast(final String s) {
        ui.post(() -> Toast.makeText(TranslateService.this, s, Toast.LENGTH_LONG).show());
    }

    private void finishScan(final List<Item> items) {
        ui.post(() -> {
            ui.removeCallbacks(watchdog);
            busy = false;
            if (items == null || items.isEmpty()) {
                btn.setText(showing ? CLOSE : LABEL);
                return;
            }
            layer.removeAllViews();
            for (Item it : items) {
                try { addBox(it); } catch (Throwable t) { Log.e(TAG, "addBox", t); }
            }
            showing = true;
            btn.setText(CLOSE);
        });
    }

    private void addBox(Item it) {
        int w = Math.max(it.r.width(), dp(110));
        int h = Math.max(it.r.height(), dp(26));
        int left = Math.max(0, Math.min(it.r.left, Math.max(0, shotW - w)));
        TextView tv = new TextView(this);
        tv.setText(it.t);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(0xE6000000);
        tv.setPadding(dp(3), dp(1), dp(3), dp(1));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(tv, 8, 18, 1, TypedValue.COMPLEX_UNIT_SP);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.START);
        p.leftMargin = left;
        p.topMargin = Math.max(0, it.r.top);
        layer.addView(tv, p);
    }

    /** Berjalan di thread background. Apa pun yang terjadi, tombol dikembalikan di blok finally. */
    private void scan(final Rect btnRect) {
        final File shot = new File(getCacheDir(), "tr_cap.png");
        final String path = shot.getAbsolutePath();
        List<Item> out = null;
        try {
            int uid = android.os.Process.myUid();
            RootShell.run("rm -f " + path + "; screencap -p " + path + "; chmod 666 " + path
                    + "; chown " + uid + ":" + uid + " " + path + "; restorecon " + path + " 2>/dev/null");
            Bitmap bmp = BitmapFactory.decodeFile(path);
            if (bmp == null) {
                String info = RootShell.run("ls -l " + path + " 2>&1").trim();
                Log.e(TAG, "decodeFile null: " + info);
                toast("Screencap gagal. " + info);
                return;
            }
            shotW = bmp.getWidth();
            Log.i(TAG, "screenshot " + bmp.getWidth() + "x" + bmp.getHeight());
            if (recognizers.isEmpty()) { toast("OCR tidak tersedia (lihat logcat ArkTr)"); return; }

            // OCR semua aksara PARALEL, bukan satu per satu.
            InputImage img = InputImage.fromBitmap(bmp, 0);
            List<Task<Text>> ocr = new ArrayList<>();
            for (TextRecognizer r : recognizers) ocr.add(r.process(img));
            Text best = null;
            int bestLen = -1;
            for (int i = 0; i < ocr.size(); i++) {
                try {
                    Text t = Tasks.await(ocr.get(i), 20, TimeUnit.SECONDS);
                    int len = t.getText().length();
                    Log.i(TAG, "ocr#" + i + " len=" + len);
                    if (len > bestLen) { best = t; bestLen = len; }
                    if (i == 0 && len >= 30) break;   // dialog Latin/Inggris jelas: tidak perlu tunggu aksara lain
                } catch (Exception e) {
                    Log.e(TAG, "ocr#" + i, e);
                }
            }

            // Deteksi bahasa + mulai terjemah semua blok, lalu kumpulkan hasilnya.
            List<Pending> pend = new ArrayList<>();
            int valid = 0;
            String lastErr = null;
            if (best != null) {
                for (Text.TextBlock b : best.getTextBlocks()) {
                    if (pend.size() >= MAX_BLOCKS) break;
                    String src = b.getText().replace('\n', ' ').trim();
                    Rect r = b.getBoundingBox();
                    if (r == null || !hasLetter(src) || Rect.intersects(r, btnRect)) continue;
                    valid++;
                    try {
                        Translator tr = translatorFor(src);
                        if (tr == null) continue;
                        Pending p = new Pending();
                        p.r = r; p.src = src; p.task = tr.translate(src);
                        pend.add(p);
                    } catch (Exception e) {
                        Log.e(TAG, "prepare", e);
                        lastErr = e.getClass().getSimpleName() + ": " + e.getMessage();
                    }
                }
            }
            out = new ArrayList<>();
            for (Pending p : pend) {
                try {
                    String res = Tasks.await(p.task, 20, TimeUnit.SECONDS);
                    if (res != null && !res.trim().isEmpty() && !res.trim().equalsIgnoreCase(p.src)) {
                        Item it = new Item();
                        it.r = p.r; it.t = res;
                        out.add(it);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "translate", e);
                    lastErr = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
            }
            if (out.isEmpty()) {
                if (lastErr != null) toast("Terjemah gagal: " + lastErr);
                else if (valid == 0) toast("OCR tidak menemukan teks di layar");
                else toast("Ada " + valid + " teks, tapi sudah berbahasa Indonesia / bahasa tidak didukung");
            }
        } catch (Throwable t) {
            Log.e(TAG, "scan", t);
            toast("Gagal: " + t.getClass().getSimpleName() + " " + t.getMessage());
            out = null;
        } finally {
            shot.delete();
            finishScan(out);
        }
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) return true;
        return false;
    }

    /** Terjemahan siap pakai untuk bahasa teks ini, atau null kalau tidak perlu (sudah Indonesia / tidak didukung). */
    private Translator translatorFor(String text) throws Exception {
        String tag = "en";
        if (langId != null) {
            tag = Tasks.await(langId.identifyLanguage(text), 10, TimeUnit.SECONDS);
            if (tag == null || "und".equals(tag)) tag = "en";
        }
        String src = TranslateLanguage.fromLanguageTag(tag);
        if (src == null && tag.contains("-")) src = TranslateLanguage.fromLanguageTag(tag.substring(0, tag.indexOf('-')));
        if (src == null || src.equals(TARGET)) return null;
        Translator tr = getTranslator(src);
        ensureModel(tr, src);
        return tr;
    }

    private synchronized Translator getTranslator(String src) {
        Translator tr = translators.get(src);
        if (tr == null) {
            tr = Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(TARGET).build());
            translators.put(src, tr);
        }
        return tr;
    }

    private void ensureModel(Translator tr, String src) throws Exception {
        if (ready.contains(src)) return;
        toast("Menyiapkan bahasa " + src + " (sekali saja, butuh internet)...");
        Tasks.await(tr.downloadModelIfNeeded(new DownloadConditions.Builder().build()), 240, TimeUnit.SECONDS);
        ready.add(src);
    }

    private void prefetch(String src) {
        try {
            ensureModel(getTranslator(src), src);
            Log.i(TAG, "model " + src + " siap");
        } catch (Throwable t) {
            Log.e(TAG, "prefetch " + src, t);
        }
    }

    @Override public void onDestroy() {
        running = false;
        ui.removeCallbacks(watchdog);
        try { wm.removeView(layer); } catch (Exception ignored) {}
        try { wm.removeView(btn); } catch (Exception ignored) {}
        for (Translator t : translators.values()) { try { t.close(); } catch (Exception ignored) {} }
        for (TextRecognizer r : recognizers) { try { r.close(); } catch (Exception ignored) {} }
        if (langId != null) { try { langId.close(); } catch (Exception ignored) {} }
        exec.shutdownNow();
        super.onDestroy();
    }
}
