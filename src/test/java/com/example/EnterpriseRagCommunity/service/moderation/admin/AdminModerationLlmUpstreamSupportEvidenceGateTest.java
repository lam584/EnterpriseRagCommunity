package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminModerationLlmUpstreamSupportEvidenceGateTest {

    @Test
    void callTextOnce_shouldDowngradeRejectWhenEvidenceMissing() {
        LlmGateway gw = mock(LlmGateway.class);
        String out = "{\"decision_suggestion\":\"REJECT\",\"risk_score\":0.9,\"labels\":[\"abuse\"],\"severity\":\"HIGH\",\"evidence\":[],\"uncertainty\":0.1,\"reasons\":[]}";
        when(gw.chatOnceRoutedNoQueue(any(), nullable(String.class), nullable(String.class), anyList(), any(), any(), nullable(Integer.class), nullable(List.class), any(), nullable(Integer.class), nullable(java.util.Map.class)))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(out, "p1", "m1", null));

        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(gw, mock(AdminModerationLlmImageSupport.class));
        StageCallResult r = support.callTextOnce("s", "hello", 0.2, 0.2, 200, null, null, null, false);
        assertThat(r.decisionSuggestion()).isEqualTo("ESCALATE");
        assertThat(r.decision()).isEqualTo("HUMAN");
        assertThat(r.reasons()).anyMatch(s -> s != null && s.contains("missing evidence"));
    }

    @Test
    void callTextOnce_shouldDowngradeRejectWhenEvidenceUnverifiable() {
        LlmGateway gw = mock(LlmGateway.class);
        String out = "{\"decision_suggestion\":\"REJECT\",\"risk_score\":0.95,\"labels\":[\"abuse\"],\"severity\":\"CRITICAL\",\"evidence\":[\"操你妹\"],\"uncertainty\":0.1,\"reasons\":[]}";
        when(gw.chatOnceRoutedNoQueue(any(), nullable(String.class), nullable(String.class), anyList(), any(), any(), nullable(Integer.class), nullable(List.class), any(), nullable(Integer.class), nullable(java.util.Map.class)))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(out, "p1", "m1", null));

        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(gw, mock(AdminModerationLlmImageSupport.class));
        StageCallResult r = support.callTextOnce("s", "NOTE: The memory store ...", 0.2, 0.2, 200, null, null, null, false);
        assertThat(r.decisionSuggestion()).isEqualTo("ESCALATE");
        assertThat(r.decision()).isEqualTo("HUMAN");
        assertThat(r.reasons()).anyMatch(s -> s != null && s.contains("unverifiable evidence"));
    }

    @Test
    void callTextOnce_shouldKeepRejectWhenEvidenceVerifiable() {
        LlmGateway gw = mock(LlmGateway.class);
        String out = "{\"decision_suggestion\":\"REJECT\",\"risk_score\":0.95,\"labels\":[\"abuse\"],\"severity\":\"CRITICAL\",\"evidence\":[\"hello\"],\"uncertainty\":0.1,\"reasons\":[]}";
        when(gw.chatOnceRoutedNoQueue(any(), nullable(String.class), nullable(String.class), anyList(), any(), any(), nullable(Integer.class), nullable(List.class), any(), nullable(Integer.class), nullable(java.util.Map.class)))
                .thenReturn(new LlmGateway.RoutedChatOnceResult(out, "p1", "m1", null));

        AdminModerationLlmUpstreamSupport support = new AdminModerationLlmUpstreamSupport(gw, mock(AdminModerationLlmImageSupport.class));
        StageCallResult r = support.callTextOnce("s", "hello world", 0.2, 0.2, 200, null, null, null, false);
        assertThat(r.decisionSuggestion()).isEqualTo("REJECT");
        assertThat(r.decision()).isEqualTo("REJECT");
    }
}
