package com.palgate.opener;

import android.util.Log;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class PalGateTokenGenerator {

    private static final String TAG = "PalGateToken";

    private static final byte[] T_C_KEY = {
        (byte)250, (byte)211, (byte)37,  (byte)114,
        (byte)129, (byte)41,  (byte)0,   (byte)0,
        (byte)0,   (byte)0,   (byte)0,   (byte)0,
        (byte)58,  (byte)180, (byte)90,  (byte)101
    };

    private static final int TIMESTAMP_OFFSET = 2;
    private static final int TOKEN_SIZE = 23;
    private static final int BLOCK_SIZE = 16;

    public static String generateDerivedToken(String phoneStr, String sessionTokenHex, int tokenTypeInt)
            throws Exception {
        return generateDerivedToken(phoneStr, sessionTokenHex, tokenTypeInt, 0);
    }

    public static String generateDerivedToken(String phoneStr, String sessionTokenHex,
                                               int tokenTypeInt, int clockOffsetSeconds)
            throws Exception {

        long phoneNumber = Long.parseLong(phoneStr.trim());
        byte[] sessionToken = hexToBytes(sessionTokenHex);
        int timestamp = (int)(System.currentTimeMillis() / 1000L) + clockOffsetSeconds;

        // Step 1
        byte[] key = Arrays.copyOf(T_C_KEY, BLOCK_SIZE);
        byte[] phoneBytes = new byte[8];
        ByteBuffer.wrap(phoneBytes).putLong(phoneNumber);
        System.arraycopy(phoneBytes, 2, key, 6, 6);
        byte[] step1Result = aesEcb(sessionToken, key, true);

        // Step 2
        byte[] state = new byte[BLOCK_SIZE];
        state[1] = 0x0A;
        state[2] = 0x0A;
        int ts = timestamp + TIMESTAMP_OFFSET;
        state[10] = (byte)((ts >> 24) & 0xFF);
        state[11] = (byte)((ts >> 16) & 0xFF);
        state[12] = (byte)((ts >>  8) & 0xFF);
        state[13] = (byte)( ts        & 0xFF);
        byte[] step2Result = aesEcb(state, step1Result, false);

        // Assemble
        byte[] result = new byte[TOKEN_SIZE];
        if (tokenTypeInt == 0)      result[0] = 0x01;
        else if (tokenTypeInt == 1) result[0] = 0x11;
        else                        result[0] = 0x21;
        System.arraycopy(phoneBytes, 2, result, 1, 6);
        System.arraycopy(step2Result, 0, result, 7, BLOCK_SIZE);

        String derived = bytesToHex(result).toUpperCase();
        Log.d(TAG, "Token: phone=" + phoneNumber + " type=" + tokenTypeInt
                + " ts=" + timestamp + " offset=" + clockOffsetSeconds
                + " derived_prefix=" + derived.substring(0, 8));
        return derived;
    }

    private static byte[] aesEcb(byte[] data, byte[] key, boolean encrypt) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(data);
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
