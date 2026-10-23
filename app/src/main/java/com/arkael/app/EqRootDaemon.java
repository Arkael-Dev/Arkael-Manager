package com.arkael.app;

import android.app.Application;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Proses ringan yang jalan sebagai ROOT (dijalankan EqService lewat "su" + app_process).
 * Alasannya: efek di output mix global (session 0) hanya diizinkan untuk uid root/system.
 *
 * Protokol (satu baris per pesan, lewat stdin/stdout):
 *   app -> daemon : CFG {json} | SESS <id> | UNSESS <id> | QUIT
 *   daemon -> app : @@HELLO ... | @@STATUS OK/GAGAL ... | @@LOG ...
 * Kalau stdin tertutup (app mati) daemon melepas semua efek lalu keluar.
 */
public final class EqRootDaemon {
    private static final Object OUT_LOCK = new Object();
    private static volatile String opPkg = "com.android.shell";

    /** Konteks palsu supaya AudioEffect punya nama paket (di app_process tidak ada Application). */
    private static final class FakeCtx extends ContextWrapper {
        FakeCtx() { super(null); }
        @Override public String getPackageName() { return opPkg; }
        public String getOpPackageName() { return opPkg; }
        @Override public android.content.Context getApplicationContext() { return this; }
    }

    private static void emit(String s) {
        synchronized (OUT_LOCK) {
            System.out.println("@@" + s.replace('\n', ' ').replace('\r', ' '));
            System.out.flush();
        }
    }

    @SuppressWarnings({"PrivateApi", "DiscouragedPrivateApi"})
    private static String prepareApp() {
        try {
            Class<?> atc = Class.forName("android.app.ActivityThread");
            Constructor<?> ctor = atc.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object at = ctor.newInstance();
            Field cur = atc.getDeclaredField("sCurrentActivityThread");
            cur.setAccessible(true);
            cur.set(null, at);

            Class<?> abd = Class.forName("android.app.ActivityThread$AppBindData");
            Constructor<?> ac = abd.getDeclaredConstructor();
            ac.setAccessible(true);
            Object data = ac.newInstance();
            ApplicationInfo ai = new ApplicationInfo();
            ai.packageName = opPkg;
            Field f = abd.getDeclaredField("appInfo");
            f.setAccessible(true);
            f.set(data, ai);
            Field mb = atc.getDeclaredField("mBoundApplication");
            mb.setAccessible(true);
            mb.set(at, data);

            Application app = new Application();
            Field base = ContextWrapper.class.getDeclaredField("mBase");
            base.setAccessible(true);
            base.set(app, new FakeCtx());
            Field mi = atc.getDeclaredField("mInitialApplication");
            mi.setAccessible(true);
            mi.set(at, app);
            return null;
        } catch (Throwable t) {
            return EqEngine.why(t);
        }
    }

    private static String runSh(String cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start();
            InputStream in = p.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < 4 * 1024 * 1024) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } catch (Throwable t) {
            return "";
        } finally {
            if (p != null) p.destroy();
        }
    }

    public static void main(String[] args) {
        final String appPkg = args.length > 0 ? args[0] : "com.arkael.app";
        String prep = prepareApp();
        emit("HELLO uid=" + android.os.Process.myUid() + " sdk=" + Build.VERSION.SDK_INT
                + " prep=" + (prep == null ? "ok" : prep));

        final EqEngine eng = new EqEngine(new EqEngine.Env() {
            @Override public void log(String s) { emit("LOG " + s); }
            @Override public String sh(String cmd) { return runSh(cmd); }
        });

        // Cari nama paket yang diterima AudioFlinger untuk uid root.
        String[] cands = {"com.android.shell", "android", "root", appPkg};
        String chosen = null;
        for (String c : cands) {
            opPkg = c;
            String err = eng.probeGlobal();
            if (err == null) { chosen = c; break; }
            emit("LOG probe session 0 dengan paket '" + c + "': " + err);
        }
        if (chosen == null) {
            opPkg = "com.android.shell";
            emit("LOG session 0 tidak bisa dibuat oleh root di ROM ini; hanya jalur per-sesi yang mungkin");
        } else {
            emit("LOG session 0 OK (paket '" + chosen + "')");
        }

        Thread ticker = new Thread(new Runnable() {
            @Override public void run() {
                while (true) {
                    try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
                    try { eng.tick(); } catch (Throwable t) { emit("LOG tick: " + EqEngine.why(t)); }
                }
            }
        }, "eq-tick");
        ticker.setDaemon(true);
        ticker.start();

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                try {
                    if (line.startsWith("CFG ")) {
                        emit("STATUS " + eng.apply(EqConfig.fromJson(line.substring(4))));
                    } else if (line.startsWith("SESS ")) {
                        eng.addSession(Integer.parseInt(line.substring(5).trim()));
                    } else if (line.startsWith("UNSESS ")) {
                        eng.removeSession(Integer.parseInt(line.substring(7).trim()));
                    } else if (line.equals("QUIT")) {
                        break;
                    }
                } catch (Throwable t) {
                    emit("LOG perintah '" + line.substring(0, Math.min(12, line.length())) + "' gagal: " + EqEngine.why(t));
                }
            }
        } catch (Throwable t) {
            emit("LOG baca stdin: " + EqEngine.why(t));
        }
        try { eng.releaseAll(); } catch (Throwable ignored) { }
        System.exit(0);
    }
}
