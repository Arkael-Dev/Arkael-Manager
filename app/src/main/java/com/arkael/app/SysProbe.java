package com.arkael.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Device-independent live stats. Nothing is hardcoded to one phone: on first use it scans /sys for
 * the sensors that actually exist (Adreno, Mali, MediaTek, thermal zones...) and reads only those.
 */
final class SysProbe {
    static final class Snapshot {
        int cpuLoad = -1, cpuAvgMhz = -1, cpuMaxMhz = -1;
        float cpuTemp = -1;
        int gpuLoad = -1, gpuMhz = -1;
        int ramPct = -1; long ramUsedMb, ramTotalMb;
        int batPct = -1; float batTemp = -1; int batMa = Integer.MIN_VALUE;
        boolean root;
    }

    private static final Pattern NUM = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private boolean discovered;
    private boolean root;
    private String tickScript = "";
    private String gpuLoadPath = "";
    private long prevTotal = -1, prevIdle = -1;

    private void discover() {
        discovered = true;
        root = RootShell.hasRoot();
        if (!root) return;
        String d = RootShell.run(
            "for z in /sys/class/thermal/thermal_zone*; do [ -r \"$z/temp\" ] && echo \"Z|$z|$(cat $z/type 2>/dev/null)\"; done\n" +
            "for f in /sys/class/kgsl/kgsl-3d0/gpuclk /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq /sys/kernel/gpu/gpu_clock " +
            "/sys/kernel/ged/hal/current_freqency /sys/class/devfreq/*gpu*/cur_freq /sys/class/devfreq/*mali*/cur_freq " +
            "/sys/class/devfreq/*kgsl*/cur_freq /sys/devices/platform/*mali*/clock /sys/devices/platform/*gpu*/cur_freq; " +
            "do [ -r \"$f\" ] && echo \"GF|$f\"; done\n" +
            "for f in /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage /sys/class/kgsl/kgsl-3d0/gpubusy /sys/kernel/gpu/gpu_busy " +
            "/sys/devices/platform/*mali*/utilization /sys/class/devfreq/*gpu*/load /sys/kernel/ged/hal/gpu_utilization; " +
            "do [ -r \"$f\" ] && echo \"GL|$f\"; done");

        List<String> cpuZones = new ArrayList<>(), allZones = new ArrayList<>();
        String gf = "", gl = "";
        for (String line : d.split("\n")) {
            String[] p = line.split("\\|", 3);
            if (p.length < 2) continue;
            if (p[0].equals("Z") && p.length == 3) {
                allZones.add(p[1]);
                String t = p[2].toLowerCase();
                if (t.contains("cpu") || t.contains("soc") || t.contains("tsens") || t.contains("cluster")
                        || t.contains("big") || t.contains("little") || t.startsWith("ap") || t.contains("mtk")) {
                    cpuZones.add(p[1]);
                }
            } else if (p[0].equals("GF") && gf.isEmpty()) {
                gf = p[1];
            } else if (p[0].equals("GL") && gl.isEmpty()) {
                gl = p[1];
            }
        }
        List<String> zones = cpuZones.isEmpty() ? allZones : cpuZones;
        if (zones.size() > 16) zones = zones.subList(0, 16);
        gpuLoadPath = gl;

        StringBuilder sb = new StringBuilder();
        sb.append("head -n1 /proc/stat\n");
        sb.append("echo CF\ncat /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_cur_freq 2>/dev/null\n");
        sb.append("echo TZ\n");
        for (String z : zones) sb.append("cat ").append(z).append("/temp 2>/dev/null\n");
        sb.append("echo GF\n");
        if (!gf.isEmpty()) sb.append("cat ").append(gf).append(" 2>/dev/null\n");
        sb.append("echo GL\n");
        if (!gl.isEmpty()) sb.append("cat ").append(gl).append(" 2>/dev/null\n");
        tickScript = sb.toString();
    }

    private static double[] nums(String s) {
        Matcher m = NUM.matcher(s);
        double[] r = new double[8];
        int n = 0;
        while (m.find() && n < 8) {
            try { r[n++] = Double.parseDouble(m.group()); } catch (Exception ignored) {}
        }
        double[] o = new double[n];
        System.arraycopy(r, 0, o, 0, n);
        return o;
    }

