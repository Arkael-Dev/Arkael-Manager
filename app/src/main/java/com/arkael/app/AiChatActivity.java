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
            bubble("AI", "API key belum diisi.\n\nTekan tombol gear di kanan atas, tempel API key dari provider kamu, lalu Simpan.\n\nBase URL default: " + AiStore.DEF_BASE
                    + "\nModel default: " + AiStore.DEF_MODEL, false);
            openSettings();
        } else {
            bubble("AI", "Siap. Model: " + AiStore.model(this) + "\nBase URL: " + AiStore.base(this)
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
        String mode = AiStore.agent(this) ? "agent: root" : "mode chat";
        status.setText(AiStore.model(this) + "  \u00B7  " + mode);
    }

    private void onSend() {
        if (busy) { Toast.makeText(this, "Tunggu jawaban sebelumnya", Toast.LENGTH_SHORT).show(); return; }
        final String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
        if (!AiStore.ready(this)) { openSettings(); return; }

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
        final StringBuilder acc = new StringBuilder();
        ask(ai, acc);
    }

    private void ask(final TextView ai, final StringBuilder acc) {
        JSONArray msgs = buildMessages();
        AiClient.chat(AiStore.base(this), AiStore.key(this), AiStore.model(this), msgs,
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
        if (!AiStore.agent(this)) { busy = false; return; }
        runTools(ai, calls, 0);
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

    private void runTools(final TextView ai, final JSONArray calls, final int depth) {
        if (depth >= MAX_TOOL_LOOPS) { busy = false; bubble("AGENT", "Batas langkah tercapai.", false); return; }
        new Thread(() -> {
            for (int i = 0; i < calls.length(); i++) {
                String cmd = "";
                String id = "";
                try {
                    JSONObject p = calls.getJSONObject(i);
                    id = p.optString("id");
                    JSONObject fn = p.optJSONObject("function");
                    if (fn == null) continue;
                    JSONObject args = new JSONObject(fn.optString("arguments", "{}"));
                    cmd = args.optString("command", "");
                } catch (Exception ignore) {}

                final String show = cmd;
                runOnUiThread(() -> bubble("AGENT \u00B7 run_bash", show, false));

                String out = "";
                try { out = RootShell.run(cmd); } catch (Exception e) { out = "gagal: " + e.getMessage(); }
                if (out == null) out = "";
                if (out.length() > MAX_TOOL_OUT) out = out.substring(0, MAX_TOOL_OUT) + "\n\u2026(output dipotong)";
                if (out.trim().isEmpty()) out = "(tidak ada output)";

                final String o = out;
                runOnUiThread(() -> bubble("OUTPUT", o, false));

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
                final StringBuilder a = new StringBuilder();
                ask(next, a);
            });
        }, "arkael-ai-tool").start();
    }

    private JSONArray buildMessages() {
        JSONArray out = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", "Kamu asisten di dalam aplikasi Android Arkael Manager. "
                    + "Jawab singkat, jelas, pakai bahasa yang sama dengan pengguna. "
                    + (AiStore.agent(this)
                        ? "Kamu punya tool run_bash untuk menjalankan perintah shell di perangkat (akses root lewat su). Gunakan hanya jika perlu, jelaskan singkat sebelum menjalankan."
                        : "Mode chat saja: jangan mengaku sudah menjalankan perintah."));
            out.put(sys);
            for (int i = 0; i < history.length(); i++) out.put(history.get(i));
        } catch (Exception ignore) {}
        return out;
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

            return new JSONArray().put(tool);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void openSettings() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(14), dp(20), dp(4));

        final EditText eKey = field("API key (tempel di sini)", AiStore.key(this), true);
        final EditText eBase = field("Base URL", AiStore.base(this), false);
        final EditText eModel = field("Model", AiStore.model(this), false);

        col.addView(tag("API KEY \u00B7 diisi manual"));
        col.addView(eKey);
        col.addView(tag("BASE URL (kompatibel OpenAI)"));
        col.addView(eBase);
        col.addView(tag("MODEL"));
        col.addView(eModel);

        final android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText("Mode agent (jalankan perintah, butuh root)");
        cb.setTextColor(Color.parseColor(TXT));
        cb.setTextSize(13);
        cb.setChecked(AiStore.agent(this));
        cb.setPadding(0, dp(10), 0, 0);
        col.addView(cb);

        TextView hint = new TextView(this);
        hint.setText("Contoh: api.deepseek.com/v1 \u2192 model deepseek-chat\nOpenAI: api.openai.com/v1 \u2192 gpt-4o-mini\nGroq: api.groq.com/openai/v1 \u2192 llama-3.3-70b-versatile");
        hint.setTextColor(Color.parseColor(SUB));
        hint.setTextSize(11);
        hint.setPadding(0, dp(10), 0, 0);
        col.addView(hint);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);

        new AlertDialog.Builder(this)
            .setTitle("Pengaturan AI")
            .setView(sv)
            .setNegativeButton("Batal", null)
            .setNeutralButton("Tes", (d, w) -> testKey(eKey, eBase, eModel))
            .setPositiveButton("Simpan", (d, w) -> {
                AiStore.save(this, eBase.getText().toString(), eKey.getText().toString(),
                        eModel.getText().toString(), cb.isChecked());
                refreshStatus();
                Toast.makeText(this, AiStore.ready(this) ? "Tersimpan" : "API key masih kosong", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void testKey(EditText eKey, EditText eBase, EditText eModel) {
        final String k = eKey.getText().toString().trim();
        final String base = eBase.getText().toString().trim();
        final String model = eModel.getText().toString().trim();
        if (k.isEmpty()) { Toast.makeText(this, "Isi API key dulu", Toast.LENGTH_SHORT).show(); return; }

        AiStore.save(this, base, k, model, AiStore.agent(this));
        final TextView tv = bubble("AI", "Menguji koneksi\u2026", false);
        final StringBuilder a = new StringBuilder();
        JSONArray msgs = new JSONArray();
        try {
            JSONObject m = new JSONObject();
            m.put("role", "user");
            m.put("content", "ping");
            msgs.put(m);
        } catch (Exception ignore) {}
        AiClient.chat(base, k, model, msgs, null, false, new AiClient.Sink() {
            @Override public void onDelta(String t) {}
            @Override public void onDone(String full, JSONArray calls) {
                runOnUiThread(() -> tv.setText("Koneksi OK \u2713\n" + (full.length() > 300 ? full.substring(0, 300) + "\u2026" : full)));
            }
            @Override public void onError(String err) {
                runOnUiThread(() -> tv.setText("Gagal \u2717\n" + err));
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
