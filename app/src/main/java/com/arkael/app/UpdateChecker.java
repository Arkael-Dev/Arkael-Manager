package com.arkael.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Cek rilis terbaru di GitHub; kalau lebih baru dari versi terpasang, tampilkan dialog update. */
public final class UpdateChecker {
    private static final String API =
            "https://api.github.com/repos/Arkael-Dev/Arkael-Manager/releases/latest";
    private static boolean done = false;

    private UpdateChecker() {}

    public static void check(final Activity act) {
        if (done) return;
        done = true;
        final SharedPreferences sp = act.getSharedPreferences("arkael_update", Context.MODE_PRIVATE);
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() != 200) return;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String ln;
                    while ((ln = br.readLine()) != null) sb.append(ln).append('\n');
                }
                JSONObject j = new JSONObject(sb.toString());
                final String tag = j.optString("tag_name", "");
                String url = j.optString("html_url", "");
                JSONArray assets = j.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        if (a.optString("name").endsWith(".apk")) {
                            url = a.optString("browser_download_url", url);
                            break;
                        }
                    }
                }
                final String link = url;
                final String notes = j.optString("body", "");
                final String cur = current(act);
                if (!tag.isEmpty() && newer(tag, cur) && !tag.equals(sp.getString("skip", ""))) {
                    act.runOnUiThread(() -> show(act, sp, tag, cur, notes, link));
                }
            } catch (Exception ignored) {
                // offline / rate limit / repo private: diam saja
            }
        }, "arkael-update").start();
    }

    private static String current(Context c) {
        try {
            String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return v == null ? "0" : v;
        } catch (Exception e) {
            return "0";
        }
    }

    /** true bila a lebih besar dari b (mis. "v3.1" > "3.0"). */
    static boolean newer(String a, String b) {
        int[] x = parts(a), y = parts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int[] parts(String v) {
        String[] s = v.replaceAll("[^0-9.]", "").split("\\.");
        int[] r = new int[s.length];
        for (int i = 0; i < s.length; i++) {
            try { r[i] = Integer.parseInt(s[i]); } catch (Exception e) { r[i] = 0; }
        }
        return r;
    }

    private static void show(final Activity act, final SharedPreferences sp, final String tag,
                             String cur, String notes, final String link) {
        if (act.isFinishing()) return;
        if (notes.length() > 600) notes = notes.substring(0, 600) + "...";
        String msg = "Installed: v" + cur + "\nLatest: " + tag + (notes.isEmpty() ? "" : "\n\n" + notes);
        new AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Update available")
                .setMessage(msg)
                .setPositiveButton("Update", (d, w) -> {
                    try { act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))); }
                    catch (Exception ignored) {}
                })
                .setNegativeButton("Later", null)
                .setNeutralButton("Skip this version", (d, w) -> sp.edit().putString("skip", tag).apply())
                .show();
    }
}
