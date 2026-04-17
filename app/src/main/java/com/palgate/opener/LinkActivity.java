package com.palgate.opener;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class LinkActivity extends AppCompatActivity {

    private EditText etPhone, etToken, etTokenType;
    private TextView tvStatus;
    private AppPrefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link);
        prefs = new AppPrefs(this);

        etPhone     = findViewById(R.id.et_phone);
        etToken     = findViewById(R.id.et_token);
        etTokenType = findViewById(R.id.et_token_type);
        tvStatus    = findViewById(R.id.tv_link_status);

        // Pre-fill if already linked
        if (prefs.isLinked()) {
            etPhone.setText(prefs.getPhone());
            etToken.setText(prefs.getToken());
            etTokenType.setText(String.valueOf(prefs.getTokenType()));
        }

        findViewById(R.id.btn_save_link).setOnClickListener(v -> save());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_how_to_get).setOnClickListener(v -> showHowTo());
    }

    private void save() {
        String phone = etPhone.getText().toString().trim();
        String token = etToken.getText().toString().trim();
        String typeStr = etTokenType.getText().toString().trim();

        if (phone.isEmpty() || token.isEmpty()) {
            tvStatus.setText("⚠ יש למלא מספר טלפון וטוקן");
            tvStatus.setTextColor(0xFFFF6B6B);
            return;
        }

        if (token.length() != 32) {
            tvStatus.setText("⚠ הטוקן חייב להיות באורך 32 תווים (כרגע: " + token.length() + ")");
            tvStatus.setTextColor(0xFFFF6B6B);
            return;
        }

        int tokenType = 1;
        try { tokenType = Integer.parseInt(typeStr); } catch (Exception ignored) {}

        prefs.saveCredentials(phone, token, tokenType);
        tvStatus.setText("✓ החיבור נשמר בהצלחה!");
        tvStatus.setTextColor(0xFF00C896);

        setResult(RESULT_OK);
        new android.os.Handler().postDelayed(this::finish, 1000);
    }

    private void showHowTo() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("איך מקבלים טוקן?")
                .setMessage(
                    "1. התקן Python על המחשב (python.org)\n\n" +
                    "2. הורד את הסקריפט:\n" +
                    "github.com/DonutByte/pylgate\n" +
                    "→ examples → generate_linked_device_session_token.py\n\n" +
                    "3. התקן דרישות:\n" +
                    "pip install qrcode requests\n\n" +
                    "4. הרץ את הסקריפט:\n" +
                    "python generate_linked_device_session_token.py\n\n" +
                    "5. סרוק את קוד ה-QR באפליקציית PalGate:\n" +
                    "☰ → מכשירים מקושרים → קשר מכשיר\n\n" +
                    "6. הסקריפט ידפיס את הטוקן — הכנס אותו כאן"
                )
                .setPositiveButton("הבנתי", null)
                .show();
    }
}
