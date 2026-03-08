package com.example.EnterpriseRagCommunity.service.moderation.admin;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;

public class AdminModerationLlmUpstreamSupportEvidenceTest {

    @Test
    void hasVerifiableEvidence_acceptsPlainEvidence() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        assertTrue(s.hasVerifiableEvidence("abc\n操你妹\nxyz", List.of("操你妹")));
    }

    @Test
    void hasVerifiableEvidence_acceptsJsonEvidenceObjectQuote() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        assertTrue(s.hasVerifiableEvidence("abc\n操你妹\nxyz", List.of("{\"source\":\"TEXT\",\"quote\":\"操你妹\",\"offset\":84554}")));
    }

    @Test
    void hasVerifiableEvidence_acceptsJsonEvidenceArrayQuote() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        assertTrue(s.hasVerifiableEvidence("abc\n操你妹\nxyz", List.of("[{\"source\":\"TEXT\",\"quote\":\"操你妹\",\"offset\":84554}]")));
    }

    @Test
    void hasVerifiableEvidence_rejectsJsonEvidenceObjectSpan() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        assertFalse(s.hasVerifiableEvidence("abc\n操你妹\nxyz", List.of("{\"start\":4,\"end\":7}")));
    }

    @Test
    void hasVerifiableEvidence_rejectsJsonEvidenceArraySpan() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        assertFalse(s.hasVerifiableEvidence("abc\n操你妹\nxyz", List.of("[{\"start\":4,\"end\":7}]")));
    }

    @Test
    void hasVerifiableEvidence_rejectsJsonEvidenceWithoutQuote() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        assertFalse(s.hasVerifiableEvidence("abc\n操你妹\nxyz", List.of("{\"source\":\"TEXT\",\"offset\":1}")));
    }

    @Test
    void hasVerifiableEvidence_rejectsOutOfRangeSpan() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        assertFalse(s.hasVerifiableEvidence("abc\n操你妹\nxyz", List.of("{\"start\":0,\"end\":999}")));
    }

    // ──── enrichEvidenceWithText tests ────

    @Test
    void enrichEvidenceWithText_anchorBased_extractsViolation() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        String auditText = "Safe text before. 违规内容在这里 Safe text after.";
        List<String> evidence = List.of("{\"before_context\":\"Safe text before. \",\"after_context\":\" Safe text after.\"}");
        List<String> enriched = s.enrichEvidenceWithText(evidence, auditText);
        assertEquals(1, enriched.size());
        assertTrue(enriched.get(0).contains("\"text\""));
        assertTrue(enriched.get(0).contains("违规内容在这里"));
    }

    @Test
    void enrichEvidenceWithText_keepsExistingText() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        String auditText = "some audit text with violation";
        List<String> evidence = List.of("{\"before_context\":\"safe-before\",\"after_context\":\"safe-after\",\"text\":\"already set\"}");
        List<String> enriched = s.enrichEvidenceWithText(evidence, auditText);
        assertEquals(1, enriched.size());
        assertTrue(enriched.get(0).contains("already set"));
    }

    @Test
    void enrichEvidenceWithText_keepsPlainTextEvidence() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        List<String> evidence = List.of("some plain text evidence");
        List<String> enriched = s.enrichEvidenceWithText(evidence, "any audit text");
        assertEquals(1, enriched.size());
        assertEquals("some plain text evidence", enriched.get(0));
    }

    @Test
    void enrichEvidenceWithText_nullAuditTextReturnsOriginal() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        List<String> evidence = List.of("{\"before_context\":\"safe-before\",\"after_context\":\"safe-after\"}");
        List<String> enriched = s.enrichEvidenceWithText(evidence, null);
        assertEquals(1, enriched.size());
        assertEquals("{\"before_context\":\"safe-before\",\"after_context\":\"safe-after\"}", enriched.get(0));
    }

    @Test
    void hasVerifiableEvidence_acceptsBeforeContextAnchor() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        assertTrue(s.hasVerifiableEvidence("abc before 违规 after xyz", List.of("{\"before_context\":\"before \",\"after_context\":\" after\"}")));
    }

    @Test
    void hasVerifiableEvidence_rejectsBeforeContextNotFound() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        assertFalse(s.hasVerifiableEvidence("abc xyz", List.of("{\"before_context\":\"not_here\",\"after_context\":\"also_not\"}")));
    }

    @Test
    void hasVerifiableEvidence_acceptsBeforeContextWithSmartQuoteMismatch() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        // LLM returns straight quotes in before_context, but inputText has smart quotes
        String inputText = "altitude \u201cbanana.\u201d \n  violation text here";
        assertTrue(s.hasVerifiableEvidence(inputText, List.of("{\"before_context\":\"altitude \\\"banana.\\\" \"}")));
    }

    @Test
    void hasVerifiableEvidence_acceptsBeforeContextWithWhitespaceCollapse() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        // LLM collapses double space to single in before_context
        String inputText = "Battery Master Switch -  The battery violation here";
        assertTrue(s.hasVerifiableEvidence(inputText, List.of("{\"before_context\":\"Switch - The battery\"}")));
    }

    @Test
    void enrichEvidenceWithText_anchorBased_fuzzyMatchExtractsViolation() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        // Smart quotes in text, straight quotes in anchor
        String auditText = "altitude \u201cbanana.\u201d \n  \u84d1\u8782\u836f\u3001\u8001\u9f20\u836f\n\n[[IMAGE_15]]\nmore text";
        List<String> evidence = List.of("{\"before_context\":\"altitude \\\"banana.\\\" \\n  \",\"after_context\":\"\\n\\n[[IMAGE_15]]\"}");
        List<String> enriched = s.enrichEvidenceWithText(evidence, auditText);
        assertEquals(1, enriched.size());
        assertTrue(enriched.get(0).contains("\"text\""), "should have text field");
        assertTrue(enriched.get(0).contains("\u84d1\u8782\u836f"), "should contain violation text");
    }

    @Test
    void enrichEvidenceWithText_fallbackStopsAtBoundary() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        String auditText = "违规开始: 蟑螂药、老鼠药、蒙汗药、迷情药+q231456154\n下一行";
        List<String> evidence = List.of("{\"before_context\":\"违规开始:\",\"after_context\":\"missing_after\"}");
        List<String> enriched = s.enrichEvidenceWithText(evidence, auditText);
        assertEquals(1, enriched.size());
        assertTrue(enriched.get(0).contains("蟑螂药、老鼠药、蒙汗药、迷情药+q231456154"));
    }

    @Test
    void hasVerifiableEvidence_acceptsBeforeContextWithSpaceAroundQuotes() {
        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        // LLM drops space before quote, original has space
        String inputText = "altitude \u201cbanana.\u201d \n  violation text here";
        assertTrue(s.hasVerifiableEvidence(inputText, List.of("{\"before_context\":\"altitude\\\"banana.\\\"\"}"))); 
    }
}
