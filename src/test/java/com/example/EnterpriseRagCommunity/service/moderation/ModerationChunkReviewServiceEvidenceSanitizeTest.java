package com.example.EnterpriseRagCommunity.service.moderation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationChunkReviewServiceEvidenceSanitizeTest {

    private static String sanitize(String raw, int limit) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod(
                "sanitizeEvidenceItemForMemory",
                String.class,
                int.class
        );
        m.setAccessible(true);
        return (String) m.invoke(null, raw, limit);
    }

    @Test
    void sanitizeKeepsValidJsonEvenWhenLengthExceedsLimit() throws Exception {
        String payload = "{\"start\":1842,\"end\":1846,\"text\":\"" + "x".repeat(600) + "\"}";

        String out = sanitize(payload, 400);

        assertEquals(payload, out);
    }

    @Test
    void sanitizeTruncatesPlainTextWhenLengthExceedsLimit() throws Exception {
        String payload = "a".repeat(600);

        String out = sanitize(payload, 400);

        assertEquals(400, out.length());
        assertEquals("a".repeat(400), out);
    }
}
