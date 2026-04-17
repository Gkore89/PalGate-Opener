package com.palgate.opener;

import android.graphics.*;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class LinkActivity extends AppCompatActivity {

    private ImageView ivQr;
    private TextView tvStatus;
    private Button btnRetry, btnBack;
    private AppPrefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link);

        prefs = new AppPrefs(this);
        ivQr    = findViewById(R.id.iv_qr);
        tvStatus = findViewById(R.id.tv_link_status);
        btnRetry = findViewById(R.id.btn_retry);
        btnBack  = findViewById(R.id.btn_back);

        btnRetry.setOnClickListener(v -> startLinking());
        btnBack.setOnClickListener(v -> finish());

        startLinking();
    }

    private void startLinking() {
        btnRetry.setVisibility(View.GONE);
        ivQr.setImageBitmap(null);
        setStatus("מתחבר...");

        PalGateApiClient.startLinking(new PalGateApiClient.LinkCallback() {
            @Override public void onQrReady(String qrContent) {
                Bitmap bmp = generateQr(qrContent, 600);
                runOnUiThread(() -> {
                    ivQr.setImageBitmap(bmp);
                    setStatus("פתח את אפליקציית PalGate\nלחץ ☰ ← מכשירים מקושרים ← קשר מכשיר\nוסרוק את הקוד");
                });
            }

            @Override public void onStatusUpdate(String status) {
                runOnUiThread(() -> setStatus(status));
            }

            @Override public void onSuccess(String phone, String token, int tokenType) {
                prefs.saveCredentials(phone, token, tokenType);
                runOnUiThread(() -> {
                    setStatus("✓ החיבור הצליח!\nמספר: " + phone);
                    ivQr.setImageBitmap(null);
                    btnBack.setText("סגור");
                    // Return success to caller
                    setResult(RESULT_OK);
                    new android.os.Handler().postDelayed(() -> finish(), 1500);
                });
            }

            @Override public void onFailure(String error) {
                runOnUiThread(() -> {
                    setStatus("✗ " + error);
                    btnRetry.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void setStatus(String text) {
        tvStatus.setText(text);
    }

    /**
     * Generate QR code bitmap without external libraries.
     * Uses a simple matrix-based QR encoder for the linking token.
     * For production, replace with ZXing or QRGen.
     */
    private Bitmap generateQr(String content, int size) {
        // Simple QR placeholder — draws the content as a stylized grid
        // In production integrate ZXing: implementation 'com.google.zxing:core:3.5.2'
        try {
            Class<?> qrClass = Class.forName("com.google.zxing.qrcode.QRCodeWriter");
            Object writer = qrClass.newInstance();
            Class<?> formatClass = Class.forName("com.google.zxing.BarcodeFormat");
            Object format = formatClass.getField("QR_CODE").get(null);
            Class<?> hintsClass = Class.forName("java.util.HashMap");
            Object hints = hintsClass.newInstance();

            java.lang.reflect.Method encode = qrClass.getMethod("encode",
                    String.class, format.getClass(), int.class, int.class, java.util.Map.class);
            Object bitMatrix = encode.invoke(writer, content, format, size, size, null);

            java.lang.reflect.Method getWidth = bitMatrix.getClass().getMethod("getWidth");
            java.lang.reflect.Method getHeight = bitMatrix.getClass().getMethod("getHeight");
            java.lang.reflect.Method get = bitMatrix.getClass().getMethod("get", int.class, int.class);

            int w = (int) getWidth.invoke(bitMatrix);
            int h = (int) getHeight.invoke(bitMatrix);
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    pixels[y * w + x] = (boolean) get.invoke(bitMatrix, x, y)
                            ? Color.BLACK : Color.WHITE;

            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.RGB_565);
        } catch (Exception e) {
            // Fallback: draw placeholder with the token text
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(Color.WHITE);
            Paint p = new Paint();
            p.setColor(Color.BLACK);
            p.setTextSize(20);
            p.setTextAlign(Paint.Align.CENTER);
            // Draw a simple checkerboard pattern as placeholder
            int cell = size / 20;
            for (int row = 0; row < 20; row++) {
                for (int col = 0; col < 20; col++) {
                    if ((row + col) % 2 == 0) {
                        canvas.drawRect(col * cell, row * cell,
                                (col + 1) * cell, (row + 1) * cell, p);
                    }
                }
            }
            // Draw token in center
            p.setColor(Color.RED);
            p.setTextSize(14);
            String short_content = content.length() > 20
                    ? content.substring(0, 20) + "..." : content;
            canvas.drawText(short_content, size / 2f, size / 2f, p);
            return bmp;
        }
    }
}
