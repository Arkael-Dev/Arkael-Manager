package com.arkael.app;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the newest GitHub release of the repo against the installed versionName.
 * Always asks GitHub (no local cache), so a new release shows up without clearing app data.
 */
final class UpdateNotifier {
    private UpdateNotifier() {}

    private static final String API = "https://api.github.com/repos/Arkael-Dev/Arkael-Manager/releases?per_page=5";
    private static final String PREFS = "ax_update";

    static final class Result {
        boolean ok, newer;
        String tag = "", url = Notifier.REPO_URL + "/releases";
    }

    /** Blocking network call: run off the main thread. */
    static Result fetch(Context c) {
        Result r = new Result();
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(API).openConnection();
            h.setUseCaches(false);
            h.setConnectTimeout(12000);
            h.setReadTimeout(15000);
            h.setRequestProperty("Accept", "application/vnd.github+json");
            h.setRequestProperty("User-Agent", "ArkaelManager");
            if (h.getResponseCode() != 200) return r;
            InputStream in = h.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONArray arr = new JSONArray(bo.toString("UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optBoolean("draft")) continue;
                r.tag = o.optString("tag_name", "");
                String u = o.optString("html_url", "");
                if (!u.isEmpty()) r.url = u;
                r.ok = !r.tag.isEmpty();
                break;
            }
            if (r.ok) r.newer = isNewer(r.tag, AppVersion.name("0"));
        } catch (Exception ignored) {
        } finally {
            if (h != null) h.disconnect();
        }
        return r;
    }

    private static final Pattern VER = Pattern.compile("\\d+(?:\\.\\d+)*");

    static boolean isNewer(String remote, String local) {
        Matcher a = VER.matcher(remote == null ? "" : remote), b = VER.matcher(local == null ? "" : local);
        if (!a.find() || !b.find()) return remote != null && !remote.equals(local);
        String[] x = a.group().split("\\."), y = b.group().split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length ? Long.parseLong(x[i]) : 0, q = i < y.length ? Long.parseLong(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    /** Background (worker): one notification per new release tag. */
    static void checkAndNotify(Context c) {
        Result r = fetch(c);
        if (!r.ok || !r.newer) return;
        android.content.SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (r.tag.equals(sp.getString("notified", ""))) return;
        Notifier.update(c, r.tag, r.url);
        sp.edit().putString("notified", r.tag).apply();
    }

    /** Foreground: dialog when newer; on manual check also says "up to date" / "failed". Also fires the notification. */
    static void checkInApp(final Activity a, final boolean manual) {
        new Thread(() -> {
            final Result r = fetch(a);
            if (r.ok && r.newer) {
                android.content.SharedPreferences sp = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                if (!r.tag.equals(sp.getString("notified", ""))) {
                    Notifier.update(a.getApplicationContext(), r.tag, r.url);
                    sp.edit().putString("notified", r.tag).apply();
                }
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                if (r.ok && r.newer) {
                    new AlertDialog.Builder(a)
                            .setTitle(R.string.ax_update_title)
                            .setMessage(a.getString(R.string.ax_update_text, r.tag))
                            .setPositiveButton(R.string.ax_download, (d, w) ->
                                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.url))))
                            .setNegativeButton(R.string.ax_later, null)
                            .show();
                } else if (manual) {
                    Toast.makeText(a, r.ok ? R.string.ax_uptodate : R.string.ax_update_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }
}
