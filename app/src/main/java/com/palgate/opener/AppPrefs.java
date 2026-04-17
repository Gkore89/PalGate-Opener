package com.palgate.opener;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {

    private static final String PREFS_NAME = "palgate_prefs";

    // ── Pre-configured building values (same for all neighbors) ──
    public static final String BUILDING_GATE1_ID  = "4G600104640";
    public static final String BUILDING_GATE2_ID  = "4G600104641";
    public static final double BUILDING_GATE1_LAT = 32.058935;
    public static final double BUILDING_GATE1_LON = 34.862667;
    public static final double BUILDING_GATE2_LAT = 32.057775;
    public static final double BUILDING_GATE2_LON = 34.863480;
    public static final String BUILDING_WIFI_SSID = "KingShaul6";
    public static final int    DEFAULT_ENTER_RADIUS  = 80;
    public static final int    DEFAULT_ENTER_SPEED   = 10;
    public static final int    DEFAULT_EXIT_SPEED    = 10;
    public static final int    DEFAULT_COOLDOWN      = 60;

    // ── Preference keys ──
    // Per-user credentials
    public static final String KEY_PHONE        = "phone";
    public static final String KEY_TOKEN        = "session_token";
    public static final String KEY_TOKEN_TYPE   = "token_type";
    public static final String KEY_LINKED       = "is_linked";

    // Vehicle 1
    public static final String KEY_V1_BT_NAME  = "v1_bt_name";

    // Vehicle 2
    public static final String KEY_V2_ENABLED  = "v2_enabled";
    public static final String KEY_V2_BT_NAME  = "v2_bt_name";

    // Overridable building settings (in case admin wants to change)
    public static final String KEY_GATE1_ID    = "gate1_id";
    public static final String KEY_GATE2_ID    = "gate2_id";
    public static final String KEY_GATE1_LAT   = "gate1_lat";
    public static final String KEY_GATE1_LON   = "gate1_lon";
    public static final String KEY_GATE2_LAT   = "gate2_lat";
    public static final String KEY_GATE2_LON   = "gate2_lon";
    public static final String KEY_GATE1_ON    = "gate1_enabled";
    public static final String KEY_GATE2_ON    = "gate2_enabled";
    public static final String KEY_WIFI_SSID   = "wifi_ssid";
    public static final String KEY_ENTER_RADIUS = "enter_radius";
    public static final String KEY_ENTER_SPEED  = "enter_speed";
    public static final String KEY_EXIT_SPEED   = "exit_speed";
    public static final String KEY_COOLDOWN     = "cooldown";
    public static final String KEY_SERVICE_ON   = "service_enabled";

    private final SharedPreferences p;

    public AppPrefs(Context ctx) {
        p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Generic accessors ──
    public String  get(String k, String def)  { return p.getString(k, def); }
    public int     getInt(String k, int def)  { return p.getInt(k, def); }
    public boolean getBool(String k, boolean def) { return p.getBoolean(k, def); }
    public double  getDouble(String k, double def) {
        return Double.longBitsToDouble(p.getLong(k, Double.doubleToLongBits(def)));
    }
    public void set(String k, String v)    { p.edit().putString(k, v).apply(); }
    public void setInt(String k, int v)    { p.edit().putInt(k, v).apply(); }
    public void setBool(String k, boolean v){ p.edit().putBoolean(k, v).apply(); }
    public void setDouble(String k, double v){
        p.edit().putLong(k, Double.doubleToLongBits(v)).apply();
    }

    // ── Credential accessors ──
    public String  getPhone()      { return get(KEY_PHONE, ""); }
    public String  getToken()      { return get(KEY_TOKEN, ""); }
    public int     getTokenType()  { return getInt(KEY_TOKEN_TYPE, 1); }
    public boolean isLinked()      { return getBool(KEY_LINKED, false) && !getToken().isEmpty(); }

    public void saveCredentials(String phone, String token, int tokenType) {
        set(KEY_PHONE, phone);
        set(KEY_TOKEN, token);
        setInt(KEY_TOKEN_TYPE, tokenType);
        setBool(KEY_LINKED, true);
    }

    public void clearCredentials() {
        set(KEY_PHONE, "");
        set(KEY_TOKEN, "");
        setBool(KEY_LINKED, false);
    }

    // ── Vehicle accessors ──
    public String  getV1BtName()   { return get(KEY_V1_BT_NAME, ""); }
    public boolean isV2Enabled()   { return getBool(KEY_V2_ENABLED, false); }
    public String  getV2BtName()   { return get(KEY_V2_BT_NAME, ""); }

    // ── Building/gate accessors (fall back to hardcoded building values) ──
    public String  getGate1Id()    { return get(KEY_GATE1_ID, BUILDING_GATE1_ID); }
    public String  getGate2Id()    { return get(KEY_GATE2_ID, BUILDING_GATE2_ID); }
    public double  getGate1Lat()   { return getDouble(KEY_GATE1_LAT, BUILDING_GATE1_LAT); }
    public double  getGate1Lon()   { return getDouble(KEY_GATE1_LON, BUILDING_GATE1_LON); }
    public double  getGate2Lat()   { return getDouble(KEY_GATE2_LAT, BUILDING_GATE2_LAT); }
    public double  getGate2Lon()   { return getDouble(KEY_GATE2_LON, BUILDING_GATE2_LON); }
    public boolean isGate1On()     { return getBool(KEY_GATE1_ON, true); }
    public boolean isGate2On()     { return getBool(KEY_GATE2_ON, true); }
    public String  getWifiSsid()   { return get(KEY_WIFI_SSID, BUILDING_WIFI_SSID); }
    public int     getEnterRadius(){ return getInt(KEY_ENTER_RADIUS, DEFAULT_ENTER_RADIUS); }
    public int     getEnterSpeed() { return getInt(KEY_ENTER_SPEED, DEFAULT_ENTER_SPEED); }
    public int     getExitSpeed()  { return getInt(KEY_EXIT_SPEED, DEFAULT_EXIT_SPEED); }
    public int     getCooldown()   { return getInt(KEY_COOLDOWN, DEFAULT_COOLDOWN); }
    public boolean isServiceOn()   { return getBool(KEY_SERVICE_ON, true); }
}
