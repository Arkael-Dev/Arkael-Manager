package com.arkael.app;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayInputStream;
import java.util.Locale;

/** Browser umum bebas iklan: pemblokir host + JS kosmetik dari AdBlock, tanpa pop-up, tanpa redirect otomatis. */
public class WebBrowserActivity extends AppCompatActivity {
    static final String EXTRA_URL = "url";
    private static final String HOME = "https://search.brave.com/";
    private static final String SEARCH = "https://search.brave.com/search?q=";
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private WebView web;
    private ProgressBar bar;
    private EditText addr;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private String mobileUa;
    private boolean desktop;
    private volatile int blocked;
    private long lastToast;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    private TextView btn(String s, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(20);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(l);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return t;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0A0D12"));
        getWindow().setNavigationBarColor(Color.parseColor("#0A0D12"));

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            v.setPadding(0, ins.getSystemWindowInsetTop(), 0, ins.getSystemWindowInsetBottom());
            return ins;
        });

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(Color.parseColor("#0A0D12"));
        top.setPadding(dp(4), dp(3), dp(4), dp(3));
        top.addView(btn("←", v -> goBackOrFinish()));

        addr = new EditText(this);
        addr.setSingleLine(true);
        addr.setHint("Search or enter address");
        addr.setHintTextColor(Color.parseColor("#667085"));
        addr.setTextColor(Color.WHITE);
        addr.setTextSize(14);
        addr.setBackground(round(Color.parseColor("#141A22"), 12));
        addr.setPadding(dp(12), 0, dp(12), 0);
        addr.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addr.setImeOptions(EditorInfo.IME_ACTION_GO);
        addr.setOnEditorActionListener((v, id, ev) -> {
            go(addr.getText().toString());
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(addr.getWindowToken(), 0);
            addr.clearFocus();
            return true;
        });
        addr.setOnFocusChangeListener((v, f) -> { if (f) addr.selectAll(); });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, dp(38), 1f);
        al.leftMargin = dp(4);
        al.rightMargin = dp(4);
        top.addView(addr, al);

        top.addView(btn("⟳", v -> { if (bar.getVisibility() == View.VISIBLE) web.stopLoading(); else web.reload(); }));
        top.addView(btn("⋮", v -> menu(v)));
        col.addView(top, new LinearLayout.LayoutParams(-1, dp(48)));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        col.addView(bar, new LinearLayout.LayoutParams(-1, dp(3)));

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        col.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
        setupWeb();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { goBackOrFinish(); }
        });

        String url = getIntent().getStringExtra(EXTRA_URL);
        web.loadUrl(url == null ? HOME : url);
    }

    private void go(String in) {
        String s = in == null ? "" : in.trim();
        if (s.isEmpty()) return;
        String url;
        if (s.matches("(?i)^https?://\\S+$")) url = s;
        else if (!s.contains(" ") && s.contains(".")) url = "https://" + s;
        else url = SEARCH + Uri.encode(s);
        web.loadUrl(url);
    }

    private void goBackOrFinish() {
        if (customView != null) { exitFullscreen(); return; }
        if (web != null && web.canGoBack()) web.goBack(); else finish();
    }

    private void menu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, "Forward").setEnabled(web.canGoForward());
        m.add(0, 2, 1, desktop ? "✓ Desktop site" : "Desktop site");
        m.add(0, 3, 2, "Ads blocked: " + blocked);
        m.add(0, 4, 3, "Copy link");
        m.add(0, 5, 4, "Clear cookies and history");
        m.add(0, 6, 5, "Close");
        pm.setOnMenuItemClickListener(it -> {
            switch (it.getItemId()) {
                case 1: if (web.canGoForward()) web.goForward(); break;
                case 2:
                    desktop = !desktop;
                    web.getSettings().setUserAgentString(desktop ? DESKTOP_UA : mobileUa);
                    web.reload();
                    break;
                case 4:
                    ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb != null && web.getUrl() != null) {
                        cb.setPrimaryClip(ClipData.newPlainText("url", web.getUrl()));
                        Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case 5:
                    CookieManager.getInstance().removeAllCookies(null);
                    web.clearHistory();
                    web.clearCache(true);
                    web.clearFormData();
                    Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show();
                    break;
                case 6: finish(); break;
                default: break;
            }
            return true;
        });
        pm.show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        String ua = s.getUserAgentString();
        if (ua != null) ua = ua.replace("; wv", "").replaceAll("Version/\\d+\\.\\d+ ", "");
        mobileUa = ua;
        if (ua != null) s.setUserAgentString(ua);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, false);

        web.setDownloadListener((url, agent, cd, mime, len) -> {
            try {
                String name = URLUtil.guessFileName(url, cd, mime);
                DownloadManager.Request rq = new DownloadManager.Request(Uri.parse(url));
                if (mime != null) rq.setMimeType(mime);
                if (agent != null) rq.addRequestHeader("User-Agent", agent);
                String ck = CookieManager.getInstance().getCookie(url);
                if (ck != null) rq.addRequestHeader("Cookie", ck);
                rq.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                rq.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(rq);
                Toast.makeText(this, "Downloading " + name, Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show();
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (AdBlock.isBlocked(u.toString(), u.getHost())) {
                    blocked++;
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!r.isForMainFrame()) return false;
                Uri u = r.getUrl();
                String sch = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
                if (!sch.equals("http") && !sch.equals("https")) { toastBlocked(); return true; }
                if (AdBlock.isBlocked(u.toString(), u.getHost())) { blocked++; toastBlocked(); return true; }
                String cur = v.getUrl();
                if (!r.hasGesture() && !r.isRedirect() && cur != null && !cur.isEmpty()
                        && !cur.equals("about:blank") && !sameSite(cur, u.getHost())) {
                    blocked++;
                    toastBlocked();
                    return true;
                }
                return false;
            }

            @Override public void onPageStarted(WebView v, String url, Bitmap f) {
                bar.setVisibility(View.VISIBLE);
                if (!addr.hasFocus() && url != null) addr.setText(url);
                v.evaluateJavascript(AdBlock.JS, null);
            }

            @Override public void onPageFinished(WebView v, String url) {
                bar.setVisibility(View.GONE);
                v.evaluateJavascript(AdBlock.JS, null);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) { bar.setProgress(p); }

            @Override public boolean onCreateWindow(WebView v, boolean dialog, boolean gesture, android.os.Message m) {
                return false;
            }

            @Override public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view;
                customCb = cb;
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                view.setBackgroundColor(Color.BLACK);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                hideSystemBars(true);
            }

            @Override public void onHideCustomView() { exitFullscreen(); }
        });
    }

    private static String bare(String h) {
        h = h == null ? "" : h.toLowerCase(Locale.ROOT);
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    private boolean sameSite(String currentUrl, String newHost) {
        String a = bare(Uri.parse(currentUrl).getHost()), b = bare(newHost);
        if (a.isEmpty() || b.isEmpty()) return true;
        return a.equals(b) || a.endsWith("." + b) || b.endsWith("." + a);
    }

    private void toastBlocked() {
        long now = System.currentTimeMillis();
        if (now - lastToast < 2500) return;
        lastToast = now;
        Toast.makeText(this, "Blocked an auto-redirect", Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars(boolean hide) {
        int f = hide
                ? (View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                   | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
                : View.SYSTEM_UI_FLAG_VISIBLE;
        getWindow().getDecorView().setSystemUiVisibility(f);
    }

    private void exitFullscreen() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        if (customCb != null) { try { customCb.onCustomViewHidden(); } catch (Exception ignored) {} customCb = null; }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        hideSystemBars(false);
    }

    @Override protected void onPause() { super.onPause(); if (web != null) web.onPause(); }

    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }

    @Override protected void onDestroy() {
        if (web != null) {
            if (web.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) web.getParent()).removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
