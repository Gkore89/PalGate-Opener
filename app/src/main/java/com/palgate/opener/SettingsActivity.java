package com.palgate.opener;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private AppPrefs prefs;

    // Credentials
    private EditText etPhone, etToken, etTokenType;

    // Gates
    private EditText etGate1Id, etGate1Lat, etGate1Lon;
    private EditText etGate2Id, etGate2Lat, etGate2Lon;
    private Switch swGate1Enabled, swGate2Enabled;

    // Vehicle 1
    private EditText etV1BtName, etV1WifiSsid;

    // Vehicle 2
    private Switch swV2Enabled;
    private EditText etV2BtName, etV2WifiSsid;

    // Trigger settings
    private EditText etEnterRadius, etEnterSpeed, etExitSpeed, etCooldown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new AppPrefs(this);

        bindViews();
        loadValues();

        findViewById(R.id.btn_save).setOnClickListener(v -> saveAndFinish());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle("Settings");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void bindViews() {
        etPhone       = findViewById(R.id.et_phone);
        etToken       = findViewById(R.id.et_token);
        etTokenType   = findViewById(R.id.et_token_type);

        etGate1Id     = findViewById(R.id.et_gate1_id);
        etGate1Lat    = findViewById(R.id.et_gate1_lat);
        etGate1Lon    = findViewById(R.id.et_gate1_lon);
        etGate2Id     = findViewById(R.id.et_gate2_id);
        etGate2Lat    = findViewById(R.id.et_gate2_lat);
        etGate2Lon    = findViewById(R.id.et_gate2_lon);
        swGate1Enabled = findViewById(R.id.sw_gate1_enabled);
        swGate2Enabled = findViewById(R.id.sw_gate2_enabled);

        etV1BtName    = findViewById(R.id.et_v1_bt);
        etV1WifiSsid  = findViewById(R.id.et_v1_wifi);

        swV2Enabled   = findViewById(R.id.sw_v2_enabled);
        etV2BtName    = findViewById(R.id.et_v2_bt);
        etV2WifiSsid  = findViewById(R.id.et_v2_wifi);

        etEnterRadius = findViewById(R.id.et_enter_radius);
        etEnterSpeed  = findViewById(R.id.et_enter_speed);
        etExitSpeed   = findViewById(R.id.et_exit_speed);
        etCooldown    = findViewById(R.id.et_cooldown);

        // Show/hide V2 fields based on switch
        swV2Enabled.setOnCheckedChangeListener((btn, checked) -> {
            int vis = checked ? android.view.View.VISIBLE : android.view.View.GONE;
            findViewById(R.id.layout_v2_fields).setVisibility(vis);
        });
    }

    private void loadValues() {
        etPhone.setText(prefs.getPhone());
        etToken.setText(prefs.getToken());
        etTokenType.setText(String.valueOf(prefs.getTokenType()));

        etGate1Id.setText(prefs.getGate1Id());
        etGate1Lat.setText(String.valueOf(prefs.getGate1Lat()));
        etGate1Lon.setText(String.valueOf(prefs.getGate1Lon()));
        etGate2Id.setText(prefs.getGate2Id());
        etGate2Lat.setText(String.valueOf(prefs.getGate2Lat()));
        etGate2Lon.setText(String.valueOf(prefs.getGate2Lon()));
        swGate1Enabled.setChecked(prefs.isGate1Enabled());
        swGate2Enabled.setChecked(prefs.isGate2Enabled());

        etV1BtName.setText(prefs.getV1BtName());
        etV1WifiSsid.setText(prefs.getV1WifiSsid());

        boolean v2 = prefs.isV2Enabled();
        swV2Enabled.setChecked(v2);
        etV2BtName.setText(prefs.getV2BtName());
        etV2WifiSsid.setText(prefs.getV2WifiSsid());
        findViewById(R.id.layout_v2_fields).setVisibility(
                v2 ? android.view.View.VISIBLE : android.view.View.GONE);

        etEnterRadius.setText(String.valueOf(prefs.getEnterRadius()));
        etEnterSpeed.setText(String.valueOf(prefs.getEnterMinSpeed()));
        etExitSpeed.setText(String.valueOf(prefs.getExitMinSpeed()));
        etCooldown.setText(String.valueOf(prefs.getCooldownSec()));
    }

    private void saveAndFinish() {
        prefs.set(AppPrefs.KEY_PHONE, etPhone.getText().toString().trim());
        prefs.set(AppPrefs.KEY_TOKEN, etToken.getText().toString().trim());
        prefs.setInt(AppPrefs.KEY_TOKEN_TYPE, parseInt(etTokenType, 1));

        prefs.set(AppPrefs.KEY_GATE1_ID, etGate1Id.getText().toString().trim());
        prefs.setDouble(AppPrefs.KEY_GATE1_LAT, parseDouble(etGate1Lat, AppPrefs.DEFAULT_GATE1_LAT));
        prefs.setDouble(AppPrefs.KEY_GATE1_LON, parseDouble(etGate1Lon, AppPrefs.DEFAULT_GATE1_LON));
        prefs.set(AppPrefs.KEY_GATE2_ID, etGate2Id.getText().toString().trim());
        prefs.setDouble(AppPrefs.KEY_GATE2_LAT, parseDouble(etGate2Lat, AppPrefs.DEFAULT_GATE2_LAT));
        prefs.setDouble(AppPrefs.KEY_GATE2_LON, parseDouble(etGate2Lon, AppPrefs.DEFAULT_GATE2_LON));
        prefs.setBool(AppPrefs.KEY_GATE1_ENABLED, swGate1Enabled.isChecked());
        prefs.setBool(AppPrefs.KEY_GATE2_ENABLED, swGate2Enabled.isChecked());

        prefs.set(AppPrefs.KEY_V1_BT_NAME, etV1BtName.getText().toString().trim());
        prefs.set(AppPrefs.KEY_V1_WIFI_SSID, etV1WifiSsid.getText().toString().trim());

        prefs.setBool(AppPrefs.KEY_V2_ENABLED, swV2Enabled.isChecked());
        prefs.set(AppPrefs.KEY_V2_BT_NAME, etV2BtName.getText().toString().trim());
        prefs.set(AppPrefs.KEY_V2_WIFI_SSID, etV2WifiSsid.getText().toString().trim());

        prefs.setInt(AppPrefs.KEY_ENTER_RADIUS_M, parseInt(etEnterRadius, AppPrefs.DEFAULT_ENTER_RADIUS));
        prefs.setInt(AppPrefs.KEY_ENTER_MIN_SPEED_KPH, parseInt(etEnterSpeed, AppPrefs.DEFAULT_ENTER_SPEED));
        prefs.setInt(AppPrefs.KEY_EXIT_MIN_SPEED_KPH, parseInt(etExitSpeed, AppPrefs.DEFAULT_EXIT_SPEED));
        prefs.setInt(AppPrefs.KEY_COOLDOWN_SEC, parseInt(etCooldown, AppPrefs.DEFAULT_COOLDOWN));

        Toast.makeText(this, "Settings saved ✓", Toast.LENGTH_SHORT).show();
        finish();
    }

    private int parseInt(EditText et, int def) {
        try { return Integer.parseInt(et.getText().toString().trim()); }
        catch (Exception e) { return def; }
    }

    private double parseDouble(EditText et, double def) {
        try { return Double.parseDouble(et.getText().toString().trim()); }
        catch (Exception e) { return def; }
    }
}
