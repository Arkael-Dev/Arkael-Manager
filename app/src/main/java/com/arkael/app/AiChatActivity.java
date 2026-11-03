package com.arkael.app;

import android.content.Context;
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
        return c;
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
        if (busy) { Toast.makeText(this, "Tunggu jawaban sebelumnya", Toast.LENGTH_SHORT).show(); return; }
        final String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
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

        bubble("KAMU", q, true);
        try {
            JSONObject m = new JSONObject();
            m.put("role", "user");
            m.put("content", q);
            history.put(m);
        } catch (Exception ignore) {}

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

        if (calls == null || calls.length() == 0) { busy = false; return; }
        runTools(ai, calls);
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
                } else {
                    out = "tool tidak dikenal: " + name;
                }
                if (out == null) out = "";
                if (out.length() > MAX_TOOL_OUT) out = out.substring(0, MAX_TOOL_OUT) + "\n\u2026(output dipotong)";
                if (out.trim().isEmpty()) out = "(tidak ada output)";

                final String o = out;
                runOnUiThread(() -> {
                    TextView t = bubble("OUTPUT \u00B7 " + name, o, false);
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

    /** generate_image: hasil berupa path file, lalu ditampilkan di dalam chat. */
    private String toolImage(JSONObject args) {
        String prompt = args.optString("prompt", "");
        String size = args.optString("size", "1024x1024");
        runOnUiThread(() -> bubble("AGENT \u00B7 generate_image", prompt, false));
        try {
            String prov = AiStore.provider(this);
            String path = AiImage.generate(this, prov, AiStore.key(this),
                    AiStore.customUrl(this), prompt, size);
            File f = new File(path);
            return "gambar tersimpan\n" + path + "\nukuran file: " + (f.length() / 1024) + " KB"
                    + "\nformat: " + AiImage.normSize(size);
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
        int seconds = AiVideo.clampSeconds(args.optInt("seconds", 10));
        String sizeStr = args.optString("size", "portrait");
        runOnUiThread(() -> bubble("AGENT \u00B7 make_video", mode + " \u00B7 " + seconds + " dtk \u00B7 " + sizeStr, false));

        try {
            if ("trim".equals(mode)) {
                String src = args.optString("source", "").trim();
                if (src.isEmpty()) return "mode trim butuh parameter source (path video)";
                File in = resolve(src);
                if (!in.isFile()) return "file video tidak ditemukan: " + src;
                String out = AiVideo.trim(this, in, seconds);
                return "video dipotong " + seconds + " dtk\n" + out;
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
                    + "\nukuran file: " + (f.length() / 1024) + " KB";
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
            JSONObject iparams = new JSONObject();
            iparams.put("type", "object");
            iparams.put("properties", ip);
            iparams.put("required", new JSONArray().put("prompt"));

            JSONObject ifn = new JSONObject();
            ifn.put("name", "generate_image");
            ifn.put("description", "Membuat gambar dari teks dan menyimpannya sebagai file PNG di perangkat.");
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

            return new JSONArray().put(tool).put(itool).put(vtool);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** Menu AI ala ai-agent: pilih provider, isi key, pilih model, tes, simpan. */
    private void openSettings() {
        final String[] names = new String[AiStore.PROVIDERS.length];
        final String cur = AiStore.provider(this);
        int check = 0;
        for (int i = 0; i < AiStore.PROVIDERS.length; i++) {
            String p = AiStore.PROVIDERS[i];
            String mark;
            if ("ollama".equals(p)) mark = "lokal, tanpa key";
            else if (AiStore.key(this, p).isEmpty()) mark = "butuh API key";
            else mark = "key tersimpan";
            names[i] = AiStore.label(p) + "  \u00B7  " + mark;
            if (p.equals(cur)) check = i;
        }
        new AlertDialog.Builder(this)
            .setTitle("Pilih AI")
            .setSingleChoiceItems(names, check, (d, w) -> {
                d.dismiss();
                editProvider(AiStore.PROVIDERS[w]);
            })
            .setNegativeButton("Batal", null)
            .setNeutralButton("Agent", (d, w) -> toggleAgent())
            .show();
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

        if (eKey != null) {
            col.addView(tag("API KEY \u00B7 diisi manual, disimpan di perangkat"));
            col.addView(eKey);
        } else {
            col.addView(tag("API KEY"));
            TextView nk = new TextView(this);
            nk.setText("Ollama lokal, tidak butuh key.");
            nk.setTextColor(Color.parseColor(SUB));
            nk.setTextSize(12);
            col.addView(nk);
        }
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
        final String key = eKey == null ? "ollama" : eKey.getText().toString().trim();
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
