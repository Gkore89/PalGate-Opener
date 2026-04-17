package com.palgate.opener;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private AppPrefs prefs;
    private Switch swGate1, swGate2, swV2, swService;
    private EditText etV2Bt, etWifi, etRadius, etEnterSpeed, etExitSpeed, etCooldown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle("הגדרות");
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        prefs = new AppPrefs(this);
        bindViews();
        loadValues();

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_pick_bt).setOnClickListener(v -> pickBt());
        findViewById(R.id.btn_pick_bt2).setOnClickListener(v -> pickBt2());
        swV2.setOnCheckedChangeListener((b, checked) ->
                findViewById(R.id.layout_v2).setVisibility(
                        checked ? android.view.View.VISIBLE : android.view.View.GONE));
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    private void bindViews() {
        swGate1     = findViewById(R.id.sw_gate1);
        swGate2     = findViewById(R.id.sw_gate2);
        swV2        = findViewById(R.id.sw_v2);
        swService   = findViewById(R.id.sw_service);
        etV2Bt      = findViewById(R.id.et_v2_bt);
        etWifi      = findViewById(R.id.et_wifi);
        etRadius    = findViewById(R.id.et_radius);
        etEnterSpeed= findViewById(R.id.et_enter_speed);
        etExitSpeed = findViewById(R.id.et_exit_speed);
        etCooldown  = findViewById(R.id.et_cooldown);
    }

    private void loadValues() {
        swGate1.setChecked(prefs.isGate1On());
        swGate2.setChecked(prefs.isGate2On());
        swService.setChecked(prefs.isServiceOn());
        swV2.setChecked(prefs.isV2Enabled());
        etV2Bt.setText(prefs.getV2BtName());
        etWifi.setText(prefs.getWifiSsid());
        etRadius.setText(String.valueOf(prefs.getEnterRadius()));
        etEnterSpeed.setText(String.valueOf(prefs.getEnterSpeed()));
        etExitSpeed.setText(String.valueOf(prefs.getExitSpeed()));
        etCooldown.setText(String.valueOf(prefs.getCooldown()));
        findViewById(R.id.layout_v2).setVisibility(
                prefs.isV2Enabled() ? android.view.View.VISIBLE : android.view.View.GONE);

        // Show current V1 BT
        TextView tvV1Bt = findViewById(R.id.tv_v1_bt_current);
        String v1bt = prefs.getV1BtName();
        tvV1Bt.setText(v1bt.isEmpty() ? "לא נבחר" : v1bt);
    }

    private void save() {
        prefs.setBool(AppPrefs.KEY_GATE1_ON, swGate1.isChecked());
        prefs.setBool(AppPrefs.KEY_GATE2_ON, swGate2.isChecked());
        prefs.setBool(AppPrefs.KEY_SERVICE_ON, swService.isChecked());
        prefs.setBool(AppPrefs.KEY_V2_ENABLED, swV2.isChecked());
        prefs.set(AppPrefs.KEY_V2_BT_NAME, etV2Bt.getText().toString().trim());
        prefs.set(AppPrefs.KEY_WIFI_SSID, etWifi.getText().toString().trim());
        prefs.setInt(AppPrefs.KEY_ENTER_RADIUS, parseInt(etRadius, AppPrefs.DEFAULT_ENTER_RADIUS));
        prefs.setInt(AppPrefs.KEY_ENTER_SPEED, parseInt(etEnterSpeed, AppPrefs.DEFAULT_ENTER_SPEED));
        prefs.setInt(AppPrefs.KEY_EXIT_SPEED, parseInt(etExitSpeed, AppPrefs.DEFAULT_EXIT_SPEED));
        prefs.setInt(AppPrefs.KEY_COOLDOWN, parseInt(etCooldown, AppPrefs.DEFAULT_COOLDOWN));
        Toast.makeText(this, "ההגדרות נשמרו ✓", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void pickBt() { showBtPicker(false); }
    private void pickBt2() { showBtPicker(true); }

    private void showBtPicker(boolean isV2) {
        try {
            android.bluetooth.BluetoothAdapter adapter =
                    android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return;
            java.util.List<String> names = new java.util.ArrayList<>();
            for (android.bluetooth.BluetoothDevice d : adapter.getBondedDevices()) {
                try { String n = d.getName(); if (n != null) names.add(n); }
                catch (SecurityException ignored) {}
            }
            if (names.isEmpty()) {
                Toast.makeText(this, "אין מכשירים מזווגים", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] items = names.toArray(new String[0]);
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("בחר רכב")
                    .setItems(items, (d, which) -> {
                        if (isV2) {
                            etV2Bt.setText(items[which]);
                        } else {
                            prefs.set(AppPrefs.KEY_V1_BT_NAME, items[which]);
                            ((TextView) findViewById(R.id.tv_v1_bt_current)).setText(items[which]);
                        }
                    })
                    .setNegativeButton("ביטול", null).show();
        } catch (SecurityException e) {
            Toast.makeText(this, "נדרשת הרשאת Bluetooth", Toast.LENGTH_SHORT).show();
        }
    }

    private int parseInt(EditText et, int def) {
        try { return Integer.parseInt(et.getText().toString().trim()); }
        catch (Exception e) { return def; }
    }
}
