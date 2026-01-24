package com.example.EnterpriseRagCommunity.utils;

import java.util.Arrays;

public final class Base32Codec {
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int[] LOOKUP = new int[256];

    static {
        Arrays.fill(LOOKUP, -1);
        for (int i = 0; i < ALPHABET.length; i++) {
            LOOKUP[ALPHABET[i]] = i;
        }
        for (int i = 0; i < 26; i++) {
            LOOKUP['a' + i] = i;
        }
    }

    private Base32Codec() {
    }

    public static String encode(byte[] data) {
        if (data == null || data.length == 0) return "";

        StringBuilder out = new StringBuilder(((data.length + 4) / 5) * 8);
        int buffer = data[0] & 0xFF;
        int next = 1;
        int bitsLeft = 8;

        while (bitsLeft > 0 || next < data.length) {
            if (bitsLeft < 5) {
                if (next < data.length) {
                    buffer <<= 8;
                    buffer |= data[next++] & 0xFF;
                    bitsLeft += 8;
                } else {
                    int pad = 5 - bitsLeft;
                    buffer <<= pad;
                    bitsLeft += pad;
                }
            }
            int index = (buffer >> (bitsLeft - 5)) & 0x1F;
            bitsLeft -= 5;
            out.append(ALPHABET[index]);
        }

        return out.toString();
    }

    public static byte[] decode(String base32) {
        if (base32 == null) return new byte[0];
        String normalized = base32.trim().replace("=", "").replace(" ", "");
        if (normalized.isEmpty()) return new byte[0];

        int outLen = normalized.length() * 5 / 8;
        byte[] result = new byte[outLen];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            int val = c < 256 ? LOOKUP[c] : -1;
            if (val < 0) {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }

            buffer <<= 5;
            buffer |= val & 0x1F;
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
                if (index == result.length) break;
            }
        }

        if (index == result.length) return result;
        return Arrays.copyOf(result, index);
    }
}

