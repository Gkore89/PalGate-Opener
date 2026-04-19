package com.palgate.opener;

import android.util.Log;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;

/**
 * PalGate derived-token generator.
 * Matches DonutByte/pylgate generate_token() exactly.
 *
 * pylgate: generate_token(session_token: bytes, phone_number: int, token_type: TokenType)
 *
 * Plaintext (16 bytes):
 *   bytes 0-3:   Unix timestamp rounded down to 30s bucket, big-endian int32
 *   bytes 4-11:  phone number as big-endian int64
 *   bytes 12-15: zeroes
 *
 * Key: session token (16 bytes from 32-char hex)
 * Cipher: AES-128-ECB, no padding
 * Output: tokenType (2 hex chars) + AES result (32 hex chars) = 34 chars
 */
public class PalGateTokenGenerator {

    private static final String TAG = "PalGateToken";

    public static String generateDerivedToken(String phoneNumber, String sessionTokenHex, int tokenType)
            throws Exception {

        long now = System.currentTimeMillis() / 1000L;
        long ts  = now - (now % 30);

        long phoneInt = Long.parseLong(phoneNumber.trim());

        byte[] plaintext = new byte[16];
        ByteBuffer buf = ByteBuffer.wrap(plaintext);
        buf.putInt((int) ts);    // bytes 0-3: timestamp
        buf.putLong(phoneInt);   // bytes 4-11: phone as int64
                                 // bytes 12-15: zero padding (already 0)

        byte[] keyBytes = hexToBytes(sessionTokenHex);

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(plaintext);

        String derived = String.format("%02x", tokenType) + bytesToHex(encrypted);

        Log.d(TAG, "Token generated: phone=" + phoneNumber
                + " type=" + tokenType
                + " ts=" + ts
                + " derived_prefix=" + derived.substring(0, 4) + "...");

        return derived;
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        return out;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
