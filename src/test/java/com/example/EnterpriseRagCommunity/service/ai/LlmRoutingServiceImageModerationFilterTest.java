package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmRoutingPolicyRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LlmRoutingServiceImageModerationFilterTest {

    @Test
    void imageModeration_onlyAllowsModelsAlsoInImageChatPool() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmRoutingPolicyRepository policyRepo = mock(LlmRoutingPolicyRepository.class);
        LlmCallQueueService queueService = mock(LlmCallQueueService.class);

        LlmRoutingService svc = new LlmRoutingService(modelRepo, policyRepo, queueService);

        LlmModelEntity a = new LlmModelEntity();
        a.setEnv("default");
        a.setPurpose("IMAGE_MODERATION");
        a.setProviderId("p1");
        a.setModelName("m1");
        a.setEnabled(true);
        a.setWeight(1);
        a.setPriority(0);
        a.setSortIndex(0);

        LlmModelEntity b = new LlmModelEntity();
        b.setEnv("default");
        b.setPurpose("IMAGE_MODERATION");
        b.setProviderId("p2");
        b.setModelName("m2");
        b.setEnabled(true);
        b.setWeight(1);
        b.setPriority(0);
        b.setSortIndex(0);

        LlmModelEntity imageChatOnly = new LlmModelEntity();
        imageChatOnly.setEnv("default");
        imageChatOnly.setPurpose("IMAGE_CHAT");
        imageChatOnly.setProviderId("p1");
        imageChatOnly.setModelName("m1");
        imageChatOnly.setEnabled(true);
        imageChatOnly.setWeight(1);
        imageChatOnly.setPriority(0);
        imageChatOnly.setSortIndex(0);

        when(modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "IMAGE_MODERATION"))
                .thenReturn(List.of(a, b));
        when(modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "IMAGE_CHAT"))
                .thenReturn(List.of(imageChatOnly));

        List<LlmRoutingService.RouteTarget> out = svc.listEnabledTargets("IMAGE_MODERATION");
        assertEquals(1, out.size());
        assertEquals("p1", out.get(0).providerId());
        assertEquals("m1", out.get(0).modelName());
    }
}

