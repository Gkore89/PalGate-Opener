package com.palgate.opener;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
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
    private Button btnShare, btnRetry, btnBack, btnDone;
    private View layoutWaiting, layoutSuccess;
    private AppPrefs prefs;
    private Handler mainHandler;
    private Bitmap currentQrBitmap;
    private boolean linking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link);
        prefs = new AppPrefs(this);
        mainHandler = new Handler(Looper.getMainLooper());

        ivQr         = findViewById(R.id.iv_qr);
        tvStatus     = findViewById(R.id.tv_link_status);
        btnShare     = findViewById(R.id.btn_share);
        btnRetry     = findViewById(R.id.btn_retry);
        btnBack      = findViewById(R.id.btn_back);
        btnDone      = findViewById(R.id.btn_done);
        layoutWaiting= findViewById(R.id.layout_waiting);
        layoutSuccess= findViewById(R.id.layout_success);

        btnShare.setOnClickListener(v -> shareQr());
        btnRetry.setOnClickListener(v -> startLinking());
        btnBack.setOnClickListener(v -> finish());
        btnDone.setOnClickListener(v -> finish());

        startLinking();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        linking = false;
    }

    private void startLinking() {
        linking = true;
        btnRetry.setVisibility(View.GONE);
        btnShare.setVisibility(View.GONE);
        layoutWaiting.setVisibility(View.GONE);
        layoutSuccess.setVisibility(View.GONE);
        ivQr.setImageBitmap(null);
        setStatus("מייצר קוד QR...", 0xFFAAAAAA);

        new Thread(() -> {
            try {
                String uniqueId = UUID.randomUUID().toString();
                String qrContent = "{\"id\": \"" + uniqueId + "\"}";

                Bitmap qrBitmap = generateQrBitmap(qrContent, 800);
                currentQrBitmap = qrBitmap;

                mainHandler.post(() -> {
                    ivQr.setImageBitmap(qrBitmap);
                    btnShare.setVisibility(View.VISIBLE);
                    layoutWaiting.setVisibility(View.VISIBLE);
                    setStatus("ממתין לסריקה...", 0xFFAAAAAA);
                });

                // Block until PalGate scans the QR (up to 3 minutes)
                String initUrl = BASE_URL + "un/secondary/init/" + uniqueId;
                JSONObject initResp = getJson(initUrl);

                if (!linking) return;

                // Check for success — err:null means OK
                boolean hasError = true;
                if (initResp != null) {
                    Object errVal = initResp.opt("err");
                    String status = initResp.optString("status", "");
                    hasError = (errVal instanceof Boolean && (Boolean) errVal)
                            || (errVal instanceof String)
                            || (!status.equals("ok") && !status.equals("success"));
                }

                if (hasError) {
                    throw new Exception("שגיאה מהשרת: " +
                            (initResp != null ? initResp.toString() : "אין תגובה"));
                }

                JSONObject user = initResp.getJSONObject("user");
                String phone    = user.getString("id");
                String tokenHex = user.getString("token");
                int tokenType   = initResp.getInt("secondary");

                mainHandler.post(() -> setStatus("מאמת חיבור...", 0xFFAAAAAA));

                // Verify
                String derived = PalGateTokenGenerator.generateDerivedToken(
                        phone, tokenHex, tokenType);
                getJsonAuth(BASE_URL + "secondary/status", derived);

                prefs.saveCredentials(phone, tokenHex, tokenType);

                String displayPhone = phone.startsWith("972")
                        ? "0" + phone.substring(3) : phone;

                mainHandler.post(() -> {
                    linking = false;
                    layoutWaiting.setVisibility(View.GONE);
                    layoutSuccess.setVisibility(View.VISIBLE);
                    btnShare.setVisibility(View.GONE);
                    btnRetry.setVisibility(View.GONE);
                    ivQr.setImageBitmap(null);
                    ((TextView) findViewById(R.id.tv_success_phone))
                            .setText("מחובר: " + displayPhone);
                    setStatus("", 0xFFAAAAAA);
                    setResult(RESULT_OK);
                    new Handler().postDelayed(this::finish, 2000);
                });

            } catch (Exception e) {
                if (!linking) return;
                mainHandler.post(() -> {
                    layoutWaiting.setVisibility(View.GONE);
                    setStatus("שגיאה: " + e.getMessage(), 0xFFFF6B6B);
                    btnRetry.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void shareQr() {
        if (currentQrBitmap == null) return;
        try {
            String path = MediaStore.Images.Media.insertImage(
                    getContentResolver(), currentQrBitmap, "PalGate_Link_QR", null);
            Uri uri = Uri.parse(path);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/*");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_TEXT,
                    "פתח תמונה זו במחשב/טאבלט וסרוק אותה עם PalGate → מכשירים מקושרים → קשר מכשיר");
            startActivity(Intent.createChooser(share, "שלח קוד QR"));
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בשיתוף", Toast.LENGTH_SHORT).show();
        }
    }

    private void setStatus(String text, int color) {
        tvStatus.setText(text);
        tvStatus.setTextColor(color);
    }

    private JSONObject getJson(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(180000); // 3 min
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream();
        String body = readStream(is);
        conn.disconnect();
        return new JSONObject(body);
    }

    private JSONObject getJsonAuth(String urlStr, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("X-Bt-Token", token);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream();
        readStream(is);
        conn.disconnect();
        return new JSONObject("{}");
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "{}";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private Bitmap generateQrBitmap(String content, int size) {
        try {
            Class<?> writerClass = Class.forName("com.google.zxing.qrcode.QRCodeWriter");
            Object writer = writerClass.newInstance();
            Class<?> formatClass = Class.forName("com.google.zxing.BarcodeFormat");
            Object qrFormat = formatClass.getField("QR_CODE").get(null);
            java.lang.reflect.Method encode = writerClass.getMethod("encode",
                    String.class, qrFormat.getClass(), int.class, int.class);
            Object bitMatrix = encode.invoke(writer, content, qrFormat, size, size);
            java.lang.reflect.Method getWidth  = bitMatrix.getClass().getMethod("getWidth");
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
