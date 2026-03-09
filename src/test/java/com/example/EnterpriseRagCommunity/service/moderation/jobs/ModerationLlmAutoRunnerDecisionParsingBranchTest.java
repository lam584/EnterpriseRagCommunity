package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationLlmAutoRunnerDecisionParsingBranchTest {

    @Test
    void verdictMethods_shouldCoverDecisionScoreAndStrictBranches() throws Exception {
        Method verdictFromScore = method("verdictFromScore", double.class, double.class, double.class);
        Method verdictFromDecisionAndScore = method("verdictFromDecisionAndScore", String.class, Double.class, double.class, double.class);
        Method stricterVerdict = method("stricterVerdict", Verdict.class, Verdict.class);

        assertEquals(Verdict.REJECT, verdictFromDecisionAndScore.invoke(null, "REJECT", 0.1d, 0.8d, 0.5d));
        assertEquals(Verdict.REVIEW, verdictFromDecisionAndScore.invoke(null, "human", 0.1d, 0.8d, 0.5d));
        assertEquals(Verdict.APPROVE, verdictFromDecisionAndScore.invoke(null, "APPROVE", null, 0.8d, 0.5d));
        assertEquals(Verdict.REJECT, verdictFromDecisionAndScore.invoke(null, "APPROVE", 0.95d, 0.8d, 0.5d));
        assertEquals(Verdict.REVIEW, verdictFromDecisionAndScore.invoke(null, "unknown", null, 0.8d, 0.5d));
        assertEquals(Verdict.APPROVE, verdictFromDecisionAndScore.invoke(null, "unknown", 0.1d, 0.8d, 0.5d));

        assertEquals(Verdict.REJECT, verdictFromScore.invoke(null, 0.4d, 0.3d, 0.8d));
        assertEquals(Verdict.REVIEW, stricterVerdict.invoke(null, Verdict.APPROVE, Verdict.REVIEW));
        assertEquals(Verdict.REJECT, stricterVerdict.invoke(null, Verdict.REVIEW, Verdict.REJECT));
    }

    @Test
    void rejectReasonAndNormalizeDecision_shouldCoverFallbackAndTagPaths() throws Exception {
        Method buildReason = method("buildUserFacingRejectReason", LlmModerationTestResponse.class, String.class);
        Method normalizeDecision = method("normalizeDecisionOrNull", String.class);

        assertEquals("Content violates policy", buildReason.invoke(null, null, " "));
        assertEquals(null, normalizeDecision.invoke(null, " "));
        assertEquals("APPROVE", normalizeDecision.invoke(null, " approve "));

        LlmModerationTestResponse byReasons = new LlmModerationTestResponse();
        byReasons.setReasons(List.of("  第一条原因  ", "", "第二条\n原因", "第三条", "第四条"));
        String reasonText = String.valueOf(buildReason.invoke(null, byReasons, "fallback"));
        assertTrue(reasonText.contains("第一条原因"));
        assertTrue(reasonText.contains("第二条 原因"));
        assertTrue(reasonText.length() <= 160);

        LlmModerationTestResponse byTags = new LlmModerationTestResponse();
        byTags.setRiskTags(List.of("暴力", "  ", "诈骗"));
        String tagText = String.valueOf(buildReason.invoke(null, byTags, "fallback"));
        assertTrue(tagText.startsWith("Matched tags: "));
        assertTrue(tagText.contains("暴力"));
    }

    @Test
    void thresholdParsers_shouldCoverValidAndInvalidBranches() throws Exception {
        Method asBooleanRequired = method("asBooleanRequired", Object.class, String.class);
        Method asDoubleRequired = method("asDoubleRequired", Object.class, String.class);
        Method asLongRequired = method("asLongRequired", Object.class, String.class);
        Method clamp01Strict = method("clamp01Strict", double.class);

        assertEquals(true, asBooleanRequired.invoke(null, "yes", "k1"));
        assertEquals(false, asBooleanRequired.invoke(null, 0, "k2"));
        assertEquals(1.5d, asDoubleRequired.invoke(null, "1.5", "k3"));
        assertEquals(12L, asLongRequired.invoke(null, "12", "k4"));
        assertEquals(0.0d, clamp01Strict.invoke(null, -5d));
        assertEquals(1.0d, clamp01Strict.invoke(null, 9d));

        assertIllegalState(() -> asBooleanRequired.invoke(null, "bad-bool", "k5"));
        assertIllegalState(() -> asDoubleRequired.invoke(null, "bad-double", "k6"));
        assertIllegalState(() -> asLongRequired.invoke(null, "bad-long", "k7"));
        assertIllegalState(() -> clamp01Strict.invoke(null, Double.NaN));
    }

    @Test
    void reviewStageAndIntersection_shouldCoverFallbackAndNormalization() throws Exception {
        Method resolveReviewStage = method("resolveReviewStage", ModerationQueueEntity.class);
        Method normalizeReviewStage = method("normalizeReviewStage", String.class);
        Method hasIntersection = method("hasIntersection", List.class, List.class);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.REPORT);
        assertEquals("reported", resolveReviewStage.invoke(null, q));
        q.setReviewStage(" APPEAL ");
        assertEquals("appeal", resolveReviewStage.invoke(null, q));

        assertEquals("default", normalizeReviewStage.invoke(null, " default "));
        assertEquals(null, normalizeReviewStage.invoke(null, "other"));

        assertEquals(true, hasIntersection.invoke(null, List.of(" a ", "b"), List.of("x", "a")));
        assertEquals(false, hasIntersection.invoke(null, List.of("a"), List.of("x")));
    }

    private static Method method(String name, Class<?>... types) throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

    private static void assertIllegalState(ThrowingCall call) {
        try {
            call.call();
            throw new AssertionError("expected IllegalStateException");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        } catch (Exception e) {
            throw new AssertionError("unexpected exception " + e.getClass().getName());
        }
    }

    private interface ThrowingCall {
        void call() throws Exception;
    }
}
