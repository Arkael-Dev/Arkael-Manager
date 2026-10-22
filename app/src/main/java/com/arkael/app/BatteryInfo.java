package com.arkael.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/** Battery temperature straight from Android (same number as Settings). No root, works on every phone. */
final class BatteryInfo {
    private BatteryInfo() {}

    /** @return degrees Celsius, or -1 when unknown. */
    static float temp(Context c) {
        try {
            Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i == null) return -1f;
            int t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);   // tenths of a degree
            return t > 0 ? t / 10f : -1f;
        } catch (Exception e) {
            return -1f;
        }
    }
}
