package com.palgate.opener;

import android.os.Bundle;
import android.os.Handler;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
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

        if (prefs.isLinked()) {
            etPhone.setText(prefs.getPhone());
            etToken.setText(prefs.getToken());
            etTokenType.setText(String.valueOf(prefs.getTokenType()));
            tvStatus.setText("מחובר כרגע — ניתן לעדכן");
            tvStatus.setTextColor(0xFF00C896);
        }

        findViewById(R.id.btn_save_link).setOnClickListener(v -> save());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_how_to_get).setOnClickListener(v -> showHowTo());
    }

    private void save() {
        String phone   = etPhone.getText().toString().trim();
        String token   = etToken.getText().toString().trim();
        String typeStr = etTokenType.getText().toString().trim();

        if (phone.isEmpty() || token.isEmpty()) {
            tvStatus.setText("יש למלא מספר טלפון וטוקן");
            tvStatus.setTextColor(0xFFFF6B6B);
            return;
        }

        if (token.length() != 32) {
            tvStatus.setText("הטוקן חייב להיות 32 תווים (כרגע: " + token.length() + ")");
            tvStatus.setTextColor(0xFFFF6B6B);
            return;
        }

        int tokenType = 1;
        try { tokenType = Integer.parseInt(typeStr); } catch (Exception ignored) {}

        prefs.saveCredentials(phone, token, tokenType);
        tvStatus.setText("החיבור נשמר בהצלחה!");
        tvStatus.setTextColor(0xFF00C896);

        setResult(RESULT_OK);
        new Handler().postDelayed(this::finish, 1200);
    }

    private void showHowTo() {
        try {
            new AlertDialog.Builder(this)
                .setTitle("איך מקבלים טוקן?")
                .setMessage(
                    "שלב 1: התקן Python\n" +
                    "הורד מ-python.org ובחר 'Add to PATH'\n\n" +
                    "שלב 2: הורד את הסקריפט\n" +
                    "github.com/DonutByte/pylgate\n" +
                    "examples/generate_linked_device_session_token.py\n\n" +
                    "שלב 3: התקן תלויות\n" +
                    "pip install qrcode requests\n\n" +
                    "שלב 4: הרץ את הסקריפט\n" +
                    "python generate_linked_device_session_token.py\n\n" +
                    "שלב 5: סרוק QR באפליקציית PalGate\n" +
                    "תפריט -> מכשירים מקושרים -> קשר מכשיר\n\n" +
                    "שלב 6: העתק את הטוקן שמודפס למסך\n" +
                    "והכנס אותו כאן יחד עם מספר הטלפון"
                )
                .setPositiveButton("הבנתי", null)
                .show();
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
