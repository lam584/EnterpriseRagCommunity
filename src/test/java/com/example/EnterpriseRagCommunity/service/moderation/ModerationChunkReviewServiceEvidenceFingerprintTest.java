package com.example.EnterpriseRagCommunity.service.moderation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ModerationChunkReviewServiceEvidenceFingerprintTest {

    private static String fp(String raw) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("evidenceFingerprint", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, raw);
    }

    @Test
    void fingerprint_sameTextAcrossChunks_shouldBeEqual() throws Exception {
        String a = "{\"text\":\"蟑螂药、老鼠药、蒙汗药、迷情药+q231456154\",\"start\":10,\"end\":30}";
        String b = "{\"text\":\"蟑螂药、老鼠药、蒙汗药、迷情药+q231456154\",\"start\":2010,\"end\":2030}";
        assertEquals(fp(a), fp(b));
    }

    @Test
    void fingerprint_sameContextWithPlaceholderNoise_shouldBeEqual() throws Exception {
        String a = "{\"before_context\":\"abc [[IMAGE_1]]\",\"after_context\":\" xyz\"}";
        String b = "{\"before_context\":\"abc\",\"after_context\":\"xyz\"}";
        assertEquals(fp(a), fp(b));
    }

    @Test
    void fingerprint_differentEvidence_shouldNotBeEqual() throws Exception {
        String a = "{\"text\":\"看看你下面\"}";
        String b = "{\"text\":\"日你妈\"}";
        assertNotEquals(fp(a), fp(b));
    }
}

