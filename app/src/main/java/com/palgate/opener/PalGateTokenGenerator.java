package com.palgate.opener;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * PalGate derived-token generator.
 * Ported from DonutByte/pylgate (GPLv3).
 *
 * The derived token is: tokenTypeByte(1) + AES-128-ECB(key=sessionToken, plaintext=timestamp(4)+phone(12))
 * where timestamp is Unix seconds rounded down to nearest 30s bucket.
 */
public class PalGateTokenGenerator {

    public static String generateDerivedToken(String phoneNumber, String sessionTokenHex, int tokenType)
            throws Exception {

        // 1. Build 16-byte plaintext: 4 bytes timestamp + 12 bytes phone (ASCII, zero-padded)
        long now = System.currentTimeMillis() / 1000L;
        long ts = now - (now % 30); // round to 30s bucket

        byte[] tsBytes = ByteBuffer.allocate(4).putInt((int) ts).array();
        byte[] phoneBytes = Arrays.copyOf(phoneNumber.getBytes("ASCII"), 12); // zero-padded to 12

        byte[] plaintext = new byte[16];
        System.arraycopy(tsBytes, 0, plaintext, 0, 4);
        System.arraycopy(phoneBytes, 0, plaintext, 4, 12);

        // 2. Key = session token bytes (16 bytes from 32-char hex string)
        byte[] keyBytes = hexToBytes(sessionTokenHex);

        // 3. AES-128-ECB encrypt (no padding — block is exactly 16 bytes)
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(plaintext);

        // 4. Derived token = tokenType as 2-char hex + encrypted as 32-char hex
        String tokenTypePart = String.format("%02x", tokenType);
        return tokenTypePart + bytesToHex(encrypted);
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
