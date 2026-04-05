package com.example.EnterpriseRagCommunity.service.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class HashingUtils {

    private HashingUtils() {
    }

    static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 failed: " + e.getMessage(), e);
        }
    }
}
