package com.palgate.opener;

import android.graphics.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class LinkActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://api1.pal-es.com/v1/bt/";
    private static final String USER_AGENT = "okhttp/4.9.3";

    private ImageView ivQr;
    private TextView tvStatus;
    private Button btnRetry, btnBack;
    private AppPrefs prefs;
    private boolean linking = false;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link);
        prefs = new AppPrefs(this);
        mainHandler = new Handler(Looper.getMainLooper());

        ivQr     = findViewById(R.id.iv_qr);
        tvStatus = findViewById(R.id.tv_link_status);
        btnRetry = findViewById(R.id.btn_retry);
        btnBack  = findViewById(R.id.btn_back);

        btnRetry.setOnClickListener(v -> startLinking());
        btnBack.setOnClickListener(v -> finish());

        startLinking();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        linking = false;
    }

    private void startLinking() {
        linking = true;
        btnRetry.setVisibility(View.GONE);
        ivQr.setImageBitmap(null);
        setStatus("מתחבר לשרת...", 0xFFAAAAAA);

        new Thread(() -> {
            try {
                // Step 1: Generate UUID
                String uniqueId = UUID.randomUUID().toString();

                // Step 2: Build QR content (exact format from pylgate)
                String qrContent = "{\"id\": \"" + uniqueId + "\"}";

                // Step 3: Show QR code
                Bitmap qrBitmap = generateQrBitmap(qrContent, 600);
                mainHandler.post(() -> {
                    ivQr.setImageBitmap(qrBitmap);
                    setStatus("סרוק את הקוד באפליקציית PalGate\n\n" +
                              "תפריט ☰ → מכשירים מקושרים → קשר מכשיר", 0xFFCCCCCC);
                });

                // Step 4: GET /un/secondary/init/<uuid> — blocks until scan
                // This endpoint returns immediately with the credentials once scanned
                mainHandler.post(() -> setStatus(
                    "ממתין לסריקה...\n\nסרוק את הקוד באפליקציית PalGate", 0xFFCCCCCC));

                String initUrl = BASE_URL + "un/secondary/init/" + uniqueId;
                JSONObject initResp = getJson(initUrl);

                if (!linking) return;

                if (initResp == null || initResp.optBoolean("err", true)) {
                    throw new Exception("תגובה שגויה מהשרת: " + (initResp != null ? initResp.toString() : "null"));
                }

                // Parse response
                JSONObject user = initResp.getJSONObject("user");
                String phone = user.getString("id");
                String tokenHex = user.getString("token");
                int tokenType = initResp.getInt("secondary");

                mainHandler.post(() -> setStatus("מאמת את החיבור...", 0xFFAAAAAA));

                // Step 5: Check status to confirm
                String derivedToken = PalGateTokenGenerator.generateDerivedToken(
                        phone, tokenHex, tokenType);
                getJsonAuth(BASE_URL + "secondary/status", derivedToken);

                // Step 6: Save credentials
                prefs.saveCredentials(phone, tokenHex, tokenType);

                mainHandler.post(() -> {
                    ivQr.setImageBitmap(null);
                    setStatus("החיבור הצליח!\nמספר: " + phone, 0xFF00C896);
                    btnRetry.setVisibility(View.GONE);
                    setResult(RESULT_OK);
                    new Handler().postDelayed(this::finish, 1500);
                });

            } catch (Exception e) {
                if (!linking) return;
                mainHandler.post(() -> {
                    setStatus("שגיאה: " + e.getMessage(), 0xFFFF6B6B);
                    btnRetry.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void setStatus(String text, int color) {
        tvStatus.setText(text);
        tvStatus.setTextColor(color);
    }

    // ── HTTP helpers ──────────────────────────────────────────────

    private JSONObject getJson(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000); // long timeout — waits for QR scan
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream();
        String body = readStream(is);
        conn.disconnect();
        return new JSONObject(body);
    }

    private JSONObject getJsonAuth(String urlStr, String derivedToken) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("X-Bt-Token", derivedToken);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream();
        String body = readStream(is);
        conn.disconnect();
        return new JSONObject(body);
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "{}";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    // ── QR code generator using ZXing ────────────────────────────

    private Bitmap generateQrBitmap(String content, int size) {
        try {
            // Use ZXing via reflection (included in dependencies)
            Class<?> writerClass = Class.forName("com.google.zxing.qrcode.QRCodeWriter");
            Object writer = writerClass.newInstance();

            Class<?> formatClass = Class.forName("com.google.zxing.BarcodeFormat");
            Object qrFormat = formatClass.getField("QR_CODE").get(null);

            java.lang.reflect.Method encode = writerClass.getMethod("encode",
                    String.class, qrFormat.getClass(), int.class, int.class);
            Object bitMatrix = encode.invoke(writer, content, qrFormat, size, size);

            java.lang.reflect.Method getWidth  = bitMatrix.getClass().getMethod("getWidth");
            java.lang.reflect.Method getHeight = bitMatrix.getClass().getMethod("getHeight");
            java.lang.reflect.Method get       = bitMatrix.getClass().getMethod("get", int.class, int.class);

            int w = (int) getWidth.invoke(bitMatrix);
            int h = (int) getHeight.invoke(bitMatrix);
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    pixels[y * w + x] = (boolean) get.invoke(bitMatrix, x, y)
                            ? Color.BLACK : Color.WHITE;

            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.RGB_565);

        } catch (Exception e) {
            // Fallback: simple placeholder
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(Color.WHITE);
            Paint p = new Paint();
            p.setColor(Color.BLACK);
            int cell = size / 25;
            for (int r = 0; r < 25; r++)
                for (int c = 0; c < 25; c++)
                    if ((r + c + r * c) % 3 == 0)
                        canvas.drawRect(c*cell, r*cell, (c+1)*cell, (r+1)*cell, p);
            return bmp;
        }
    }
}
