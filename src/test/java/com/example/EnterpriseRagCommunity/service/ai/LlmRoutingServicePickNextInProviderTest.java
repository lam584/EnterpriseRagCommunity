package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmRoutingPolicyRepository;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmRoutingServicePickNextInProviderTest {

    @Test
    void pickNextInProvider_should_filter_by_provider_id() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmRoutingPolicyRepository policyRepo = mock(LlmRoutingPolicyRepository.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);

        when(policyRepo.findById(any())).thenReturn(java.util.Optional.empty());
        when(queue.snapshot(anyInt(), anyInt(), anyInt()))
                .thenReturn(new LlmCallQueueService.QueueSnapshot(0, 0, 0, 0, List.of(), List.of(), List.of()));

        LlmModelEntity m1 = new LlmModelEntity();
        m1.setProviderId("p1");
        m1.setModelName("m1");
        m1.setEnabled(true);
        m1.setWeight(10);
        m1.setPriority(0);

        LlmModelEntity m2 = new LlmModelEntity();
        m2.setProviderId("p2");
        m2.setModelName("m2");
        m2.setEnabled(true);
        m2.setWeight(10);
        m2.setPriority(0);

        when(modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("POST_EMBEDDING")))
                .thenReturn(List.of(m1, m2));

        LlmRoutingService svc = new LlmRoutingService(modelRepo, policyRepo, queue);
        LlmRoutingService.RouteTarget picked = svc.pickNextInProvider(LlmQueueTaskType.POST_EMBEDDING, "p1", new HashSet<>());

        assertEquals("p1", picked.providerId());
        assertEquals("m1", picked.modelName());
    }

    @Test
    void pickNextInProvider_should_return_null_when_no_targets_in_provider() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmRoutingPolicyRepository policyRepo = mock(LlmRoutingPolicyRepository.class);
        LlmCallQueueService queue = mock(LlmCallQueueService.class);

        when(policyRepo.findById(any())).thenReturn(java.util.Optional.empty());
        when(queue.snapshot(anyInt(), anyInt(), anyInt()))
                .thenReturn(new LlmCallQueueService.QueueSnapshot(0, 0, 0, 0, List.of(), List.of(), List.of()));

        LlmModelEntity m1 = new LlmModelEntity();
        m1.setProviderId("p2");
        m1.setModelName("m2");
        m1.setEnabled(true);
        m1.setWeight(10);
        m1.setPriority(0);

        when(modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("POST_EMBEDDING")))
                .thenReturn(List.of(m1));

        LlmRoutingService svc = new LlmRoutingService(modelRepo, policyRepo, queue);
        LlmRoutingService.RouteTarget picked = svc.pickNextInProvider(LlmQueueTaskType.POST_EMBEDDING, "p1", new HashSet<>());

        assertNull(picked);
    }
}

