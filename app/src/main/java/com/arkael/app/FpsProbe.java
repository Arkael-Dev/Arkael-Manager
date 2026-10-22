package com.arkael.app;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Frame rate of the game on screen (needs root).
 *
 * SurfaceFlinger keeps a frame counter for every layer. We sample all counters once a second and look at
 * how much each one grew: that is the frame rate of that layer. Then we pick the game's layer:
 *   1. a layer whose name contains the foreground package (exact match, shown as "60 fps"), or
 *   2. if no layer name carries the package (some games/ROMs name it differently), the busiest layer that is
 *      not system UI / launcher / our own overlay (shown as "60~ fps"), or
 *   3. the display refresh counter (/sys/class/drm/.../measured_fps) on phones that have it (also "~").
 * No "wait": the first call takes two samples half a second apart, so a number appears at once.
 */
final class FpsProbe {
    private String status = "";
    private boolean approx;
    private boolean tsOn;
    private long tsAtNs;
    private final Map<String, Long> prev = new HashMap<String, Long>();
    private String lastLayer = "";
    private int drmState;            // 0 = untried, 1 = works, 2 = not available
    private long flipPrev = -1, flipAt;
    private int failStreak;
    private boolean diagWritten;

    /** Short reason when read() returned -1. */
    String status() { return status; }
    /** "~" when the number is a best guess (layer picked by activity, not by name). */
    String mark() { return approx ? "~" : ""; }
    /** Name of the layer the number comes from (for the diagnostics report). */
    String layer() { return lastLayer; }

    /** @return frames per second, or -1 when it cannot be measured. */
    int read() {
        String p = Foreground.pkg();
        int t = timeStats(p);
        if (t >= 0) { failStreak = 0; return t; }
        int f = pageFlips();
        if (f >= 0) { failStreak = 0; approx = true; lastLayer = "display flips"; status = ""; return f; }
        int d = drm();
        if (d >= 0) { failStreak = 0; approx = true; lastLayer = "display"; status = ""; return d; }
        if (++failStreak >= 8 && !diagWritten) { diagWritten = true; writeDiag(p); }
        if (diagWritten) status = "diag file";
        return -1;
    }

    // ---------------------------------------------------------------- SurfaceFlinger time stats

    private Map<String, Long> sample() {
        return parse(RootShell.run("dumpsys SurfaceFlinger --timestats -dump 2>/dev/null | grep -E 'layerName|totalFrames'"));
    }

    private int timeStats(String p) {
        if (!tsOn) {
            RootShell.run("dumpsys SurfaceFlinger --timestats -enable >/dev/null 2>&1");
            tsOn = true;
        }
        Map<String, Long> cur = sample();
        long now = System.nanoTime();
        if (tsAtNs == 0) {                       // first sample: take a second one right away
            prev.clear(); prev.putAll(cur); tsAtNs = now;
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            cur = sample();
            now = System.nanoTime();
        }
        if (cur.isEmpty()) { status = "stats?"; tsAtNs = 0; return -1; }
        double dt = (now - tsAtNs) / 1e9;
        if (dt < 0.2) return -1;
        Pick k = pick(cur, prev, dt, p);
        prev.clear(); prev.putAll(cur); tsAtNs = now;
        if (k == null) { status = "nolayer"; return -1; }
        status = ""; approx = k.approx; lastLayer = k.layer;
        return k.fps;
    }

    static final class Pick {
        int fps; String layer; boolean approx;
    }

    private static final String[] SKIP = {
        "com.android.", "com.google.android.inputmethod", "com.miui", "com.xiaomi", "com.arkael.app",
        "statusbar", "navigationbar", "wallpaper", "launcher", "inputmethod", "screendecor", "pointericon",
        "dim layer", "letterbox", "notificationshade", "taskbar", "magnification", "splash", "background for",
        "bounds for", "animation", "backdrop", "wms", "display overlay", "mirror"
    };

    private static boolean skipped(String lower) {
        for (String s : SKIP) if (lower.contains(s)) return true;
        return false;
    }

