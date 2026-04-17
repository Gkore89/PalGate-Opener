package com.palgate.opener;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Ultra-minimal version to diagnose crash.
 * No service, no BT, no location — just a screen.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Don't even use XML layout — build UI in code to eliminate any resource issues
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("PalGate Opener");
        title.setTextSize(24);
        layout.addView(title);

        TextView status = new TextView(this);
        status.setText("האפליקציה עובדת! ✓\n\nגרסת אבחון — אין שירות רקע");
        status.setTextSize(16);
        status.setPadding(0, 32, 0, 0);
        layout.addView(status);

        Button btnTest = new Button(this);
        btnTest.setText("בדוק חיבור PalGate");
        btnTest.setOnClickListener(v -> {
            status.setText("לוחץ... ✓\nהאפליקציה מגיבה כראוי");
        });
        layout.addView(btnTest);

        setContentView(layout);
    }
}
