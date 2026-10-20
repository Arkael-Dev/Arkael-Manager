package com.arkael.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Frame rate of the app on screen (needs root). Two methods, the second one is the fallback:
 *  1. SurfaceFlinger --latency on every layer of the app (the SurfaceView of a game is picked automatically)
 *  2. SurfaceFlinger time stats (frame counter per layer, difference per second)
 * When it cannot measure, status() says why, and the overlay shows it next to the dashes.
 */
final class FpsProbe {
    private String pkg = "";
    private final List<String> cands = new ArrayList<String>();
    private long lastList;
    private String status = "";

    private boolean tsOn;
    private long tsAt;
    private final Map<String, Long> tsPrev = new HashMap<String, Long>();

    /** Short reason when read() returned -1. */
    String status() { return status; }

    /** @return frames per second, or -1 when it cannot be measured. */
    int read() {
        String p = Foreground.pkg();
        if (p.isEmpty()) { status = "app?"; return -1; }
        long now = System.currentTimeMillis();
        if (!p.equals(pkg)) { pkg = p; cands.clear(); lastList = 0; tsPrev.clear(); tsAt = 0; }
        if (cands.isEmpty() && now - lastList >= 3000) { lastList = now; findLayers(p); }

        boolean hadLayers = !cands.isEmpty();
        int fps = hadLayers ? latency() : -1;
        if (fps > 0) return fps;
        int t = timeStats(p);
        if (t > 0) return t;
        if (fps == 0 || t == 0) return 0;
        cands.clear();                       // look for the layers again next round
        status = hadLayers ? "frames?" : "layer?";
        return -1;
    }

    // ---------------------------------------------------------------- method 1: latency

    private void findLayers(String p) {
        cands.clear();
        if (!p.matches("[A-Za-z0-9_.]+")) return;
        String o = RootShell.run("dumpsys SurfaceFlinger --list 2>/dev/null | grep -F '" + p + "'");
        List<String> blast = new ArrayList<String>(), view = new ArrayList<String>(), other = new ArrayList<String>();
        for (String l : o.split("\n")) {
            l = l.trim();
            if (l.isEmpty() || l.indexOf('\'') >= 0) continue;
            if (l.startsWith("Background for") || l.startsWith("Bounds for")) continue;
            if (l.contains("SurfaceView")) { if (l.contains("BLAST")) blast.add(l); else view.add(l); }
            else other.add(l);
        }
        cands.addAll(blast); cands.addAll(view); cands.addAll(other);
        while (cands.size() > 5) cands.remove(cands.size() - 1);
    }

    private int latency() {
        StringBuilder sh = new StringBuilder();
        for (String c : cands) sh.append("echo '@@'; dumpsys SurfaceFlinger --latency '").append(c).append("' 2>/dev/null\n");
        String out = RootShell.run(sh.toString());
        long nowNs = System.nanoTime();
        int best = -1;
        for (String block : out.split("@@")) {
            int f = block(block, nowNs);
            if (f > best) best = f;
        }
        return best;
    }

    /** fps of one latency block, -1 when the block holds no timestamps at all. */
    private static int block(String block, long nowNs) {
        String[] lines = block.split("\n");
        int valid = 0, n = 0;
        long first = Long.MAX_VALUE, last = 0;
        for (int i = 1; i < lines.length; i++) {
            String[] t = lines[i].trim().split("\\s+");
            if (t.length < 3) continue;
            long ts = stamp(t[1]);
            if (ts <= 0) ts = stamp(t[2]);           // some builds leave the present time empty
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

    // ---------------------------------------------------------------- method 2: time stats

    private int timeStats(String p) {
        if (!tsOn) {
            RootShell.run("dumpsys SurfaceFlinger --timestats -enable >/dev/null 2>&1");
            tsOn = true;
        }
        String o = RootShell.run("dumpsys SurfaceFlinger --timestats -dump 2>/dev/null | grep -E 'layerName|totalFrames'");
        long now = System.currentTimeMillis();
        Map<String, Long> cur = new HashMap<String, Long>();
        String name = "";
        for (String l : o.split("\n")) {
            l = l.trim();
            int eq = l.indexOf('=');
            if (eq < 0) continue;
            String v = l.substring(eq + 1).trim();
            if (l.startsWith("layerName")) name = v;
            else if (l.startsWith("totalFrames") && name.contains(p)) {
                try { cur.put(name, Long.parseLong(v)); } catch (NumberFormatException ignored) {}
            }
        }
        int fps = -1;
        if (tsAt > 0 && !cur.isEmpty()) {
            double dt = (now - tsAt) / 1000.0;
            long bestDelta = -1;
            for (Map.Entry<String, Long> e : cur.entrySet()) {
                Long prev = tsPrev.get(e.getKey());
                if (prev != null && e.getValue() >= prev) bestDelta = Math.max(bestDelta, e.getValue() - prev);
            }
            if (bestDelta >= 0 && dt > 0.2) fps = (int) Math.round(bestDelta / dt);
        }
        tsPrev.clear(); tsPrev.putAll(cur); tsAt = now;
        return fps;
    }
}