    /** Chooses the game's layer from two consecutive counter snapshots. Null when nothing is moving or known. */
    static Pick pick(Map<String, Long> cur, Map<String, Long> prev, double dt, String pkg) {
        String lp = pkg == null ? "" : pkg.toLowerCase();
        long matchBest = -1, anyBest = -1;
        String matchName = "", anyName = "";
        for (Map.Entry<String, Long> e : cur.entrySet()) {
            Long pv = prev.get(e.getKey());
            if (pv == null || e.getValue() < pv) continue;
            long d = e.getValue() - pv;
            String low = e.getKey().toLowerCase();
            if (!lp.isEmpty() && low.contains(lp)) {
                if (d > matchBest) { matchBest = d; matchName = e.getKey(); }
            } else if (!skipped(low)) {
                if (d > anyBest) { anyBest = d; anyName = e.getKey(); }
            }
        }
        Pick k = new Pick();
        if (matchBest > 0) {                                  // the app's own layer is moving
            k.fps = (int) Math.round(matchBest / dt); k.layer = matchName; k.approx = false;
            return k;
        }
        if (anyBest >= Math.max(2, 3 * dt)) {                 // name did not match: busiest app-like layer
            k.fps = (int) Math.round(anyBest / dt); k.layer = anyName; k.approx = true;
            return k;
        }
        if (matchBest == 0) {                                 // app layer exists but nothing is drawn (paused)
            k.fps = 0; k.layer = matchName; k.approx = false;
            return k;
        }
        return null;
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

    // ---------------------------------------------------------------- display counter (Qualcomm / Xiaomi)

    private static final Pattern NUM = Pattern.compile("\\d+(?:\\.\\d+)?");

    private int drm() {
        if (drmState == 2) return -1;
        String o = RootShell.run("for f in /sys/class/drm/sde-crtc-0/measured_fps /sys/class/graphics/fb0/measured_fps " +
                "/sys/devices/virtual/graphics/fb0/measured_fps; do [ -r \"$f\" ] && { cat \"$f\"; break; }; done 2>/dev/null");
        Matcher m = NUM.matcher(o);
        if (m.find()) {
            try {
                double v = Double.parseDouble(m.group());
                if (v >= 1 && v < 500) { drmState = 1; return (int) Math.round(v); }
            } catch (NumberFormatException ignored) {}
        }
        if (drmState == 0) drmState = 2;
        return -1;
    }

    // ---------------------------------------------------------------- display page flips (works on most Android versions)

    private static final Pattern PARCEL = Pattern.compile("Parcel\\(([0-9a-fA-F]{8})\\s+([0-9a-fA-F]{8})");

    /** service call SurfaceFlinger 1013 = number of frames the display has shown; its growth per second is the screen's frame rate. */
    private int pageFlips() {
        String o = RootShell.run("service call SurfaceFlinger 1013 2>/dev/null");
        Matcher m = PARCEL.matcher(o);
        if (!m.find()) return -1;
        long v;
        try { v = Long.parseLong(m.group(2), 16); } catch (NumberFormatException e) { return -1; }
        long now = System.nanoTime();
        long pv = flipPrev, pt = flipAt;
        flipPrev = v; flipAt = now;
        if (pv < 0 || v < pv) return -1;
        double dt = (now - pt) / 1e9;
        if (dt < 0.2) return -1;
        int fps = (int) Math.round((v - pv) / dt);
        return fps > 480 ? -1 : fps;
    }

    // ---------------------------------------------------------------- raw data for debugging

    /** Saved once, automatically, when every method failed: Download/arkael_fps_auto.txt */
    private void writeDiag(String pkg) {
        RootShell.run("{ echo \"pkg=" + pkg.replaceAll("[^A-Za-z0-9_.]", "") + "\"; getprop ro.build.version.release; getprop ro.product.model; getprop ro.build.version.sdk\n" +
            "echo '=== timestats (first 150 lines)'; dumpsys SurfaceFlinger --timestats -dump 2>&1 | head -150\n" +
            "echo '=== SurfaceFlinger --list (first 80)'; dumpsys SurfaceFlinger --list 2>&1 | head -80\n" +
            "echo '=== service call 1013'; service call SurfaceFlinger 1013 2>&1\n" +
            "echo '=== measured_fps'; cat /sys/class/drm/sde-crtc-0/measured_fps 2>&1\n" +
            "} > /sdcard/Download/arkael_fps_auto.txt 2>&1");
    }
}
