package com.example.EnterpriseRagCommunity.service.access;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;

@Service
public class TotpService {
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock = Clock.systemUTC();

    public byte[] generateSecretBytes(int length) {
        if (length <= 0) throw new IllegalArgumentException("length must be positive");
        byte[] secret = new byte[length];
        secureRandom.nextBytes(secret);
        return secret;
    }

    public String generateCode(byte[] secret, long epochSeconds, String algorithm, int digits, int periodSeconds) {
        if (secret == null || secret.length == 0) throw new IllegalArgumentException("secret is required");
        if (digits != 6 && digits != 8) throw new IllegalArgumentException("digits must be 6 or 8");
        if (periodSeconds <= 0) throw new IllegalArgumentException("periodSeconds must be positive");

        long counter = Math.floorDiv(epochSeconds, periodSeconds);
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();

        byte[] hash = hmac(secret, counterBytes, algorithm);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary =
                ((hash[offset] & 0x7F) << 24) |
                        ((hash[offset + 1] & 0xFF) << 16) |
                        ((hash[offset + 2] & 0xFF) << 8) |
                        (hash[offset + 3] & 0xFF);

        int mod = digits == 6 ? 1_000_000 : 100_000_000;
        int otp = binary % mod;
        return String.format(Locale.ROOT, "%0" + digits + "d", otp);
    }

    public boolean verifyCode(byte[] secret, String code, String algorithm, int digits, int periodSeconds, int skewSteps) {
        if (code == null) return false;
        String normalized = code.trim();
        if (normalized.length() != digits) return false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c < '0' || c > '9') return false;
        }

        long epochSeconds = clock.instant().getEpochSecond();
        return verifyCodeAt(secret, normalized, epochSeconds, algorithm, digits, periodSeconds, skewSteps);
    }

    public boolean verifyCodeAt(byte[] secret, String code, long epochSeconds, String algorithm, int digits, int periodSeconds, int skewSteps) {
        if (skewSteps < 0 || skewSteps > 10) throw new IllegalArgumentException("skewSteps out of range");

        for (int i = -skewSteps; i <= skewSteps; i++) {
            long ts = epochSeconds + (long) i * periodSeconds;
            if (generateCode(secret, ts, algorithm, digits, periodSeconds).equals(code)) return true;
        }
        return false;
    }

    private static byte[] hmac(byte[] secret, byte[] data, String algorithm) {
        String macAlg = normalizeMacAlgorithm(algorithm);
        try {
            Mac mac = Mac.getInstance(macAlg);
            mac.init(new SecretKeySpec(secret, macAlg));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to compute HMAC with " + macAlg + ": " + e.getMessage(), e);
        }
    }

    private static String normalizeMacAlgorithm(String algorithm) {
        String a = algorithm == null ? "" : algorithm.trim();
        if (a.isEmpty()) return "HmacSHA1";
        String upper = a.toUpperCase(Locale.ROOT);
        if (upper.startsWith("HMAC")) return a;
        return switch (upper) {
            case "SHA1" -> "HmacSHA1";
            case "SHA256" -> "HmacSHA256";
            case "SHA512" -> "HmacSHA512";
            default -> throw new IllegalArgumentException("Unsupported TOTP algorithm: " + algorithm);
        };
    }
}

