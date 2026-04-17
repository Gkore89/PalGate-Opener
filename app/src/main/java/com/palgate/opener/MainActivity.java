package com.palgate.opener;

import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 96, 48, 48);
        layout.setBackgroundColor(0xFF0A0A0A);

        TextView title = new TextView(this);
        title.setText("PalGate Opener");
        title.setTextSize(24);
        title.setTextColor(0xFFFFFFFF);
        layout.addView(title);

        tvStatus = new TextView(this);
        tvStatus.setText("מפעיל שירות...");
        tvStatus.setTextSize(15);
        tvStatus.setTextColor(0xFF888888);
        tvStatus.setPadding(0, 32, 0, 32);
        layout.addView(tvStatus);

        Button btnStart = new Button(this);
        btnStart.setText("הפעל שירות");
        btnStart.setOnClickListener(v -> startMonitorService());
        layout.addView(btnStart);

        setContentView(layout);

        // Try starting service
        startMonitorService();
    }

    private void startMonitorService() {
        try {
            tvStatus.setText("מנסה להפעיל שירות...");
            Intent i = new Intent(this, GateMonitorService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
            tvStatus.setText("✓ השירות הופעל בהצלחה!\n\nAndroid: " + Build.VERSION.RELEASE
                    + "\nSDK: " + Build.VERSION.SDK_INT);
        } catch (Exception e) {
            tvStatus.setText("✗ שגיאה בהפעלת שירות:\n\n" + e.getClass().getSimpleName()
                    + "\n" + e.getMessage());
        }
    }
}
