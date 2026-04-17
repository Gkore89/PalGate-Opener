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
import java.util.Set;

public class GateMonitorService extends Service {

    private static final String TAG = "GateMonitor";
    private static final String CHANNEL_ID = "palgate_monitor";
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

    private final LocationListener locListener = new LocationListener() {
        @Override public void onLocationChanged(Location loc) { checkTriggers(loc); }
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
    };

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (dev == null) return;
            String name = getDeviceName(dev);
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())
                    && isKnownVehicle(name)) {
                broadcast("בתוך הרכב — פעיל", "BT: " + name);
                updateNotif("פעיל — " + name);
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())
                    && isKnownVehicle(name)) {
                broadcast("המתנה", "BT נותק: " + name);
                updateNotif("המתנה");
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = new AppPrefs(this);
        locationMgr = (LocationManager) getSystemService(LOCATION_SERVICE);
        wifiMgr = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        createChannel();
        startForeground(NOTIF_ID, buildNotif("PalGate Opener פעיל"));
        registerBtReceiver();
        startLocation();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_OPEN.equals(intent.getAction())) {
            String gateId = intent.getStringExtra(EXTRA_GATE_ID);
            if (gateId != null) triggerGate(gateId, "ידני");
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(btReceiver); } catch (Exception ignored) {}
        try { locationMgr.removeUpdates(locListener); } catch (Exception ignored) {}
    }

    @Override public IBinder onBind(Intent i) { return null; }

    // ── Location ──────────────────────────────────────────────────

    private void startLocation() {
        try {
            locationMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 5, locListener);
            locationMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 5, locListener);
        } catch (SecurityException e) { Log.e(TAG, "no location perm"); }
    }

    // ── Trigger logic ─────────────────────────────────────────────

    private void checkTriggers(Location loc) {
        if (!prefs.isServiceOn() || !prefs.isLinked()) return;

        float speedKph = loc.getSpeed() * 3.6f;

        // ENTER: near gate + vehicle BT + speed
        if (speedKph >= prefs.getEnterSpeed() && isVehicleBtConnected()) {
            if (prefs.isGate1On()) {
                float d1 = dist(loc, prefs.getGate1Lat(), prefs.getGate1Lon());
                if (d1 <= prefs.getEnterRadius() && !cooldown(1)) {
                    triggerGate(prefs.getGate1Id(), "כניסה שער 1");
                    return;
                }
            }
            if (prefs.isGate2On()) {
                float d2 = dist(loc, prefs.getGate2Lat(), prefs.getGate2Lon());
                if (d2 <= prefs.getEnterRadius() && !cooldown(2)) {
                    triggerGate(prefs.getGate2Id(), "כניסה שער 2");
                    return;
                }
            }
        }

        // EXIT: parking WiFi + speed
        if (speedKph >= prefs.getExitSpeed() && isOnParkingWifi() && !cooldown(1)) {
            triggerGate(prefs.getGate1Id(), "יציאה (WiFi+מהירות)");
        }
    }

    private void triggerGate(String gateId, String reason) {
        if (gateId.equals(prefs.getGate1Id())) lastGate1Ms = System.currentTimeMillis();
        else lastGate2Ms = System.currentTimeMillis();

        broadcast("פותח " + gateId + "...", reason);
        updateNotif("פותח שער — " + reason);

        PalGateApiClient.openGate(gateId, prefs.getPhone(), prefs.getToken(),
                prefs.getTokenType(), new PalGateApiClient.OpenCallback() {
                    @Override public void onSuccess(String id) {
                        String msg = "✓ שער נפתח (" + reason + ")";
                        broadcast("מוכן", msg);
                        updateNotif(msg);
                    }
                    @Override public void onFailure(String id, String err) {
                        String msg = "✗ שגיאה: " + err;
                        broadcast("שגיאה", msg);
                        updateNotif(msg);
                    }
                });
    }

    // ── BT helpers ────────────────────────────────────────────────

    private void registerBtReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(btReceiver, f);
    }

    private boolean isKnownVehicle(String name) {
        if (name == null) return false;
        if (name.equals(prefs.getV1BtName())) return true;
        if (prefs.isV2Enabled() && name.equals(prefs.getV2BtName())) return true;
        return false;
    }

    private boolean isVehicleBtConnected() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null || !a.isEnabled()) return false;
            for (BluetoothDevice d : a.getBondedDevices()) {
                if (isKnownVehicle(getDeviceName(d))) {
                    try {
                        java.lang.reflect.Method m = d.getClass().getMethod("isConnected");
                        if ((Boolean) m.invoke(d)) return true;
                    } catch (Exception ignored) {}
                }
            }
        } catch (SecurityException ignored) {}
        return false;
    }

    private String getDeviceName(BluetoothDevice d) {
        try { return d.getName(); } catch (SecurityException e) { return ""; }
    }

    // ── WiFi helper ───────────────────────────────────────────────

    private boolean isOnParkingWifi() {
        try {
            WifiInfo info = wifiMgr.getConnectionInfo();
            if (info == null) return false;
            String ssid = info.getSSID().replace("\"", "");
            return ssid.equals(prefs.getWifiSsid());
        } catch (Exception e) { return false; }
    }

    // ── Utilities ─────────────────────────────────────────────────

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
        Intent i = new Intent(ACTION_STATUS);
        i.putExtra(EXTRA_STATUS, status);
        i.putExtra(EXTRA_EVENT, event);
        sendBroadcast(i);
    }

    // ── Notification ──────────────────────────────────────────────

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "ניטור שער", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotif(String text) {
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

    private void updateNotif(String text) {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotif(text));
    }
}
