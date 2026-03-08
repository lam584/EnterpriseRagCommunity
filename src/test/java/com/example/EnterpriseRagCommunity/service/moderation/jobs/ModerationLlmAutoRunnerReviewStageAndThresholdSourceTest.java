package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationLlmAutoRunnerReviewStageAndThresholdSourceTest {

    @Test
    void resolveReviewStage_prefersQueueFieldAppeal() throws Exception {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.REPORT);
        q.setReviewStage("appeal");

        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("resolveReviewStage", ModerationQueueEntity.class);
        m.setAccessible(true);
        String reviewStage = (String) m.invoke(null, q);

        assertEquals("appeal", reviewStage);
    }

    @Test
    void resolveReviewStage_fallsBackToReportedForReportCase() throws Exception {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.REPORT);
        q.setReviewStage(null);

        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("resolveReviewStage", ModerationQueueEntity.class);
        m.setAccessible(true);
        String reviewStage = (String) m.invoke(null, q);

        assertEquals("reported", reviewStage);
    }

    @Test
    void resolveThresholdsRequired_marksByReviewStageSource() throws Exception {
        Map<String, Object> policyConfig = Map.of(
                "thresholds", Map.of(
                        "default", Map.of("T_allow", 0.2, "T_reject", 0.8),
                        "by_review_stage", Map.of(
                                "appeal", Map.of("T_allow", 0.3, "T_reject", 0.85)
                        )
                )
        );

        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("resolveThresholdsRequired", Map.class, String.class, List.class);
        m.setAccessible(true);
        Object th = m.invoke(null, policyConfig, "appeal", List.of());

        Method source = th.getClass().getDeclaredMethod("source");
        source.setAccessible(true);
        assertEquals("policy.by_review_stage", source.invoke(th));
    }

    @Test
    void resolveThresholdsPreferred_marksFallbackSourceWhenPolicyUnavailable() throws Exception {
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setChunkLlmHumanThreshold(0.45);
        fb.setChunkLlmRejectThreshold(0.77);

        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(
                "resolveThresholdsPreferred",
                Map.class,
                String.class,
                List.class,
                ModerationConfidenceFallbackConfigEntity.class
        );
        m.setAccessible(true);
        Object th = m.invoke(null, Map.of(), null, List.of(), fb);

        Method source = th.getClass().getDeclaredMethod("source");
        source.setAccessible(true);
        assertEquals("fallback.chunk_config", source.invoke(th));
    }
}
