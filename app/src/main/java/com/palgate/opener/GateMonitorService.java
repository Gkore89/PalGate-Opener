package com.palgate.opener;

import android.app.*;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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

    // Intent actions for broadcast updates to UI
    public static final String ACTION_STATUS_UPDATE = "com.palgate.opener.STATUS_UPDATE";
    public static final String EXTRA_STATUS_TEXT = "status_text";
    public static final String EXTRA_LAST_EVENT = "last_event";

    private AppPrefs prefs;
    private LocationManager locationManager;
    private WifiManager wifiManager;

    private long lastGate1TriggerMs = 0;
    private long lastGate2TriggerMs = 0;

    private String currentStatus = "Monitoring...";
    private String lastEvent = "None";

    // Location listener for GPS speed + position
    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            checkTriggers(location);
        }
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
    };

    // BT broadcast receiver
    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) onBluetoothConnected(device.getName());
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) onBluetoothDisconnected(device.getName());
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new AppPrefs(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Monitoring active"));

        registerBtReceiver();
        startLocationUpdates();

        Log.d(TAG, "GateMonitorService started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "OPEN_GATE".equals(intent.getAction())) {
            String gateId = intent.getStringExtra("gate_id");
            if (gateId != null) triggerGate(gateId, "Manual");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(btReceiver); } catch (Exception ignored) {}
        try { locationManager.removeUpdates(locationListener); } catch (Exception ignored) {}
    }

    @Override public IBinder onBind(Intent i) { return null; }

    // ── Location updates ──────────────────────────────────────────

    private void startLocationUpdates() {
        try {
            // Request GPS updates every 3 seconds, min 5m movement
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 3000, 5, locationListener);
            // Fallback: network provider (works underground)
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 3000, 5, locationListener);
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission denied", e);
        }
    }

    // ── Core trigger logic ────────────────────────────────────────

    private void checkTriggers(Location location) {
        if (!prefs.isServiceEnabled()) return;

        float speedKph = location.getSpeed() * 3.6f; // m/s to km/h
        boolean isMovingFast = speedKph >= prefs.getEnterMinSpeed();
        boolean isMovingForExit = speedKph >= prefs.getExitMinSpeed();

        Log.d(TAG, "Location update: speed=" + speedKph + " kph");

        // ── ENTER scenario: check proximity to gates ──────────────
        if (isMovingFast && isVehicleBtConnected()) {

            if (prefs.isGate1Enabled()) {
                float distToGate1 = distanceTo(location,
                        prefs.getGate1Lat(), prefs.getGate1Lon());
                if (distToGate1 <= prefs.getEnterRadius() && !onCooldown(1)) {
                    Log.d(TAG, "Enter Gate 1 triggered, dist=" + distToGate1 + "m");
                    triggerGate(prefs.getGate1Id(), "Enter (Gate 1)");
                    return;
                }
            }

            if (prefs.isGate2Enabled()) {
                float distToGate2 = distanceTo(location,
                        prefs.getGate2Lat(), prefs.getGate2Lon());
                if (distToGate2 <= prefs.getEnterRadius() && !onCooldown(2)) {
                    Log.d(TAG, "Enter Gate 2 triggered, dist=" + distToGate2 + "m");
                    triggerGate(prefs.getGate2Id(), "Enter (Gate 2)");
                    return;
                }
            }
        }

        // ── EXIT scenario: on parking WiFi + moving ───────────────
        if (isMovingForExit && isOnParkingWifi() && !onCooldown(1)) {
            Log.d(TAG, "Exit Gate 1 triggered via WiFi+speed");
            triggerGate(prefs.getGate1Id(), "Exit (WiFi+speed)");
        }
    }

    // ── Gate trigger ──────────────────────────────────────────────

    private void triggerGate(String gateId, String reason) {
        // Mark cooldown immediately to prevent double-trigger
        if (gateId.equals(prefs.getGate1Id())) lastGate1TriggerMs = System.currentTimeMillis();
        if (gateId.equals(prefs.getGate2Id())) lastGate2TriggerMs = System.currentTimeMillis();

        setStatus("Opening " + gateId + "...", reason);

        PalGateApiClient.openGate(
                gateId,
                prefs.getPhone(),
                prefs.getToken(),
                prefs.getTokenType(),
                new PalGateApiClient.OpenGateCallback() {
                    @Override
                    public void onSuccess(String id) {
                        String msg = "✓ Gate opened (" + reason + ")";
                        setStatus("Ready", msg);
                        updateNotification(msg);
                        Log.d(TAG, msg);
                    }
                    @Override
                    public void onFailure(String id, String error) {
                        String msg = "✗ Gate failed: " + error;
                        setStatus("Error", msg);
                        updateNotification(msg);
                        Log.e(TAG, msg);
                    }
                });
    }

    // ── BT helpers ────────────────────────────────────────────────

    private void registerBtReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(btReceiver, filter);
    }

    private void onBluetoothConnected(String deviceName) {
        Log.d(TAG, "BT connected: " + deviceName);
        if (isKnownVehicleBt(deviceName)) {
            setStatus("In vehicle — monitoring", "BT connected: " + deviceName);
        }
    }

    private void onBluetoothDisconnected(String deviceName) {
        Log.d(TAG, "BT disconnected: " + deviceName);
        if (isKnownVehicleBt(deviceName)) {
            setStatus("Standby (not in vehicle)", "BT disconnected: " + deviceName);
        }
    }

    private boolean isKnownVehicleBt(String name) {
        if (name == null) return false;
        if (name.equals(prefs.getV1BtName())) return true;
        if (prefs.isV2Enabled() && name.equals(prefs.getV2BtName())) return true;
        return false;
    }

    /** Check if any known vehicle BT is currently connected */
    private boolean isVehicleBtConnected() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return false;
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            for (BluetoothDevice d : bonded) {
                if (isKnownVehicleBt(d.getName())) {
                    // Check connection state via reflection (works without BLUETOOTH_ADMIN)
                    try {
                        java.lang.reflect.Method m = d.getClass().getMethod("isConnected");
                        return (Boolean) m.invoke(d);
                    } catch (Exception ignored) {}
                }
            }
        } catch (SecurityException ignored) {}
        return false;
    }

    // ── WiFi helper ───────────────────────────────────────────────

    private boolean isOnParkingWifi() {
        try {
            WifiInfo info = wifiManager.getConnectionInfo();
            if (info == null) return false;
            String ssid = info.getSSID().replace("\"", ""); // strip quotes Android adds
            if (ssid.equals(prefs.getV1WifiSsid())) return true;
            if (prefs.isV2Enabled() && ssid.equals(prefs.getV2WifiSsid())) return true;
        } catch (Exception ignored) {}
        return false;
    }

    // ── Utilities ─────────────────────────────────────────────────

    private float distanceTo(Location loc, double lat, double lon) {
        float[] results = new float[1];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), lat, lon, results);
        return results[0];
    }

    private boolean onCooldown(int gateNum) {
        long lastMs = gateNum == 1 ? lastGate1TriggerMs : lastGate2TriggerMs;
        long cooldownMs = prefs.getCooldownSec() * 1000L;
        return (System.currentTimeMillis() - lastMs) < cooldownMs;
    }

    private void setStatus(String status, String event) {
        currentStatus = status;
        lastEvent = event;
        Intent i = new Intent(ACTION_STATUS_UPDATE);
        i.putExtra(EXTRA_STATUS_TEXT, status);
        i.putExtra(EXTRA_LAST_EVENT, event);
        sendBroadcast(i);
    }

    // ── Notification ──────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Gate Monitor", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("PalGate background monitor");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PalGate Opener")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification(text));
    }
}
