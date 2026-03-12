package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyId;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmRoutingPolicyRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LlmRoutingServiceTest {

    @Test
    void getPolicy_whenRepositoryThrows_returnsDefaultPolicy() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenThrow(new DataAccessResourceFailureException("db down"));

        LlmRoutingService.Policy p = fixture.service.getPolicy("TEXT_CHAT");
        assertEquals(LlmRoutingService.Strategy.WEIGHTED_RR, p.strategy());
        assertEquals(2, p.maxAttempts());
        assertEquals(2, p.failureThreshold());
        assertEquals(30_000, p.cooldownMs());
    }

    @Test
    void getPolicy_whenNotFound_returnsDefaultByTaskType() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.empty());

        LlmRoutingService.Policy moderation = fixture.service.getPolicy("MODERATION");
        LlmRoutingService.Policy textChat = fixture.service.getPolicy("TEXT_CHAT");

        assertEquals(LlmRoutingService.Strategy.PRIORITY_FALLBACK, moderation.strategy());
        assertEquals(LlmRoutingService.Strategy.WEIGHTED_RR, textChat.strategy());
    }

    @Test
    void getPolicy_whenInvalidAndOutOfRange_clampsAndFallsBack() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any()))
                .thenReturn(Optional.of(policy("default", "TEXT_CHAT", "bad_strategy", 999, 0, 9_999_999)));

        LlmRoutingService.Policy p = fixture.service.getPolicy("TEXT_CHAT");
        assertEquals(LlmRoutingService.Strategy.WEIGHTED_RR, p.strategy());
        assertEquals(10, p.maxAttempts());
        assertEquals(1, p.failureThreshold());
        assertEquals(3_600_000, p.cooldownMs());
    }

        @Test
        void getPolicy_whenFieldsAreNull_usesFallbackDefaults() {
                Fixture fixture = fixture();
                LlmRoutingPolicyEntity e = policy("default", "TEXT_CHAT", null, 2, 2, 30_000);
                e.setMaxAttempts(null);
                e.setFailureThreshold(null);
                e.setCooldownMs(null);
                when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(e));

                LlmRoutingService.Policy p = fixture.service.getPolicy("TEXT_CHAT");
                assertEquals(LlmRoutingService.Strategy.WEIGHTED_RR, p.strategy());
                assertEquals(2, p.maxAttempts());
                assertEquals(2, p.failureThreshold());
                assertEquals(30_000, p.cooldownMs());
        }

    @Test
    void listEnabledTargets_filtersNullAndBlankProviderModel() {
        Fixture fixture = fixture();
        List<LlmModelEntity> rows = new ArrayList<>();
        rows.add(null);
        rows.add(model("default", "", "MULTIMODAL_CHAT", "m0", true, 1, 0));
        rows.add(model("default", "p1", "MULTIMODAL_CHAT", " ", true, 1, 0));
        rows.add(model("default", "p2", "MULTIMODAL_CHAT", "m2", true, 3, 2));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("MULTIMODAL_CHAT")))
                .thenReturn(rows);

        List<LlmRoutingService.RouteTarget> out = fixture.service.listEnabledTargets("TEXT_CHAT");
        assertEquals(1, out.size());
        assertEquals("p2", out.get(0).providerId());
        assertEquals("m2", out.get(0).modelName());
    }

    @Test
    void listEnabledTargets_imageModeration_appliesImageChatAllowlist() {
        Fixture fixture = fixture();
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("MULTIMODAL_MODERATION")))
                .thenReturn(List.of(
                        model("default", "p1", "MULTIMODAL_MODERATION", "m1", true, 1, 0),
                        model("default", "p2", "MULTIMODAL_MODERATION", "m2", true, 1, 0)
                ));

        List<LlmRoutingService.RouteTarget> out = fixture.service.listEnabledTargets("IMAGE_MODERATION");
        assertEquals(2, out.size());
        assertEquals("p1", out.get(0).providerId());
        assertEquals("m1", out.get(0).modelName());
    }

    @Test
    void listEnabledTargets_imageModeration_allowlistSkipsNullAndBlankRows() {
        Fixture fixture = fixture();
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("MULTIMODAL_MODERATION")))
                .thenReturn(List.of(
                        model("default", "p1", "MULTIMODAL_MODERATION", "m1", true, 1, 0),
                        model("default", "p2", "MULTIMODAL_MODERATION", "m2", true, 1, 0)
                ));

        LlmModelEntity blankProvider = model("default", " ", "MULTIMODAL_MODERATION", "m1", true, 1, 0);
        LlmModelEntity blankModel = model("default", "p2", "MULTIMODAL_MODERATION", " ", true, 1, 0);
        LlmModelEntity allow = model("default", "p2", "MULTIMODAL_MODERATION", "m2", true, 1, 0);
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("MULTIMODAL_MODERATION")))
                .thenReturn(Arrays.asList(null, blankProvider, blankModel, allow));

        List<LlmRoutingService.RouteTarget> out = fixture.service.listEnabledTargets("IMAGE_MODERATION");
        assertEquals(1, out.size());
        assertEquals("p2", out.get(0).providerId());
        assertEquals("m2", out.get(0).modelName());
    }

    @Test
    void isEnabledTarget_imageModeration_requiresImageChatAlsoEnabled() {
        Fixture fixture = fixture();
        when(fixture.modelRepo.existsByEnvAndPurposeAndProviderIdAndModelNameAndEnabledTrue("default", "MULTIMODAL_MODERATION", "p1", "m1"))
                .thenReturn(false, true);

        boolean first = fixture.service.isEnabledTarget(LlmQueueTaskType.IMAGE_MODERATION, "p1", "m1");
        boolean second = fixture.service.isEnabledTarget(LlmQueueTaskType.IMAGE_MODERATION, "p1", "m1");

        assertFalse(first);
        assertTrue(second);
    }

        @Test
        void isEnabledTarget_whenPrimaryExistsFalse_shortCircuitsToFalse() {
                Fixture fixture = fixture();
                when(fixture.modelRepo.existsByEnvAndPurposeAndProviderIdAndModelNameAndEnabledTrue("default", "MULTIMODAL_CHAT", "p1", "m1"))
                                .thenReturn(false);

                boolean ok = fixture.service.isEnabledTarget(LlmQueueTaskType.TEXT_CHAT, "p1", "m1");
                assertFalse(ok);
                verify(fixture.modelRepo, times(1))
                                .existsByEnvAndPurposeAndProviderIdAndModelNameAndEnabledTrue("default", "MULTIMODAL_CHAT", "p1", "m1");
        }

        @Test
        void isEnabledTarget_whenProviderOrModelBlank_returnsFalse() {
                Fixture fixture = fixture();
                assertFalse(fixture.service.isEnabledTarget(LlmQueueTaskType.TEXT_CHAT, " ", "m1"));
                assertFalse(fixture.service.isEnabledTarget(LlmQueueTaskType.TEXT_CHAT, "p1", " "));
        }

    @Test
    void listEnabledTargets_chatAlias_usesTextChatPurpose() {
        Fixture fixture = fixture();
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("MULTIMODAL_CHAT")))
                .thenReturn(List.of(model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 1)));

        List<LlmRoutingService.RouteTarget> out = fixture.service.listEnabledTargets("CHAT");
        assertEquals(1, out.size());
        verify(fixture.modelRepo).findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT");
    }

    @Test
    void weightedRoundRobinDistributesByWeight() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 10, 2, 30_000)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("MULTIMODAL_CHAT")))
                .thenReturn(List.of(
                        model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 5, 0),
                        model("default", "p2", "MULTIMODAL_CHAT", "m2", true, 1, 0)
                ));

        int c1 = 0;
        int c2 = 0;
        for (int i = 0; i < 6; i++) {
            LlmRoutingService.RouteTarget t = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
            assertNotNull(t);
            if ("m1".equals(t.modelName())) c1++;
            if ("m2".equals(t.modelName())) c2++;
        }

        assertEquals(5, c1);
        assertEquals(1, c2);
    }

    @Test
    void priorityFallbackPicksHighestPriority() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "MODERATION", "PRIORITY_FALLBACK", 3, 2, 30_000)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("MULTIMODAL_MODERATION")))
                .thenReturn(List.of(
                        model("default", "p1", "MULTIMODAL_MODERATION", "m1", true, 1, 10),
                        model("default", "p2", "MULTIMODAL_MODERATION", "m2", true, 100, 5)
                ));

        LlmRoutingService.RouteTarget t = fixture.service.pickNext(LlmQueueTaskType.MODERATION, Set.of());
        assertNotNull(t);
        assertEquals("m1", t.modelName());
    }

    @Test
    void cooldownSkipsFailedTarget() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 60_000)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("MULTIMODAL_CHAT")))
                .thenReturn(List.of(
                        model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0),
                        model("default", "p2", "MULTIMODAL_CHAT", "m2", true, 1, 0)
                ));

        LlmRoutingService.RouteTarget first = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(first);
        fixture.service.recordFailure(LlmQueueTaskType.TEXT_CHAT, first);

        LlmRoutingService.RouteTarget next = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(next);
        assertTrue(!next.modelName().equals(first.modelName()) || !next.providerId().equals(first.providerId()));
    }

    @Test
    void pickNext_returnsNull_whenAllExcluded() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        LlmModelEntity only = model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0);
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(only));

        LlmRoutingService.RouteTarget t = fixture.service.pickNext(
                LlmQueueTaskType.TEXT_CHAT,
                Set.of(new LlmRoutingService.TargetId("p1", "m1"))
        );
        assertNull(t);
    }

    @Test
    void pickNextInProvider_whenProviderBlank_delegatesToPickNext() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0)));

        LlmRoutingService.RouteTarget direct = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        fixture.service.resetRuntimeState();
        LlmRoutingService.RouteTarget delegated = fixture.service.pickNextInProvider(LlmQueueTaskType.TEXT_CHAT, "  ", Set.of());

        assertNotNull(direct);
        assertNotNull(delegated);
        assertEquals(direct.providerId(), delegated.providerId());
        assertEquals(direct.modelName(), delegated.modelName());
    }

    @Test
    void pickNextInProvider_returnsNull_whenNoTargetsInProvider() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0)));

        LlmRoutingService.RouteTarget t = fixture.service.pickNextInProvider(LlmQueueTaskType.TEXT_CHAT, "p2", Set.of());
        assertNull(t);
    }

    @Test
    void pickNextInProvider_strictRateEmpty_fallsBackToNonStrict() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        LlmModelEntity a = model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 10, 0);
        LlmModelEntity b = model("default", "p1", "MULTIMODAL_CHAT", "m2", true, 1, 0);
        a.setQps(0.1);
        b.setQps(0.1);
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(a, b));

        LlmRoutingService.RouteTarget t = fixture.service.pickNextInProvider(LlmQueueTaskType.TEXT_CHAT, "p1", Set.of());
        assertNotNull(t);
        assertEquals("p1", t.providerId());
    }

    @Test
    void pickNext_strictRateEmpty_fallsBackToNonStrict() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        LlmModelEntity a = model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 10, 0);
        LlmModelEntity b = model("default", "p2", "MULTIMODAL_CHAT", "m2", true, 1, 0);
        a.setQps(0.1);
        b.setQps(0.1);
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(a, b));

        LlmRoutingService.RouteTarget t = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(t);
    }

    @Test
    void pickNext_whenReserveFailsOnBest_triesNextCandidate() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        LlmModelEntity first = model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 100, 0);
        LlmModelEntity second = model("default", "p2", "MULTIMODAL_CHAT", "m2", true, 1, 0);
        first.setQps(0.1);
        second.setQps(null);
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(first, second));

        LlmRoutingService.RouteTarget t = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(t);
        assertEquals("m2", t.modelName());
    }

    @Test
    void pickNextInProvider_whenSingleEligibleReserveFails_returnsThatBestTarget() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        LlmModelEntity only = model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0);
        only.setQps(0.1);
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(only));

        LlmRoutingService.RouteTarget t = fixture.service.pickNextInProvider(LlmQueueTaskType.TEXT_CHAT, "p1", Set.of());
        assertNotNull(t);
        assertEquals("p1", t.providerId());
        assertEquals("m1", t.modelName());
    }

    @Test
    void pickNextInProvider_whenReserveFailsOnBest_triesNextCandidate() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        LlmModelEntity first = model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 100, 0);
        LlmModelEntity second = model("default", "p1", "MULTIMODAL_CHAT", "m2", true, 1, 0);
        first.setQps(0.1);
        second.setQps(null);
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(first, second));

        LlmRoutingService.RouteTarget t = fixture.service.pickNextInProvider(LlmQueueTaskType.TEXT_CHAT, "p1", Set.of());
        assertNotNull(t);
        assertEquals("m2", t.modelName());
    }

    @Test
    void pickNextInProvider_priorityFallback_tieBreaksByWeightAndLexicalOrder() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "MODERATION", "PRIORITY_FALLBACK", 3, 1, 30_000)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_MODERATION"))
                .thenReturn(List.of(
                        model("default", "p1", "MULTIMODAL_MODERATION", "m2", true, 5, 10),
                        model("default", "p1", "MULTIMODAL_MODERATION", "m1", true, 5, 10),
                        model("default", "p1", "MULTIMODAL_MODERATION", "m0", true, 4, 10)
                ));

        LlmRoutingService.RouteTarget t = fixture.service.pickNextInProvider(LlmQueueTaskType.MODERATION, "p1", Set.of());
        assertNotNull(t);
        assertEquals("m1", t.modelName());
    }

        @Test
        void pickNext_whenSingleEligibleReserveFails_returnsThatBestTarget() {
                Fixture fixture = fixture();
                when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
                LlmModelEntity only = model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0);
                only.setQps(0.1);
                when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                                .thenReturn(List.of(only));

                LlmRoutingService.RouteTarget t = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
                assertNotNull(t);
                assertEquals("m1", t.modelName());
        }

        @Test
        void weightedRoundRobin_whenWeightsNonPositive_stillSelectsCandidates() {
                Fixture fixture = fixture();
                when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 10, 2, 30_000)));
                when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default"), eq("MULTIMODAL_CHAT")))
                                .thenReturn(List.of(
                                                model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 0, 0),
                                                model("default", "p2", "MULTIMODAL_CHAT", "m2", true, 0, 0)
                                ));

                LlmRoutingService.RouteTarget t1 = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
                LlmRoutingService.RouteTarget t2 = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());

                assertNotNull(t1);
                assertNotNull(t2);
        }

    @Test
    void pickNext_withNonPositiveQps_bypassesRateGate() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        LlmModelEntity m = model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0);
        m.setQps(0.0);
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(m));

        for (int i = 0; i < 3; i++) {
            LlmRoutingService.RouteTarget t = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
            assertNotNull(t);
            assertEquals("m1", t.modelName());
        }
    }

    @Test
    void recordFailure_429_forcesCooldownWithinBounds() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 5, 60_000)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0)));

        LlmRoutingService.RouteTarget t = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(t);
        long nowBefore = System.currentTimeMillis();
        fixture.service.recordFailure(LlmQueueTaskType.TEXT_CHAT, t, "429");
        long nowAfter = System.currentTimeMillis();

        LlmRoutingService.RuntimeSnapshot snapshot = fixture.service.snapshot(LlmQueueTaskType.TEXT_CHAT);
        assertEquals(1, snapshot.items().size());
        long cooldownUntil = snapshot.items().get(0).cooldownUntilMs();
        assertTrue(cooldownUntil >= nowBefore + 500);
        assertTrue(cooldownUntil <= nowAfter + 15_000);
    }

    @Test
    void recordFailure_429_whenPolicyCooldownTooSmall_stillUses500msFloor() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 5, 100)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0)));

        LlmRoutingService.RouteTarget t = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(t);
        long nowBefore = System.currentTimeMillis();
        fixture.service.recordFailure(LlmQueueTaskType.TEXT_CHAT, t, "429");

        LlmRoutingService.RuntimeSnapshot snapshot = fixture.service.snapshot(LlmQueueTaskType.TEXT_CHAT);
        long cooldownUntil = snapshot.items().get(0).cooldownUntilMs();
        assertTrue(cooldownUntil >= nowBefore + 500);
    }

    @Test
    void recordFailure_capsConsecutiveFailuresAt10000() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 20_000, 0)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0)));

        LlmRoutingService.RouteTarget t = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(t);
        for (int i = 0; i < 11_000; i++) {
            fixture.service.recordFailure(LlmQueueTaskType.TEXT_CHAT, t, "500");
        }

        LlmRoutingService.RuntimeSnapshot snapshot = fixture.service.snapshot(LlmQueueTaskType.TEXT_CHAT);
        assertEquals(10_000, snapshot.items().get(0).consecutiveFailures());
    }

    @Test
    void recordSuccess_clearsFailuresAndCooldown() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0)));

        LlmRoutingService.RouteTarget t = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(t);
        fixture.service.recordFailure(LlmQueueTaskType.TEXT_CHAT, t);
        fixture.service.recordSuccess(LlmQueueTaskType.TEXT_CHAT, t);

        LlmRoutingService.RuntimeSnapshot snapshot = fixture.service.snapshot(LlmQueueTaskType.TEXT_CHAT);
        assertEquals(0, snapshot.items().get(0).consecutiveFailures());
        assertEquals(0L, snapshot.items().get(0).cooldownUntilMs());
    }

        @Test
        void recordFailure_whenThresholdReached_setsCooldown() {
                Fixture fixture = fixture();
                when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 10_000)));
                when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                                .thenReturn(List.of(model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0)));

                LlmRoutingService.RouteTarget target = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
                assertNotNull(target);

                long before = System.currentTimeMillis();
                fixture.service.recordFailure(LlmQueueTaskType.TEXT_CHAT, target, "500");

                LlmRoutingService.RuntimeSnapshot snapshot = fixture.service.snapshot(LlmQueueTaskType.TEXT_CHAT);
                assertEquals(1, snapshot.items().get(0).consecutiveFailures());
                assertTrue(snapshot.items().get(0).cooldownUntilMs() >= before);

                LlmRoutingService.RouteTarget duringCooldown = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
                assertNull(duringCooldown);
        }

    @Test
    void snapshot_filtersInvalidTargets_andSortsDeterministically() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        LlmModelEntity bad = model("default", " ", "MULTIMODAL_CHAT", "m0", true, 1, 0);
        LlmModelEntity c = model("default", "z-provider", "MULTIMODAL_CHAT", "z-model", true, 1, 0);
        LlmModelEntity a = model("default", "a-provider", "MULTIMODAL_CHAT", "a-model", true, 1, 0);
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(bad, c, a));

        LlmRoutingService.RuntimeSnapshot snapshot = fixture.service.snapshot(LlmQueueTaskType.TEXT_CHAT);
        assertEquals(2, snapshot.items().size());
        assertEquals("a-provider", snapshot.items().get(0).providerId());
        assertEquals("a-model", snapshot.items().get(0).modelName());
        assertEquals("z-provider", snapshot.items().get(1).providerId());
    }

        @Test
        void snapshot_aggregatesRunningCountByProviderModel() {
                Fixture fixture = fixture();
                when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
                when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                                .thenReturn(List.of(model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0)));

                LlmCallQueueService.TaskSnapshot t1 = mock(LlmCallQueueService.TaskSnapshot.class);
                LlmCallQueueService.TaskSnapshot t2 = mock(LlmCallQueueService.TaskSnapshot.class);
                LlmCallQueueService.TaskSnapshot invalid = mock(LlmCallQueueService.TaskSnapshot.class);
                when(t1.getProviderId()).thenReturn("p1");
                when(t1.getModel()).thenReturn("m1");
                when(t2.getProviderId()).thenReturn("p1");
                when(t2.getModel()).thenReturn("m1");
                when(invalid.getProviderId()).thenReturn(" ");
                when(invalid.getModel()).thenReturn("m1");
                when(fixture.queueService.snapshot(anyInt(), anyInt(), anyInt()))
                                .thenReturn(new LlmCallQueueService.QueueSnapshot(0, 0, 3, 0, List.of(t1, t2, invalid), List.of(), List.of()));

                LlmRoutingService.RuntimeSnapshot snapshot = fixture.service.snapshot(LlmQueueTaskType.TEXT_CHAT);
                assertEquals(1, snapshot.items().size());
                assertEquals(2, snapshot.items().get(0).runningCount());
        }

    @Test
    void resetRuntimeState_clearsCooldownAndAllowsSelectionAgain() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 60_000)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0)));

        LlmRoutingService.RouteTarget first = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(first);
        fixture.service.recordFailure(LlmQueueTaskType.TEXT_CHAT, first);
        LlmRoutingService.RouteTarget duringCooldown = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNull(duringCooldown);

        fixture.service.resetRuntimeState();
        LlmRoutingService.RouteTarget afterReset = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(afterReset);
        assertEquals("m1", afterReset.modelName());
    }

    @Test
    void snapshot_reflectsRateTokensAndLastRefillAfterDispatch() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        LlmModelEntity m = model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0);
        m.setQps(2.0);
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(m));

                LlmRoutingService.RouteTarget warmup = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
                assertNotNull(warmup);
                try {
                        Thread.sleep(600L);
                } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                }
                LlmRoutingService.RouteTarget first = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        LlmRoutingService.RuntimeSnapshot snap = fixture.service.snapshot(LlmQueueTaskType.TEXT_CHAT);

        assertNotNull(first);
        assertEquals(1, snap.items().size());
        assertTrue(snap.items().get(0).lastRefillAtMs() > 0);
        assertTrue(snap.items().get(0).lastDispatchAtMs() > 0);
        assertTrue(snap.items().get(0).rateTokens() >= 0.0);
    }

        @Test
        void pickNext_rateRefill_allowsDispatchAfterElapsedTime() {
                Fixture fixture = fixture();
                when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
                LlmModelEntity m = model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0);
                m.setQps(1.0);
                when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                                .thenReturn(List.of(m));

                LlmRoutingService.RouteTarget first = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
                assertNotNull(first);

                try {
                        Thread.sleep(1100L);
                } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                }

                LlmRoutingService.RouteTarget second = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
                assertNotNull(second);

                LlmRoutingService.RuntimeSnapshot snapshot = fixture.service.snapshot(LlmQueueTaskType.TEXT_CHAT);
                assertEquals(1, snapshot.items().size());
                assertTrue(snapshot.items().get(0).lastRefillAtMs() > 0L);
        }

        @Test
        void snapshot_whenTaskTypeNull_usesUnknownPath() {
                Fixture fixture = fixture();
                when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "UNKNOWN", "WEIGHTED_RR", 3, 1, 30_000)));
                when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "UNKNOWN"))
                                .thenReturn(List.of(model("default", "p1", "UNKNOWN", "m1", true, 1, 0)));

                LlmRoutingService.RuntimeSnapshot snapshot = fixture.service.snapshot(null);
                assertEquals(1, snapshot.items().size());
                assertEquals("UNKNOWN", snapshot.items().get(0).taskType());
        }

    @Test
    void recordFailure_andRecordSuccess_ignoreNullInputs() {
        Fixture fixture = fixture();
        fixture.service.recordFailure(null, null);
        fixture.service.recordFailure(LlmQueueTaskType.TEXT_CHAT, null);
        fixture.service.recordSuccess(null, null);
        fixture.service.recordSuccess(LlmQueueTaskType.TEXT_CHAT, null);

        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 1, 0)));

        LlmRoutingService.RouteTarget t = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(t);
    }

    @Test
    void pickNext_skipsRunningHotModel_underStrictRateThenPicksAnother() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));

        LlmModelEntity m1 = model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 5, 0);
        LlmModelEntity m2 = model("default", "p2", "MULTIMODAL_CHAT", "m2", true, 1, 0);
        m1.setQps(0.1);
        m2.setQps(null);
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(m1, m2));

        List<LlmCallQueueService.TaskSnapshot> running = new ArrayList<>();
        LlmCallQueueService.TaskSnapshot task = mock(LlmCallQueueService.TaskSnapshot.class);
        when(task.getProviderId()).thenReturn("p1");
        when(task.getModel()).thenReturn("m1");
        running.add(task);
        when(fixture.queueService.snapshot(anyInt(), anyInt(), anyInt()))
                .thenReturn(new LlmCallQueueService.QueueSnapshot(0, 0, 1, 0, running, List.of(), List.of()));

        LlmRoutingService.RouteTarget t = fixture.service.pickNext(LlmQueueTaskType.TEXT_CHAT, Set.of());
        assertNotNull(t);
        assertEquals("m2", t.modelName());
    }

    @Test
    void pickNext_excludeCanForceAlternativeModel() {
        Fixture fixture = fixture();
        when(fixture.policyRepo.findById(any())).thenReturn(Optional.of(policy("default", "TEXT_CHAT", "WEIGHTED_RR", 3, 1, 30_000)));
        when(fixture.modelRepo.findByEnvAndPurposeAndEnabledTrueOrderBySortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc("default", "MULTIMODAL_CHAT"))
                .thenReturn(List.of(
                        model("default", "p1", "MULTIMODAL_CHAT", "m1", true, 10, 0),
                        model("default", "p2", "MULTIMODAL_CHAT", "m2", true, 1, 0)
                ));

        LlmRoutingService.RouteTarget t = fixture.service.pickNext(
                LlmQueueTaskType.TEXT_CHAT,
                Set.of(new LlmRoutingService.TargetId("p1", "m1"))
        );

        assertNotNull(t);
        assertEquals("m2", t.modelName());
        assertNotEquals("m1", t.modelName());
    }

    private static Fixture fixture() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmRoutingPolicyRepository policyRepo = mock(LlmRoutingPolicyRepository.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        when(llmCallQueueService.snapshot(anyInt(), anyInt(), anyInt()))
                .thenReturn(new LlmCallQueueService.QueueSnapshot(0, 0, 0, 0, List.of(), List.of(), List.of()));
        LlmRoutingService service = new LlmRoutingService(modelRepo, policyRepo, llmCallQueueService);
        return new Fixture(modelRepo, policyRepo, llmCallQueueService, service);
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
