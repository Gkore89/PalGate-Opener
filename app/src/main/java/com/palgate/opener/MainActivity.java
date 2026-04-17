package com.palgate.opener;

import android.Manifest;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import android.view.*;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_LINK = 200;

    private TextView tvStatus, tvLastEvent, tvLinkedAs;
    private ImageView ivDot;
    private Button btnLink, btnOpenGate1, btnOpenGate2, btnSettings;
    private AppPrefs prefs;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            try {
                updateStatus(
                    intent.getStringExtra(GateMonitorService.EXTRA_STATUS),
                    intent.getStringExtra(GateMonitorService.EXTRA_EVENT));
            } catch (Exception ignored) {}
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            prefs = new AppPrefs(this);

            tvStatus    = findViewById(R.id.tv_status);
            tvLastEvent = findViewById(R.id.tv_last_event);
            tvLinkedAs  = findViewById(R.id.tv_linked_as);
            ivDot       = findViewById(R.id.iv_dot);
            btnLink     = findViewById(R.id.btn_link);
            btnOpenGate1= findViewById(R.id.btn_gate1);
            btnOpenGate2= findViewById(R.id.btn_gate2);
            btnSettings = findViewById(R.id.btn_settings);

            btnLink.setOnClickListener(v -> openLinking());
            btnOpenGate1.setOnClickListener(v -> manualOpen(prefs.getGate1Id(), "שער צפון"));
            btnOpenGate2.setOnClickListener(v -> manualOpen(prefs.getGate2Id(), "שער דרום"));
            btnSettings.setOnClickListener(v ->
                    startActivity(new Intent(this, SettingsActivity.class)));

            requestPerms();
            startMonitorService();

        } catch (Exception e) {
            showError("שגיאת אתחול: " + e.getMessage());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        try {
            registerReceiver(statusReceiver,
                    new IntentFilter(GateMonitorService.ACTION_STATUS));
            refreshUI();
        } catch (Exception ignored) {}
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_LINK && res == RESULT_OK) {
            refreshUI();
            showBtPicker();
        }
    }

    private void openLinking() {
        try {
            startActivityForResult(new Intent(this, LinkActivity.class), REQ_LINK);
        } catch (Exception e) { showError("שגיאה: " + e.getMessage()); }
    }

    private void manualOpen(String gateId, String gateName) {
        if (!prefs.isLinked()) {
            Toast.makeText(this, "יש לקשר תחילה את חשבון PalGate", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(this, GateMonitorService.class);
            i.setAction(GateMonitorService.ACTION_OPEN);
            i.putExtra(GateMonitorService.EXTRA_GATE_ID, gateId);
            startService(i);
            tvStatus.setText("פותח " + gateName + "...");
        } catch (Exception e) { showError("שגיאה: " + e.getMessage()); }
    }

    private void showBtPicker() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return;
            List<String> names = new ArrayList<>();
            for (BluetoothDevice d : adapter.getBondedDevices()) {
                try {
                    String name = d.getName();
                    if (name != null && !name.isEmpty()) names.add(name);
                } catch (SecurityException ignored) {}
            }
            if (names.isEmpty()) {
                Toast.makeText(this, "לא נמצאו מכשירי Bluetooth מזווגים", Toast.LENGTH_LONG).show();
                return;
            }
            String[] items = names.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("בחר את הרכב שלך (Bluetooth)")
                    .setItems(items, (dialog, which) -> {
                        prefs.set(AppPrefs.KEY_V1_BT_NAME, items[which]);
                        Toast.makeText(this, "נשמר: " + items[which], Toast.LENGTH_SHORT).show();
                        refreshUI();
                    })
                    .setNegativeButton("ביטול", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "שגיאת Bluetooth", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshUI() {
        try {
            boolean linked = prefs.isLinked();
            String phone = prefs.getPhone();
            String bt = prefs.getV1BtName();

            tvLinkedAs.setText(linked
                    ? "מחובר: " + phone + (bt.isEmpty() ? "" : " | " + bt)
                    : "לא מחובר");

            btnLink.setText(linked ? "חבר מחדש / שנה חשבון" : "קשר חשבון PalGate");
            btnOpenGate1.setEnabled(linked);
            btnOpenGate2.setEnabled(linked);

            if (linked && bt.isEmpty()) {
                tvStatus.setText("בחר רכב להפעלה אוטומטית ←");
                ivDot.setImageResource(R.drawable.dot_yellow);
            } else if (linked) {
                tvStatus.setText("ממתין לחיבור רכב...");
                ivDot.setImageResource(R.drawable.dot_green);
            } else {
                tvStatus.setText("יש לקשר חשבון PalGate");
                ivDot.setImageResource(R.drawable.dot_red);
            }
        } catch (Exception ignored) {}
    }

    private void updateStatus(String status, String event) {
        runOnUiThread(() -> {
            try {
                if (status != null) tvStatus.setText(status);
                if (event != null) tvLastEvent.setText("אחרון: " + event);
                if (status != null) {
                    boolean ok = !status.startsWith("שגיאה") && !status.startsWith("✗");
                    ivDot.setImageResource(ok ? R.drawable.dot_green : R.drawable.dot_red);
                }
            } catch (Exception ignored) {}
        });
    }

    private void startMonitorService() {
        try {
            Intent i = new Intent(this, GateMonitorService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(i);
            else
                startService(i);
        } catch (Exception e) {
            showError("שגיאה בהפעלת שירות: " + e.getMessage());
        }
    }

    private void requestPerms() {
        try {
            List<String> needed = new ArrayList<>();
            String[] perms = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.POST_NOTIFICATIONS
            };
            for (String p : perms)
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                    needed.add(p);
            if (!needed.isEmpty())
                ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), 100);

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 101);
        } catch (Exception ignored) {}
    }

    private void showError(String msg) {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("שגיאה")
                    .setMessage(msg)
                    .setPositiveButton("אישור", null)
                    .show();
        } catch (Exception ignored) {}
    }
}
