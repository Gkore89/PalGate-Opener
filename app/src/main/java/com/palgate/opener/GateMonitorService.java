package com.palgate.opener;

import android.app.*;
import android.content.Intent;
import android.os.*;
import androidx.core.app.NotificationCompat;

public class GateMonitorService extends Service {

    private static final String CHANNEL_ID = "palgate_monitor";
    private static final int NOTIF_ID = 1001;

    public static final String ACTION_STATUS = "com.palgate.opener.STATUS";
    public static final String EXTRA_STATUS  = "status";
    public static final String EXTRA_EVENT   = "event";
    public static final String ACTION_OPEN   = "OPEN_GATE";
    public static final String EXTRA_GATE_ID = "gate_id";

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIF_ID, buildNotif("PalGate פעיל"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "PalGate", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotif(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PalGate Opener")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }
}
