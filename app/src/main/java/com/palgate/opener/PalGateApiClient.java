package com.palgate.opener;

import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class PalGateApiClient {

    private static final String TAG = "PalGateApi";
    private static final String OPEN_URL = "https://api1.pal-es.com/v1/bt/user/open-door/";
    private static final String LINK_INIT_URL = "https://api1.pal-es.com/v1/bt/user/link-device/qr";
    private static final String LINK_STATUS_URL = "https://api1.pal-es.com/v1/bt/user/link-device/status";

    public interface OpenCallback {
        void onSuccess(String gateId);
        void onFailure(String gateId, String error);
    }

    public interface LinkCallback {
        void onQrReady(String qrContent);
        void onStatusUpdate(String status);
        void onSuccess(String phone, String sessionToken, int tokenType);
        void onFailure(String error);
    }

    // ── Open gate ────────────────────────────────────────────────

    public static void openGate(String gateId, String phone, String token,
                                int tokenType, OpenCallback cb) {
        new Thread(() -> {
            try {
                String derived = PalGateTokenGenerator.generateDerivedToken(phone, token, tokenType);
                HttpURLConnection conn = openConn(OPEN_URL + gateId, "POST", derived);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write("{}".getBytes());
                }
                int code = conn.getResponseCode();
                String errBody = "";
                    if (code < 200 || code >= 300) {
                InputStream errStream = conn.getErrorStream();
                    if (errStream != null) errBody = readStream(errStream);
                }
                conn.disconnect();
                    if (code >= 200 && code < 300) cb.onSuccess(gateId);
                    else cb.onFailure(gateId, "HTTP " + code + ": " + errBody);
                }).start();
    }

    // ── PalGate device linking via QR ────────────────────────────
    private static String readStream(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }
   
    public static void startLinking(LinkCallback cb) {
        new Thread(() -> {
            try {
                // 1. Generate session UUID
                String sessionId = UUID.randomUUID().toString().replace("-", "");

                // 2. Request QR linking token from PalGate
                cb.onStatusUpdate("מתחבר לשרת PalGate...");
                JSONObject initBody = new JSONObject();
                initBody.put("sessionId", sessionId);
                initBody.put("platform", "android");

                JSONObject initResp = postJson(LINK_INIT_URL, initBody, null);

                String qrToken;
                if (initResp != null && initResp.has("qrToken")) {
                    qrToken = initResp.getString("qrToken");
                } else {
                    // Fallback: use sessionId directly as QR payload
                    qrToken = sessionId;
                }

                // 3. Emit QR content
                String qrContent = qrToken;
                cb.onQrReady(qrContent);
                cb.onStatusUpdate("סרוק את הקוד באפליקציית PalGate");

                // 4. Poll for scan completion (max 2 minutes)
                for (int i = 0; i < 60; i++) {
                    Thread.sleep(2000);

                    JSONObject pollBody = new JSONObject();
                    pollBody.put("sessionId", sessionId);
                    pollBody.put("qrToken", qrToken);

                    JSONObject pollResp = postJson(LINK_STATUS_URL, pollBody, null);

                    if (pollResp != null) {
                        String status = pollResp.optString("status", "");
                        if ("linked".equals(status) || "success".equals(status)) {
                            String phone  = pollResp.optString("phoneNumber", "");
                            String sToken = pollResp.optString("sessionToken", "");
                            int    tType  = pollResp.optInt("tokenType", 1);
                            if (!phone.isEmpty() && !sToken.isEmpty()) {
                                cb.onSuccess(phone, sToken, tType);
                                return;
                            }
                        }
                    }
                    cb.onStatusUpdate("ממתין לסריקה... " + (i + 1) + "/60");
                }
                cb.onFailure("פג תוקף הקוד. נסה שוב.");
            } catch (Exception e) {
                Log.e(TAG, "linking error", e);
                cb.onFailure("שגיאה: " + e.getMessage());
            }
        }).start();
    }

    // ── HTTP helpers ──────────────────────────────────────────────

    static JSONObject postJson(String urlStr, JSONObject body, String derivedToken) {
        try {
            HttpURLConnection conn = openConn(urlStr, "POST", derivedToken);
            byte[] bodyBytes = body.toString().getBytes("UTF-8");
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }
            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300
                    ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) { conn.disconnect(); return null; }
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            conn.disconnect();
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "postJson error", e);
            return null;
        }
    }

    private static HttpURLConnection openConn(String urlStr, String method,
                                               String derivedToken) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        if (derivedToken != null) conn.setRequestProperty("X-Bt-Token", derivedToken);
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        return conn;
    }
}
