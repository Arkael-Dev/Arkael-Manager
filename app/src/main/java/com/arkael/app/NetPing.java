package com.arkael.app;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Latensi jaringan tanpa root: waktu TCP handshake ke server DNS publik.
 * ICMP (ping biasa) sering diblokir untuk app non-root di Android, TCP tidak.
 */
final class NetPing {
    private NetPing() {}

    static final String[] NAMES = {"Cloudflare", "Google"};
    static final String[] HOSTS = {"1.1.1.1", "8.8.8.8"};
    static final int[] PORTS = {53, 53};

    /** @return milidetik, atau -1 kalau gagal. */
    static int ms(String host, int port, int timeoutMs) {
        Socket s = new Socket();
        try {
            long t0 = System.nanoTime();
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            long d = (System.nanoTime() - t0) / 1000000L;
            return (int) Math.max(1, d);
        } catch (Throwable e) {
            return -1;
        } finally {
            try { s.close(); } catch (Throwable ignored) {}
        }
    }

    /** Ping kedua server, ambil yang terbaik. -1 kalau semua gagal. */
    static int best() {
        int b = -1;
        for (int i = 0; i < HOSTS.length; i++) {
            int v = ms(HOSTS[i], PORTS[i], 1200);
            if (v > 0 && (b < 0 || v < b)) b = v;
        }
        return b;
    }

    static String label(int ms) {
        return ms < 0 ? "—" : ms + " ms";
    }

    /** 0 hijau (bagus), 1 kuning, 2 merah (buruk / gagal). */
    static int grade(int ms) {
        if (ms < 0) return 2;
        if (ms < 90) return 0;
        if (ms < 180) return 1;
        return 2;
    }
}
