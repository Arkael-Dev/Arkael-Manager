package com.arkael.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Frame rate of the app on screen (needs root).
 * Main method: SurfaceFlinger time stats. Every layer has a frame counter; the counter of the app's layer
 * (a layer whose name carries the app's package name) grows by `fps` per second. No guessing: no match, no number. Works on Android 12-16 (checked on Android 16 / HyperOS).
 * Fallback for old phones: SurfaceFlinger --latency (it returns nothing on Android 14+).
 * When it cannot measure, status() says why and the overlay shows it next to the dashes.
 */
final class FpsProbe {
    private String pkg = "";
    private String status = "";
    private boolean tsOn;
    private long tsAtNs;
    private final Map<String, Long> prev = new HashMap<String, Long>();
    private final List<String> latCands = new ArrayList<String>();
    private long lastList;
    private String lastLayer = "";

    /** Short reason when read() returned -1. */
    String status() { return status; }
    /** Name of the layer the number comes from (for the diagnostics report). */
    String layer() { return lastLayer; }

    /** @return frames per second, or -1 when it cannot be measured. */
    int read() {
        String p = Foreground.pkg();
        if (p.isEmpty()) { status = "app?"; return -1; }
        if (!p.equals(pkg)) { pkg = p; prev.clear(); tsAtNs = 0; latCands.clear(); lastList = 0; lastLayer = ""; }
        int t = timeStats(p);
        if (t >= 0) return t;
        if (!status.equals("wait")) {
            int l = latency(p);
            if (l >= 0) { status = ""; return l; }
        }
        return -1;
    }

    // ---------------------------------------------------------------- time stats

    private int timeStats(String p) {
        if (!tsOn) {
            RootShell.run("dumpsys SurfaceFlinger --timestats -enable >/dev/null 2>&1");
            tsOn = true;
        }
        String o = RootShell.run("dumpsys SurfaceFlinger --timestats -dump 2>/dev/null | grep -E 'layerName|totalFrames'");
        long now = System.nanoTime();
        Map<String, Long> cur = parse(o);
        if (cur.isEmpty()) { status = "stats?"; return -1; }
        int fps = -1;
        if (tsAtNs > 0) {
            double dt = (now - tsAtNs) / 1e9;
            String lp = p.toLowerCase();
            long best = -1; String bestName = "";
            for (Map.Entry<String, Long> e : cur.entrySet()) {
                Long pv = prev.get(e.getKey());
                if (pv == null || e.getValue() < pv) continue;
                long d = e.getValue() - pv;
                if (e.getKey().toLowerCase().contains(lp) && d > best) { best = d; bestName = e.getKey(); }
            }
            if (best >= 0 && dt > 0.2) { fps = (int) Math.round(best / dt); lastLayer = bestName; status = ""; }
            else status = "nolayer";
        } else {
            status = "wait";
        }
        prev.clear(); prev.putAll(cur); tsAtNs = now;
        return fps;
    }

    /** layerName / totalFrames pairs: the name line comes first, its counter right after. */
    static Map<String, Long> parse(String dump) {
        Map<String, Long> m = new HashMap<String, Long>();
        String name = "";
        for (String l : dump.split("\n")) {
            l = l.trim();
            int eq = l.indexOf('=');
            if (eq < 0) continue;
            String v = l.substring(eq + 1).trim();
            if (l.startsWith("layerName")) name = v;
            else if (l.startsWith("totalFrames") && !name.isEmpty()) {
                try {
                    long n = Long.parseLong(v);
                    Long old = m.get(name);
                    if (old == null || n > old) m.put(name, n);
                } catch (NumberFormatException ignored) {}
                name = "";
            }
        }
        return m;
    }

    // ---------------------------------------------------------------- old phones: latency

    private int latency(String p) {
        long now = System.currentTimeMillis();
        if (latCands.isEmpty()) {
            if (now - lastList < 4000) return -1;
            lastList = now;
            if (!p.matches("[A-Za-z0-9_.]+")) return -1;
            String o = RootShell.run("dumpsys SurfaceFlinger --list 2>/dev/null | grep -F '" + p + "'");
            for (String l : o.split("\n")) {
                l = l.trim();
                if (l.isEmpty() || l.indexOf('\'') >= 0 || l.startsWith("RequestedLayerState")) continue;
                if (latCands.size() < 4) latCands.add(l);
            }
            if (latCands.isEmpty()) return -1;
        }
        StringBuilder sh = new StringBuilder();
        for (String c : latCands) sh.append("echo '@@'; dumpsys SurfaceFlinger --latency '").append(c).append("' 2>/dev/null\n");
        long nowNs = System.nanoTime();
        int best = -1;
        for (String block : RootShell.run(sh.toString()).split("@@")) best = Math.max(best, block(block, nowNs));
        if (best < 0) latCands.clear();
        return best;
    }

    private static int block(String block, long nowNs) {
        String[] lines = block.split("\n");
        int valid = 0, n = 0;
        long first = Long.MAX_VALUE, last = 0;
        for (int i = 1; i < lines.length; i++) {
            String[] t = lines[i].trim().split("\\s+");
            if (t.length < 3) continue;
            long ts = stamp(t[1]);
            if (ts <= 0) ts = stamp(t[2]);
            if (ts <= 0) continue;
            valid++;
            if (ts > nowNs - 1000000000L && ts <= nowNs + 300000000L) {
                n++;
                if (ts < first) first = ts;
                if (ts > last) last = ts;
            }
        }
        if (valid == 0) return -1;
        if (n >= 3 && last > first) return (int) Math.round((n - 1) * 1e9 / (last - first));
        return n;
    }

    private static long stamp(String s) {
        try {
            long v = Long.parseLong(s);
            return v <= 0 || v == Long.MAX_VALUE ? -1 : v;
        } catch (NumberFormatException e) { return -1; }
    }
}
