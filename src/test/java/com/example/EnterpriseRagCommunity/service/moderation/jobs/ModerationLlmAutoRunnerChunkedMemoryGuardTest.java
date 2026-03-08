package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationLlmAutoRunnerChunkedMemoryGuardTest {

    private static Verdict guard(Verdict v, Map<String, Object> mem, ModerationConfidenceFallbackConfigEntity fb) throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(
                "guardChunkedAggregateByMemory",
                Verdict.class,
                Map.class,
                ModerationConfidenceFallbackConfigEntity.class,
                Map.class,
                String.class,
                List.class
        );
        m.setAccessible(true);
        return (Verdict) m.invoke(null, v, mem, fb, null, null, null);
    }

    @Test
    void guardUpgradesApproveToReview_whenMemoryMaxScoreAtLeastHumanThreshold() throws Exception {
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setChunkLlmHumanThreshold(0.5);

        Map<String, Object> mem = Map.of("maxScore", 0.9);

        assertEquals(Verdict.REVIEW, guard(Verdict.APPROVE, mem, fb));
    }

    @Test
    void guardKeepsApprove_whenMemoryMaxScoreBelowHumanThreshold() throws Exception {
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setChunkLlmHumanThreshold(0.5);

        Map<String, Object> mem = Map.of("maxScore", 0.2);

        assertEquals(Verdict.APPROVE, guard(Verdict.APPROVE, mem, fb));
    }

    @Test
    void guardDoesNotChangeRejectOrReview() throws Exception {
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setChunkLlmHumanThreshold(0.5);

        Map<String, Object> mem = Map.of("maxScore", 0.9);

        assertEquals(Verdict.REJECT, guard(Verdict.REJECT, mem, fb));
        assertEquals(Verdict.REVIEW, guard(Verdict.REVIEW, mem, fb));
    }
}
