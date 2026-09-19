package br.com.craftonica.firmware;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class FirmwareHashes {
    private FirmwareHashes() {
    }

    static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }

    static byte[] fromHex(String value) {
        if (value == null || (value.length() & 1) != 0) throw new IllegalArgumentException("Invalid hexadecimal value");
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid hexadecimal value");
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }
}
