package com.arkael.app;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayInputStream;
import java.util.Locale;

/** In-app browser for the anime / donghua sites: blocks ads, pop-ups and redirects to other sites. */
public class BrowserActivity extends AppCompatActivity {
    static final String EXTRA_URL = "url";
    static final String EXTRA_TITLE = "title";

    /** Sites that may be navigated to (top level). Anything else = an ad redirect and gets blocked. */
    private static final String[] ALLOWED = {"anichin", "animexin", "samehadaku"};

    private WebView web;
    private ProgressBar bar;
    private TextView titleTv;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private String startHost = "";
    private int blocked;

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null) { finish(); return; }
        Uri u = Uri.parse(url);
        startHost = u.getHost() == null ? "" : u.getHost();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            v.setPadding(0, ins.getSystemWindowInsetTop(), 0, ins.getSystemWindowInsetBottom());
            return ins;
        });

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // top bar
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(Color.parseColor("#0A0D12"));
        top.setPadding(dp(4), dp(2), dp(4), dp(2));
        top.addView(barBtn("←", v -> goBackOrFinish()));
        titleTv = new TextView(this);
        String t = getIntent().getStringExtra(EXTRA_TITLE);
        titleTv.setText((t == null ? startHost : t) + "  ·  " + getString(R.string.ax_no_ads));
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(14);
        titleTv.setSingleLine(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = dp(8);
        top.addView(titleTv, tl);
        top.addView(barBtn("⟳", v -> web.reload()));
        top.addView(barBtn("✕", v -> finish()));
        col.addView(top, new LinearLayout.LayoutParams(-1, dp(46)));

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

        web.loadUrl(url);
    }

    private TextView barBtn(String s, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(20);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(l);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return t;
    }

    private void goBackOrFinish() {
        if (customView != null) { exitFullscreen(); return; }
        if (web != null && web.canGoBack()) web.goBack(); else finish();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportMultipleWindows(false);              // no pop-up windows
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        // look like normal mobile Chrome (sites sometimes serve a broken page to "wv")
        String ua = s.getUserAgentString();
        if (ua != null) s.setUserAgentString(ua.replace("; wv", "").replaceAll("Version/\\d+\\.\\d+ ", ""));
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);        // embedded video players / Cloudflare check

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
                // intent:, market:, tg: ... = ads trying to open other apps
                if (!sch.equals("http") && !sch.equals("https")) { toastBlocked(); return true; }
                if (AdBlock.isBlocked(u.toString(), u.getHost())) { toastBlocked(); return true; }
                if (!allowedHost(u.getHost())) { toastBlocked(); return true; }
                return false;
            }

            @Override public void onPageStarted(WebView v, String url, Bitmap f) {
                bar.setVisibility(View.VISIBLE);
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
                return false;   // never open pop-ups
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

    private boolean allowedHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (!startHost.isEmpty() && (h.equals(startHost) || h.endsWith("." + startHost))) return true;
        for (String a : ALLOWED) if (h.contains(a)) return true;
        return false;
    }

    private long lastToast;
    private void toastBlocked() {
        long now = System.currentTimeMillis();
        if (now - lastToast < 2500) return;
        lastToast = now;
        Toast.makeText(this, R.string.ax_redirect_blocked, Toast.LENGTH_SHORT).show();
    }

    private void exitFullscreen() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        if (customCb != null) { try { customCb.onCustomViewHidden(); } catch (Exception ignored) {} customCb = null; }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        hideSystemBars(false);
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars(boolean hide) {
        int f = View.SYSTEM_UI_FLAG_VISIBLE;
        if (hide) f = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        getWindow().getDecorView().setSystemUiVisibility(f);
    }

    @Override protected void onPause() { super.onPause(); if (web != null) web.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (web != null) {
            ViewGroup p = (ViewGroup) web.getParent();
            if (p != null) p.removeView(web);
            web.stopLoading();
            web.destroy();
            web = null;
        }
    }
}
