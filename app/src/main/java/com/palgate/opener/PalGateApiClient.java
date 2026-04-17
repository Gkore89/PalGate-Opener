package com.palgate.opener;

import android.util.Log;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PalGateApiClient {

    private static final String TAG = "PalGateApi";
    private static final String BASE_URL = "https://api1.pal-es.com/v1/bt/user/open-door/";

    public interface OpenGateCallback {
        void onSuccess(String gateId);
        void onFailure(String gateId, String error);
    }

    /**
     * Opens a gate. Must be called from a background thread.
     */
    public static void openGate(String gateId, String phoneNumber, String sessionToken,
                                 int tokenType, OpenGateCallback callback) {
        new Thread(() -> {
            try {
                String derivedToken = PalGateTokenGenerator.generateDerivedToken(
                        phoneNumber, sessionToken, tokenType);

                String urlStr = BASE_URL + gateId;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-Bt-Token", derivedToken);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                // Empty JSON body
                try (OutputStream os = conn.getOutputStream()) {
                    os.write("{}".getBytes());
                }

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Gate " + gateId + " response: " + responseCode);

                if (responseCode >= 200 && responseCode < 300) {
                    callback.onSuccess(gateId);
                } else {
                    callback.onFailure(gateId, "HTTP " + responseCode);
                }
                conn.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Error opening gate " + gateId, e);
                callback.onFailure(gateId, e.getMessage());
            }
        }).start();
    }
}
