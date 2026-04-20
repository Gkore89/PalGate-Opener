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

                Log.d(TAG, "Opening gate=" + gateId + " tokenType=" + tokenType);

                // Correct endpoint: GET /device/{id}/open-gate?outputNum=1
                // Header: x-bt-token (lowercase)
                String urlStr = BASE + "device/" + gateId + "/open-gate?outputNum=1";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setRequestProperty("x-bt-token", derived);
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Accept-Language", "en-us");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int code = conn.getResponseCode();
                String body = "";
                try {
                    InputStream is = code >= 200 && code < 300
                            ? conn.getInputStream() : conn.getErrorStream();
                    if (is != null) body = readStream(is);
                } catch (Exception ignored) {}
                conn.disconnect();

                Log.d(TAG, "open-gate response: " + code + " body=" + body);

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
