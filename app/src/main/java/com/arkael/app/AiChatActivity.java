package com.arkael.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

/** Chat AI di dalam app. Key diisi manual lewat tombol gear. Opsional mode agent (butuh root). */
public class AiChatActivity extends AppCompatActivity {

    private static final String BG = "#0A0D12";
    private static final String CARD = "#14181F";
    private static final String USER = "#1C2C3E";
    private static final String TXT = "#FFFFFF";
    private static final String SUB = "#8B95A3";
    private static final String ACCENT = "#00E5FF";
    private static final int MAX_TOOL_LOOPS = 6;
    private static final int MAX_TOOL_OUT = 4000;

    private LinearLayout list;
    private ScrollView scroll;
    private EditText input;
    private TextView status;
    private final JSONArray history = new JSONArray();
    private boolean busy = false;
    private int toolLoops = 0;
    /** Lampiran pesan berikutnya; dikosongkan setelah terkirim. */
    private final List<Attachment> pending = new ArrayList<>();

    /* --- voice chat --- */
    private AiVoice voice;
    private boolean micOn = false;
    private boolean autoSendAfterStt = false;
    private Button micBtn;
    private Button btnSpk;   /* tombol bisukan suara jawaban, terpisah dari mode suara */
    private static final int REQ_MIC_AI = 7102;

    /** File yang dilampirkan plus tugasnya: kirim ke AI, langsung dibuatkan gambar, atau video. */
    private static final class Attachment {
        static final String TO_AI = "ai";
        static final String IMAGE = "image";
        static final String VIDEO = "video";

        final File file;
        String task = TO_AI;

        Attachment(File f) { this.file = f; }

        boolean isImage() { return AiFiles.isImage(file); }

        String taskLabel() {
            if (IMAGE.equals(task)) return "jadikan gambar";
            if (VIDEO.equals(task)) return "jadikan video";
            return isImage() ? "lihat gambar" : "baca file";
        }

