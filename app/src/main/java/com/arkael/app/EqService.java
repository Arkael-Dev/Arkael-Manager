package com.arkael.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Menjaga equalizer tetap hidup. Urutan percobaan:
 *  1. daemon root (app_process sebagai uid 0)  -> session 0 global + per-sesi
 *  2. mesin yang sama di dalam app             -> session 0 (kalau ROM mengizinkan) / per-sesi via "su dumpsys"
 */
public class EqService extends Service {
    static final String ACTION_APPLY = "com.arkael.app.EQ_APPLY";
    static final String ACTION_STOP = "com.arkael.app.EQ_STOP";
    private static final String CH = "ark_eq";
    private static final int NID = 7311;
    private static final String DAEMON_CLASS = "com.arkael.app.EqRootDaemon";

    private static volatile String status = "Mati";
    private static volatile boolean running = false;
    private static final StringBuilder LOG = new StringBuilder();

    static String status() { return status; }
    static boolean isRunning() { return running; }

    static String log() { synchronized (LOG) { return LOG.length() == 0 ? "(belum ada log)" : LOG.toString(); } }

    static void addLog(String s) {
        synchronized (LOG) {
            LOG.append(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()))
                    .append("  ").append(s).append('\n');
            if (LOG.length() > 12000) LOG.delete(0, LOG.length() - 9000);
        }
    }

    /** Dipanggil UI setiap setelan berubah (dan saat master dinyalakan). */
    static void push(Context c) {
        EqConfig cfg = EqConfig.load(c);
        if (!cfg.enabled && !running) return;
        Intent i = new Intent(c, EqService.class).setAction(ACTION_APPLY);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception e) {
            addLog("Gagal start service: " + EqEngine.why(e));
        }
    }

    // ------------------------------------------------------------------ state

    private HandlerThread ht;
    private Handler h;
    private Process dproc;
    private OutputStream dout;
    private volatile boolean daemonUp;
    private volatile boolean daemonBad;
    private volatile String daemonStatus = "";
    private EqEngine local;
    private boolean localMode;
    private boolean sessRegistered;

    private final EqEngine.Env localEnv = new EqEngine.Env() {
        @Override public void log(String s) { addLog(s); }
        @Override public String sh(String cmd) { return RootShell.run(cmd); }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            try { if (localMode && local != null) local.tick(); } catch (Throwable t) { addLog("tick: " + EqEngine.why(t)); }
            if (h != null) h.postDelayed(this, 3000);
        }
    };

    private final BroadcastReceiver sessRx = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent it) {
            final int id = it.getIntExtra("android.media.extra.AUDIO_SESSION", 0);
            final boolean open = "android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION".equals(it.getAction());
            if (id <= 0 || h == null) return;
            h.post(new Runnable() {
                @Override public void run() {
                    if (daemonUp) send((open ? "SESS " : "UNSESS ") + id);
                    else if (local != null) { if (open) local.addSession(id); else local.removeSession(id); }
                }
            });
        }
    };

    // ------------------------------------------------------------------ lifecycle

    @Override public void onCreate() {
        super.onCreate();
        ht = new HandlerThread("ark-eq");
        ht.start();
        h = new Handler(ht.getLooper());
        running = true;
        try {
            IntentFilter f = new IntentFilter();
            f.addAction("android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION");
            f.addAction("android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION");
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(sessRx, f, Context.RECEIVER_EXPORTED);
            else registerReceiver(sessRx, f);
            sessRegistered = true;
        } catch (Throwable t) { addLog("Receiver sesi: " + EqEngine.why(t)); }
        h.postDelayed(ticker, 3000);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            EqConfig c = EqConfig.load(this);
            c.enabled = false;
            c.save(this);
            h.post(new Runnable() { @Override public void run() { shutdown(); } });
            return START_NOT_STICKY;
        }
        h.post(new Runnable() { @Override public void run() { applyNow(); } });
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onDestroy() {
        running = false;
        if (sessRegistered) try { unregisterReceiver(sessRx); } catch (Throwable ignored) { }
        final HandlerThread t = ht;
        if (h != null) h.post(new Runnable() {
            @Override public void run() { teardown(); if (t != null) t.quitSafely(); }
        });
        status = "Mati";
        super.onDestroy();
    }

    // ------------------------------------------------------------------ kerja

    private void shutdown() {
        teardown();
        status = "Mati";
        stopForeground(true);
        stopSelf();
    }

    private void teardown() {
        if (dproc != null) {
            try { send("QUIT"); } catch (Throwable ignored) { }
            try { dproc.destroy(); } catch (Throwable ignored) { }
            dproc = null; dout = null; daemonUp = false;
        }
        if (local != null) { local.releaseAll(); local = null; }
        localMode = false;
    }

    private void applyNow() {
        try {
            EqConfig c = EqConfig.load(this);
            if (!c.enabled) { shutdown(); return; }
            if (!daemonUp && !localMode) chooseBackend();
            if (daemonUp) {
                daemonStatus = "";
                send("CFG " + c.toJson());
                // jawaban datang lewat reader (@@STATUS)
            } else if (local != null) {
                setStatus(local.apply(c), false);
            }
        } catch (Throwable t) {
            addLog("applyNow: " + EqEngine.why(t));
            setStatus("GAGAL " + EqEngine.why(t), false);
        }
    }

    private void chooseBackend() {
        if (!daemonBad && startDaemon()) return;
        addLog("Memakai mesin di dalam app (tanpa daemon root)");
        local = new EqEngine(localEnv);
        localMode = true;
    }

    private boolean startDaemon() {
        try {
            if (!RootShell.hasRoot()) { addLog("Root tidak tersedia / ditolak"); daemonBad = true; return false; }
            final CountDownLatch hello = new CountDownLatch(1);
            final Process p = new ProcessBuilder("su").redirectErrorStream(true).start();
            dproc = p;
            dout = p.getOutputStream();
            final BufferedReader rd = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            Thread t = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        String ln;
                        while ((ln = rd.readLine()) != null) {
                            if (ln.startsWith("@@HELLO")) { addLog("daemon: " + ln.substring(2)); hello.countDown(); }
                            else if (ln.startsWith("@@STATUS ")) onDaemonStatus(ln.substring(9));
                            else if (ln.startsWith("@@LOG ")) addLog("daemon: " + ln.substring(6));
                            else if (ln.length() > 0) addLog("[sh] " + ln);
                        }
                    } catch (Throwable ignored) { }
                    daemonUp = false;
                    addLog("daemon berhenti");
                }
            }, "ark-eq-rd");
            t.setDaemon(true);
            t.start();

            String pkg = getPackageName();
            String cmd = "CLASSPATH=$(pm path " + pkg + " | head -n 1 | cut -d: -f2) exec app_process / " + DAEMON_CLASS + " " + pkg + "\n";
            dout.write(cmd.getBytes("UTF-8"));
            dout.flush();
            if (!hello.await(8, TimeUnit.SECONDS)) {
                addLog("daemon tidak menjawab dalam 8 detik");
                try { p.destroy(); } catch (Throwable ignored) { }
                dproc = null; dout = null; daemonBad = true;
                return false;
            }
            daemonUp = true;
            return true;
        } catch (Throwable t) {
            addLog("start daemon: " + EqEngine.why(t));
            dproc = null; dout = null; daemonBad = true;
            return false;
        }
    }

    private void onDaemonStatus(final String s) {
        if (s.startsWith("GAGAL") && h != null) {
            // Daemon jalan tapi tidak bisa memasang efek: coba mesin di dalam app sebagai cadangan.
            h.post(new Runnable() {
                @Override public void run() {
                    addLog("daemon: " + s);
                    if (daemonUp && !localMode) {
                        teardown();
                        daemonBad = true;
                        chooseBackend();
                        applyNow();
                    }
                }
            });
            return;
        }
        setStatus(s, true);
    }

    private void send(String line) {
        try {
            OutputStream o = dout;
            if (o == null) return;
            o.write((line + "\n").getBytes("UTF-8"));
            o.flush();
        } catch (Throwable t) {
            addLog("kirim ke daemon gagal: " + EqEngine.why(t));
            daemonUp = false;
        }
    }

    private void setStatus(String s, boolean viaRoot) {
        String t = s.startsWith("OK ") ? s.substring(3) : s;
        status = (viaRoot ? "Root · " : "App · ") + t;
        addLog("status: " + status);
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NID, buildNote());
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ notifikasi

    private void goForeground() {
        Notification n = buildNote();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(NID, n, 0x40000000 /* specialUse */);
            else startForeground(NID, n);
        } catch (Throwable t) {
            addLog("startForeground: " + EqEngine.why(t));
        }
    }

    private Notification buildNote() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, getString(R.string.ax_eq_title), NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        int pf = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, EqualizerActivity.class), pf);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, EqService.class).setAction(ACTION_STOP), pf);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle(getString(R.string.ax_eq_note_title))
                .setContentText(EqText.loc(this, status))
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(0, getString(R.string.ax_eq_note_stop), stop).build());
        return b.build();
    }
}
