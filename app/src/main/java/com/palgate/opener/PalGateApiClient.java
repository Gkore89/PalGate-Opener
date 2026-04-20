package com.palgate.opener;

import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class PalGateApiClient {

    private static final String TAG = "PalGateApi";
    private static final String BASE = "https://api1.pal-es.com/v1/bt/";
    private static final String USER_AGENT = "okhttp/4.9.3";

    public interface OpenCallback {
        void onSuccess(String gateId);
        void onFailure(String gateId, String error);
    }

    public static void openGate(String gateId, String phone, String token,
                                int tokenType, OpenCallback cb) {
        new Thread(() -> {
            try {
                String derived = PalGateTokenGenerator.generateDerivedToken(
                        phone, token, tokenType);

                Log.d(TAG, "=== OPEN GATE DEBUG ===");
                Log.d(TAG, "Phone: " + phone);
                Log.d(TAG, "TokenType: " + tokenType);
                Log.d(TAG, "SessionToken length: " + token.length());
                Log.d(TAG, "DerivedToken: " + derived);
                Log.d(TAG, "GateId: " + gateId);

                // Step 1: verify token works via check-token
                long ts = System.currentTimeMillis() / 1000L;
                String checkUrl = BASE + "user/check-token?ts=" + ts + "&ts_diff=0";
                int checkCode = doGet(checkUrl, derived);
                Log.d(TAG, "check-token response: " + checkCode);

                if (checkCode == 401) {
                    cb.onFailure(gateId, "check-token 401 — טוקן לא תקין. נסה לקשר מחדש.");
                    return;
                }

                // Step 2: open the gate
                String openUrl = BASE + "user/open-door/" + gateId;
                String[] result = doPost(openUrl, derived, "{}");
                int code = Integer.parseInt(result[0]);
                String body = result[1];

                Log.d(TAG, "open-door response: " + code + " body=" + body);

                if (code >= 200 && code < 300) {
                    cb.onSuccess(gateId);
                } else {
                    cb.onFailure(gateId, "HTTP " + code + ": " + body);
                }
            } catch (Exception e) {
                Log.e(TAG, "openGate error", e);
                cb.onFailure(gateId, e.getMessage());
            }
        }).start();
    }

    private static int doGet(String urlStr, String derivedToken) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("X-Bt-Token", derivedToken);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        int code = conn.getResponseCode();
        conn.disconnect();
        return code;
    }

    private static String[] doPost(String urlStr, String derivedToken,
                                    String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("X-Bt-Token", derivedToken);
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes());
        }
        int code = conn.getResponseCode();
        String respBody = "";
        try {
            InputStream is = code >= 200 && code < 300
                    ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) respBody = readStream(is);
        } catch (Exception ignored) {}
        conn.disconnect();
        return new String[]{String.valueOf(code), respBody};
    }

    static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
