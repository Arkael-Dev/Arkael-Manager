package com.arkael.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Suara dua arah untuk chat AI: mikrofon -> teks (STT) dan teks -> suara (TTS).
 * Semua di perangkat, tanpa layanan tambahan.
 */
final class AiVoice implements TextToSpeech.OnInitListener {

    interface MicCb {
        void onRms(float level);
        void onPartial(String text);
        void onFinal(String text);
        void onError(String msg);
        void onEnd();
    }

    private final Context app;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSay;
    private Runnable onSpeakDone;
    private SpeechRecognizer sr;
    private MicCb mic;

    AiVoice(Context c) { this.app = c.getApplicationContext(); }

    /* ---------------- Text to speech ---------------- */

    /** Siapkan TTS. Panggil sekali saat chat dibuka. */
    void initTts() {
        if (tts != null) return;
        String eng = AiStore.voiceEngine(app);
        if (eng == null || eng.isEmpty()) tts = new TextToSpeech(app, this);
        else tts = new TextToSpeech(app, this, eng);
    }

    @Override public void onInit(int status) {
        ttsReady = status == TextToSpeech.SUCCESS && tts != null;
        if (!ttsReady) { pendingSay = null; return; }
        try { tts.setSpeechRate(1.0f); tts.setPitch(1.0f); } catch (Exception ignore) {}
        Locale l = localeOf(AiStore.voiceLang(app));
        try {
            int r = tts.setLanguage(l);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());
            }
        } catch (Exception ignore) {}
        try {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { fireDone(); }
                @Override public void onError(String id) { fireDone(); }
            });
        } catch (Exception ignore) {}
        if (pendingSay != null) {
            String t = pendingSay;
            pendingSay = null;
            speak(t);
        }
    }

    interface EngineCb { void onList(List<String[]> items); }

    /** Daftar mesin TTS terpasang: [paket, label]. */
    void listEngines(final EngineCb cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                List<String[]> out = new ArrayList<>();
                try {
                    Intent i = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
                    List<ResolveInfo> list = Build.VERSION.SDK_INT >= 33
                            ? app.getPackageManager().queryIntentServices(i, PackageManager.ResolveInfoFlags.of(0))
                            : app.getPackageManager().queryIntentServices(i, 0);
                    if (list != null) {
                        for (ResolveInfo ri : list) {
                            String p = ri.serviceInfo.packageName;
                            String label = String.valueOf(ri.serviceInfo.loadLabel(app.getPackageManager()));
                            out.add(new String[]{p, label + " (" + p + ")"});
                        }
                    }
                    /* mesin utama TTS_AUDIO juga ikut ditawarkan */
                    String def = TextToSpeech.getDefaultEngine(app);
                    if (def != null) {
                        boolean have = false;
                        for (String[] e : out) if (e[0].equals(def)) { have = true; break; }
                        if (!have) out.add(0, new String[]{def, def});
                    }
                } catch (Exception ignore) {}
                cb.onList(out);
            }
        }, "arkael-tts-list").start();
    }

    boolean ttsOk() { return ttsReady; }

    /** Dipanggil setelah TTS selesai (atau gagal), di thread mana pun. */
    private void fireDone() {
        final Runnable r = onSpeakDone;
        onSpeakDone = null;
        if (r != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }

    /** Bacakan teks lalu jalankan after di main thread setelah suara habis. */
    void speakThen(String text, Runnable after) {
        onSpeakDone = after;
        speak(text);
        if (!ttsReady || !AiStore.voiceOn(app)) fireDone();
    }

    /** Bacakan teks. Tools/kode dibersihkan dulu supaya tidak dibacakan mentah. */
    void speak(String text) {
        if (!AiStore.voiceOn(app)) return;
        final String clean = speakable(text);
        if (clean.isEmpty()) return;
        initTts();
        if (!ttsReady) { pendingSay = clean; return; }
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "arkael-ai");
            } else {
                Bundle p = new Bundle();
                p.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "arkael-ai");
                tts.speak(clean, TextToSpeech.QUEUE_FLUSH, p);
            }
        } catch (Exception ignore) {}
    }

    void stopSpeak() { try { if (tts != null) tts.stop(); } catch (Exception ignore) {} }

    void shutdown() {
        stopMic();
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Exception ignore) {}
        tts = null;
        ttsReady = false;
        pendingSay = null;
    }

    /** Buang blok kode, URL, penanda markdown, dan emoji: TTS jadi tidak berisik. */
    static String speakable(String s) {
        if (s == null) return "";
        String t = s.replaceAll("(?s)```.*?(```|$)", " ");
        t = t.replaceAll("`[^`]*`", " ");
        t = t.replaceAll("https?://\\S+", " ");
        t = t.replaceAll("(?m)^#{1,6}\\s*", "");
        t = t.replaceAll("[*_>|]+", " ");
        t = t.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        // emoji & simbol di luar rentang teks biasa
        t = t.replaceAll("[\\p{So}\\p{Cn}\\p{Cs}]", " ");
        t = t.replaceAll("\\s+", " ").trim();
        if (t.length() > 4000) t = t.substring(0, 4000);
        return t;
    }

    static Locale localeOf(String tag) {
        if (tag == null || tag.trim().isEmpty()) return Locale.getDefault();
        String[] p = tag.trim().replace('_', '-').split("-");
        if (p.length >= 2) return new Locale(p[0], p[1]);
        return new Locale(p[0]);
    }

    /* ---------------- Speech to text ---------------- */

    private static boolean hasRecognizer(Context c) {
        if (!SpeechRecognizer.isRecognitionAvailable(c)) return false;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        List<ResolveInfo> r = c.getPackageManager()
                .queryIntentServices(i, Build.VERSION.SDK_INT >= 33
                        ? PackageManager.ResolveInfoFlags.of(0) : 0);
        return r != null && !r.isEmpty();
    }

    /** Mulai rekam suara. Hasil akhir dikirim ke cb.onFinal. */
    boolean startMic(final MicCb cb) {
        stopMic();
        if (!SpeechRecognizer.isRecognitionAvailable(app) || !hasRecognizer(app)) {
            cb.onError("Layanan pengenal suara tidak ada di perangkat."
                    + "\n\nPasang \"Speech Recognition & Synthesis\" (Google) lewat Play Store"
                    + " atau \"Speech Services by Google\", lalu coba lagi.");
            return false;
        }
        mic = cb;
        sr = SpeechRecognizer.createSpeechRecognizer(app);
        sr.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle b) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rms) { if (mic != null) mic.onRms(rms); }
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int err) {
                String m;
                switch (err) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        m = "Tidak ada suara yang terdengar. Coba lagi dan bicara lebih dekat.";
                        break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        m = "Izin mikrofon ditolak. Beri izin RECORD_AUDIO untuk app ini.";
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        m = "Pengenal suara butuh internet dan tidak bisa terhubung.";
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        m = "Pengenal suara sedang dipakai. Tunggu sebentar.";
                        break;
                    default:
                        m = "Pengenal suara gagal (kode " + err + ").";
                }
                if (mic != null) mic.onError(m);
                releaseSr();
            }
            @Override public void onResults(Bundle b) {
                ArrayList<String> r = b == null ? null
                        : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String best = (r == null || r.isEmpty()) ? "" : r.get(0);
                if (mic != null) {
                    if (best == null || best.trim().isEmpty()) mic.onError("Ucapan tidak terbaca. Coba lagi.");
                    else mic.onFinal(best.trim());
                    mic.onEnd();
                }
                releaseSr();
            }
            @Override public void onPartialResults(Bundle b) {
                ArrayList<String> r = b == null ? null
                        : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (mic != null && r != null && !r.isEmpty()) mic.onPartial(r.get(0));
            }
            @Override public void onEvent(int t, Bundle b) {}
        });

        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, AiStore.voiceLang(app));
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, AiStore.voiceLang(app));
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.getPackageName());
        try {
            sr.startListening(i);
            return true;
        } catch (Exception e) {
            cb.onError("Tidak bisa memulai mikrofon: " + e.getMessage());
            releaseSr();
            return false;
        }
    }

    void stopMic() {
        try { if (sr != null) { sr.stopListening(); sr.cancel(); sr.destroy(); } } catch (Exception ignore) {}
        sr = null;
        mic = null;
    }

    private void releaseSr() {
        try { if (sr != null) { sr.destroy(); } } catch (Exception ignore) {}
        sr = null;
        mic = null;
    }
}
