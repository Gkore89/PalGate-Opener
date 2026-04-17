package com.palgate.opener;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.widget.ImageView;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvLastEvent, tvPhone, tvGate1, tvGate2;
    private ImageView ivStatusDot;
    private AppPrefs prefs;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String status = intent.getStringExtra(GateMonitorService.EXTRA_STATUS_TEXT);
            String event = intent.getStringExtra(GateMonitorService.EXTRA_LAST_EVENT);
            updateStatusUI(status, event);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new AppPrefs(this);

        tvStatus    = findViewById(R.id.tv_status);
        tvLastEvent = findViewById(R.id.tv_last_event);
        tvPhone     = findViewById(R.id.tv_phone);
        tvGate1     = findViewById(R.id.tv_gate1_id);
        tvGate2     = findViewById(R.id.tv_gate2_id);
        ivStatusDot = findViewById(R.id.iv_status_dot);

        findViewById(R.id.btn_open_gate1).setOnClickListener(v -> openGate(prefs.getGate1Id()));
        findViewById(R.id.btn_open_gate2).setOnClickListener(v -> openGate(prefs.getGate2Id()));
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        updateInfoUI();
        requestPermissions();
        startMonitorService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(statusReceiver,
                new IntentFilter(GateMonitorService.ACTION_STATUS_UPDATE));
        updateInfoUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
    }

    private void openGate(String gateId) {
        Intent i = new Intent(this, GateMonitorService.class);
        i.setAction("OPEN_GATE");
        i.putExtra("gate_id", gateId);
        startService(i);
        updateStatusUI("Opening " + gateId + "...", "Manual trigger");
    }

    private void updateStatusUI(String status, String event) {
        runOnUiThread(() -> {
            tvStatus.setText(status);
            tvLastEvent.setText("Last: " + event);
            boolean ok = status != null && !status.startsWith("Error");
            ivStatusDot.setImageResource(ok ?
                    R.drawable.dot_green : R.drawable.dot_red);
        });
    }

    private void updateInfoUI() {
        String phone = prefs.getPhone();
        tvPhone.setText(phone.isEmpty() ? "⚠ Not configured" : "Phone: " + phone);
        tvGate1.setText("Gate 1: " + prefs.getGate1Id());
        tvGate2.setText("Gate 2: " + prefs.getGate2Id());
    }

    private void startMonitorService() {
        Intent i = new Intent(this, GateMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private void requestPermissions() {
        String[] perms = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS
        };
        boolean needRequest = false;
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        if (needRequest) ActivityCompat.requestPermissions(this, perms, 100);

        // Background location needs a separate request
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 101);
        }
    }
}
