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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.TextViewCompat;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Auto-translate layar game: screencap (root) -> OCR (semua aksara) -> deteksi bahasa -> terjemah ke Indonesia.
 * Hanya MEMBACA layar dan menggambar overlay; tidak menyentuh file/memori game.
 * Tap tombol = terjemahkan / hapus hasil. Tekan lama tombol = matikan fitur.
 */
public class TranslateService extends Service {
    private static final String TARGET = TranslateLanguage.INDONESIAN;

    private static class Item { Rect r; String t; }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Map<String, Translator> translators = new HashMap<>();
    private final List<TextRecognizer> recognizers = new ArrayList<>();
    private LanguageIdentifier langId;
    private WindowManager wm;
    private FrameLayout layer;
    private TextView btn;
    private boolean busy = false, showing = false;
    static volatile boolean running = false;
    private int shotW = 0;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        running = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        recognizers.add(TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS));
        recognizers.add(TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build()));
        recognizers.add(TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build()));
        recognizers.add(TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build()));
        langId = LanguageIdentification.getClient();
        addLayer();
        addButton();
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
        if (Build.VERSION.SDK_INT >= 28) lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        wm.addView(layer, lp);
    }

    private void addButton() {
        btn = new TextView(this);
        btn.setText("\u6587A");
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

    private void onTap() {
        if (busy) return;
        if (showing) { layer.removeAllViews(); showing = false; return; }
        busy = true;
        btn.setText("...");
        btn.setVisibility(View.INVISIBLE);   // sembunyikan agar tidak ikut ter-OCR
        ui.postDelayed(new Runnable() { @Override public void run() { exec.execute(new Runnable() { @Override public void run() { scan(); } }); } }, 250);
    }

    private void toast(final String s) {
        ui.post(new Runnable() { @Override public void run() { Toast.makeText(TranslateService.this, s, Toast.LENGTH_SHORT).show(); } });
    }

    private void finish(final List<Item> items) {
        ui.post(new Runnable() { @Override public void run() {
            btn.setVisibility(View.VISIBLE);
            btn.setText("\u6587A");
            busy = false;
            if (items == null) return;
            if (items.isEmpty()) { toast("Tidak ada teks terdeteksi"); return; }
            layer.removeAllViews();
            for (Item it : items) addBox(it);
            showing = true;
        } });
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

    /** Berjalan di thread background. */
    private void scan() {
        String path = new File(getCacheDir(), "tr_cap.png").getAbsolutePath();
        RootShell.run("rm -f " + path + "; screencap -p " + path + "; chmod 666 " + path);
        Bitmap bmp = BitmapFactory.decodeFile(path);
        if (bmp == null) { toast("Screencap gagal (cek akses root)"); finish(null); return; }
        shotW = bmp.getWidth();
        List<Item> out = new ArrayList<>();
        try {
            Text best = null;
            int bestLen = -1;
            for (int i = 0; i < recognizers.size(); i++) {
                Text t = Tasks.await(recognizers.get(i).process(InputImage.fromBitmap(bmp, 0)));
                int len = t.getText().length();
                if (len > bestLen) { best = t; bestLen = len; }
                if (i == 0 && len >= 30) break;   // dialog Latin/Inggris jelas: tidak perlu coba aksara lain
            }
            if (best != null) {
                for (Text.TextBlock b : best.getTextBlocks()) {
                    String src = b.getText().replace('\n', ' ').trim();
                    Rect r = b.getBoundingBox();
                    if (r == null || !hasLetter(src)) continue;
                    String tr = translate(src);
                    if (tr == null) continue;
                    Item it = new Item(); it.r = r; it.t = tr;
                    out.add(it);
                }
            }
        } catch (Exception e) {
            toast("Gagal: " + e.getMessage());
            bmp.recycle();
            new File(path).delete();
            finish(null);
            return;
        }
        bmp.recycle();
        new File(path).delete();
        finish(out);
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) return true;
        return false;
    }

    private String translate(String text) throws Exception {
        String tag = Tasks.await(langId.identifyLanguage(text));
        if (tag == null || tag.equals("und")) tag = "en";
        String src = TranslateLanguage.fromLanguageTag(tag);
        if (src == null && tag.contains("-")) src = TranslateLanguage.fromLanguageTag(tag.substring(0, tag.indexOf('-')));
        if (src == null || src.equals(TARGET)) return null;   // sudah Indonesia / tidak didukung
        Translator tr = translators.get(src);
        if (tr == null) {
            tr = Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(TARGET).build());
            translators.put(src, tr);
        }
        Tasks.await(tr.downloadModelIfNeeded(new DownloadConditions.Builder().build()));   // sekali saja, perlu internet
        return Tasks.await(tr.translate(text));
    }

    @Override public void onDestroy() {
        try { wm.removeView(layer); } catch (Exception ignored) {}
        try { wm.removeView(btn); } catch (Exception ignored) {}
        for (Translator t : translators.values()) t.close();
        for (TextRecognizer r : recognizers) r.close();
        if (langId != null) langId.close();
        exec.shutdownNow();
        running = false;
        super.onDestroy();
    }
}
