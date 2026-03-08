package com.example.EnterpriseRagCommunity.service.moderation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationChunkReviewServiceEvidenceFingerprintRemainingBranchesTest {

    @Test
    void evidenceFingerprint_shouldReturnEmptyForNullAndBlankInput() throws Exception {
        assertEquals("", fp(null));
        assertEquals("", fp("   "));
    }

    @Test
    void evidenceFingerprint_shouldNormalizeTextPathWithPlaceholderWhitespaceAndCase() throws Exception {
        String a = "{\"text\":\"  A [[IMAGE_1]]\\n\\nB  \"}";
        String b = "{\"text\":\"a b\"}";
        assertEquals(fp(a), fp(b));
        assertTrue(fp(a).startsWith("text|"));
    }

    @Test
    void evidenceFingerprint_shouldUseQuotePathWhenTextAndContextMissing() throws Exception {
        String a = "{\"quote\":\"  HeLLo   [[IMAGE_2]]  WoRLD  \"}";
        assertEquals("quote|hello world", fp(a));
    }

    @Test
    void evidenceFingerprint_shouldFallbackToRawPathWhenJsonStructureHasNoEvidenceFields() throws Exception {
        assertEquals("raw|{}", fp("{}"));
    }

    @Test
    void evidenceFingerprint_shouldFallbackToRawPathWhenJsonParseFails() throws Exception {
        assertEquals("raw|{not-json}", fp("{not-json}"));
    }

    @Test
    void evidenceFingerprint_shouldFallbackToRawPathForPlainText() throws Exception {
        assertEquals("raw|some plain text", fp("  SOME   PLAIN   TEXT "));
    }

    private static String fp(String raw) throws Exception {
        Method m = ModerationChunkReviewService.class.getDeclaredMethod("evidenceFingerprint", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, raw);
    }
}
