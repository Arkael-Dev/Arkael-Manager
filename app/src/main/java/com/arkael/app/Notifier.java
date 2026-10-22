package com.arkael.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** Channels + the two notifications: donation reminder and "new version". Both open a link on tap. */
final class Notifier {
    private Notifier() {}

    static final String DONATE_URL = "https://t.me/arkaelkernel/716";
    static final String REPO_URL = "https://github.com/Arkael-Dev/Arkael-Manager";
    private static final String CH_DONATE = "ax_donate";
    private static final String CH_UPDATE = "ax_update";
    private static final int ID_DONATE = 7101, ID_UPDATE = 7102;

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        // IMPORTANCE_HIGH = pops up at the top of the screen (heads-up)
        nm.createNotificationChannel(new NotificationChannel(CH_DONATE, c.getString(R.string.ax_ch_donate), NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_UPDATE, c.getString(R.string.ax_ch_update), NotificationManager.IMPORTANCE_HIGH));
    }

    static boolean canPost(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        return NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    private static PendingIntent open(Context c, String url, int req) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getActivity(c, req, i, fl);
    }

    @SuppressLint("MissingPermission")
    private static void post(Context c, String ch, int id, int icon, String title, String text, String url) {
        if (!canPost(c)) return;
        ensureChannels(c);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, ch)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setContentIntent(open(c, url, id));
        try { NotificationManagerCompat.from(c).notify(id, b.build()); } catch (SecurityException ignored) {}
    }

    static void donate(Context c) {
        post(c, CH_DONATE, ID_DONATE, R.drawable.ax_ic_heart,
                c.getString(R.string.ax_donate_title), c.getString(R.string.ax_donate_text), DONATE_URL);
    }

    static void update(Context c, String tag, String url) {
        post(c, CH_UPDATE, ID_UPDATE, R.drawable.ax_ic_update,
                c.getString(R.string.ax_update_title), c.getString(R.string.ax_update_text, tag), url);
    }
}