    /** Any unit -> MHz, guessed from magnitude (Hz, kHz or MHz depending on the driver). */
    private static int toMhz(double v) {
        if (v <= 0) return -1;
        if (v > 1e7) return (int) (v / 1e6);
        if (v > 1e4) return (int) (v / 1e3);
        return (int) v;
    }

    Snapshot read(Context ctx) {
        Snapshot s = new Snapshot();
        if (!discovered) discover();
        s.root = root;

        if (root && !tickScript.isEmpty()) {
            String out = RootShell.run(tickScript);
            String sec = "";
            double freqSum = 0, freqMax = 0; int freqN = 0;
            double tMax = -1;
            for (String line : out.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.equals("CF") || line.equals("TZ") || line.equals("GF") || line.equals("GL")) { sec = line; continue; }
                if (sec.isEmpty()) {                       // first line: /proc/stat "cpu ..."
                    if (line.startsWith("cpu")) {
                        double[] v = nums(line);
                        if (v.length >= 5) {
                            double total = 0;
                            for (int i = 0; i < Math.min(v.length, 8); i++) total += v[i];
                            double idle = v[3] + (v.length > 4 ? v[4] : 0);
                            if (prevTotal >= 0 && total > prevTotal) {
                                double dt = total - prevTotal, di = idle - prevIdle;
                                s.cpuLoad = (int) Math.max(0, Math.min(100, Math.round(100.0 * (dt - di) / dt)));
                            }
                            prevTotal = (long) total; prevIdle = (long) idle;
                        }
                    }
                } else if (sec.equals("CF")) {
                    double[] v = nums(line);
                    if (v.length > 0 && v[0] > 0) { freqSum += v[0]; freqN++; if (v[0] > freqMax) freqMax = v[0]; }
                } else if (sec.equals("TZ")) {
                    double[] v = nums(line);
                    if (v.length > 0) {
                        double t = v[0] > 1000 ? v[0] / 1000.0 : v[0];
                        if (t > 0 && t < 150 && t > tMax) tMax = t;
                    }
                } else if (sec.equals("GF")) {
                    double[] v = nums(line);
                    if (v.length > 0 && s.gpuMhz < 0) s.gpuMhz = toMhz(v[0]);
                } else if (sec.equals("GL")) {
                    double[] v = nums(line);
                    if (v.length > 0 && s.gpuLoad < 0) {
                        double pct;
                        if (gpuLoadPath.endsWith("gpubusy") && v.length >= 2 && v[1] > 0) pct = 100.0 * v[0] / v[1];
                        else pct = v[0];
                        s.gpuLoad = (int) Math.max(0, Math.min(100, Math.round(pct)));
                    }
                }
            }
            if (freqN > 0) { s.cpuAvgMhz = (int) (freqSum / freqN / 1000); s.cpuMaxMhz = (int) (freqMax / 1000); }
            if (tMax > 0) s.cpuTemp = (float) tMax;
        }

        try {   // RAM (no root needed)
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            s.ramTotalMb = mi.totalMem / (1024 * 1024);
            s.ramUsedMb = (mi.totalMem - mi.availMem) / (1024 * 1024);
            if (mi.totalMem > 0) s.ramPct = (int) (100 * (mi.totalMem - mi.availMem) / mi.totalMem);
        } catch (Exception ignored) {}

        try {   // battery (no root needed)
            Intent b = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int lvl = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (lvl >= 0 && scale > 0) s.batPct = lvl * 100 / scale;
                int t = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (t > 0) s.batTemp = t / 10f;
            }
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            long ua = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (ua != 0 && ua != Long.MIN_VALUE) {
                long ma = Math.abs(ua) > 20000 ? ua / 1000 : ua;   // some devices report mA, most report uA
                s.batMa = (int) ma;
            }
        } catch (Exception ignored) {}

        if (s.cpuTemp < 0 && s.batTemp > 0) s.cpuTemp = s.batTemp;   // fallback when no zone is readable
        return s;
    }
}
