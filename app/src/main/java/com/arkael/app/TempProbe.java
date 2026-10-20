package com.arkael.app;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Temperature with a choice of source, because every phone exposes different sensors:
 *  CPU_MAX  hottest CPU core zone          CPU_AVG  average of all CPU zones
 *  BOARD    the board sensor next to the SoC (cpu_therm)      BATTERY  battery temperature
 * Sensors that do not belong to the chip (modem, PA, GPU, charger ...) are never used for the CPU values.
 */
final class TempProbe {
    static final int CPU_MAX = 0, CPU_AVG = 1, BOARD = 2, BATTERY = 3;
    static final String[] NAMES = {"CPU hottest core", "CPU average", "Board (next to chip)", "Battery"};

    private static final Set<String> CPU = new HashSet<String>(Arrays.asList(
        "cpu", "cpuss", "mtktscpu", "mtkcpu", "soc", "apc", "kryo", "big", "little", "mid", "prime",
        "gold", "silver", "cluster", "cpul", "cpub", "mcpu"));
    private static final Set<String> SKIP = new HashSet<String>(Arrays.asList(
        "battery", "bat", "charger", "chg", "usb", "pa", "pmic", "modem", "mdm", "wlan", "wifi", "xo",
        "quiet", "skin", "shell", "case", "gpu", "gpuss", "npu", "nsp", "ddr", "camera", "flash", "lcd",
        "display", "bcl", "pcb", "board", "vbat", "ibat", "conn", "nand", "ufs", "therm"));
    private static final String CMD =
        "for z in /sys/class/thermal/thermal_zone*; do echo \"$(cat $z/type 2>/dev/null)|$(cat $z/temp 2>/dev/null)\"; done";

    float cpuMax = -1f, cpuAvg = -1f, board = -1f, zoneBat = -1f;
    private float ema = -1f;
    private int lastSrc = -1;

    /** @return degrees Celsius, or -1 when nothing usable was found. batteryC comes from BatteryInfo. */
    float read(boolean root, int src, float batteryC) {
        if (src != lastSrc) { ema = -1f; lastSrc = src; }
        float v = -1f;
        if (src == BATTERY && batteryC > 0f) {
            v = batteryC;
        } else if (root) {
            parse(RootShell.run(CMD));
            if (src == CPU_AVG) v = cpuAvg;
            else if (src == BOARD) v = board;
            else if (src == BATTERY) v = zoneBat;
            else v = cpuMax;
            if (v <= 0f) v = cpuMax > 0f ? cpuMax : (board > 0f ? board : zoneBat);
        }
        if (v <= 0f) v = batteryC;
        if (v <= 0f) return -1f;
        ema = ema < 0f ? v : ema * 0.5f + v * 0.5f;
        return ema;
    }

    void parse(String dump) {
        float max = -1f, sum = 0f, brd = -1f, bat = -1f;
        int n = 0;
        for (String line : dump.split("\n")) {
            int i = line.indexOf('|');
            if (i <= 0) continue;
            String type = line.substring(0, i).trim().toLowerCase();
            float t = norm(line.substring(i + 1).trim());
            if (t <= 0f) continue;
            if (type.equals("battery")) bat = t;
            if (type.equals("cpu_therm") || type.equals("cpu-therm")) brd = t;
            boolean cpu = false, skip = false;
            for (String k : type.split("[-_0-9]+")) {
                if (k.isEmpty()) continue;
                if (CPU.contains(k)) cpu = true;
                if (SKIP.contains(k)) skip = true;
            }
            if (cpu && !skip) { n++; sum += t; if (t > max) max = t; }
        }
        cpuMax = max;
        cpuAvg = n > 0 ? sum / n : -1f;
        board = brd;
        zoneBat = bat;
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
