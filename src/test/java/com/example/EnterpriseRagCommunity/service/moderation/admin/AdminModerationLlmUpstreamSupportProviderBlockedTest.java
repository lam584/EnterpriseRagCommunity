package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminModerationLlmUpstreamSupportProviderBlockedTest {

    @Test
    void callTextOnce_shouldClassifyProviderOutputBlocked() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        String errBody = "{\"error\":{\"message\":\"Output data may contain inappropriate content. For details, see: `https://help.aliyun.com/zh/model-studio/error-code#inappropriate-content` \",\"type\":\"data_inspection_failed\",\"param\":null,\"code\":\"data_inspection_failed\"},\"id\":\"chatcmpl-xx\",\"request_id\":\"rid-123\"}";
        when(llmGateway.chatOnceRoutedNoQueue(
            eq(LlmQueueTaskType.MULTIMODAL_MODERATION),
                nullable(String.class),
                nullable(String.class),
                anyList(),
                nullable(Double.class),
                nullable(Double.class),
                nullable(Integer.class),
                nullable(List.class),
                any(),
                nullable(Integer.class),
                nullable(Map.class)
        )).thenThrow(new IllegalStateException("上游AI调用失败: Upstream returned HTTP 400: " + errBody));

        AdminModerationLlmUpstreamSupport s = new AdminModerationLlmUpstreamSupport(llmGateway, mock(AdminModerationLlmImageSupport.class));
        StageCallResult r = s.callTextOnce("s", "u", 0.2, 0.2, 128, "aliyun", null, false, false);
        assertNotNull(r);
        assertEquals("ESCALATE", r.decisionSuggestion());
        assertEquals("HUMAN", r.decision());
        assertTrue(r.riskTags().contains("PROVIDER_OUTPUT_BLOCKED"));
        assertTrue(r.reasons().stream().anyMatch(x -> x != null && x.contains("上游输出安全审查拦截")));
        assertTrue(r.reasons().stream().anyMatch(x -> x != null && x.contains("upstream_request_id=rid-123")));
    }
}

