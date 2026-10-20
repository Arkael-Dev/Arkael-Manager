package com.arkael.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Everything that changes system state for gaming. Each setter remembers the original value the first
 * time it runs, so revertAll() can put the phone back exactly as it was. All calls need root and block,
 * so call them from a background thread.
 */
final class Booster {
    static final class GpuInfo {
        String maxPath = "";
        int[] mhz = new int[0];
        long[] raw = new long[0];
        boolean ok() { return !maxPath.isEmpty() && mhz.length > 1; }
    }

    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_\\-]+");
    private static final String POLICIES = "/sys/devices/system/cpu/cpufreq/policy*";
    private static final String[] THERMAL_SVCS = {"thermal-engine", "thermald", "mi_thermald", "thermal_manager"};

    private Booster() {}

    private static SharedPreferences snap(Context c) { return c.getSharedPreferences("arkael_snap", Context.MODE_PRIVATE); }

    /** True when something is changed that revertAll() would undo. */
    static boolean dirty(Context c) { return !snap(c).getAll().isEmpty(); }

    /** Leftovers from a game session that did not end cleanly (a manual boost is not a leftover). */
    static boolean leftover(Context c) { return dirty(c) && !snap(c).contains("manual"); }

    static void markManual(Context c) { snap(c).edit().putBoolean("manual", true).apply(); }
    static boolean isDnd(Context c) { return snap(c).contains("dnd"); }
    static boolean isHeadsUpBlocked(Context c) { return snap(c).contains("heads"); }

    // ---------------------------------------------------------------- RAM

    private static long availMb(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.availMem / (1024 * 1024);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Drops caches and kills cached background apps. deep = also trims every app's cache (can take seconds). */
    static String cleanRam(Context c, boolean deep) {
        long before = availMb(c);
        StringBuilder sb = new StringBuilder("sync\necho 3 > /proc/sys/vm/drop_caches\nam kill-all 2>/dev/null\n");
        if (deep) sb.append("cmd package trim-caches 4G 2>/dev/null || pm trim-caches 4G 2>/dev/null\n");
        sb.append("echo 3 > /proc/sys/vm/drop_caches\n");
        RootShell.run(sb.toString());
        try { Thread.sleep(400); } catch (InterruptedException ignored) {}
        long freed = Math.max(0, availMb(c) - before);
        return "Freed " + freed + " MB";
    }

    // ---------------------------------------------------------------- CPU governor

    static List<String> governors() {
        List<String> r = new ArrayList<String>();
        String o = RootShell.run("cat /sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors 2>/dev/null || " +
                "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors 2>/dev/null");
        for (String g : o.trim().split("\\s+")) if (WORD.matcher(g).matches()) r.add(g);
        return r;
    }

    static String perfGovernor() { return governors().contains("performance") ? "performance" : ""; }

    static void setGovernor(Context c, String gov) {
        if (gov == null || !WORD.matcher(gov).matches()) return;
        SharedPreferences sp = snap(c);
        if (!sp.contains("gov")) {
            String o = RootShell.run("for p in " + POLICIES + "; do echo \"$p $(cat $p/scaling_governor 2>/dev/null)\"; done");
            sp.edit().putString("gov", o).apply();
        }
        RootShell.run("for p in " + POLICIES + "; do echo " + gov + " > $p/scaling_governor 2>/dev/null; done");
    }

    private static void restoreGovernor(Context c) {
        SharedPreferences sp = snap(c);
        String o = sp.getString("gov", null);
        if (o == null) return;
        StringBuilder sb = new StringBuilder();
        for (String line : o.split("\n")) {
            String[] t = line.trim().split(" ");
            if (t.length != 2) continue;
            if (!t[0].startsWith("/sys/devices/system/cpu/cpufreq/policy") || !t[0].matches("[A-Za-z0-9_/]+")) continue;
            if (!WORD.matcher(t[1]).matches()) continue;
            sb.append("echo ").append(t[1]).append(" > ").append(t[0]).append("/scaling_governor 2>/dev/null\n");
        }
        if (sb.length() > 0) RootShell.run(sb.toString());
        sp.edit().remove("gov").apply();
    }

    // ---------------------------------------------------------------- GPU max clock

    private static int toMhz(long v) {
        if (v > 10000000L) return (int) (v / 1000000L);
        if (v > 10000L) return (int) (v / 1000L);
        return (int) v;
    }

    static GpuInfo gpu() {
        GpuInfo g = new GpuInfo();
        String o = RootShell.run(
            "for f in /sys/class/kgsl/kgsl-3d0/devfreq/max_freq /sys/class/kgsl/kgsl-3d0/max_gpuclk " +
            "/sys/class/devfreq/*gpu*/max_freq /sys/class/devfreq/*mali*/max_freq /sys/class/devfreq/*kgsl*/max_freq; do\n" +
            "  [ -w \"$f\" ] || continue\n" +
            "  d=$(dirname \"$f\"); a=\"\"\n" +
            "  for n in available_frequencies gpu_available_frequencies; do [ -r \"$d/$n\" ] && a=\"$d/$n\" && break; done\n" +
            "  echo \"P|$f\"\n" +
            "  [ -n \"$a\" ] && echo \"F|$(cat $a)\"\n" +
            "  break\n" +
            "done");
        String path = "", fr = "";
        for (String line : o.split("\n")) {
            if (line.startsWith("P|")) path = line.substring(2).trim();
            else if (line.startsWith("F|")) fr = line.substring(2).trim();
        }
        if (path.isEmpty() || fr.isEmpty() || !path.matches("[A-Za-z0-9_/.\\-]+")) return g;
        TreeMap<Integer, Long> map = new TreeMap<Integer, Long>();
        for (String t : fr.split("\\s+")) {
            try {
                long v = Long.parseLong(t);
                if (v > 0) map.put(toMhz(v), v);
            } catch (NumberFormatException ignored) {}
        }
        g.maxPath = path;
        g.mhz = new int[map.size()];
        g.raw = new long[map.size()];
        int i = 0;
        for (Map.Entry<Integer, Long> e : map.entrySet()) { g.mhz[i] = e.getKey(); g.raw[i] = e.getValue(); i++; }
        return g;
    }

    static boolean setGpuMax(Context c, int mhz) {
        GpuInfo g = gpu();
        if (!g.ok()) return false;
        long raw = -1;
        for (int i = 0; i < g.mhz.length; i++) if (g.mhz[i] == mhz) raw = g.raw[i];
        if (raw < 0) return false;
        SharedPreferences sp = snap(c);
        if (!sp.contains("gpu")) {
            String cur = RootShell.run("cat " + g.maxPath).trim();
            if (cur.matches("\\d+")) sp.edit().putString("gpu", g.maxPath + "|" + cur).apply();
        }
        RootShell.run("echo " + raw + " > " + g.maxPath + " 2>/dev/null");
        return true;
    }

    private static void restoreGpu(Context c) {
        SharedPreferences sp = snap(c);
        String s = sp.getString("gpu", null);
        if (s == null) return;
        String[] t = s.split("\\|");
        if (t.length == 2 && t[0].matches("/sys/[A-Za-z0-9_/.\\-]+") && t[1].matches("\\d+")) {
            RootShell.run("echo " + t[1] + " > " + t[0] + " 2>/dev/null");
        }
        sp.edit().remove("gpu").apply();
    }

    // ---------------------------------------------------------------- ZRAM (not reverted automatically)

    static void setZram(int mb) {
        if (mb <= 0) return;
        long bytes = mb * 1024L * 1024L;
        RootShell.run(
            "z=/sys/block/zram0\n" +
            "if [ -d $z ] && [ \"$(cat $z/disksize)\" != \"" + bytes + "\" ]; then\n" +
            "  alg=$(sed 's/.*\\[\\(.*\\)\\].*/\\1/' $z/comp_algorithm 2>/dev/null)\n" +
            "  swapoff /dev/block/zram0 2>/dev/null\n" +
            "  echo 1 > $z/reset\n" +
            "  [ -n \"$alg\" ] && echo $alg > $z/comp_algorithm 2>/dev/null\n" +
            "  echo " + bytes + " > $z/disksize\n" +
            "  mkswap /dev/block/zram0 >/dev/null 2>&1\n" +
            "  swapon /dev/block/zram0 2>/dev/null\n" +
            "fi");
    }

    // ---------------------------------------------------------------- thermal (user-space services only)

    static void setThermalRelax(Context c, boolean on) {
        SharedPreferences sp = snap(c);
        if (on) {
            if (sp.contains("thermal")) return;
            StringBuilder s = new StringBuilder();
            for (String n : THERMAL_SVCS) {
                s.append("[ \"$(getprop init.svc.").append(n).append(")\" = running ] && { echo \"T|").append(n)
                 .append("\"; stop ").append(n).append("; }\n");
            }
            String o = RootShell.run(s.toString());
            StringBuilder names = new StringBuilder();
            for (String line : o.split("\n")) if (line.startsWith("T|")) names.append(line.substring(2).trim()).append(' ');
            sp.edit().putString("thermal", names.toString().trim()).apply();
        } else {
            String names = sp.getString("thermal", null);
            if (names == null) return;
            for (String n : names.split("\\s+")) if (WORD.matcher(n).matches()) RootShell.run("start " + n);
            sp.edit().remove("thermal").apply();
        }
    }

    // ---------------------------------------------------------------- notifications

    static void setDnd(Context c, boolean on) {
        SharedPreferences sp = snap(c);
        if (on) {
            if (!sp.contains("dnd")) {
                String z = RootShell.run("settings get global zen_mode").trim();
                sp.edit().putString("dnd", z.matches("\\d+") ? z : "0").apply();
            }
            RootShell.run("cmd notification set_dnd priority 2>/dev/null || settings put global zen_mode 1");
        } else {
            String z = sp.getString("dnd", null);
            if (z == null) return;
            if (z.equals("0")) RootShell.run("cmd notification set_dnd off 2>/dev/null || settings put global zen_mode 0");
            else RootShell.run("settings put global zen_mode " + z);
            sp.edit().remove("dnd").apply();
        }
    }

    static void setHeadsUp(Context c, boolean block) {
        SharedPreferences sp = snap(c);
        if (block) {
            if (!sp.contains("heads")) {
                String v = RootShell.run("settings get global heads_up_notifications_enabled").trim();
                sp.edit().putString("heads", v.matches("\\d+") ? v : "1").apply();
            }
            RootShell.run("settings put global heads_up_notifications_enabled 0");
        } else {
            String v = sp.getString("heads", null);
            if (v == null) return;
            RootShell.run("settings put global heads_up_notifications_enabled " + v);
            sp.edit().remove("heads").apply();
        }
    }

    // ---------------------------------------------------------------- profile / revert

    static void applyProfile(Context c, GameProfiles.Profile p, boolean withZram) {
        if (p.dnd) setDnd(c, true);
        if (p.headsUp) setHeadsUp(c, true);
        if (p.thermalRelax) setThermalRelax(c, true);
        if (!p.gov.isEmpty()) setGovernor(c, p.gov);
        if (p.gpuMaxMhz > 0) setGpuMax(c, p.gpuMaxMhz);
        if (withZram && p.zramMb > 0) setZram(p.zramMb);
        if (p.cleanRam) cleanRam(c, false);
    }

    static void revertAll(Context c) {
        setDnd(c, false);
        setHeadsUp(c, false);
        setThermalRelax(c, false);
        restoreGovernor(c);
        restoreGpu(c);
        snap(c).edit().clear().apply();
    }
}