        /** Saran prompt untuk AI, melihat isi file lebih dulu. */
        String promptFor() {
            String n = file.getName();
            if (IMAGE.equals(task)) return "Buat ulang gambar ini jadi versi baru: " + n;
            if (VIDEO.equals(task)) return "Buat video dari gambar " + n;
            String body = AiFiles.isText(file) ? AiFiles.textOf(file, 3000) : null;
            if (body == null || body.trim().isEmpty()) return "Jelaskan isi file " + n;
            return "Jelaskan isi file ini:\n(" + n + ")\n\n" + body;
        }
    }
    private LinearLayout attachBar;
    /** Maksud lampiran yang sedang dipilih (lihat Attachment). */
    private String pendingTask = Attachment.TO_AI;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor(BG));
        getWindow().setNavigationBarColor(Color.parseColor(BG));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor(BG));

        root.addView(header());

        scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(10), dp(14), dp(10));
        scroll.addView(list);
        root.addView(scroll);

        status = new TextView(this);
        status.setTextColor(Color.parseColor(SUB));
        status.setTextSize(11);
        status.setPadding(dp(16), 0, dp(16), dp(4));
        root.addView(status);

        root.addView(composer());
        setContentView(root);

        voice = new AiVoice(this);
        if (AiStore.voiceOn(this)) voice.initTts();

        if (!AiStore.ready(this)) {
            bubble("AI", "API key belum diisi.\n\nTekan tombol gear di kanan atas, pilih AI-nya, tempel API key, lalu Simpan.\n\nProvider awal: DeepSeek", false);
            openSettings();
        } else {
            String prov = AiStore.provider(this);
            bubble("AI", "Siap.\n" + AiStore.label(prov) + " \u00B7 " + AiStore.model(this)
                    + "\nkey " + (AiStore.needsKey(prov) ? AiStore.keyMask(AiStore.key(this)) : "tidak perlu")
                    + "\nMode agent: " + (AiStore.agent(this) ? "AKTIF (perintah dijalankan sebagai root)" : "nonaktif"), false);
        }
        refreshStatus();
    }

    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(14), dp(12), dp(14), dp(10));

        TextView back = new TextView(this);
        back.setText("\u2190");
        back.setTextColor(Color.parseColor(TXT));
        back.setTextSize(24);
        back.setPadding(0, 0, dp(14), 0);
        back.setOnClickListener(v -> finish());
        h.addView(back);

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView title = new TextView(this);
        title.setText("AI AGENT");
        title.setTextColor(Color.parseColor(TXT));
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Chat dengan model pilihanmu");
        sub.setTextColor(Color.parseColor(SUB));
        sub.setTextSize(11);
        t.addView(sub);
        h.addView(t);

        TextView gear = new TextView(this);
        gear.setText("\u2699");
        gear.setTextColor(Color.parseColor(ACCENT));
        gear.setTextSize(22);
        gear.setPadding(dp(10), 0, dp(6), 0);
        gear.setOnClickListener(v -> openSettings());
        h.addView(gear);

        TextView clr = new TextView(this);
        clr.setText("\u2715");
        clr.setTextColor(Color.parseColor(SUB));
        clr.setTextSize(18);
        clr.setPadding(dp(8), 0, 0, 0);
        clr.setOnClickListener(v -> {
            for (int i = history.length() - 1; i >= 0; i--) history.remove(i);
            list.removeAllViews();
            bubble("AI", "Riwayat dihapus.", false);
        });
        h.addView(clr);
        return h;
    }

    private View composer() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        attachBar = new LinearLayout(this);
        attachBar.setOrientation(LinearLayout.VERTICAL);
        attachBar.setPadding(dp(12), dp(4), dp(12), 0);
        attachBar.setVisibility(View.GONE);
        col.addView(attachBar);

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.BOTTOM);
        c.setPadding(dp(12), dp(6), dp(12), dp(14));

        input = new EditText(this);
        input.setHint("Tulis pesan\u2026");
        input.setHintTextColor(Color.parseColor(SUB));
        input.setTextColor(Color.parseColor(TXT));
        input.setTextSize(14);
        input.setGravity(Gravity.TOP);
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(bg(CARD, 18));
        input.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1f);
        input.setLayoutParams(ilp);
        c.addView(input);

        Button clip = new Button(this);
        clip.setText("\uD83D\uDCCE");
        clip.setTextSize(15);
        clip.setBackground(bg(CARD, 18));
        clip.setTextColor(Color.parseColor(ACCENT));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(46), dp(46));
        clp.setMarginStart(dp(8));
        clip.setLayoutParams(clp);
        clip.setOnClickListener(v -> pickFile(Attachment.TO_AI));
        c.addView(clip);

        micBtn = new Button(this);
        micBtn.setText("\uD83C\uDFA4");
        micBtn.setTextSize(15);
        micBtn.setBackground(bg(CARD, 18));
        micBtn.setTextColor(Color.parseColor(AiStore.voiceOn(this) ? BG : ACCENT));
        if (AiStore.voiceOn(this)) micBtn.setBackground(bg(ACCENT, 18));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(46), dp(46));
        mlp.setMarginStart(dp(8));
        micBtn.setLayoutParams(mlp);
        micBtn.setOnClickListener(v -> onMic());
        c.addView(micBtn);

        btnSpk = new Button(this);
        btnSpk.setTextSize(15);
        btnSpk.setBackground(bg(CARD, 18));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(46), dp(46));
        blp.setMarginStart(dp(8));
        btnSpk.setLayoutParams(blp);
        btnSpk.setOnClickListener(v -> toggleSpeak());
        c.addView(btnSpk);
        applyVoiceUi();

        Button send = new Button(this);
        send.setText("\u27A4");
        send.setTextColor(Color.parseColor(BG));
        send.setTextSize(16);
        send.setBackground(bg(ACCENT, 18));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(52), dp(46));
        slp.setMarginStart(dp(8));
        send.setLayoutParams(slp);
        send.setOnClickListener(v -> onSend());
        c.addView(send);
        col.addView(c);
        return col;
    }

    /** Pemilih file sistem: semua tipe, boleh banyak sekaligus. task = maksud lampiran. */
    private void pickFile(String task) {
        if (pending.size() >= AiFiles.MAX_ATTACH) {
            Toast.makeText(this, "Maksimal " + AiFiles.MAX_ATTACH + " lampiran", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
            pendingTask = task == null ? Attachment.TO_AI : task;
            startActivityForResult(i, 4711);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak ada pemilih file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 4711 || res != RESULT_OK || data == null) return;
        List<android.net.Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        for (android.net.Uri u : uris) {
            if (pending.size() >= AiFiles.MAX_ATTACH) break;
            try {
                Attachment a = new Attachment(AiFiles.saveFromUri(this, u));
                a.task = pendingTask;
                pending.add(a);
            } catch (Exception e) {
                Toast.makeText(this, "Gagal melampirkan: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        renderAttach();
    }

    /**
     * Ketuk chip = ganti tugas. Gambar: lihat <-> jadikan gambar <-> jadikan video.
     * File lain: baca <-> jadikan gambar (dipakai sebagai acuan).
     */
    private void cycleTask(Attachment a) {
        if (a.isImage()) {
            a.task = Attachment.TO_AI.equals(a.task) ? Attachment.IMAGE
                    : Attachment.IMAGE.equals(a.task) ? Attachment.VIDEO : Attachment.TO_AI;
        } else {
            a.task = Attachment.TO_AI.equals(a.task) ? Attachment.IMAGE : Attachment.TO_AI;
        }
        if (Attachment.IMAGE.equals(a.task) && a.isImage()) {
            Toast.makeText(this, "Gambar akan dipakai sebagai acuan generate gambar", Toast.LENGTH_SHORT).show();
        }
        renderAttach();
    }

    /** Tombol aksi cepat di bawah chip: gambar / video / kirim saja. */
    private void attachActions(LinearLayout bar) {
        LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        acts.setPadding(0, dp(2), 0, dp(6));
        bar.addView(acts);

        acts.addView(miniButton("\u2728 Gambar", () -> startAttachTask(Attachment.IMAGE)));
        acts.addView(miniButton("\uD83C\uDFAC Video", () -> startAttachTask(Attachment.VIDEO)));
        acts.addView(miniButton("\u27A4 Kirim", this::onSend));
    }

    private Button miniButton(String label, Runnable onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setTextColor(Color.parseColor(ACCENT));
        b.setBackground(bg(CARD, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
        lp.setMarginEnd(dp(6));
        b.setLayoutParams(lp);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    /** Kalau sudah ada lampiran: langsung jalankan tugasnya. Kalau belum: buka pemilih file. */
    private void startAttachTask(String task) {
        if (pending.isEmpty()) { pickFile(task); return; }
        for (Attachment a : pending) a.task = task;
        renderAttach();
        onSend();
    }

    /** Chip lampiran: nama, jenis, ukuran, tombol buang. */
    private void renderAttach() {
        if (attachBar == null) return;
        attachBar.removeAllViews();
        if (pending.isEmpty()) { attachBar.setVisibility(View.GONE); return; }
        attachBar.setVisibility(View.VISIBLE);

        TextView head = new TextView(this);
        head.setText("LAMPIRAN \u00B7 " + pending.size() + " file");
        head.setTextColor(Color.parseColor(ACCENT));
        head.setTextSize(10);
        head.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        head.setPadding(0, 0, 0, dp(4));
        attachBar.addView(head);

        for (int i = 0; i < pending.size(); i++) {
            final Attachment a = pending.get(i);
            final File f = a.file;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(bg(CARD, 12));
            row.setPadding(dp(10), dp(7), dp(10), dp(7));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.bottomMargin = dp(4);
            row.setLayoutParams(rlp);

            TextView nm = new TextView(this);
            nm.setText(f.getName() + "\n" + AiFiles.kind(f) + " \u00B7 " + AiFiles.humanSize(f.length())
                    + "  \u00B7  " + a.taskLabel());
            nm.setOnClickListener(v -> cycleTask(a));
            nm.setTextColor(Color.parseColor(TXT));
            nm.setTextSize(11);
            nm.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(nm);

            TextView x = new TextView(this);
            x.setText("\u2715");
            x.setTextColor(Color.parseColor(SUB));
            x.setTextSize(15);
            x.setPadding(dp(10), 0, dp(2), 0);
            x.setOnClickListener(v -> { pending.remove(a); renderAttach(); });
            row.addView(x);

            attachBar.addView(row);
        }
        attachActions(attachBar);
        scrollDown();
    }

    private GradientDrawable bg(String color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(Color.parseColor(color));
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView bubble(String who, String text, boolean user) {
        TextView label = new TextView(this);
        label.setText(who);
        label.setTextColor(Color.parseColor(user ? ACCENT : SUB));
        label.setTextSize(10);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(dp(user ? 0 : 2), dp(6), dp(user ? 2 : 0), dp(2));
        label.setGravity(user ? Gravity.END : Gravity.START);
        list.addView(label);

        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.parseColor(TXT));
        t.setTextSize(14);
        t.setTextIsSelectable(true);
        t.setBackground(bg(user ? USER : CARD, 16));
        t.setPadding(dp(13), dp(10), dp(13), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = user ? Gravity.END : Gravity.START;
        t.setLayoutParams(lp);
        list.addView(t);
        scrollDown();
        return t;
    }

    private void scrollDown() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void refreshStatus() {
        String prov = AiStore.provider(this);
        String mode = AiStore.agent(this) ? "agent: root" : "mode chat";
        String k = AiStore.needsKey(prov) ? "key " + AiStore.keyMask(AiStore.key(this)) : "tanpa key";
        status.setText(AiStore.label(prov) + "  \u00B7  " + AiStore.model(this) + "  \u00B7  " + k + "  \u00B7  " + mode);
    }

    private void onSend() {
        if (voice != null) voice.stopSpeak();
        if (busy) { Toast.makeText(this, "Tunggu jawaban sebelumnya", Toast.LENGTH_SHORT).show(); return; }
        final String q = input.getText().toString().trim();
        if (q.isEmpty() && pending.isEmpty()) return;
        if (!AiStore.ready(this)) {
            bubble("AI", AiStore.problem(this) + ".\n\nMenu \u2699 terbuka: pilih AI, tempel key, tekan Tes.", false);
            openSettings();
            return;
        }

        input.setText("");
        try {
            InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(input.getWindowToken(), 0);
        } catch (Exception ignore) {}

        final List<Attachment> files = new ArrayList<>(pending);
        pending.clear();
        renderAttach();

        // Lampiran bergambar/video dijalankan langsung lewat tool, tanpa menunggu model memutuskan.
        final List<Attachment> toMedia = new ArrayList<>();
        final List<Attachment> toAi = new ArrayList<>();
        for (Attachment a : files) {
            boolean media = (Attachment.IMAGE.equals(a.task) || Attachment.VIDEO.equals(a.task))
                    && a.isImage();
            if (media) toMedia.add(a); else toAi.add(a);
        }

        StringBuilder shown = new StringBuilder(q);
        for (Attachment a : files) {
            shown.append(shown.length() == 0 ? "" : "\n").append("\uD83D\uDCCE ").append(a.file.getName())
                 .append("  (").append(AiFiles.kind(a.file)).append(" \u00B7 ")
                 .append(AiFiles.humanSize(a.file.length())).append(" \u00B7 ").append(a.taskLabel()).append(")");
        }
        bubble("KAMU", shown.toString(), true);
        for (Attachment a : toAi) showAttachmentPreview(a.file);

        String text = q;
        if (text.isEmpty() && !toMedia.isEmpty()) {
            text = toMedia.stream().map(Attachment::promptFor).reduce((x, y) -> x + "\n" + y).orElse("");
        }

        try {
            JSONObject m = new JSONObject();
            m.put("role", "user");
            m.put("content", userContent(text, toAi));
            history.put(m);
        } catch (Exception ignore) {}

        if (!toMedia.isEmpty()) {
            final String prompt = text;
            final TextView ai0 = bubble("AI", "", false);
            busy = true;
            toolLoops = 0;
            new Thread(() -> {
                StringBuilder sb = new StringBuilder();
                for (Attachment a : toMedia) {
                    String out = Attachment.VIDEO.equals(a.task)
                            ? toolVideoFromPrompt(prompt, a.file)
                            : toolImageFromPrompt(prompt, a.file);
                    sb.append(out).append("\n\n");
                    final String o = out;
                    final String nm = Attachment.VIDEO.equals(a.task) ? "make_video" : "generate_image";
                    runOnUiThread(() -> {
                        TextView t = bubble("OUTPUT \u00B7 " + nm, o, false);
                        showMedia(t, o);
                    });
                }
                final String result = sb.toString().trim();
                runOnUiThread(() -> {
                    ai0.setText(result);
                    try {
                        JSONObject tm = new JSONObject();
                        tm.put("role", "assistant");
                        tm.put("content", result);
                        history.put(tm);
                    } catch (Exception ignore) {}
                    busy = false;
                });
            }, "arkael-ai-media").start();
            return;
        }

        final TextView ai = bubble("AI", "", false);
        busy = true;
        toolLoops = 0;
        final StringBuilder acc = new StringBuilder();
        ask(ai, acc);
    }

    private void ask(final TextView ai, final StringBuilder acc) {
        JSONArray msgs = buildMessages();
        final String prov = AiStore.provider(this);
        final String ep = AiStore.url(prov, AiStore.customUrl(this));
        AiClient.chat(ep, prov, AiStore.key(this), AiStore.model(this),
                AiStore.tokenField(prov), AiStore.maxTokens(this), msgs,
                AiStore.agent(this) ? tools() : null, true, new AiClient.Sink() {
            @Override public void onDelta(final String text) {
                runOnUiThread(() -> {
                    acc.append(text);
                    ai.setText(acc.toString());
                    scrollDown();
                });
            }
            @Override public void onDone(final String full, final JSONArray calls) {
                runOnUiThread(() -> done(ai, acc, full, calls));
            }
            @Override public void onError(final String err) {
                runOnUiThread(() -> {
                    busy = false;
                    ai.setText(acc.length() > 0 ? acc.toString() : "");
                    bubble("ERROR", err, false);
                });
            }
        });
    }

    private void done(TextView ai, StringBuilder acc, String full, JSONArray calls) {
        String text = acc.length() > 0 ? acc.toString() : full;
        ai.setText(text);

        addAssistant(text, calls);

        if (calls == null || calls.length() == 0) {
            busy = false;
            /* Hands-free: baca jawaban, lalu dengarkan lagi setelah suara habis. */
            if (voice != null && AiStore.voiceOn(this)) {
                voice.speakThen(text, () -> { if (micOn && !busy) startMicListening(); });
            } else if (micOn) {
                startMicListening();
            }
            return;
        }
        runTools(ai, calls);
    }

    /* ---------------- voice chat ---------------- */

    private boolean hasMicPermission() {
        return android.os.Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /** Segarkan tampilan tombol mic + speaker sesuai state sebenarnya. */
    private void applyVoiceUi() {
        if (micBtn != null) {
            micBtn.setTextColor(Color.parseColor(micOn ? BG : ACCENT));
            micBtn.setBackground(bg(micOn ? ACCENT : CARD, 18));
        }
        if (btnSpk != null) {
            boolean on = AiStore.voiceOn(this);
            btnSpk.setText(on ? "\uD83D\uDD0A" : "\uD83D\uDD07");
            btnSpk.setTextColor(Color.parseColor(on ? ACCENT : SUB));
        }
    }

    /** Tombol speaker: bisukan / nyalakan suara jawaban. Pendengar mic tetap jalan. */
    private void toggleSpeak() {
        boolean on = !AiStore.voiceOn(this);
        AiStore.setVoiceOn(this, on);
        if (on) {
            if (voice == null) voice = new AiVoice(this);
            voice.initTts();
        } else if (voice != null) {
            voice.stopSpeak();
        }
        applyVoiceUi();
        Toast.makeText(this, on ? "Suara jawaban nyala" : "Suara jawaban dibisukan", Toast.LENGTH_SHORT).show();
    }

    /** Tombol mic: nyalakan/matikan mode suara. Saat nyala, balasan dibacakan. */
    private void onMic() {
        if (micOn) { stopVoice(); return; }
        AiStore.setVoiceOn(this, true);
        micOn = true;
        applyVoiceUi();
        if (voice == null) voice = new AiVoice(this);
        voice.initTts();
        if (!hasMicPermission()) {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC_AI);
            }
            return;
        }
        autoSendAfterStt = true;
        startMicListening();
    }

    private void stopVoice() {
        micOn = false;
        autoSendAfterStt = false;
        if (voice != null) { voice.stopMic(); voice.stopSpeak(); }
        AiStore.setVoiceOn(this, false);
        applyVoiceUi();
    }

    /** Rekam satu ucapan; hasilnya masuk ke kotak pesan lalu langsung dikirim. */
    private void startMicListening() {
        if (voice == null) voice = new AiVoice(this);
        if (input != null) input.setHint("Mendengar\u2026");
        if (status != null) status.setText("\uD83C\uDFA4 mendengar\u2026");

        final String before = input == null ? "" : input.getText().toString().trim();
        voice.startMic(new AiVoice.MicCb() {
            @Override public void onRms(float level) {
                runOnUiThread(() -> {
                    int bars = (int) Math.max(0, Math.min(10, (level + 2f) * 2f));
                    StringBuilder p = new StringBuilder("\uD83C\uDFA4 ");
                    for (int i = 0; i < 10; i++) p.append(i < bars ? "\u2588" : "\u2591");
                    if (status != null) status.setText(p.toString());
                });
            }
            @Override public void onPartial(String text) {
                runOnUiThread(() -> { if (input != null && !text.isEmpty()) input.setText(text); });
            }
            @Override public void onFinal(String text) {
                runOnUiThread(() -> {
                    if (input != null) input.setText(text);
                    refreshStatus();
                    if (autoSendAfterStt && !busy) onSend();
                });
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    if (input != null && !before.isEmpty()) input.setText(before);
                    refreshStatus();
                    Toast.makeText(AiChatActivity.this, msg, Toast.LENGTH_LONG).show();
                });
            }
            @Override public void onEnd() {
                runOnUiThread(() -> { if (input != null) input.setHint("Tulis pesan\u2026"); });
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (voice != null) voice.shutdown();
        voice = null;
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_MIC_AI) {
            if (res != null && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
                autoSendAfterStt = true;
                startMicListening();
            } else {
                stopVoice();
                Toast.makeText(this, "Izin mikrofon ditolak. Mode suara dimatikan.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void addAssistant(String text, JSONArray calls) {
        try {
            JSONObject m = new JSONObject();
            m.put("role", "assistant");
            m.put("content", text == null ? "" : text);
            if (calls != null && calls.length() > 0) m.put("tool_calls", calls);
            history.put(m);
        } catch (Exception ignore) {}
    }

    private void runTools(final TextView ai, final JSONArray calls) {
        if (++toolLoops > MAX_TOOL_LOOPS) {
            busy = false;
            bubble("AGENT", "Batas " + MAX_TOOL_LOOPS + " langkah tool tercapai. Lanjutkan dengan pesan baru.", false);
            return;
        }
        new Thread(() -> {
            for (int i = 0; i < calls.length(); i++) {
                String name = "";
                String id = "";
                JSONObject args = new JSONObject();
                try {
                    JSONObject p = calls.getJSONObject(i);
                    id = p.optString("id");
                    JSONObject fn = p.optJSONObject("function");
                    if (fn == null) continue;
                    name = fn.optString("name", "");
                    String raw = fn.optString("arguments", "{}");
                    if (!raw.trim().startsWith("{")) raw = "{}";
                    args = new JSONObject(raw);
                } catch (Exception ignore) {}

                String out;
                if (!AiStore.agent(this) && "run_bash".equals(name)) {
                    out = "run_bash tidak aktif. Nyalakan Mode agent (menu \u2699 \u2192 Agent) dulu.";
                } else if ("generate_image".equals(name)) {
                    out = toolImage(args);
                } else if ("make_video".equals(name)) {
                    out = toolVideo(args);
                } else if ("run_bash".equals(name)) {
                    out = toolBash(args);
                } else if ("read_file".equals(name)) {
                    out = toolReadFile(args);
                } else if ("write_file".equals(name)) {
                    out = toolWriteFile(args);
                } else if ("list_dir".equals(name)) {
                    out = toolListDir(args);
                } else if ("make_dir".equals(name)) {
                    out = toolMakeDir(args);
                } else if ("save_to_gallery".equals(name)) {
                    out = toolToGallery(args);
                } else if ("find_image".equals(name)) {
                    out = toolFindImage(args);
                } else if ("unzip".equals(name)) {
                    out = toolUnzip(args);
                } else if (name.startsWith("git_")) {
                    out = toolGit(name, args);
                } else {
                    out = "tool tidak dikenal: " + name;
                }
                if (out == null) out = "";
                if (out.length() > MAX_TOOL_OUT) out = out.substring(0, MAX_TOOL_OUT) + "\n\u2026(output dipotong)";
                if (out.trim().isEmpty()) out = "(tidak ada output)";

                final String o = out;
                final String nm = name;
                runOnUiThread(() -> {
                    TextView t = bubble("OUTPUT \u00B7 " + nm, o, false);
                    showMedia(t, o);
                });

                try {
                    JSONObject t = new JSONObject();
                    t.put("role", "tool");
                    t.put("tool_call_id", id);
                    t.put("content", out);
                    history.put(t);
                } catch (Exception ignore) {}
            }
            runOnUiThread(() -> {
                final TextView next = bubble("AI", "", false);
                ask(next, new StringBuilder());
            });
        }, "arkael-ai-tool").start();
    }

    /** run_bash: shell di perangkat, sudah punya jalan sendiri (root lewat su). */
    private String toolBash(JSONObject args) {
        String cmd = args.optString("command", "");
        if (cmd.trim().isEmpty()) return "perintah kosong";
        runOnUiThread(() -> bubble("AGENT \u00B7 run_bash", cmd, false));
        String out;
        try { out = RootShell.run(cmd); } catch (Exception e) { return "gagal: " + e.getMessage(); }
        if (out == null) return "";
        if (out.trim().isEmpty()) return "(tidak ada output)";
        return out;
    }

    /** Salin hasil ke galeri perangkat (muncul di aplikasi Galeri). */
    private String toGalleryLine(File f, String kind) {
        try {
            AiPublish.toGallery(this, f, kind);
            return "galeri: sudah masuk " + ("video".equals(kind) ? "Movies/Arkael" : "Pictures/Arkael");
        } catch (Exception e) {
            return "galeri: gagal (" + e.getMessage() + ")";
        }
    }

    /** generate_image: hasil berupa path file, lalu ditampilkan di dalam chat. */
    private String toolImage(JSONObject args) {
        String prompt = args.optString("prompt", "");
        String size = args.optString("size", "1024x1024");
        runOnUiThread(() -> bubble("AGENT \u00B7 generate_image", prompt, false));
        String ref = args.optString("reference", "").trim();
        if (!ref.isEmpty()) {
            File rf = resolve(ref);
            ref = rf.isFile() ? rf.getAbsolutePath() : "";
        }
        try {
            String prov = AiStore.provider(this);
            String path = AiImage.generate(this, prov, AiStore.key(this),
                    AiStore.customUrl(this), prompt, size, ref);
            File f = new File(path);
            String gal;
            try {
                AiPublish.toGallery(this, f, "image");
                gal = "galeri: sudah masuk Pictures/Arkael\n";
            } catch (Exception e) {
                gal = "galeri: gagal (" + e.getMessage() + ")\n";
            }
            return "gambar tersimpan\n" + path + "\nukuran file: " + (f.length() / 1024) + " KB"
                    + "\nformat: " + AiImage.normSize(size) + "\n" + gal;
        } catch (Exception e) {
            return "gagal membuat gambar: " + e.getMessage()
                    + "\n\nPetunjuk: kalau provider butuh key (OpenAI), isi key di menu \u2699. "
                    + "Tanpa key, coba provider lain \u2014 gambar tetap bisa dibuat lewat jalur gratis.";
        }
    }

    /**
     * make_video: dua mode.
     *  - images: daftar path gambar (hasil generate_image atau file di perangkat) -> video.
     *  - text:   teks/daftar adegan -> video kartu teks bertahap.
     *  - trim:   source = path video + durasi -> potong.
     * Durasi bebas (detik), dibagi rata ke semua clip.
     */
    private String toolVideo(JSONObject args) {
        String mode = args.optString("mode", "text").trim().toLowerCase();
        int seconds = AiVideo.clampSeconds(args.optInt("seconds", 0));
        String sizeStr = args.optString("size", "portrait");
        runOnUiThread(() -> bubble("AGENT \u00B7 make_video", mode + " \u00B7 " + seconds + " dtk \u00B7 " + sizeStr, false));

        try {
            if ("trim".equals(mode)) {
                String src = args.optString("source", "").trim();
                if (src.isEmpty()) return "mode trim butuh parameter source (path video)";
                File in = resolve(src);
                if (!in.isFile()) return "file video tidak ditemukan: " + src;
                String out = AiVideo.trim(this, in, seconds);
                return "video dipotong " + seconds + " dtk\n" + out + "\n" + toGalleryLine(new File(out), "video");
            }

            List<AiVideo.Clip> clips = new ArrayList<>();

            if ("images".equals(mode)) {
                JSONArray arr = args.optJSONArray("images");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String p = arr.optString(i, "").trim();
                        if (p.isEmpty()) continue;
                        File f = resolve(p);
                        if (f.isFile()) clips.add(new AiVideo.Clip(f));
                    }
                }
                String one = args.optString("image", "").trim();
                if (clips.isEmpty() && !one.isEmpty()) {
                    File f = resolve(one);
                    if (f.isFile()) clips.add(new AiVideo.Clip(f));
                }
                if (clips.isEmpty()) return "tidak ada gambar yang bisa dipakai. Isi images[] atau image.";
            } else {
                JSONArray arr = args.optJSONArray("scenes");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String t = arr.optString(i, "").trim();
                        if (!t.isEmpty()) clips.add(new AiVideo.Clip(t));
                    }
                }
                if (clips.isEmpty()) {
                    String t = args.optString("text", "").trim();
                    if (t.isEmpty()) t = args.optString("prompt", "").trim();
                    if (t.isEmpty()) return "mode text butuh parameter text atau scenes[]";
                    clips.add(new AiVideo.Clip(t));   // satu kartu teks, durasi sesuai permintaan
                }
            }

            int[] wh = AiVideo.parseSize(sizeStr);
            String out = AiVideo.build(this, clips, seconds, wh[0], wh[1]);
            File f = new File(out);
            return "video dibuat\n" + out
                    + "\ndurasi: " + seconds + " detik \u00B7 " + wh[0] + "x" + wh[1]
                    + " \u00B7 " + AiVideo.FPS + " fps \u00B7 " + clips.size() + " bagian"
                    + "\nukuran file: " + (f.length() / 1024) + " KB"
                    + "\n" + toGalleryLine(f, "video");
        } catch (Exception e) {
            return "gagal membuat video: " + e.getMessage();
        }
    }

    /** Path absolut dipakai apa adanya; nama saja dicari di folder gambar/video app dan Pictures umum. */
    private File resolve(String p) {
        File f = new File(p);
        if (f.isAbsolute()) return f;
        File[] roots = {
                new File(getExternalFilesDir(null), "Pictures"),
                new File(getExternalFilesDir(null), "Movies"),
                new File(getExternalFilesDir(null), ""),
        };
        for (File r : roots) {
            File c = new File(r, p);
            if (c.isFile()) return c;
        }
        return f;
    }

    /** Kalau output tool menyebut file gambar/video, tampilkan pratinjau di chat. */
    private void showMedia(TextView tv, String out) {
        String path = null;
        for (String line : out.split("\n")) {
            String s = line.trim();
            if (s.startsWith("/") && (s.endsWith(".png") || s.endsWith(".jpg")
                    || s.endsWith(".jpeg") || s.endsWith(".mp4"))) { path = s; break; }
        }
        if (path == null) return;
        File f = new File(path);
        if (!f.isFile()) return;

        if (path.endsWith(".mp4")) {
            TextView play = new TextView(this);
            play.setText("\u25B6  Putar video");
            play.setTextColor(Color.parseColor(BG));
            play.setTextSize(13);
            play.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            play.setGravity(Gravity.CENTER);
            play.setBackground(bg(ACCENT, 14));
            play.setPadding(dp(12), dp(10), dp(12), dp(10));
            play.setOnClickListener(v -> openVideo(f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = dp(6);
            list.addView(play, lp);
            scrollDown();
            return;
        }

        try {
            android.graphics.Bitmap bm = AiVideo.decode(f, dp(360), dp(360));
            if (bm == null) return;
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setImageBitmap(bm);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            iv.setBackground(bg(CARD, 16));
            iv.setPadding(dp(4), dp(4), dp(4), dp(4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            list.addView(iv, lp);
            scrollDown();
        } catch (Exception ignore) {}
    }

    /**
     * Android 7+ wajib pakai content:// untuk mengirim file ke app lain.
     * Folder app (getExternalFilesDir) bisa dibagi lewat FileProvider bawaan paket ini;
     * kalau tidak tersedia, jatuh ke file:// dengan peringatan.
     */
    private android.net.Uri openFileUri(File f) {
        try {
            return androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".ai_files", f);
        } catch (Exception e) {
            return android.net.Uri.fromFile(f);
        }
    }

    private void openVideo(File f) {
        try {
            android.net.Uri u = openFileUri(f);
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            i.setDataAndType(u, "video/mp4");
            i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Tidak bisa membuka video: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Lampiran gambar -> generate_image dengan gambar itu sebagai acuan visual. */
    private String toolImageFromPrompt(String prompt, File ref) {
        try {
            JSONObject a = new JSONObject();
            a.put("prompt", prompt == null || prompt.trim().isEmpty()
                    ? "Variasi baru dari gambar " + ref.getName()
                    : prompt);
            a.put("size", "1024x1024");
            if (ref != null) a.put("reference", ref.getAbsolutePath());
            return toolImage(a);
        } catch (Exception e) {
            return "gagal membuat gambar: " + e.getMessage();
        }
    }

    /** Lampiran gambar -> make_video mode images dengan satu klip. */
    private String toolVideoFromPrompt(String prompt, File img) {
        try {
            JSONObject a = new JSONObject();
            a.put("mode", "images");
            JSONArray arr = new JSONArray();
            arr.put(img.getAbsolutePath());
            a.put("images", arr);
            a.put("seconds", 15);
            a.put("size", "portrait");
            return toolVideo(a);
        } catch (Exception e) {
            return "gagal membuat video: " + e.getMessage();
        }
    }

    /**
     * Isi pesan pengguna. Gambar dikirim sebagai image_url data URL (vision),
     * teks ditempel inline, biner diberi path supaya bisa dibaca tool read_file.
     */
    private Object userContent(String q, List<Attachment> files) throws Exception {
        if (files.isEmpty()) return q;

        JSONArray parts = new JSONArray();
        if (!q.isEmpty()) {
            JSONObject t = new JSONObject();
            t.put("type", "text");
            t.put("text", q);
            parts.put(t);
        }

        StringBuilder paths = new StringBuilder();
        for (Attachment a : files) {
            File f = a.file;
            String kind = AiFiles.kind(f);
            if ("image".equals(kind)) {
                if (f.length() > AiFiles.MAX_IMAGE_BYTES) {
                    parts.put(textPart("Lampiran gambar " + f.getName() + " terlalu besar ("
                            + AiFiles.humanSize(f.length()) + "), tidak dikirim sebagai gambar.\n"
                            + "Path: " + f.getAbsolutePath()));
                    continue;
                }
                try {
                    String b64 = AiFiles.base64Of(f);
                    JSONObject url = new JSONObject();
                    url.put("url", "data:" + AiFiles.mime(f) + ";base64," + b64);
                    JSONObject img = new JSONObject();
                    img.put("type", "image_url");
                    img.put("image_url", url);
                    parts.put(img);
                    paths.append("- ").append(f.getName()).append(" = ").append(f.getAbsolutePath())
                         .append(" (gambar, terkirim sebagai vision)\n");
                } catch (Exception e) {
                    parts.put(textPart("Gambar " + f.getName() + " gagal dibaca: " + e.getMessage()));
                }
            } else if ("text".equals(kind)) {
                String body = AiFiles.textOf(f, 60000);
                parts.put(textPart("=== FILE: " + f.getName() + " (" + AiFiles.humanSize(f.length())
                        + ") ===\nPath: " + f.getAbsolutePath() + "\n\n" + (body == null ? "" : body)
                        + "\n=== AKHIR " + f.getName() + " ==="));
                paths.append("- ").append(f.getName()).append(" = ").append(f.getAbsolutePath())
                     .append(" (teks, isi sudah di atas)\n");
            } else {
                String head = AiFiles.hexdump(f, 1024);
                parts.put(textPart("=== FILE BINER: " + f.getName() + " (" + AiFiles.humanSize(f.length())
                        + ") ===\nPath: " + f.getAbsolutePath()
                        + "\n\nCuplikan hex:\n" + head
                        + "\nUntuk membaca isinya pakai tool read_file dengan path di atas.\n"
                        + "Kalau zip, pakai tool unzip.\n=== AKHIR " + f.getName() + " ==="));
                paths.append("- ").append(f.getName()).append(" = ").append(f.getAbsolutePath())
                     .append(" (biner)\n");
            }
        }
        if (paths.length() > 0) {
            parts.put(textPart("File yang dilampirkan pengguna di perangkat ini:\n" + paths
                    + "Pakai path itu untuk tool read_file / write_file / unzip / git_add. "
                    + "Jangan mengarang isi file: baca dulu."));
        }
        return parts;
    }

    private JSONObject textPart(String t) throws Exception {
        JSONObject o = new JSONObject();
        o.put("type", "text");
        o.put("text", t);
        return o;
    }

    /** Pratinjau lampiran pengguna di dalam chat. */
    private void showAttachmentPreview(File f) {
        if (!AiFiles.isImage(f)) return;
        try {
            android.graphics.Bitmap bm = AiVideo.decode(f, dp(360), dp(360));
            if (bm == null) return;
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setImageBitmap(bm);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            iv.setBackground(bg(USER, 16));
            iv.setPadding(dp(4), dp(4), dp(4), dp(4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.gravity = Gravity.END;
            lp.topMargin = dp(4);
            list.addView(iv, lp);
            scrollDown();
        } catch (Exception ignore) {}
    }

    /** Path absolut dipakai apa adanya; nama saja dicari di folder gambar/video app dan Pictures umum. */
    private JSONArray buildMessages() {
        JSONArray out = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", AiPersona.system(AiStore.agent(this)));
            out.put(sys);
            for (int i = 0; i < history.length(); i++) out.put(history.get(i));
        } catch (Exception ignore) {}
        return out;
    }

    /** Tool yang dikirim ke model. run_bash tetap dikirim, tapi ditolak saat mode agent mati. */
    /** Tool akses file: dipakai AI untuk membaca/menulis file di perangkat. */
    private java.util.List<JSONObject> fileTools() {
        java.util.List<JSONObject> list = new ArrayList<>();
        try {
            JSONObject rp = new JSONObject();
            rp.put("type", "object");
            JSONObject rf = new JSONObject();
            rf.put("type", "string");
            rf.put("description", "Path absolut file, atau path relatif ke repo agent.");
            rp.put("properties", new JSONObject().put("path", rf));
            rp.put("required", new JSONArray().put("path"));
            list.add(fnTool("read_file", "Membaca isi file dari perangkat (teks, atau cuplikan hex untuk biner). "
                    + "Selalu baca file sebelum mengubahnya.", rp));

            JSONObject wp = new JSONObject();
            wp.put("type", "object");
            JSONObject wpf = new JSONObject();
            wpf.put("type", "string");
            wpf.put("description", "Path absolut file yang ditulis. Folder dibuat otomatis.");
            JSONObject wpc = new JSONObject();
            wpc.put("type", "string");
            wpc.put("description", "Isi file baru, teks lengkap.");
            wp.put("properties", new JSONObject().put("path", wpf).put("content", wpc));
            wp.put("required", new JSONArray().put("path").put("content"));
            list.add(fnTool("write_file", "Menulis (atau menimpa) satu file teks di perangkat.", wp));

            JSONObject lp = new JSONObject();
            lp.put("type", "object");
            JSONObject lpf = new JSONObject();
            lpf.put("type", "string");
            lpf.put("description", "Folder yang dilihat. Kosong = repo agent.");
            lp.put("properties", new JSONObject().put("path", lpf));
            list.add(fnTool("list_dir", "Melihat isi folder: nama, ukuran, dan tanda [dir] untuk subfolder.", lp));

            JSONObject mp = new JSONObject();
            mp.put("type", "object");
            JSONObject mpf = new JSONObject();
            mpf.put("type", "string");
            mpf.put("description", "Folder yang dibuat (path absolut, atau relatif ke repo agent).");
            mp.put("properties", new JSONObject().put("path", mpf));
            mp.put("required", new JSONArray().put("path"));
            list.add(fnTool("make_dir", "Membuat folder baru beserta folder induknya.", mp));

            JSONObject gp = new JSONObject();
            gp.put("type", "object");
            JSONObject gpf = new JSONObject();
            gpf.put("type", "string");
            gpf.put("description", "Path file gambar/video yang sudah ada di perangkat.");
            gp.put("properties", new JSONObject().put("path", gpf));
            gp.put("required", new JSONArray().put("path"));
            list.add(fnTool("save_to_gallery", "Menyalin file gambar/video ke galeri perangkat "
                    + "(Pictures/Arkael atau Movies/Arkael) supaya muncul di aplikasi Galeri.", gp));

            JSONObject fp = new JSONObject();
            fp.put("type", "object");
            JSONObject fpq = new JSONObject();
            fpq.put("type", "string");
            fpq.put("description", "Kata kunci gambar yang dicari, mis. \"red panda\" atau \"Monas Jakarta\".");
            fp.put("properties", new JSONObject().put("query", fpq));
            fp.put("required", new JSONArray().put("query"));
            list.add(fnTool("find_image", "Mencari gambar asli (foto nyata, bukan hasil AI) di internet "
                    + "lewat Wikimedia Commons, mengunduhnya, lalu menyimpannya ke galeri perangkat. "
                    + "Pakai ini kalau pengguna minta \"carikan gambar/foto ...\".", fp));

            JSONObject up = new JSONObject();
            up.put("type", "object");
            JSONObject upz = new JSONObject();
            upz.put("type", "string");
            upz.put("description", "Path file zip.");
            JSONObject upd = new JSONObject();
            upd.put("type", "string");
            upd.put("description", "Folder tujuan ekstrak (dibuat kalau belum ada).");
            up.put("properties", new JSONObject().put("path", upz).put("dest", upd));
            up.put("required", new JSONArray().put("path"));
            list.add(fnTool("unzip", "Mengekstrak file zip ke folder tujuan. Entri yang keluar folder ditolak.", up));
        } catch (Exception ignore) {}
        return list;
    }

    /** Tool git: alur repo agent (init, tulis file, commit, push). */
    private java.util.List<JSONObject> gitTools() {
        java.util.List<JSONObject> list = new ArrayList<>();
        try {
            list.add(fnTool("git_init", "Menyiapkan repo agent di perangkat dari template (sekali saja). "
                    + "Mengembalikan path repo dan status git.", emptyParams()));

            JSONObject cp = new JSONObject();
            cp.put("type", "object");
            JSONObject cm = new JSONObject();
            cm.put("type", "string");
            cm.put("description", "Pesan commit singkat, bahasa Indonesia.");
            cp.put("properties", new JSONObject().put("message", cm));
            cp.put("required", new JSONArray().put("message"));
            list.add(fnTool("git_commit", "Menambah semua perubahan (git add -A) dan commit di repo agent.", cp));

            list.add(fnTool("git_push", "Push repo agent ke GitHub. Butuh user/token di menu \u2699 \u2192 Git. "
                    + "Token tidak pernah ditampilkan.", emptyParams()));

            list.add(fnTool("git_status", "Menampilkan branch, perubahan, dan remote repo agent (tanpa token).",
                    emptyParams()));
        } catch (Exception ignore) {}
        return list;
    }

    private JSONObject emptyParams() throws Exception {
        JSONObject p = new JSONObject();
        p.put("type", "object");
        p.put("properties", new JSONObject());
        return p;
    }

    private JSONObject fnTool(String name, String desc, JSONObject params) throws Exception {
        JSONObject fn = new JSONObject();
        fn.put("name", name);
        fn.put("description", desc);
        fn.put("parameters", params);
        JSONObject t = new JSONObject();
        t.put("type", "function");
        t.put("function", fn);
        return t;
    }

    /** read_file: teks apa adanya, biner sebagai hexdump. */
    private String toolReadFile(JSONObject args) {
        String p = args.optString("path", "").trim();
        if (p.isEmpty()) return "path kosong";
        File f = resolveIn(p);
        if (!f.isFile()) return "file tidak ditemukan: " + f.getAbsolutePath();
        runOnUiThread(() -> bubble("AGENT \u00B7 read_file", f.getAbsolutePath(), false));
        if (AiFiles.isText(f) || AiFiles.looksText(f)) {
            String body = AiFiles.textOf(f, 60000);
            return "path: " + f.getAbsolutePath() + "\nukuran: " + AiFiles.humanSize(f.length()) + "\n\n" + body;
        }
        return "path: " + f.getAbsolutePath() + "\nukuran: " + AiFiles.humanSize(f.length())
                + "\njenis: biner\n\n" + AiFiles.hexdump(f, 2048);
    }

    /** write_file: tulis teks, buat folder induk, jangan sentuh folder sistem tanpa izin. */
    private String toolWriteFile(JSONObject args) {
        String p = args.optString("path", "").trim();
        String body = args.optString("content", "");
        if (p.isEmpty()) return "path kosong";
        File f = new File(p);
        if (!f.isAbsolute()) f = new File(AiGit.repo(this), p);
        if (isForbidden(f)) {
            return "menolak menulis ke area sistem: " + f.getAbsolutePath()
                    + "\nTulis ke folder app (mis. " + AiGit.repo(this).getAbsolutePath() + ") dulu.";
        }
        try {
            File dir = f.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) return "tidak bisa membuat folder " + dir;
            java.io.OutputStream os = new java.io.FileOutputStream(f);
            try {
                os.write(body.getBytes("UTF-8"));
            } finally {
                os.close();
            }
            final String done = f.getAbsolutePath();
            runOnUiThread(() -> bubble("AGENT \u00B7 write_file", done, false));
            return "tertulis: " + f.getAbsolutePath() + "\n" + AiFiles.humanSize(f.length());
        } catch (Exception e) {
            return "gagal menulis: " + e.getMessage();
        }
    }

    /** Tolak path sistem yang bisa merusak perangkat. */
    private boolean isForbidden(File f) {
        try {
            String c = f.getCanonicalPath();
            for (String bad : new String[]{"/system", "/vendor", "/product", "/init.rc", "/data/system", "/proc", "/sys"}) {
                if (c.equals(bad) || c.startsWith(bad + "/")) return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** list_dir: isi folder, dir ditandai, ukuran file ditulis. */
    private String toolListDir(JSONObject args) {
        String p = args.optString("path", "").trim();
        File dir = p.isEmpty() ? AiGit.repo(this) : resolveIn(p);
        if (!dir.exists()) {
            File alt = resolve(p);
            if (alt.exists()) dir = alt;
        }
        if (!dir.isDirectory()) {
            return dir.getAbsolutePath() + " bukan folder"
                    + "\n\nPetunjuk: pakai path folder, mis. " + AiGit.repo(this).getAbsolutePath();
        }
        File[] kids = dir.listFiles();
        if (kids == null) return "tidak bisa membaca folder " + dir.getAbsolutePath();
        java.util.Arrays.sort(kids, new java.util.Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        StringBuilder sb = new StringBuilder();
        sb.append("folder: ").append(dir.getAbsolutePath()).append("\n");
        int d = 0, f = 0;
        for (File k : kids) {
            if (k.isDirectory()) {
                d++;
                int n = k.listFiles() == null ? 0 : k.listFiles().length;
                sb.append("[dir]  ").append(k.getName()).append("/  (").append(n).append(" item)\n");
            } else {
                f++;
                sb.append("       ").append(k.getName()).append("  ").append(AiFiles.humanSize(k.length())).append("\n");
            }
        }
        if (kids.length == 0) sb.append("(folder kosong)\n");
        sb.append("\n").append(d).append(" folder, ").append(f).append(" file");
        return sb.toString();
    }

    /** make_dir: bikin folder, induk sekalian. */
    private String toolMakeDir(JSONObject args) {
        String p = args.optString("path", "").trim();
        if (p.isEmpty()) return "path kosong";
        File f = new File(p);
        if (!f.isAbsolute()) f = new File(AiGit.repo(this), p);
        if (isForbidden(f)) return "menolak membuat folder di area sistem: " + f.getAbsolutePath();
        if (f.isDirectory()) return "folder sudah ada: " + f.getAbsolutePath();
        if (f.isFile()) return "sudah ada file dengan nama itu: " + f.getAbsolutePath();
        if (!f.mkdirs()) return "gagal membuat folder: " + f.getAbsolutePath();
        int n = f.listFiles() == null ? 0 : f.listFiles().length;
        return "folder dibuat: " + f.getAbsolutePath() + "\nisi: " + n + " item";
    }

    /** save_to_gallery: file yang sudah ada -> koleksi galeri perangkat. */
    private String toolToGallery(JSONObject args) {
        String p = args.optString("path", "").trim();
        if (p.isEmpty()) return "path kosong";
        File f = resolveIn(p);
        if (!f.isFile()) {
            File alt = resolve(p);
            if (alt.isFile()) f = alt;
        }
        if (!f.isFile()) return "file tidak ditemukan: " + p;
        final String path = f.getAbsolutePath();
        runOnUiThread(() -> bubble("AGENT \u00B7 save_to_gallery", path, false));
        try {
            String kind = AiPublish.kindOf(f);
            android.net.Uri u = AiPublish.toGallery(this, f, kind);
            String where = "image".equals(kind) ? "Pictures/Arkael"
                    : "video".equals(kind) ? "Movies/Arkael"
                    : "audio".equals(kind) ? "Music/Arkael" : "Download/Arkael";
            return "masuk galeri \u2713\n" + f.getName() + "\n" + where
                    + "\nukuran: " + AiFiles.humanSize(f.length())
                    + "\nuri: " + u;
        } catch (Exception e) {
            return "gagal simpan ke galeri: " + e.getMessage()
                    + "\n\nKalau gagal terus, pakai jalur file biasa: " + path;
        }
    }

    /** find_image: cari gambar asli di internet, unduh, taruh di galeri. */
    private String toolFindImage(JSONObject args) {
        String q = args.optString("query", "").trim();
        if (q.isEmpty()) q = args.optString("prompt", "").trim();
        if (q.isEmpty()) return "kata kunci kosong";
        final String key = q;
        runOnUiThread(() -> bubble("AGENT \u00B7 find_image", key, false));
        try {
            java.util.List<AiPublish.Hit> hits = AiPublish.search(this, key);
            StringBuilder sb = new StringBuilder();
            int ok = 0;
            for (AiPublish.Hit h : hits) {
                if (ok >= 3) break;
                try {
                    File f = AiPublish.download(this, h);
                    android.net.Uri u = AiPublish.toGallery(this, f, "image");
                    ok++;
                    final String fpath = f.getAbsolutePath();
                    runOnUiThread(() -> bubble("GAMBAR \u00B7 " + key, fpath, false));
                    sb.append("gambar ").append(ok).append(": ").append(f.getName()).append('\n');
                    sb.append("  judul : ").append(h.title).append('\n');
                    sb.append("  ukuran: ").append(h.width).append('x').append(h.height).append('\n');
                    sb.append("  file  : ").append(fpath).append('\n');
                    if (!h.page.isEmpty()) sb.append("  sumber: ").append(h.page).append('\n');
                    sb.append("  galeri: tersimpan di Pictures/Arkael\n\n");
                } catch (Exception e) {
                    sb.append("gagal unduh ").append(h.title).append(": ").append(e.getMessage()).append('\n');
                }
            }
            if (ok == 0) return "gagal mengambil gambar untuk \"" + key + "\".\n\n" + sb;
            return "ketemu " + ok + " gambar, sudah masuk galeri (Pictures/Arkael).\n\n" + sb;
        } catch (Exception e) {
            return "pencarian gambar gagal: " + e.getMessage();
        }
    }

    private String toolUnzip(JSONObject args) {
        String p = args.optString("path", "").trim();
        String d = args.optString("dest", "").trim();
        if (p.isEmpty()) return "path zip kosong";
        File z = resolveIn(p);
        File dest = d.isEmpty() ? new File(AiFiles.inbox(this), "unzip_" + System.currentTimeMillis()) : new File(d);
        runOnUiThread(() -> bubble("AGENT \u00B7 unzip", z.getAbsolutePath(), false));
        try {
            return AiFiles.unzip(z, dest);
        } catch (Exception e) {
            return "gagal unzip: " + e.getMessage();
        }
    }

    /** git_*: repo agent, hasil selalu diredaksi dari token. */
    private String toolGit(String name, JSONObject args) {
        runOnUiThread(() -> bubble("AGENT \u00B7 " + name, AiStore.gitOwner(this) + "/"
                + AiStore.gitRepo(this) + " \u00B7 " + AiStore.gitBranch(this), false));
        if (!AiStore.agent(this)) {
            return "tool git butuh Mode agent. Nyalakan di menu \u2699 \u2192 Agent.";
        }
        if ("git_init".equals(name)) return AiGit.ensure(this);
        if (!new File(AiGit.repo(this), ".git").exists()) AiGit.ensure(this);

        String branch = AiStore.gitBranch(this);
        if ("git_status".equals(name)) {
            return AiGit.exec(this, "git status --short --branch && echo --- && git log --oneline -5");
        }
        if ("git_commit".equals(name)) {
            String msg = args.optString("message", "").trim();
            if (msg.isEmpty()) msg = "update dari Arkael-Ai-Agent";
            msg = msg.replace("'", "").replace("\n", " ");
            String cmd = "git checkout -q -B " + AiGit.shell(branch) + " 2>&1\n"
                    + "git add -A 2>&1\n"
                    + "git -c user.name=" + AiGit.shell(AiStore.gitUser(this))
                    + " -c user.email=" + AiGit.shell(AiStore.gitEmail(this))
                    + " commit -q -m " + AiGit.shell(msg) + " 2>&1\n"
                    + "git log --oneline -3";
            return AiGit.exec(this, cmd);
        }
        if ("git_push".equals(name)) {
            String remote = AiGit.remote(this);
            if (remote.isEmpty() || AiStore.gitToken(this).isEmpty()) {
                return "push belum bisa: isi Owner, Repo, dan Token GitHub di menu \u2699 \u2192 Git.";
            }
            String cmd = "git checkout -q -B " + AiGit.shell(branch) + " 2>&1\n"
                    + "git remote get-url origin >/dev/null 2>&1 || git remote add origin " + AiGit.shell(remote) + "\n"
                    + "git remote set-url origin " + AiGit.shell(remote) + " 2>&1\n"
                    + "git push -u origin " + AiGit.shell(branch) + " 2>&1";
            return AiGit.exec(this, cmd);
        }
        return "tool git tidak dikenal: " + name;
    }

    /** Path: absolut dipakai apa adanya, relatif dicari di repo agent lalu folder cache lampiran. */
    private File resolveIn(String p) {
        File f = new File(p);
        if (f.isAbsolute()) return f;
        File inRepo = new File(AiGit.repo(this), p);
        if (inRepo.exists()) return inRepo;
        File inInbox = new File(AiFiles.inbox(this), p);
        if (inInbox.exists()) return inInbox;
        File byPath = resolve(p);
        if (byPath.exists()) return byPath;
        return inRepo;
    }

    private JSONArray tools() {
        try {
            JSONObject props = new JSONObject();
            JSONObject cmd = new JSONObject();
            cmd.put("type", "string");
            cmd.put("description", "Perintah shell POSIX yang akan dijalankan di perangkat.");
            props.put("command", cmd);

            JSONObject params = new JSONObject();
            params.put("type", "object");
            params.put("properties", props);
            params.put("required", new JSONArray().put("command"));

            JSONObject fn = new JSONObject();
            fn.put("name", "run_bash");
            fn.put("description", "Menjalankan perintah shell di perangkat dan mengembalikan stdout+stderr.");
            fn.put("parameters", params);

            JSONObject tool = new JSONObject();
            tool.put("type", "function");
            tool.put("function", fn);

            // generate_image
            JSONObject ip = new JSONObject();
            JSONObject iprompt = new JSONObject();
            iprompt.put("type", "string");
            iprompt.put("description", "Deskripsi gambar yang diinginkan, bahasa apa pun, detail jelas.");
            ip.put("prompt", iprompt);
            JSONObject isize = new JSONObject();
            isize.put("type", "string");
            isize.put("description", "1024x1024 (kotak), 1024x1536 (tegak), 1536x1024 (lebar).");
            ip.put("size", isize);
            JSONObject iref = new JSONObject();
            iref.put("type", "string");
            iref.put("description", "Opsional. Path gambar acuan di perangkat (img2img). "
                    + "Isi kalau pengguna melampirkan gambar yang mau diubah.");
            ip.put("reference", iref);
            JSONObject iparams = new JSONObject();
            iparams.put("type", "object");
            iparams.put("properties", ip);
            iparams.put("required", new JSONArray().put("prompt"));

            JSONObject ifn = new JSONObject();
            ifn.put("name", "generate_image");
            ifn.put("description", "Membuat gambar dari teks dan menyimpannya sebagai file PNG di perangkat. "
                    + "Panggil ini setiap kali pengguna minta gambar, ilustrasi, poster, atau avatar. "
                    + "Kalau ada gambar acuan, isi reference dengan path-nya.");
            ifn.put("parameters", iparams);

            JSONObject itool = new JSONObject();
            itool.put("type", "function");
            itool.put("function", ifn);

            // make_video
            JSONObject vp = new JSONObject();
            JSONObject vmode = new JSONObject();
            vmode.put("type", "string");
            vmode.put("description", "images = dari gambar, text = kartu teks, trim = potong video yang ada.");
            vp.put("mode", vmode);
            JSONObject vimages = new JSONObject();
            vimages.put("type", "array");
            vimages.put("description", "Daftar path gambar (boleh hasil generate_image).");
            vimages.put("items", new JSONObject().put("type", "string"));
            vp.put("images", vimages);
            JSONObject vscenes = new JSONObject();
            vscenes.put("type", "array");
            vscenes.put("description", "Daftar teks per adegan untuk mode text.");
            vscenes.put("items", new JSONObject().put("type", "string"));
            vp.put("scenes", vscenes);
            JSONObject vsec = new JSONObject();
            vsec.put("type", "integer");
            vsec.put("description", "Durasi total dalam detik, bebas 1-120. Contoh 10 untuk 10 detik.");
            vp.put("seconds", vsec);
            JSONObject vsize = new JSONObject();
            vsize.put("type", "string");
            vsize.put("description", "portrait, landscape, square, 480p, 720p, 1080p, atau WxH.");
            vp.put("size", vsize);
            JSONObject vsrc = new JSONObject();
            vsrc.put("type", "string");
            vsrc.put("description", "Path video sumber untuk mode trim.");
            vp.put("source", vsrc);
            JSONObject vparams = new JSONObject();
            vparams.put("type", "object");
            vparams.put("properties", vp);
            vparams.put("required", new JSONArray().put("mode"));

            JSONObject vfn = new JSONObject();
            vfn.put("name", "make_video");
            vfn.put("description", "Membuat video MP4 dari gambar atau teks dengan durasi bebas, "
                    + "atau memotong video yang sudah ada.");
            vfn.put("parameters", vparams);

            JSONObject vtool = new JSONObject();
            vtool.put("type", "function");
            vtool.put("function", vfn);

            JSONArray all = new JSONArray().put(tool).put(itool).put(vtool);
            for (JSONObject t : fileTools()) all.put(t);
            if (AiStore.agent(this)) for (JSONObject t : gitTools()) all.put(t);
            return all;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** Menu AI ala ai-agent: pilih provider, isi key, pilih model, tes, simpan. */
    private void openSettings() {
        final int n = AiStore.PROVIDERS.length;
        final String[] names = new String[n + 2];
        final String cur = AiStore.provider(this);
        int check = 0;
        names[n] = "\u2699 Git  \u00B7  " + AiStore.gitOwner(this) + "/" + AiStore.gitRepo(this)
                + "  \u00B7  " + (AiStore.gitToken(this).isEmpty() ? "belum ada token" : "token tersimpan");
        names[n + 1] = "\uD83D\uDD0A Suara  \u00B7  " + AiStore.voiceLang(this)
                + "  \u00B7  " + (AiStore.voiceOn(this) ? "aktif" : "nonaktif");
        for (int i = 0; i < n; i++) {
            String p = AiStore.PROVIDERS[i];
            String mark;
            mark = AiStore.key(this, p).isEmpty() ? "butuh API key" : "key tersimpan";
            names[i] = AiStore.label(p) + "  \u00B7  " + mark;
            if (p.equals(cur)) check = i;
        }
        new AlertDialog.Builder(this)
            .setTitle("Pilih AI")
            .setSingleChoiceItems(names, check, (d, w) -> {
                d.dismiss();
                if (w == n + 1) openVoiceSettings();
                else if (w == n) openGitSettings();
                else editProvider(AiStore.PROVIDERS[w]);
            })
            .setNegativeButton("Batal", null)
            .setNeutralButton("Agent", (d, w) -> toggleAgent())
            .show();
    }

    /** Setelan suara: bahasa STT/TTS, mesin TTS, dan status mode suara. */
    private void openVoiceSettings() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(14), dp(20), dp(4));

        final String[] langs = {"id-ID", "en-US", "ms-MY", "zh-CN", "ja-JP", "ar-SA"};
        final String[] langNames = {"Indonesia (id-ID)", "English US (en-US)", "Melayu (ms-MY)",
                "\u4E2D\u6587 (zh-CN)", "\u65E5\u672C\u8A9E (ja-JP)", "\u0627\u0644\u0639\u0631\u0628\u064A\u0629 (ar-SA)"};
        String curLang = AiStore.voiceLang(this);
        int li = 0;
        for (int i = 0; i < langs.length; i++) if (langs[i].equalsIgnoreCase(curLang)) li = i;

        col.addView(tag("BAHASA SUARA \u00B7 pengenal & pembaca"));
        TextView langBtn = new TextView(this);
        langBtn.setText(langNames[li]);
        langBtn.setTextColor(Color.parseColor(TXT));
        langBtn.setTextSize(15);
        langBtn.setPadding(dp(14), dp(12), dp(14), dp(12));
        langBtn.setBackground(bg(CARD, 14));
        final int[] picked = {li};
        langBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Bahasa suara")
                .setSingleChoiceItems(langNames, picked[0], (d, w) -> {
                    picked[0] = w;
                    langBtn.setText(langNames[w]);
                    AiStore.setVoiceLang(this, langs[w]);
                    d.dismiss();
                }).show());
        col.addView(langBtn);

        final LinearLayout engines = new LinearLayout(this);
        engines.setOrientation(LinearLayout.VERTICAL);
        col.addView(tag("MESIN TTS"));
        col.addView(engines);

        final AiVoice probe = new AiVoice(this);
        probe.listEngines(new AiVoice.EngineCb() {
            @Override public void onList(final List<String[]> items) {
                runOnUiThread(() -> {
                    engines.removeAllViews();
                    final List<String> pkg = new ArrayList<>();
                    final List<String> name = new ArrayList<>();
                    pkg.add("");
                    name.add("Bawaan sistem");
                    for (String[] e : items) { pkg.add(e[0]); name.add(e[1]); }
                    final String[] arrPkg = pkg.toArray(new String[0]);
                    final String[] arrName = name.toArray(new String[0]);
                    final String cur = AiStore.voiceEngine(AiChatActivity.this);
                    int ci = 0;
                    for (int i = 0; i < arrPkg.length; i++) if (arrPkg[i].equals(cur)) ci = i;

                    TextView b = new TextView(AiChatActivity.this);
                    b.setText(arrName[ci]);
                    b.setTextColor(Color.parseColor(TXT));
                    b.setTextSize(15);
                    b.setPadding(dp(14), dp(12), dp(14), dp(12));
                    b.setBackground(bg(CARD, 14));
                    final int[] sel = {ci};
                    b.setOnClickListener(v -> new AlertDialog.Builder(AiChatActivity.this)
                            .setTitle("Mesin TTS")
                            .setSingleChoiceItems(arrName, sel[0], (d, w) -> {
                                sel[0] = w;
                                b.setText(arrName[w]);
                                AiStore.setVoiceEngine(AiChatActivity.this, arrPkg[w]);
                                if (voice != null) { voice.stopSpeak(); voice.shutdown(); voice = new AiVoice(AiChatActivity.this); voice.initTts(); }
                                d.dismiss();
                            }).show());
                    engines.addView(b);

                    TextView note = new TextView(AiChatActivity.this);
                    note.setText(items.isEmpty()
                            ? "Tak ada mesin TTS terpasang. Pasang \"Speech Recognition & Synthesis\" dari Play Store."
                            : "Bahasa harus didukung mesin TTS yang dipilih. Kalau suara tidak keluar, ganti mesin atau bahasa.");
                    note.setTextColor(Color.parseColor(SUB));
                    note.setTextSize(12);
                    note.setPadding(dp(2), dp(10), dp(2), dp(6));
                    engines.addView(note);
                });
            }
        });

        TextView test = new TextView(this);
        test.setText("\u25B6 Tes suara");
        test.setTextColor(Color.parseColor(ACCENT));
        test.setTextSize(14);
        test.setPadding(dp(2), dp(14), dp(2), dp(6));
        test.setOnClickListener(v -> {
            if (voice != null) { voice.stopSpeak(); voice.shutdown(); }
            voice = new AiVoice(this);
            voice.initTts();
            final String sample = "Halo, suara Arkael siap dipakai.";
            final boolean was = AiStore.voiceOn(this);
            AiStore.setVoiceOn(this, true);
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                voice.speak(sample);
                AiStore.setVoiceOn(this, was);
            }, 700);
        });
        col.addView(test);

        new AlertDialog.Builder(this)
            .setTitle("Suara AI")
            .setView(col)
            .setPositiveButton("Simpan", (d, w) -> { refreshStatus(); applyVoiceUi(); })
            .setNegativeButton("Tutup", null)
            .show();
    }

    /** Setelan repo Git untuk mode agent: owner, repo, branch, identitas, token. */
    private void openGitSettings() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(14), dp(20), dp(4));

        col.addView(tag("REPO GITHUB"));
        final EditText eOwner = field("owner (user/org)", AiStore.gitOwner(this), false);
        final EditText eRepo = field("nama repo", AiStore.gitRepo(this), false);
        final EditText eBranch = field("branch", AiStore.gitBranch(this), false);
        col.addView(eOwner);
        col.addView(eRepo);
        col.addView(eBranch);

        col.addView(tag("IDENTITAS COMMIT"));
        final EditText eUser = field("user.name", AiStore.gitUser(this), false);
        final EditText eMail = field("user.email", AiStore.gitEmail(this), false);
        col.addView(eUser);
        col.addView(eMail);

        col.addView(tag("TOKEN"));
        final EditText eTok = field("personal access token (repo scope)", AiStore.gitToken(this), true);
        col.addView(eTok);

        TextView note = new TextView(this);
        note.setText("Token hanya disimpan di perangkat dan tidak pernah ditampilkan utuh.\n"
                + "Repo lokal: " + AiGit.repo(this).getAbsolutePath()
                + "\ngit di ROM: " + (AiGit.gitAvailable() ? "tersedia" : "TIDAK ADA (commit/push butuh git)"));
        note.setTextColor(Color.parseColor(SUB));
        note.setTextSize(10);
        note.setPadding(0, dp(8), 0, 0);
        col.addView(note);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);

        new AlertDialog.Builder(this)
            .setTitle("Git \u00B7 repo agent")
            .setView(sv)
            .setNegativeButton("Tutup", null)
            .setNeutralButton("Siapkan", (d, w) -> {
                saveGit(eOwner, eRepo, eBranch, eUser, eMail, eTok);
                runGitAsync("init", null);
            })
            .setPositiveButton("Simpan", (d, w) -> {
                saveGit(eOwner, eRepo, eBranch, eUser, eMail, eTok);
                Toast.makeText(this, "Setelan Git tersimpan", Toast.LENGTH_SHORT).show();
                refreshStatus();
            })
            .show();
    }

    private void saveGit(EditText o, EditText r, EditText b, EditText u, EditText m, EditText t) {
        AiStore.setGitOwner(this, o.getText().toString());
        AiStore.setGitRepo(this, r.getText().toString());
        AiStore.setGitBranch(this, b.getText().toString());
        AiStore.setGitUser(this, u.getText().toString());
        AiStore.setGitEmail(this, m.getText().toString());
        AiStore.setGitToken(this, t.getText().toString());
    }

    /** Jalankan git di thread lain; hasil masuk ke chat. */
    private void runGitAsync(String what, String message) {
        final TextView tv = bubble("GIT \u00B7 " + what, "menjalankan\u2026", false);
        new Thread(() -> {
            String out;
            try {
                JSONObject a = new JSONObject();
                if (message != null) a.put("message", message);
                out = toolGit("git_" + what, a);
            } catch (Exception e) {
                out = "gagal: " + e.getMessage();
            }
            final String o = AiGit.redact(out);
            runOnUiThread(() -> {
                tv.setText(o);
                showMedia(tv, o);
                scrollDown();
            });
        }, "arkael-ai-git").start();
    }

    private void toggleAgent() {
        final boolean on = !AiStore.agent(this);
        if (!on) {
            AiStore.setAgent(this, false);
            refreshStatus();
            bubble("AGENT", "Mode agent mati. Chat saja.", false);
            return;
        }
        /* cek root di thread lain: su bisa memunculkan dialog izin dan memblokir UI */
        new Thread(() -> {
            final boolean root = RootShell.hasRoot();
            runOnUiThread(() -> {
                if (!root) askRootMissing();
                else {
                    AiStore.setAgent(AiChatActivity.this, true);
                    refreshStatus();
                    bubble("AGENT", "Mode agent AKTIF. Perintah dijalankan sebagai root.", false);
                }
            });
        }, "arkael-ai-root").start();
    }

    private void askRootMissing() {
        new AlertDialog.Builder(this)
            .setTitle("Root tidak tersedia")
            .setMessage("Mode agent menjalankan perintah lewat su. Perangkat ini belum memberi akses root, jadi perintah akan gagal.\n\nNyalakan tetap?")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Nyalakan", (d, w) -> {
                AiStore.setAgent(this, true);
                refreshStatus();
                bubble("AGENT", "Mode agent AKTIF. Perintah dijalankan sebagai root dan ditampilkan di chat.", false);
            })
            .show();
    }

    /** Editor untuk satu provider: key, model, custom URL, tes. */
    private void editProvider(final String prov) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(14), dp(20), dp(4));

        final EditText eKey = AiStore.needsKey(prov) ? field("tempel API key di sini", AiStore.key(this, prov), true) : null;
        final EditText eModel = field("model", AiStore.model(this, prov), false);
        final EditText eUrl = "custom".equals(prov)
                ? field("https://host/v1/chat/completions", AiStore.customUrl(this), false) : null;

        col.addView(tag("PROVIDER"));
        TextView pv = new TextView(this);
        pv.setText(AiStore.label(prov));
        pv.setTextColor(Color.parseColor(TXT));
        pv.setTextSize(14);
        pv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        col.addView(pv);

        col.addView(tag("API KEY \u00B7 diisi manual, disimpan di perangkat"));
        col.addView(eKey);
        if (eUrl != null) {
            col.addView(tag("ENDPOINT"));
            col.addView(eUrl);
        }
        col.addView(tag("MODEL"));
        col.addView(eModel);

        TextView hint = new TextView(this);
        hint.setText("Endpoint: " + AiStore.url(prov, AiStore.customUrl(this))
                + "\nTombol Tes mengirim satu permintaan kecil. Kalau berhasil, pengaturan langsung disimpan.");
        hint.setTextColor(Color.parseColor(SUB));
        hint.setTextSize(11);
        hint.setPadding(0, dp(10), 0, 0);
        col.addView(hint);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);

        new AlertDialog.Builder(this)
            .setTitle("Atur " + AiStore.label(prov))
            .setView(sv)
            .setNegativeButton("Batal", null)
            .setNeutralButton("Tes", (d, w) -> testKey(prov, eKey, eModel, eUrl))
            .setPositiveButton("Simpan", (d, w) -> {
                persist(prov, eKey, eModel, eUrl);
                Toast.makeText(this, "Tersimpan: " + AiStore.label(prov), Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void persist(String prov, EditText eKey, EditText eModel, EditText eUrl) {
        AiStore.setProvider(this, prov);
        if (eKey != null) AiStore.setKey(this, prov, eKey.getText().toString());
        if (eModel != null) AiStore.setModel(this, prov, eModel.getText().toString());
        if (eUrl != null) AiStore.setCustomUrl(this, eUrl.getText().toString());
        refreshStatus();
    }

    private void testKey(final String prov, EditText eKey, EditText eModel, EditText eUrl) {
        final String key = eKey == null ? "" : eKey.getText().toString().trim();
        final String model = eModel == null ? AiStore.model(this, prov) : eModel.getText().toString().trim();
        final String custom = eUrl == null ? AiStore.customUrl(this) : eUrl.getText().toString().trim();
        final String ep = AiStore.url(prov, custom);

        if (AiStore.needsKey(prov) && key.isEmpty()) {
            Toast.makeText(this, "Isi API key dulu", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ep.isEmpty()) {
            Toast.makeText(this, "Endpoint masih kosong", Toast.LENGTH_SHORT).show();
            return;
        }

        final TextView tv = bubble("TES " + AiStore.label(prov), "Menghubungi " + ep + "\u2026", false);
        final StringBuilder a = new StringBuilder();
        final String shownModel = model.isEmpty() ? AiStore.defModel(prov) : model;

        JSONArray msgs = new JSONArray();
        try {
            JSONObject m = new JSONObject();
            m.put("role", "user");
            m.put("content", "ping");
            msgs.put(m);
        } catch (Exception ignore) {}

        AiClient.chat(ep, prov, key, shownModel, AiStore.tokenField(prov),
                AiStore.maxTokens(this), msgs, null, false, new AiClient.Sink() {
            @Override public void onDelta(String t) {}
            @Override public void onDone(String full, JSONArray calls) {
                runOnUiThread(() -> {
                    tv.setText("Koneksi OK \u2713  " + AiStore.label(prov) + " \u00B7 " + shownModel
                            + "\n\n" + (full.length() > 300 ? full.substring(0, 300) + "\u2026" : full));
                    AiStore.setProvider(AiChatActivity.this, prov);
                    if (eKey != null) AiStore.setKey(AiChatActivity.this, prov, key);
                    AiStore.setModel(AiChatActivity.this, prov, shownModel);
                    if ("custom".equals(prov)) AiStore.setCustomUrl(AiChatActivity.this, custom);
                    refreshStatus();
                    Toast.makeText(AiChatActivity.this, "Tersimpan", Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onError(String err) {
                runOnUiThread(() -> tv.setText("Gagal \u2717  " + AiStore.label(prov)
                        + "\n\n" + err + "\n\nCek key, model, dan endpoint."));
            }
        });
    }

    private TextView tag(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.parseColor(ACCENT));
        t.setTextSize(10);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private EditText field(String hint, String value, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(Color.parseColor(TXT));
        e.setHintTextColor(Color.parseColor(SUB));
        e.setTextSize(13);
        e.setBackground(bg(CARD, 12));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (password) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }
}
