package com.palgate.opener;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.location.*;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.*;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.util.Random;

public class GateMonitorService extends Service {

    private static final String TAG = "GateMonitor";
    private static final String CHANNEL_ID = "palgate_monitor";
    private static final String CHANNEL_EVENTS = "palgate_events";
    private static final int NOTIF_ID = 1001;

    public static final String ACTION_STATUS  = "com.palgate.opener.STATUS";
    public static final String EXTRA_STATUS   = "status";
    public static final String EXTRA_EVENT    = "event";
    public static final String ACTION_OPEN    = "OPEN_GATE";
    public static final String EXTRA_GATE_ID  = "gate_id";

    private AppPrefs prefs;
    private LocationManager locationMgr;
    private WifiManager wifiMgr;
    private long lastGate1Ms = 0, lastGate2Ms = 0;
    private boolean locationActive = false;

    private final LocationListener locListener = new LocationListener() {
        @Override public void onLocationChanged(Location loc) {
            try { checkTriggers(loc); } catch (Exception e) { Log.e(TAG, "loc error", e); }
        }
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
    };

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            try {
                BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (dev == null) return;
                String name = getDeviceName(dev);
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction()))
                    onBtConnected(name);
                else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction()))
                    onBtDisconnected(name);
            } catch (Exception e) { Log.e(TAG, "bt error", e); }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new AppPrefs(this);
        locationMgr = (LocationManager) getSystemService(LOCATION_SERVICE);
        wifiMgr = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        createChannels();
        startForeground(NOTIF_ID, buildStatusNotif("ממתין לחיבור רכב..."));
        registerBtReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null && ACTION_OPEN.equals(intent.getAction())) {
                String gateId = intent.getStringExtra(EXTRA_GATE_ID);
                if (gateId != null) triggerGate(gateId, "ידני");
            }
        } catch (Exception e) { Log.e(TAG, "startCommand error", e); }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(btReceiver); } catch (Exception ignored) {}
        stopLocation();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    // ── BT ────────────────────────────────────────────────────────

    private void onBtConnected(String name) {
        if (!isKnownVehicle(name)) return;
        startLocation();
        broadcast("בתוך הרכב — פעיל", "BT: " + name);
        updateStatusNotif("פעיל — " + name);
    }

    private void onBtDisconnected(String name) {
        if (!isKnownVehicle(name)) return;
        stopLocation();
        broadcast("ממתין לחיבור רכב...", "BT נותק: " + name);
        updateStatusNotif("ממתין לחיבור רכב...");
    }

    private void registerBtReceiver() {
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            registerReceiver(btReceiver, f);
        } catch (Exception e) { Log.e(TAG, "bt register error", e); }
    }

    private boolean isKnownVehicle(String name) {
        if (name == null) return false;
        String v1 = prefs.getV1BtName();
        String v2 = prefs.getV2BtName();
        return (!v1.isEmpty() && v1.equals(name)) ||
               (prefs.isV2Enabled() && !v2.isEmpty() && v2.equals(name));
    }

    private String getDeviceName(BluetoothDevice d) {
        try { return d.getName(); } catch (SecurityException e) { return ""; }
    }

    // ── Location ──────────────────────────────────────────────────

    private void startLocation() {
        if (locationActive) return;
        try {
            locationMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 5, locListener);
            locationMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 5, locListener);
            locationActive = true;
        } catch (SecurityException e) { Log.e(TAG, "no location perm", e); }
    }

    private void stopLocation() {
        try { locationMgr.removeUpdates(locListener); } catch (Exception ignored) {}
        locationActive = false;
    }

    // ── Triggers ──────────────────────────────────────────────────

    private void checkTriggers(Location loc) {
        if (!prefs.isServiceOn() || !prefs.isLinked()) return;
        float speedKph = loc.getSpeed() * 3.6f;

        // ENTER
        if (speedKph >= prefs.getEnterSpeed()) {
            if (prefs.isGate1On()) {
                float d1 = dist(loc, prefs.getGate1Lat(), prefs.getGate1Lon());
                if (d1 <= prefs.getEnterRadius() && !cooldown(1)) {
                    triggerGate(prefs.getGate1Id(), "כניסה — שער צפון");
                    return;
                }
            }
            if (prefs.isGate2On()) {
                float d2 = dist(loc, prefs.getGate2Lat(), prefs.getGate2Lon());
                if (d2 <= prefs.getEnterRadius() && !cooldown(2)) {
                    triggerGate(prefs.getGate2Id(), "כניסה — שער דרום");
                    return;
                }
            }
        }

        // EXIT
        if (speedKph >= prefs.getExitSpeed() && isOnParkingWifi() && !cooldown(1)) {
            triggerGate(prefs.getGate1Id(), "יציאה — שער צפון");
        }
    }

    private void triggerGate(String gateId, String reason) {
        boolean isGate1 = gateId.equals(prefs.getGate1Id());
        if (isGate1) lastGate1Ms = System.currentTimeMillis();
        else lastGate2Ms = System.currentTimeMillis();

        String gateName = isGate1 ? "שער צפון" : "שער דרום";

        broadcast("פותח " + gateName + "...", reason);
        updateStatusNotif("פותח " + gateName + "...");

        PalGateApiClient.openGate(gateId, prefs.getPhone(), prefs.getToken(),
                prefs.getTokenType(), new PalGateApiClient.OpenCallback() {
                    @Override public void onSuccess(String id) {
                        String msg = "✓ " + gateName + " נפתח";
                        broadcast("מוכן", msg);
                        updateStatusNotif(msg);
                        showEventNotif("פותח " + gateName, reason);
                    }
                    @Override public void onFailure(String id, String err) {
                        String msg = "✗ שגיאה: " + err;
                        broadcast("שגיאה", msg);
                        updateStatusNotif(msg);
                        showEventNotif("שגיאה — " + gateName, err);
                    }
                });
    }

    // ── WiFi ──────────────────────────────────────────────────────

    private boolean isOnParkingWifi() {
        try {
            WifiInfo info = wifiMgr.getConnectionInfo();
            if (info == null) return false;
            return info.getSSID().replace("\"", "").equals(prefs.getWifiSsid());
        } catch (Exception e) { return false; }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private float dist(Location loc, double lat, double lon) {
        float[] r = new float[1];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), lat, lon, r);
        return r[0];
    }

    private boolean cooldown(int gate) {
        long last = gate == 1 ? lastGate1Ms : lastGate2Ms;
        return (System.currentTimeMillis() - last) < prefs.getCooldown() * 1000L;
    }

    private void broadcast(String status, String event) {
        try {
            Intent i = new Intent(ACTION_STATUS);
            i.putExtra(EXTRA_STATUS, status);
            i.putExtra(EXTRA_EVENT, event);
            sendBroadcast(i);
        } catch (Exception ignored) {}
    }

    // ── Notifications ─────────────────────────────────────────────

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Persistent status notification (silent)
            NotificationChannel status = new NotificationChannel(
                    CHANNEL_ID, "סטטוס PalGate", NotificationManager.IMPORTANCE_LOW);
            status.setSound(null, null);

            // Event notifications (brief, with sound)
            NotificationChannel events = new NotificationChannel(
                    CHANNEL_EVENTS, "אירועי שער", NotificationManager.IMPORTANCE_HIGH);

            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(status);
            nm.createNotificationChannel(events);
        }
    }

    private Notification buildStatusNotif(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PalGate Opener")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void updateStatusNotif(String text) {
        try {
            getSystemService(NotificationManager.class)
                    .notify(NOTIF_ID, buildStatusNotif(text));
        } catch (Exception ignored) {}
    }

    /** Short popup notification when gate opens — auto-dismisses */
    private void showEventNotif(String title, String text) {
        try {
            int id = new Random().nextInt(9000) + 1000;
            Notification n = new NotificationCompat.Builder(this, CHANNEL_EVENTS)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_directions)
                    .setAutoCancel(true)
                    .setTimeoutAfter(4000) // auto-dismiss after 4 seconds
                    .build();
            getSystemService(NotificationManager.class).notify(id, n);
        } catch (Exception ignored) {}
    }
}
