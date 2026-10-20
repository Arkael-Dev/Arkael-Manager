package com.arkael.app;

/** Frame rate of the app on screen, read from SurfaceFlinger frame timestamps (needs root). */
final class FpsProbe {
    private String pkg = "", layer = "";
    private long lastList;

    /** @return frames per second, or -1 when it cannot be measured. */
    int read() {
        String p = Foreground.pkg();
        if (p.isEmpty()) return -1;
        long now = System.currentTimeMillis();
        if (!p.equals(pkg)) { pkg = p; layer = ""; lastList = 0; }
        if (layer.isEmpty()) {
            if (now - lastList < 4000) return -1;
            lastList = now;
            layer = findLayer(p);
            if (layer.isEmpty()) return -1;
        }
        String out = RootShell.run("dumpsys SurfaceFlinger --latency '" + layer + "' 2>/dev/null");
        long nowNs = System.nanoTime();
        String[] lines = out.split("\n");
        int valid = 0, n = 0;
        long first = Long.MAX_VALUE, last = 0;
        for (int i = 1; i < lines.length; i++) {
            String[] t = lines[i].trim().split("\\s+");
            if (t.length < 3) continue;
            long ts;
            try { ts = Long.parseLong(t[1]); } catch (NumberFormatException e) { continue; }
            if (ts <= 0 || ts == Long.MAX_VALUE) continue;
            valid++;
            if (ts > nowNs - 1000000000L && ts <= nowNs + 300000000L) {
                n++;
                if (ts < first) first = ts;
                if (ts > last) last = ts;
            }
        }
        if (valid == 0) { layer = ""; return -1; }   // layer is gone, look again next time
        if (n >= 3 && last > first) return (int) Math.round((n - 1) * 1e9 / (last - first));
        return n;
    }

    private String findLayer(String p) {
        String o = RootShell.run("dumpsys SurfaceFlinger --list 2>/dev/null | grep -F '" + p + "'");
        String blast = "", view = "", other = "";
        for (String l : o.split("\n")) {
            l = l.trim();
            if (l.isEmpty() || l.indexOf('\'') >= 0) continue;
            if (l.startsWith("Background for") || l.startsWith("Bounds for")) continue;
            if (l.contains("SurfaceView")) {
                if (l.contains("BLAST") && blast.isEmpty()) blast = l;
                else if (view.isEmpty()) view = l;
            } else if (other.isEmpty()) {
                other = l;
            }
        }
        if (!blast.isEmpty()) return blast;
        return !view.isEmpty() ? view : other;
    }
}
