package com.example.EnterpriseRagCommunity.service.access;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceBranchTest {

    @Test
    void generateSecretBytes_and_generateCode_should_cover_argument_validation() {
        TotpService service = new TotpService();
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

        assertThrows(IllegalArgumentException.class, () -> service.generateSecretBytes(0));
        assertThrows(IllegalArgumentException.class, () -> service.generateCode(null, 0L, "SHA1", 6, 30));
        assertThrows(IllegalArgumentException.class, () -> service.generateCode(new byte[0], 0L, "SHA1", 6, 30));
        assertThrows(IllegalArgumentException.class, () -> service.generateCode(secret, 0L, "SHA1", 7, 30));
        assertThrows(IllegalArgumentException.class, () -> service.generateCode(secret, 0L, "SHA1", 6, 0));
    }

    @Test
    void generateCode_should_cover_algorithm_normalization_and_errors() {
        TotpService service = new TotpService();
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

        assertDoesNotThrow(() -> service.generateCode(secret, 59L, null, 6, 30));
        assertDoesNotThrow(() -> service.generateCode(secret, 59L, "  HmacSHA256  ", 6, 30));
        assertDoesNotThrow(() -> service.generateCode(secret, 59L, "sha512", 8, 30));
        assertThrows(IllegalArgumentException.class, () -> service.generateCode(secret, 59L, "md5", 6, 30));
    }

    @Test
    void verifyCode_should_cover_null_length_and_nondigit_paths() {
        TotpService service = new TotpService();
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

        assertFalse(service.verifyCode(secret, null, "SHA1", 6, 30, 1));
        assertFalse(service.verifyCode(secret, "12345", "SHA1", 6, 30, 1));
        assertFalse(service.verifyCode(secret, "12a456", "SHA1", 6, 30, 1));
    }

    @Test
    void verifyCodeAt_should_cover_skew_validation_and_true_false_paths() {
        TotpService service = new TotpService();
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        long epochSeconds = 1_234_567_890L;
        int periodSeconds = 30;
        String currentCode = service.generateCode(secret, epochSeconds, "SHA1", 6, periodSeconds);
        String nextStepCode = service.generateCode(secret, epochSeconds + periodSeconds, "SHA1", 6, periodSeconds);

        assertThrows(IllegalArgumentException.class, () ->
                service.verifyCodeAt(secret, currentCode, epochSeconds, "SHA1", 6, periodSeconds, -1));
        assertThrows(IllegalArgumentException.class, () ->
                service.verifyCodeAt(secret, currentCode, epochSeconds, "SHA1", 6, periodSeconds, 11));

        assertTrue(service.verifyCodeAt(secret, currentCode, epochSeconds, "SHA1", 6, periodSeconds, 0));
        assertTrue(service.verifyCodeAt(secret, nextStepCode, epochSeconds, "SHA1", 6, periodSeconds, 1));
        assertFalse(service.verifyCodeAt(secret, "000000", epochSeconds, "SHA1", 6, periodSeconds, 1));
    }
}
