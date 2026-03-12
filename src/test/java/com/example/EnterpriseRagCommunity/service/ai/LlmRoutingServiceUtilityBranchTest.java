package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyId;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmRoutingPolicyRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmRoutingServiceUtilityBranchTest {

    @Test
    void getPolicy_taskTypeOverload_andClampLowerBounds_shouldWork() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any()))
                .thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 0, 30, -1)));

        LlmRoutingService.Policy policy = fixture.service.getPolicy(LlmQueueTaskType.TEXT_CHAT);
        assertEquals(1, policy.maxAttempts());
        assertEquals(20, policy.failureThreshold());
        assertEquals(0, policy.cooldownMs());
    }

    @Test
    void getPolicy_whenTextModerationAndImageModerationMissing_shouldUsePriorityFallback() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.empty());

        LlmRoutingService.Policy p1 = fixture.service.getPolicy("TEXT_MODERATION");
        LlmRoutingService.Policy p2 = fixture.service.getPolicy("IMAGE_MODERATION");
        assertEquals(LlmRoutingService.Strategy.PRIORITY_FALLBACK, p1.strategy());
        assertEquals(LlmRoutingService.Strategy.PRIORITY_FALLBACK, p2.strategy());
    }

    @Test
    void listEnabledTargets_taskTypeOverload_shouldMapNullWeightAndPriorityToZero() {
        Fixture fixture = fixture();
        LlmModelEntity model = model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 1);
        model.setWeight(null);
        model.setPriority(null);
        model.setQps(null);
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("MULTIMODAL_CHAT")))
                .thenReturn(List.of(model));

        List<LlmRoutingService.RouteTarget> out = fixture.service.listEnabledTargets(LlmQueueTaskType.TEXT_CHAT);
        assertEquals(1, out.size());
        assertEquals(0, out.get(0).weight());
        assertEquals(0, out.get(0).priority());
        assertNull(out.get(0).qps());
    }

    @Test
    void pickNext_whenAllRowsInvalid_shouldReturnNull() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        LlmModelEntity bad = model("default", " ", "MULTIMODAL_CHAT", " ", true, 1, 0);
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(Arrays.asList(null, bad));

        LlmRoutingService.RouteTarget target = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNull(target);
    }

    @Test
    void snapshot_runningListContainingNull_shouldSkipNullAndCountValid() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
            .thenReturn(List.of(model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0)));

        LlmCallQueueService.TaskSnapshot valid = mock(LlmCallQueueService.TaskSnapshot.class);
        when(valid.getProviderId()).thenReturn("p1");
        when(valid.getModel()).thenReturn("m1");
        when(fixture.queueService.snapshot(anyInt(), anyInt(), anyInt()))
                .thenReturn(new LlmCallQueueService.QueueSnapshot(0, 0, 2, 0, Arrays.asList(null, valid), List.of(), List.of()));

        LlmRoutingService.RuntimeSnapshot snapshot = fixture.service.snapshot(LlmQueueTaskType.TEXT_CHAT);
        assertEquals(1, snapshot.items().size());
        assertEquals(1, snapshot.items().get(0).runningCount());
    }

    @Test
    void recordFailure_whenBelowThresholdAndNot429_shouldNotEnterCooldown() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 3, 30_000)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
            .thenReturn(List.of(model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0)));

        LlmRoutingService.RouteTarget target = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(target);
        fixture.service.recordFailure(LlmQueueTaskType.TEXT_CHAT, target, "500");

        LlmRoutingService.RuntimeSnapshot snapshot = fixture.service.snapshot(LlmQueueTaskType.TEXT_CHAT);
        assertEquals(1, snapshot.items().get(0).consecutiveFailures());
        assertEquals(0L, snapshot.items().get(0).cooldownUntilMs());
    }

    @Test
    void privatePickMethods_shouldHandleEmptyInputs() throws Exception {
        Fixture fixture = fixture();

        Method pickPriorityFallback = LlmRoutingService.class.getDeclaredMethod("pickPriorityFallback", List.class);
        pickPriorityFallback.setAccessible(true);
        Object priorityResult = pickPriorityFallback.invoke(null, List.of());
        assertNull(priorityResult);

        Method pickWeightedRoundRobin = LlmRoutingService.class
                .getDeclaredMethod("pickWeightedRoundRobin", LlmQueueTaskType.class, List.class);
        pickWeightedRoundRobin.setAccessible(true);
        Object weightedResult = pickWeightedRoundRobin.invoke(fixture.service, LlmQueueTaskType.TEXT_CHAT, List.of());
        assertNull(weightedResult);
    }

    @Test
    void isEnabledTarget_withNullTaskType_shouldUseUnknownPurpose() {
        Fixture fixture = fixture();
        when(fixture.modelRepo.existsByEnvAndPurposeAndProviderIdAndModelNameAndEnabledTrue("default", "UNKNOWN", "p1", "m1"))
                .thenReturn(false);

        boolean ok = fixture.service.isEnabledTarget(null, "p1", "m1");
        assertFalse(ok);
    }

    private static Fixture fixture() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmRoutingPolicyRepository policyRepo = mock(LlmRoutingPolicyRepository.class);
        LlmCallQueueService queueService = mock(LlmCallQueueService.class);
        when(queueService.snapshot(anyInt(), anyInt(), anyInt()))
                .thenReturn(new LlmCallQueueService.QueueSnapshot(0, 0, 0, 0, List.of(), List.of(), List.of()));
        LlmRoutingService service = new LlmRoutingService(modelRepo, policyRepo, queueService);
        return new Fixture(modelRepo, policyRepo, queueService, service);
    }

    private record Fixture(
            LlmModelRepository modelRepo,
            LlmRoutingPolicyRepository policyRepo,
            LlmCallQueueService queueService,
            LlmRoutingService service
    ) {
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
