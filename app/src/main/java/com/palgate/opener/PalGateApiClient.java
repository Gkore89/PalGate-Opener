package com.palgate.opener;

import android.util.Log;
import okhttp3.*;
import java.io.IOException;

public class PalGateApiClient {

    private static final String TAG = "PalGateApi";
    private static final String BASE = "https://api1.pal-es.com/v1/bt/";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .build();

    public interface OpenCallback {
        void onSuccess(String gateId);
        void onFailure(String gateId, String error);
    }

    public static void openGate(String gateId, String phone, String token,
                                int tokenType, OpenCallback cb) {
        new Thread(() -> {
            int[] offsets = {0, 30, -30, 60, -60};
            String lastError = "unknown";
            for (int offset : offsets) {
                try {
                    String derived = PalGateTokenGenerator.generateDerivedToken(
                            phone, token, tokenType, offset);

                    String url = BASE + "device/" + gateId + "/open-gate?outputNum=1";
                    Request request = new Request.Builder()
                            .url(url)
                            .get()
                            .header("User-Agent", "okhttp/4.9.3")
                            .header("x-bt-token", derived)
                            .header("Accept", "*/*")
                            .header("Accept-Language", "en-us")
                            .build();

                    try (Response response = client.newCall(request).execute()) {
                        int code = response.code();
                        String body = response.body() != null ? response.body().string() : "";
                        Log.d(TAG, "open-gate offset=" + offset + " code=" + code + " body=" + body);

                        if (code >= 200 && code < 300) {
                            cb.onSuccess(gateId);
                            return;
                        }
                        lastError = "HTTP " + code + ": " + body;
                        if (code != 401) break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "openGate error", e);
                    lastError = e.getMessage();
                }
            }
            cb.onFailure(gateId, lastError);
        }).start();
    }

    // Check token using OkHttp
    public static int checkToken(String derived) {
        try {
            long ts = System.currentTimeMillis() / 1000L;
            String url = BASE + "user/check-token?ts=" + ts + "&ts_diff=0";
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", "okhttp/4.9.3")
                    .header("x-bt-token", derived)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.code();
            }
        } catch (Exception e) {
            return -1;
        }
    }
}
