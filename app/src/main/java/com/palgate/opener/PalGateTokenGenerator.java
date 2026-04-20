package com.palgate.opener;

import android.util.Log;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * PalGate derived-token generator.
 * Exact port of DonutByte/pylgate token_generator.py
 *
 * T_C_KEY = [250, 211, 37, 114, 129, 41, 0, 0, 0, 0, 0, 0, 58, 180, 90, 101]
 * TIMESTAMP_OFFSET = 2
 * TOKEN_SIZE = 23
 * BLOCK_SIZE = 16
 *
 * step1: inject phone into T_C_KEY[6:12], AES-ECB ENCRYPT session_token with that key
 * step2: build 16-byte state (0x0A0A at [1:3], timestamp+2 at [10:14] big-endian),
 *        AES-ECB DECRYPT state using step1 result as key
 * result[0]    = 0x01 (SMS) / 0x11 (PRIMARY) / 0x21 (SECONDARY)
 * result[1:7]  = phone number upper 6 bytes (big-endian int64 bytes [2:8])
 * result[7:23] = step2 result
 * return result.hex().toUpperCase()
 */
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

        long phoneNumber = Long.parseLong(phoneStr.trim());
        byte[] sessionToken = hexToBytes(sessionTokenHex);
        int timestamp = (int)(System.currentTimeMillis() / 1000L);

        // step 1: build key from T_C_KEY with phone injected at [6:12]
        byte[] key = Arrays.copyOf(T_C_KEY, BLOCK_SIZE);
        // struct.pack(">Q", phone_number)[2:8] → bytes 2..7 of big-endian int64
        byte[] phoneBytes = new byte[8];
        ByteBuffer.wrap(phoneBytes).putLong(phoneNumber);
        // key[6:12] = phoneBytes[2:8]
        System.arraycopy(phoneBytes, 2, key, 6, 6);

        // AES-ECB ENCRYPT session_token with key
        byte[] step1Result = aesEcb(sessionToken, key, true);

        // step 2: build 16-byte state
        byte[] state = new byte[BLOCK_SIZE];
        // next_state[1:3] = struct.pack("<H", 0x0A0A) → little-endian 0x0A0A = [0x0A, 0x0A]
        state[1] = 0x0A;
        state[2] = 0x0A;
        // next_state[10:14] = struct.pack(">I", timestamp + offset)
        int ts = timestamp + TIMESTAMP_OFFSET;
        state[10] = (byte)((ts >> 24) & 0xFF);
        state[11] = (byte)((ts >> 16) & 0xFF);
        state[12] = (byte)((ts >>  8) & 0xFF);
        state[13] = (byte)( ts        & 0xFF);

        // AES-ECB DECRYPT state using step1 result as key
        byte[] step2Result = aesEcb(state, step1Result, false);

        // Assemble 23-byte result
        byte[] result = new byte[TOKEN_SIZE];
        // byte 0: token type marker
        if (tokenTypeInt == 0)      result[0] = 0x01; // SMS
        else if (tokenTypeInt == 1) result[0] = 0x11; // PRIMARY
        else                        result[0] = 0x21; // SECONDARY

        // bytes 1-6: phoneBytes[2:8] (upper 6 bytes of big-endian int64)
        System.arraycopy(phoneBytes, 2, result, 1, 6);

        // bytes 7-22: step2 result
        System.arraycopy(step2Result, 0, result, 7, BLOCK_SIZE);

        String derived = bytesToHex(result).toUpperCase();
        Log.d(TAG, "Token: phone=" + phoneNumber + " type=" + tokenTypeInt
                + " ts=" + timestamp + " derived=" + derived.substring(0, 6) + "...");
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
