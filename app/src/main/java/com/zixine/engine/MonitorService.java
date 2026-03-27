package com.zixine.engine;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.Choreographer;
import androidx.core.app.NotificationCompat;

public class MonitorService extends Service {
    private int f = 0; private long s = 0;
    @Override public int onStartCommand(Intent i, int fl, int id) {
        NotificationChannel c = new NotificationChannel("zn", "Monitor", 2);
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override public void doFrame(long n) {
                f++; long now = System.currentTimeMillis();
                if (now - s >= 1000) { 
                    startForeground(1, new NotificationCompat.Builder(MonitorService.this, "zn").setContentTitle("zixine").setContentText("FPS: " + f).setSmallIcon(android.R.drawable.ic_menu_compass).setOngoing(true).build());
                    f = 0; s = now;
                }
                Choreographer.getInstance().postFrameCallback(this);
            }
        });
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
