package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyId;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmRoutingPolicyRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LlmRoutingServiceTest {

    @Test
    void weightedRoundRobinDistributesByWeight() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmRoutingPolicyRepository policyRepo = mock(LlmRoutingPolicyRepository.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);

        when(llmCallQueueService.snapshot(anyInt(), anyInt(), anyInt()))
                .thenReturn(new LlmCallQueueService.QueueSnapshot(0, 0, 0, 0, List.of(), List.of(), List.of()));
        when(policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 10, 2, 30_000)));
        when(modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("TEXT_CHAT")))
                .thenReturn(List.of(
                        model("default", "p1", "TEXT_CHAT", "m1", true, 5, 0),
                        model("default", "p2", "TEXT_CHAT", "m2", true, 1, 0)
                ));

        LlmRoutingService svc = new LlmRoutingService(modelRepo, policyRepo, llmCallQueueService);

        int c1 = 0;
        int c2 = 0;
        for (int i = 0; i < 6; i++) {
            LlmRoutingService.RouteTarget t = svc.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
            assertNotNull(t);
            if ("m1".equals(t.modelName())) c1++;
            if ("m2".equals(t.modelName())) c2++;
        }

        assertEquals(5, c1);
        assertEquals(1, c2);
    }

    @Test
    void priorityFallbackPicksHighestPriority() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmRoutingPolicyRepository policyRepo = mock(LlmRoutingPolicyRepository.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);

        when(llmCallQueueService.snapshot(anyInt(), anyInt(), anyInt()))
                .thenReturn(new LlmCallQueueService.QueueSnapshot(0, 0, 0, 0, List.of(), List.of(), List.of()));
        when(policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "MODERATION", "PRIORITY_FALLBACK", 3, 2, 30_000)));
        when(modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("MODERATION")))
                .thenReturn(List.of(
                        model("default", "p1", "MODERATION", "m1", true, 1, 10),
                        model("default", "p2", "MODERATION", "m2", true, 100, 5)
                ));

        LlmRoutingService svc = new LlmRoutingService(modelRepo, policyRepo, llmCallQueueService);

        LlmRoutingService.RouteTarget t = svc.pickNext(LlmQueueTaskType.MODERATION, Set.of());
        assertNotNull(t);
        assertEquals("m1", t.modelName());
    }

    @Test
    void cooldownSkipsFailedTarget() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmRoutingPolicyRepository policyRepo = mock(LlmRoutingPolicyRepository.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);

        when(llmCallQueueService.snapshot(anyInt(), anyInt(), anyInt()))
                .thenReturn(new LlmCallQueueService.QueueSnapshot(0, 0, 0, 0, List.of(), List.of(), List.of()));
        when(policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 60_000)));
        when(modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("TEXT_CHAT")))
                .thenReturn(List.of(
                        model("default", "p1", "TEXT_CHAT", "m1", true, 1, 0),
                        model("default", "p2", "TEXT_CHAT", "m2", true, 1, 0)
                ));

        LlmRoutingService svc = new LlmRoutingService(modelRepo, policyRepo, llmCallQueueService);

        LlmRoutingService.RouteTarget first = svc.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(first);
        svc.recordFailure(LlmQueueTaskType.TEXT_CHAT, first);

        LlmRoutingService.RouteTarget next = svc.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(next);
        assertTrue(!next.modelName().equals(first.modelName()) || !next.providerId().equals(first.providerId()));
    }

    private static LlmRoutingPolicyEntity policy(String env, String taskType, String strategy, int maxAttempts, int failureThreshold, int cooldownMs) {
        LlmRoutingPolicyEntity e = new LlmRoutingPolicyEntity();
        e.setId(new LlmRoutingPolicyId(env, taskType));
        e.setStrategy(strategy);
        e.setMaxAttempts(maxAttempts);
        e.setFailureThreshold(failureThreshold);
        e.setCooldownMs(cooldownMs);
        e.setProbeEnabled(Boolean.FALSE);
        e.setProbeIntervalMs(null);
        e.setProbePath(null);
        e.setUpdatedBy(null);
        e.setUpdatedAt(null);
        return e;
    }

    private static LlmModelEntity model(String env, String providerId, String purpose, String modelName, boolean enabled, int weight, int priority) {
        LlmModelEntity e = new LlmModelEntity();
        e.setEnv(env);
        e.setProviderId(providerId);
        e.setPurpose(purpose);
        e.setModelName(modelName);
        e.setEnabled(enabled);
        e.setIsDefault(Boolean.FALSE);
        e.setWeight(weight);
        e.setPriority(priority);
        e.setSortIndex(0);
        return e;
    }
}
