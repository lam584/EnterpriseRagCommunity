package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdminModerationLlmUpstreamSupportRiskTagsParseTest {

    @Test
    void parseDecision_shouldPreferRiskTagsOverLabels() {
        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        ParsedDecision out = support.parseDecisionFromAssistantText("{\"labels\":[\"violence\"],\"riskTags\":[\"spam\"]}");
        assertThat(out.riskTags).containsExactly("spam");
    }

    @Test
    void parseDecision_shouldFallbackToLabelsWhenRiskTagsAbsent() {
        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        ParsedDecision out = support.parseDecisionFromAssistantText("{\"labels\":[\"violence\"]}");
        assertThat(out.riskTags).containsExactly("violence");
    }

    @Test
    void parseDecision_shouldAcceptRiskTagsSnakeCase() {
        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(mock(LlmGateway.class), mock(AdminModerationLlmImageSupport.class));
        ParsedDecision out = support.parseDecisionFromAssistantText("{\"labels\":[\"violence\"],\"risk_tags\":[\"spam\"]}");
        assertThat(out.riskTags).containsExactly("spam");
    }
}

