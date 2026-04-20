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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.UUID;

public class LinkActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://api1.pal-es.com/v1/bt/";
    private static final String USER_AGENT = "okhttp/4.9.3";

    // Tab buttons
    private Button btnTabQr, btnTabManual;
    private View layoutQr, layoutManual;

    // QR tab
    private ImageView ivQr;
    private TextView tvStatus;
    private Button btnShare, btnRetry, btnBack;
    private View layoutWaiting, layoutSuccess;
    private TextView tvSuccessPhone;
    private Bitmap currentQrBitmap;
    private boolean linking = false;

    // Manual tab
    private EditText etManualPhone, etManualToken, etManualTokenType;
    private Button btnManualSave;
    private TextView tvManualStatus;

    private AppPrefs prefs;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link);
        prefs = new AppPrefs(this);
        mainHandler = new Handler(Looper.getMainLooper());

        btnTabQr     = findViewById(R.id.btn_tab_qr);
        btnTabManual = findViewById(R.id.btn_tab_manual);
        layoutQr     = findViewById(R.id.layout_qr_tab);
        layoutManual = findViewById(R.id.layout_manual_tab);

        ivQr          = findViewById(R.id.iv_qr);
        tvStatus      = findViewById(R.id.tv_link_status);
        btnShare      = findViewById(R.id.btn_share);
        btnRetry      = findViewById(R.id.btn_retry);
        btnBack       = findViewById(R.id.btn_back);
        layoutWaiting = findViewById(R.id.layout_waiting);
        layoutSuccess = findViewById(R.id.layout_success);
        tvSuccessPhone= findViewById(R.id.tv_success_phone);

        etManualPhone    = findViewById(R.id.et_manual_phone);
        etManualToken    = findViewById(R.id.et_manual_token);
        etManualTokenType= findViewById(R.id.et_manual_token_type);
        btnManualSave    = findViewById(R.id.btn_manual_save);
        tvManualStatus   = findViewById(R.id.tv_manual_status);

        btnTabQr.setOnClickListener(v -> showTab(true));
        btnTabManual.setOnClickListener(v -> showTab(false));
        btnShare.setOnClickListener(v -> shareQr());
        btnRetry.setOnClickListener(v -> startLinking());
        btnBack.setOnClickListener(v -> finish());
        findViewById(R.id.btn_done).setOnClickListener(v -> finish());
        btnManualSave.setOnClickListener(v -> saveManual());

        // Pre-fill manual fields if already linked
        if (prefs.isLinked()) {
            String phone = prefs.getPhone();
            if (phone.startsWith("972")) phone = "0" + phone.substring(3);
            etManualPhone.setText(phone);
            etManualToken.setText(prefs.getToken());
            etManualTokenType.setText(String.valueOf(prefs.getTokenType()));
        }

        showTab(true);
        startLinking();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        linking = false;
    }

    private void showTab(boolean qr) {
        layoutQr.setVisibility(qr ? View.VISIBLE : View.GONE);
        layoutManual.setVisibility(qr ? View.GONE : View.VISIBLE);
        btnTabQr.setAlpha(qr ? 1f : 0.5f);
        btnTabManual.setAlpha(qr ? 0.5f : 1f);
        if (!qr) linking = false;
        else if (!linking) startLinking();
    }

    // ── QR linking ────────────────────────────────────────────────

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

                JSONObject initResp = getJson(BASE_URL + "un/secondary/init/" + uniqueId);
                if (!linking) return;

                boolean hasError = true;
                if (initResp != null) {
                    Object errVal = initResp.opt("err");
                    String status = initResp.optString("status", "");
                    hasError = (errVal instanceof Boolean && (Boolean) errVal)
                            || (errVal instanceof String)
                            || (!status.equals("ok") && !status.equals("success"));
                }
                if (hasError) throw new Exception("שגיאה: " + (initResp != null ? initResp : "null"));

                JSONObject user = initResp.getJSONObject("user");
                String phone    = user.getString("id");
                String tokenHex = user.getString("token");
                int tokenType   = initResp.getInt("secondary");

                mainHandler.post(() -> setStatus("מאמת חיבור...", 0xFFAAAAAA));

                String derived = PalGateTokenGenerator.generateDerivedToken(phone, tokenHex, tokenType);
                getJsonAuth(BASE_URL + "secondary/status", derived);

                prefs.saveCredentials(phone, tokenHex, tokenType);
                String display = phone.startsWith("972") ? "0" + phone.substring(3) : phone;

                mainHandler.post(() -> {
                    linking = false;
                    layoutWaiting.setVisibility(View.GONE);
                    layoutSuccess.setVisibility(View.VISIBLE);
                    btnShare.setVisibility(View.GONE);
                    btnRetry.setVisibility(View.GONE);
                    ivQr.setImageBitmap(null);
                    tvSuccessPhone.setText("מחובר: " + display + "\nסוג טוקן: " + tokenType);
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

    // ── Manual entry ──────────────────────────────────────────────

    private void saveManual() {
        String phone = etManualPhone.getText().toString().trim();
        String token = etManualToken.getText().toString().trim();
        String typeStr = etManualTokenType.getText().toString().trim();

        if (phone.isEmpty() || token.isEmpty()) {
            tvManualStatus.setText("יש למלא טלפון וטוקן");
            tvManualStatus.setTextColor(0xFFFF6B6B);
            return;
        }

        // Normalize phone
        if (phone.startsWith("0")) phone = "972" + phone.substring(1);
        else if (!phone.startsWith("972")) phone = "972" + phone;

        int tokenType = 1;
        try { tokenType = Integer.parseInt(typeStr); } catch (Exception ignored) {}

        final String finalPhone = phone;
        final int finalType = tokenType;
        final String finalToken = token;

        tvManualStatus.setText("מאמת...");
        tvManualStatus.setTextColor(0xFFAAAAAA);
        btnManualSave.setEnabled(false);

        new Thread(() -> {
            try {
                String derived = PalGateTokenGenerator.generateDerivedToken(finalPhone, finalToken, finalType);
                long ts = System.currentTimeMillis() / 1000L;
                String checkUrl = BASE_URL + "user/check-token?ts=" + ts + "&ts_diff=0";

                okhttp3.OkHttpClient okClient = new okhttp3.OkHttpClient();
                okhttp3.Request req = new okhttp3.Request.Builder()
                        .url(checkUrl)
                        .get()
                        .header("User-Agent", USER_AGENT)
                        .header("x-bt-token", derived)
                        .build();
                int code;
                String body = "";
                try (okhttp3.Response resp = okClient.newCall(req).execute()) {
                    code = resp.code();
                    if (resp.body() != null) body = resp.body().string();
                }

                // Debug info
                final String debug =
                    "Phone: " + finalPhone + "\n" +
                    "Token len: " + finalToken.length() + "\n" +
                    "Type: " + finalType + "\n" +
                    "Derived: " + derived.substring(0, 8) + "...\n" +
                    "HTTP: " + code + "\n" +
                    "Body: " + body;

                final int respCode = code;
                mainHandler.post(() -> {
                    btnManualSave.setEnabled(true);
                    if (respCode >= 200 && respCode < 300) {
                        prefs.saveCredentials(finalPhone, finalToken, finalType);
                        tvManualStatus.setText("✓ טוקן תקין! שמור.\n" + debug);
                        tvManualStatus.setTextColor(0xFF00C896);
                        setResult(RESULT_OK);
                        new Handler().postDelayed(this::finish, 3000);
                    } else {
                        tvManualStatus.setText("✗ נכשל\n" + debug);
                        tvManualStatus.setTextColor(0xFFFF6B6B);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    btnManualSave.setEnabled(true);
                    tvManualStatus.setText("שגיאה: " + e.getMessage());
                    tvManualStatus.setTextColor(0xFFFF6B6B);
                });
            }
        }).start();
    }

    private void shareQr() {
        if (currentQrBitmap == null) return;
        try {
            String path = MediaStore.Images.Media.insertImage(
                    getContentResolver(), currentQrBitmap, "PalGate_Link_QR", null);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/*");
            share.putExtra(Intent.EXTRA_STREAM, Uri.parse(path));
            share.putExtra(Intent.EXTRA_TEXT,
                    "פתח תמונה זו במחשב/טאבלט וסרוק עם PalGate ← מכשירים מקושרים ← קשר מכשיר");
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
        conn.setReadTimeout(180000);
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = readStream(is);
        conn.disconnect();
        return new JSONObject(body);
    }

    private void getJsonAuth(String urlStr, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("x-bt-token", token);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.getResponseCode();
        conn.disconnect();
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
            new Canvas(bmp).drawColor(Color.WHITE);
            return bmp;
        }
    }
}
