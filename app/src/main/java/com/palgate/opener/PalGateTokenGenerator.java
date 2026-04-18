package com.palgate.opener;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;

/**
 * PalGate derived-token generator.
 * Matches DonutByte/pylgate generate_token() exactly.
 *
 * pylgate signature: generate_token(session_token: bytes, phone_number: int, token_type: TokenType)
 *
 * Plaintext (16 bytes):
 *   - bytes 0-3:  Unix timestamp rounded to 30s, big-endian int32
 *   - bytes 4-11: phone number as big-endian int64 (NOT ASCII string)
 *   - bytes 12-15: zeroes (padding)
 *
 * Key: session token bytes (16 bytes from 32-char hex)
 * Cipher: AES-128-ECB, no padding
 *
 * Derived token = tokenType (2 hex chars) + encrypted (32 hex chars) = 34 chars total
 */
public class PalGateTokenGenerator {

    public static String generateDerivedToken(String phoneNumber, String sessionTokenHex, int tokenType)
            throws Exception {

        long now = System.currentTimeMillis() / 1000L;
        long ts  = now - (now % 30);

        // Phone number as long integer, encoded as big-endian 8 bytes
        long phoneInt = Long.parseLong(phoneNumber.trim());

        // Build 16-byte plaintext: 4 bytes timestamp + 8 bytes phone + 4 bytes zero
        byte[] plaintext = new byte[16];
        ByteBuffer buf = ByteBuffer.wrap(plaintext);
        buf.putInt((int) ts);       // bytes 0-3
        buf.putLong(phoneInt);      // bytes 4-11
        // bytes 12-15 remain zero

        // Key = session token hex decoded to 16 bytes
        byte[] keyBytes = hexToBytes(sessionTokenHex);

        // AES-128-ECB encrypt
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(plaintext);

        // Result: 2-char token type + 32-char encrypted hex = 34 chars
        return String.format("%02x", tokenType) + bytesToHex(encrypted);
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
