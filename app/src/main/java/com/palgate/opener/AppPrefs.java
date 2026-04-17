package com.palgate.opener;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {

    private static final String PREFS_NAME = "palgate_prefs";

    // PalGate credentials
    public static final String KEY_PHONE = "phone";
    public static final String KEY_TOKEN = "session_token";
    public static final String KEY_TOKEN_TYPE = "token_type";

    // Gate IDs
    public static final String KEY_GATE1_ID = "gate1_id";
    public static final String KEY_GATE2_ID = "gate2_id";
    public static final String KEY_GATE1_LAT = "gate1_lat";
    public static final String KEY_GATE1_LON = "gate1_lon";
    public static final String KEY_GATE2_LAT = "gate2_lat";
    public static final String KEY_GATE2_LON = "gate2_lon";
    public static final String KEY_GATE1_ENABLED = "gate1_enabled";
    public static final String KEY_GATE2_ENABLED = "gate2_enabled";

    // Vehicle 1
    public static final String KEY_V1_BT_NAME = "v1_bt_name";
    public static final String KEY_V1_WIFI_SSID = "v1_wifi_ssid";

    // Vehicle 2
    public static final String KEY_V2_BT_NAME = "v2_bt_name";
    public static final String KEY_V2_WIFI_SSID = "v2_wifi_ssid";
    public static final String KEY_V2_ENABLED = "v2_enabled";

    // Trigger settings
    public static final String KEY_ENTER_RADIUS_M = "enter_radius_m";
    public static final String KEY_ENTER_MIN_SPEED_KPH = "enter_min_speed_kph";
    public static final String KEY_EXIT_MIN_SPEED_KPH = "exit_min_speed_kph";
    public static final String KEY_COOLDOWN_SEC = "cooldown_sec";
    public static final String KEY_SERVICE_ENABLED = "service_enabled";

    // Defaults
    public static final String DEFAULT_GATE1_ID = "4G600104640";
    public static final String DEFAULT_GATE2_ID = "4G600104641";
    public static final double DEFAULT_GATE1_LAT = 32.058935;
    public static final double DEFAULT_GATE1_LON = 34.862667;
    public static final double DEFAULT_GATE2_LAT = 32.057775;
    public static final double DEFAULT_GATE2_LON = 34.863480;
    public static final String DEFAULT_V1_BT = "Tesla Model 3 Tesly";
    public static final String DEFAULT_V1_WIFI = "KingShaul6";
    public static final int DEFAULT_ENTER_RADIUS = 80;
    public static final int DEFAULT_ENTER_SPEED = 10;
    public static final int DEFAULT_EXIT_SPEED = 10;
    public static final int DEFAULT_COOLDOWN = 60;

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String get(String key, String def) { return prefs.getString(key, def); }
    public int getInt(String key, int def) { return prefs.getInt(key, def); }
    public boolean getBool(String key, boolean def) { return prefs.getBoolean(key, def); }
    public double getDouble(String key, double def) {
        return Double.longBitsToDouble(prefs.getLong(key, Double.doubleToLongBits(def)));
    }

    public void set(String key, String val) { prefs.edit().putString(key, val).apply(); }
    public void setInt(String key, int val) { prefs.edit().putInt(key, val).apply(); }
    public void setBool(String key, boolean val) { prefs.edit().putBoolean(key, val).apply(); }
    public void setDouble(String key, double val) {
        prefs.edit().putLong(key, Double.doubleToLongBits(val)).apply();
    }

    // Convenience getters with defaults
    public String getPhone()       { return get(KEY_PHONE, ""); }
    public String getToken()       { return get(KEY_TOKEN, ""); }
    public int    getTokenType()   { return getInt(KEY_TOKEN_TYPE, 1); }
    public String getGate1Id()     { return get(KEY_GATE1_ID, DEFAULT_GATE1_ID); }
    public String getGate2Id()     { return get(KEY_GATE2_ID, DEFAULT_GATE2_ID); }
    public double getGate1Lat()    { return getDouble(KEY_GATE1_LAT, DEFAULT_GATE1_LAT); }
    public double getGate1Lon()    { return getDouble(KEY_GATE1_LON, DEFAULT_GATE1_LON); }
    public double getGate2Lat()    { return getDouble(KEY_GATE2_LAT, DEFAULT_GATE2_LAT); }
    public double getGate2Lon()    { return getDouble(KEY_GATE2_LON, DEFAULT_GATE2_LON); }
    public boolean isGate1Enabled(){ return getBool(KEY_GATE1_ENABLED, true); }
    public boolean isGate2Enabled(){ return getBool(KEY_GATE2_ENABLED, true); }
    public String getV1BtName()    { return get(KEY_V1_BT_NAME, DEFAULT_V1_BT); }
    public String getV1WifiSsid()  { return get(KEY_V1_WIFI_SSID, DEFAULT_V1_WIFI); }
    public String getV2BtName()    { return get(KEY_V2_BT_NAME, ""); }
    public String getV2WifiSsid()  { return get(KEY_V2_WIFI_SSID, ""); }
    public boolean isV2Enabled()   { return getBool(KEY_V2_ENABLED, false); }
    public int getEnterRadius()    { return getInt(KEY_ENTER_RADIUS_M, DEFAULT_ENTER_RADIUS); }
    public int getEnterMinSpeed()  { return getInt(KEY_ENTER_MIN_SPEED_KPH, DEFAULT_ENTER_SPEED); }
    public int getExitMinSpeed()   { return getInt(KEY_EXIT_MIN_SPEED_KPH, DEFAULT_EXIT_SPEED); }
    public int getCooldownSec()    { return getInt(KEY_COOLDOWN_SEC, DEFAULT_COOLDOWN); }
    public boolean isServiceEnabled(){ return getBool(KEY_SERVICE_ENABLED, true); }
}
