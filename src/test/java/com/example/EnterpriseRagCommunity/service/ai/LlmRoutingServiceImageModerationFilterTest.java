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
    void imageModeration_should_use_multimodal_moderation_pool() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmRoutingPolicyRepository policyRepo = mock(LlmRoutingPolicyRepository.class);
        LlmCallQueueService queueService = mock(LlmCallQueueService.class);

        LlmRoutingService svc = new LlmRoutingService(modelRepo, policyRepo, queueService);

        LlmModelEntity a = new LlmModelEntity();
        a.setEnv("default");
        a.setPurpose("MULTIMODAL_MODERATION");
        a.setProviderId("p1");
        a.setModelName("m1");
        a.setEnabled(true);
        a.setWeight(1);
        a.setPriority(0);
        a.setSortIndex(0);

        LlmModelEntity b = new LlmModelEntity();
        b.setEnv("default");
        b.setPurpose("MULTIMODAL_MODERATION");
        b.setProviderId("p2");
        b.setModelName("m2");
        b.setEnabled(true);
        b.setWeight(1);
        b.setPriority(0);
        b.setSortIndex(0);

        when(modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_MODERATION"))
                .thenReturn(List.of(a, b));

        List<LlmRoutingService.RouteTarget> out = svc.listEnabledTargets("IMAGE_MODERATION");
        assertEquals(2, out.size());
        assertEquals("p1", out.get(0).providerId());
        assertEquals("m1", out.get(0).modelName());
    }
}

