package com.palgate.opener;

import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class PalGateApiClient {

    private static final String TAG = "PalGateApi";
    private static final String OPEN_URL = "https://api1.pal-es.com/v1/bt/user/open-door/";

    public interface OpenCallback {
        void onSuccess(String gateId);
        void onFailure(String gateId, String error);
    }

    public static void openGate(String gateId, String phone, String token,
                                int tokenType, OpenCallback cb) {
        new Thread(() -> {
            try {
                String derived = PalGateTokenGenerator.generateDerivedToken(phone, token, tokenType);
                Log.d(TAG, "Opening gate " + gateId + " tokenType=" + tokenType + " phone=" + phone);

                URL url = new URL(OPEN_URL + gateId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "okhttp/4.9.3");
                conn.setRequestProperty("X-Bt-Token", derived);
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write("{}".getBytes());
                }

                int code = conn.getResponseCode();
                String body = "";
                try {
                    InputStream is = code >= 200 && code < 300
                            ? conn.getInputStream() : conn.getErrorStream();
                    if (is != null) body = readStream(is);
                } catch (Exception ignored) {}
                conn.disconnect();

                Log.d(TAG, "Gate response: " + code + " body=" + body);

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

    static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
