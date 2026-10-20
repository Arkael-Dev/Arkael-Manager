package com.arkael.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Package name of the app on screen (needs root). Cached so several callers share one dumpsys. */
final class Foreground {
    private static final Pattern PKG = Pattern.compile("u\\d+ ([A-Za-z0-9_.]+)/");
    private static String cached = "";
    private static long at;

    private Foreground() {}

    static synchronized String pkg() {
        long now = System.currentTimeMillis();
        if (now - at < 1500) return cached;
        at = now;
        String out = RootShell.run(
            "r=$(dumpsys activity activities 2>/dev/null | grep -m1 -E 'topResumedActivity|mResumedActivity')\n" +
            "[ -z \"$r\" ] && r=$(dumpsys window 2>/dev/null | grep -m1 mCurrentFocus)\n" +
            "echo \"$r\"");
        Matcher m = PKG.matcher(out);
        if (m.find()) cached = m.group(1);
        return cached;
    }
}
