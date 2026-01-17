package com.example.EnterpriseRagCommunity.service.moderation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Utilities for moderation_samples text normalization + hashing.
 *
 * Notes:
 * - Keep it dependency-free (no Apache Commons).
 * - Normalization here is intentionally conservative: trim + collapse whitespace.
 */
public final class ModerationSampleTextUtils {

    private ModerationSampleTextUtils() {
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        // Trim, collapse whitespace to single space.
        String s = raw.trim();
        if (s.isEmpty()) return "";
        s = s.replaceAll("\\s+", " ");
        return s;
    }

    /** sha256 hex (lowercase). */
    public static String sha256Hex(String s) {
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
