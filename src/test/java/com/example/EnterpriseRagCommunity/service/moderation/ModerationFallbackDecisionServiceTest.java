package com.example.EnterpriseRagCommunity.service.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModerationFallbackDecisionServiceTest {

    @Test
    void verdictFromLlmScore_requiresThresholds() {
        assertThrows(IllegalStateException.class, () -> ModerationFallbackDecisionService.verdictFromLlmScore(0.5, null, 0.3));
        assertThrows(IllegalStateException.class, () -> ModerationFallbackDecisionService.verdictFromLlmScore(0.5, 0.8, null));
    }

    @Test
    void verdictFromLlmScore_shouldClampScore() {
        assertEquals(Verdict.REJECT, ModerationFallbackDecisionService.verdictFromLlmScore(2.0, 0.8, 0.3));
        assertEquals(Verdict.APPROVE, ModerationFallbackDecisionService.verdictFromLlmScore(-1.0, 0.8, 0.3));
    }

    @Test
    void verdictFromLlmScore_shouldClampThresholds() {
        assertEquals(Verdict.APPROVE, ModerationFallbackDecisionService.verdictFromLlmScore(0.99, 2.0, 1.5));
        assertEquals(Verdict.REJECT, ModerationFallbackDecisionService.verdictFromLlmScore(0.01, -1.0, -0.5));
    }
}
