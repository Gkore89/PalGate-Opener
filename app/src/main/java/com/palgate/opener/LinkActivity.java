package com.palgate.opener;

import android.content.*;
import android.os.*;
import android.telephony.SmsMessage;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * SMS-based PalGate login — single device, no QR needed.
 *
 * Flow:
 * 1. User enters phone number
 * 2. App calls PalGate's SMS login endpoint → PalGate sends 5-digit SMS
 * 3. Android SMS BroadcastReceiver auto-reads the code
 * 4. App calls verify endpoint → gets session token
 * 5. Credentials saved, user is linked
 */
public class LinkActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://api1.pal-es.com/v1/bt/";
    private static final String USER_AGENT = "okhttp/4.9.3";

    // Views - step 1: phone
    private View layoutPhone;
    private EditText etPhone;
    private Button btnSendSms;

    // Views - step 2: OTP
    private View layoutOtp;
    private TextView tvOtpInstruction;
    private EditText etOtp;
    private Button btnVerify, btnResend;

    // Views - common
    private TextView tvStatus;
    private Button btnBack;
    private ProgressBar progressBar;

    private AppPrefs prefs;
    private Handler mainHandler;
    private String normalizedPhone; // international format e.g. 972524886478
    private boolean waitingForSms = false;

    // SMS auto-read receiver
    private final BroadcastReceiver smsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (!waitingForSms) return;
            try {
                Object[] pdus = (Object[]) intent.getExtras().get("pdus");
                if (pdus == null) return;
                for (Object pdu : pdus) {
                    SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu);
                    String body = msg.getMessageBody();
                    if (body != null && body.contains("PalGate")) {
                        // Extract 5-digit code
                        String code = extractCode(body);
                        if (code != null) {
                            mainHandler.post(() -> {
                                etOtp.setText(code);
                                setStatus("קוד SMS התקבל אוטומטית!", 0xFF00C896);
                                // Auto-verify
                                verifyCode(code);
                            });
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore SMS parse errors
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_link);
        prefs = new AppPrefs(this);
        mainHandler = new Handler(Looper.getMainLooper());

        layoutPhone = findViewById(R.id.layout_phone);
        layoutOtp   = findViewById(R.id.layout_otp);
        etPhone     = findViewById(R.id.et_phone);
        btnSendSms  = findViewById(R.id.btn_send_sms);
        tvOtpInstruction = findViewById(R.id.tv_otp_instruction);
        etOtp       = findViewById(R.id.et_otp);
        btnVerify   = findViewById(R.id.btn_verify);
        btnResend   = findViewById(R.id.btn_resend);
        tvStatus    = findViewById(R.id.tv_link_status);
        btnBack     = findViewById(R.id.btn_back);
        progressBar = findViewById(R.id.progress_bar);

        btnSendSms.setOnClickListener(v -> sendSms());
        btnVerify.setOnClickListener(v -> verifyCode(etOtp.getText().toString().trim()));
        btnResend.setOnClickListener(v -> { showStep(1); sendSms(); });
        btnBack.setOnClickListener(v -> finish());

        // Show step 1 initially
        showStep(1);

        // Pre-fill if already linked
        if (prefs.isLinked()) {
            String phone = prefs.getPhone();
            if (phone.startsWith("972"))
                phone = "0" + phone.substring(3);
            etPhone.setText(phone);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        // Register SMS receiver
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        filter.setPriority(999);
        registerReceiver(smsReceiver, filter);
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(smsReceiver); } catch (Exception ignored) {}
        waitingForSms = false;
    }

    // ── Step 1: Send SMS ──────────────────────────────────────────

    private void sendSms() {
        String phone = etPhone.getText().toString().trim();
        if (phone.isEmpty()) {
            setStatus("יש להכניס מספר טלפון", 0xFFFF6B6B);
            return;
        }

        // Normalize to international format
        if (phone.startsWith("0")) {
            normalizedPhone = "972" + phone.substring(1);
        } else if (phone.startsWith("972")) {
            normalizedPhone = phone;
        } else {
            normalizedPhone = "972" + phone;
        }

        showLoading(true);
        setStatus("שולח SMS לנייד שלך...", 0xFFAAAAAA);

        new Thread(() -> {
            try {
                // Try PalGate's SMS login endpoint
                // Based on reverse engineering: POST to /un/user/login with phone number
                JSONObject body = new JSONObject();
                body.put("id", normalizedPhone);

                JSONObject resp = postJson(BASE_URL + "un/user/login", body, null);

                mainHandler.post(() -> {
                    showLoading(false);
                    if (resp != null && !resp.optBoolean("err", true)
                            && "ok".equals(resp.optString("status"))) {
                        // SMS sent successfully
                        waitingForSms = true;
                        showStep(2);
                        tvOtpInstruction.setText(
                            "קוד 5 ספרות נשלח ל-" + formatPhone(normalizedPhone) +
                            "\nהקוד יתמלא אוטומטית");
                        setStatus("ממתין לקוד SMS...", 0xFFAAAAAA);
                    } else {
                        // Endpoint not found or failed — try alternative endpoint
                        tryAlternativeSmsEndpoint();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    tryAlternativeSmsEndpoint();
                });
            }
        }).start();
    }

    private void tryAlternativeSmsEndpoint() {
        showLoading(true);
        setStatus("מנסה שיטה חלופית...", 0xFFAAAAAA);

        new Thread(() -> {
            try {
                // Alternative endpoint patterns based on PalGate API structure
                String[] endpoints = {
                    "un/user/send-sms",
                    "un/user/verify-phone",
                    "un/user/register",
                    "un/user/otp"
                };

                for (String endpoint : endpoints) {
                    JSONObject body = new JSONObject();
                    body.put("id", normalizedPhone);
                    body.put("phone", normalizedPhone);

                    JSONObject resp = postJson(BASE_URL + endpoint, body, null);
                    if (resp != null && !resp.optBoolean("err", true)
                            && "ok".equals(resp.optString("status"))) {
                        mainHandler.post(() -> {
                            showLoading(false);
                            waitingForSms = true;
                            showStep(2);
                            tvOtpInstruction.setText(
                                "קוד 5 ספרות נשלח ל-" + formatPhone(normalizedPhone));
                            setStatus("ממתין לקוד SMS...", 0xFFAAAAAA);
                        });
                        return;
                    }
                }

                // All endpoints failed — SMS API not found
                // Fall back to QR linking as backup
                mainHandler.post(() -> {
                    showLoading(false);
                    setStatus("לא ניתן לשלוח SMS אוטומטית.\n" +
                              "נסה שיטת QR במקום.", 0xFFFF6B6B);
                    btnResend.setVisibility(View.GONE);
                    // Show manual OTP entry anyway in case user gets SMS from another method
                    showStep(2);
                    tvOtpInstruction.setText("הכנס את הקוד שקיבלת ב-SMS");
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    setStatus("שגיאה: " + e.getMessage(), 0xFFFF6B6B);
                });
            }
        }).start();
    }

    // ── Step 2: Verify OTP ────────────────────────────────────────

    private void verifyCode(String code) {
        if (code == null || code.length() != 5) {
            setStatus("יש להכניס קוד בן 5 ספרות", 0xFFFF6B6B);
            return;
        }

        waitingForSms = false;
        showLoading(true);
        setStatus("מאמת קוד...", 0xFFAAAAAA);

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("id", normalizedPhone);
                body.put("code", code);
                body.put("otp", code);

                // Try verify endpoint
                String[] verifyEndpoints = {
                    "un/user/verify",
                    "un/user/login-verify",
                    "un/user/confirm"
                };

                JSONObject resp = null;
                for (String endpoint : verifyEndpoints) {
                    resp = postJson(BASE_URL + endpoint, body, null);
                    if (resp != null && !resp.optBoolean("err", true)
                            && "ok".equals(resp.optString("status"))) {
                        break;
                    }
                }

                final JSONObject finalResp = resp;
                mainHandler.post(() -> {
                    showLoading(false);
                    if (finalResp != null && !finalResp.optBoolean("err", true)
                            && "ok".equals(finalResp.optString("status"))) {

                        // Extract credentials from response
                        try {
                            JSONObject user = finalResp.optJSONObject("user");
                            String phone = user != null
                                    ? user.optString("id", normalizedPhone)
                                    : normalizedPhone;
                            String token = user != null
                                    ? user.optString("token", "")
                                    : finalResp.optString("token", "");
                            int tokenType = finalResp.optInt("tokenType", 0);

                            if (!token.isEmpty()) {
                                prefs.saveCredentials(phone, token, tokenType);
                                setStatus("החיבור הצליח!\n" + formatPhone(phone), 0xFF00C896);
                                setResult(RESULT_OK);
                                new Handler().postDelayed(this::finish, 1500);
                            } else {
                                setStatus("הקוד אומת אך לא התקבל טוקן.\nנסה שוב.", 0xFFFF6B6B);
                            }
                        } catch (Exception e) {
                            setStatus("שגיאה בעיבוד התגובה: " + e.getMessage(), 0xFFFF6B6B);
                        }
                    } else {
                        setStatus("קוד שגוי. נסה שוב.", 0xFFFF6B6B);
                        etOtp.setText("");
                        waitingForSms = true;
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    setStatus("שגיאה: " + e.getMessage(), 0xFFFF6B6B);
                });
            }
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void showStep(int step) {
        layoutPhone.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        layoutOtp.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSendSms.setEnabled(!show);
        btnVerify.setEnabled(!show);
    }

    private void setStatus(String text, int color) {
        tvStatus.setText(text);
        tvStatus.setTextColor(color);
    }

    private String formatPhone(String phone) {
        return phone.startsWith("972") ? "0" + phone.substring(3) : phone;
    }

    private String extractCode(String smsBody) {
        // Extract 5-digit code from SMS body
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\\b(\\d{5})\\b").matcher(smsBody);
        return m.find() ? m.group(1) : null;
    }

    // ── HTTP helpers ──────────────────────────────────────────────

    private JSONObject postJson(String urlStr, JSONObject body, String token) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Content-Type", "application/json");
            if (token != null) conn.setRequestProperty("X-Bt-Token", token);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            byte[] bodyBytes = body.toString().getBytes("UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300
                    ? conn.getInputStream() : conn.getErrorStream();
            String respBody = readStream(is);
            conn.disconnect();
            return new JSONObject(respBody);
        } catch (Exception e) {
            return null;
        }
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "{}";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
