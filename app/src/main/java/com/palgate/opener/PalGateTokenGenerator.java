package com.palgate.opener;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class PalGateTokenGenerator {

    public static String generateDerivedToken(String phoneNumber, String sessionTokenHex, int tokenType)
            throws Exception {
        long now = System.currentTimeMillis() / 1000L;
        long ts = now - (now % 30);

        byte[] tsBytes = ByteBuffer.allocate(4).putInt((int) ts).array();
        byte[] phoneBytes = Arrays.copyOf(phoneNumber.getBytes("ASCII"), 12);

        byte[] plaintext = new byte[16];
        System.arraycopy(tsBytes, 0, plaintext, 0, 4);
        System.arraycopy(phoneBytes, 0, plaintext, 4, 12);

        byte[] keyBytes = hexToBytes(sessionTokenHex);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(plaintext);

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
