package com.arkael.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * CPU temperature that matches what the SoC really does. Only thermal zones that belong to the CPU/SoC are
 * used (modem, battery, charger, PA, GPU, skin ... are ignored, they made the number jump around), the hottest
 * CPU zone wins, and the result is smoothed so it does not flicker.
 */
final class TempProbe {
    private static final Set<String> CPU = new HashSet<String>(Arrays.asList(
        "cpu", "cpuss", "mtktscpu", "mtkcpu", "soc", "apc", "kryo", "big", "little", "mid", "prime",
        "gold", "silver", "cluster", "cpul", "cpub", "mcpu"));
    private static final Set<String> SKIP = new HashSet<String>(Arrays.asList(
        "battery", "bat", "charger", "chg", "usb", "pa", "pmic", "modem", "mdm", "wlan", "wifi", "xo",
        "quiet", "skin", "shell", "case", "gpu", "gpuss", "npu", "nsp", "ddr", "camera", "flash", "lcd",
        "display", "bcl", "pcb", "board", "vbat", "ibat", "conn", "nand", "ufs"));

    private float ema = -1f;
    private float lastBattery = -1f;

    /** @return degrees Celsius, or -1 when nothing usable was found. */
    float read(boolean root) {
        if (!root) return -1f;
        String o = RootShell.run(
            "for z in /sys/class/thermal/thermal_zone*; do echo \"$(cat $z/type 2>/dev/null)|$(cat $z/temp 2>/dev/null)\"; done");
        float v = parse(o);
        if (v <= 0f) return -1f;
        ema = ema < 0f ? v : ema * 0.5f + v * 0.5f;
        return ema;
    }

    /** Last battery zone seen, handy as a fallback. */
    float battery() { return lastBattery; }

    float parse(String dump) {
        float best = -1f, bat = -1f;
        for (String line : dump.split("\n")) {
            int i = line.indexOf('|');
            if (i <= 0) continue;
            String type = line.substring(0, i).trim().toLowerCase();
            float t = norm(line.substring(i + 1).trim());
            if (t <= 0f) continue;
            String[] tok = type.split("[-_0-9]+");
            boolean cpu = false, skip = false;
            for (String k : tok) {
                if (k.isEmpty()) continue;
                if (CPU.contains(k)) cpu = true;
                if (SKIP.contains(k)) skip = true;
            }
            if (type.equals("battery")) bat = t;
            if (cpu && !skip && t > best) best = t;
        }
        if (bat > 0f) lastBattery = bat;
        return best;
    }

    /** Zones report millidegrees, sometimes tenths or plain degrees. Garbage (disconnected sensors) is dropped. */
    static float norm(String raw) {
        if (raw.isEmpty()) return -1f;
        float v;
        try { v = Float.parseFloat(raw); } catch (NumberFormatException e) { return -1f; }
        if (v > 1000f) v /= 1000f;
        else if (v > 150f) v /= 10f;
        if (v < 10f || v > 125f) return -1f;
        return v;
    }
}
