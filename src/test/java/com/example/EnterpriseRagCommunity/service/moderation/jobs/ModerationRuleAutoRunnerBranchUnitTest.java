package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationRulesEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.RuleType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationRuleHitsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationRulesRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.trace.ModerationPipelineTraceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationRuleAutoRunnerBranchUnitTest {

    private ModerationQueueRepository queueRepository;
    private ModerationRulesRepository rulesRepository;
    private ModerationRuleHitsRepository ruleHitsRepository;
    private CommentsRepository commentsRepository;
    private ModerationActionsRepository moderationActionsRepository;
    private ModerationConfidenceFallbackConfigRepository fallbackRepository;
    private ModerationPolicyConfigRepository policyConfigRepository;
    private AdminModerationQueueService queueService;
    private ModerationContentTextLoader textLoader;
    private ModerationPipelineTraceService pipelineTraceService;
    private AuditLogWriter auditLogWriter;
    private ModerationRuleAutoRunner runner;

    @BeforeEach
    void setUp() {
        queueRepository = mock(ModerationQueueRepository.class);
        rulesRepository = mock(ModerationRulesRepository.class);
        ruleHitsRepository = mock(ModerationRuleHitsRepository.class);
        commentsRepository = mock(CommentsRepository.class);
        moderationActionsRepository = mock(ModerationActionsRepository.class);
        fallbackRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        queueService = mock(AdminModerationQueueService.class);
        textLoader = mock(ModerationContentTextLoader.class);
        pipelineTraceService = mock(ModerationPipelineTraceService.class);
        auditLogWriter = mock(AuditLogWriter.class);
        runner = new ModerationRuleAutoRunner(
                queueRepository,
                rulesRepository,
                ruleHitsRepository,
                commentsRepository,
                moderationActionsRepository,
                fallbackRepository,
                policyConfigRepository,
                queueService,
                textLoader,
                pipelineTraceService,
                auditLogWriter
        );
    }

    @Test
    void privateStaticHelpers_should_cover_mapping_and_parser_branches() throws Exception {
        assertEquals(QueueStage.HUMAN, invokeStatic("mapNextStage", new Class[]{String.class}, (Object) null));
        assertEquals(QueueStage.LLM, invokeStatic("mapNextStage", new Class[]{String.class}, "llm"));
        assertEquals(QueueStage.VEC, invokeStatic("mapNextStage", new Class[]{String.class}, "VEC"));
        assertEquals(QueueStage.HUMAN, invokeStatic("mapNextStage", new Class[]{String.class}, "reject"));
        assertEquals(QueueStage.HUMAN, invokeStatic("mapNextStage", new Class[]{String.class}, "unknown"));

        assertNull(invokeStatic("normalizeAction", new Class[]{String.class}, (Object) null));
        assertNull(invokeStatic("normalizeAction", new Class[]{String.class}, "  "));
        assertEquals("HUMAN", invokeStatic("normalizeAction", new Class[]{String.class}, " human "));

        assertEquals("x", invokeStatic("firstNonBlank", new Class[]{String.class, String.class}, "x", "y"));
        assertEquals("y", invokeStatic("firstNonBlank", new Class[]{String.class, String.class}, " ", "y"));
        assertNull(invokeStatic("firstNonBlank", new Class[]{String.class, String.class}, " ", null));

        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("a", Map.of("b", Map.of("c", "true", "d", "false", "e", "  ", "f", "abc", "n", 7)));
        assertEquals(Boolean.TRUE, invokeStatic("deepGetBool", new Class[]{Map.class, String.class}, nested, "a.b.c"));
        assertEquals(Boolean.FALSE, invokeStatic("deepGetBool", new Class[]{Map.class, String.class}, nested, "a.b.d"));
        assertNull(invokeStatic("deepGetBool", new Class[]{Map.class, String.class}, nested, "a.b.e"));
        assertNull(invokeStatic("deepGetBool", new Class[]{Map.class, String.class}, nested, "a.b.f"));
        assertEquals("true", invokeStatic("deepGetString", new Class[]{Map.class, String.class}, nested, "a.b.c"));
        assertNull(invokeStatic("deepGetString", new Class[]{Map.class, String.class}, nested, "a.b.none"));
        assertEquals(7, invokeStatic("deepGetInt", new Class[]{Map.class, String.class}, nested, "a.b.n"));
        assertEquals(8, invokeStatic("deepGetInt", new Class[]{Map.class, String.class}, Map.of("x", "8"), "x"));
        assertNull(invokeStatic("deepGetInt", new Class[]{Map.class, String.class}, Map.of("x", "bad"), "x"));
        assertNull(invokeStatic("deepGet", new Class[]{Map.class, String.class}, null, "a.b"));
        assertNull(invokeStatic("deepGet", new Class[]{Map.class, String.class}, nested, ""));
    }

    @Test
    void runForQueueId_should_cover_guard_branches() {
        runner.runForQueueId(null);
        verifyNoInteractions(queueRepository);

        when(queueRepository.findById(1L)).thenThrow(new RuntimeException("x"));
        runner.runForQueueId(1L);

        when(queueRepository.findById(2L)).thenReturn(Optional.empty());
        runner.runForQueueId(2L);

        ModerationQueueEntity wrongStage = queue(3L, QueueStage.VEC, QueueStatus.PENDING, ContentType.POST);
        when(queueRepository.findById(3L)).thenReturn(Optional.of(wrongStage));
        runner.runForQueueId(3L);

        ModerationQueueEntity wrongStatus = queue(4L, QueueStage.RULE, QueueStatus.HUMAN, ContentType.POST);
        when(queueRepository.findById(4L)).thenReturn(Optional.of(wrongStatus));
        runner.runForQueueId(4L);
    }

    @Test
    void runOnce_should_cover_scan_and_handle_exceptions() {
        doThrow(new RuntimeException("scan")).when(queueRepository).findAllByCurrentStage(QueueStage.RULE);
        runner.runOnce();

        ModerationQueueEntity q = queue(5L, QueueStage.RULE, QueueStatus.PENDING, ContentType.POST);
        doReturn(Arrays.asList(null, q)).when(queueRepository).findAllByCurrentStage(QueueStage.RULE);
        when(queueRepository.tryLockForAutoRun(anyLong(), eq(QueueStage.RULE), any(), eq(QueueStatus.REVIEWING), eq("RULE_AUTO"), any(), any())).thenReturn(1);
        when(queueRepository.findById(5L)).thenReturn(Optional.of(q));
        when(fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        runner.runOnce();
    }

    @Test
    void handleOne_should_cover_lock_and_disabled_paths() throws Exception {
        ModerationQueueEntity q = queue(10L, QueueStage.RULE, QueueStatus.PENDING, ContentType.POST);
        when(queueRepository.tryLockForAutoRun(anyLong(), eq(QueueStage.RULE), any(), eq(QueueStatus.REVIEWING), eq("RULE_AUTO"), any(), any()))
                .thenThrow(new RuntimeException("lock"));
        invokeHandleOne(q);

        when(queueRepository.tryLockForAutoRun(anyLong(), eq(QueueStage.RULE), any(), eq(QueueStatus.REVIEWING), eq("RULE_AUTO"), any(), any()))
                .thenReturn(0);
        invokeHandleOne(q);

        when(queueRepository.tryLockForAutoRun(anyLong(), eq(QueueStage.RULE), any(), eq(QueueStatus.REVIEWING), eq("RULE_AUTO"), any(), any()))
                .thenReturn(1);
        when(queueRepository.findById(10L)).thenReturn(Optional.of(q));
        when(pipelineTraceService.ensureRun(any())).thenReturn(run(100L, "trace-100"));
        when(pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenThrow(new RuntimeException("step"));
        when(fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fallback(false)));
        invokeHandleOne(q);

        verify(queueRepository, never()).updateStageAndStatusIfPendingOrReviewing(anyLong(), any(), any(), any());
    }

    @Test
    void handleOne_should_cover_empty_text_and_no_rules_paths() throws Exception {
        ModerationQueueEntity q1 = queue(11L, QueueStage.RULE, QueueStatus.PENDING, ContentType.POST);
        stubCommon(q1, fallback(true), policy(Map.of("precheck", Map.of("rule", Map.of("enabled", true)))), step(11L));
        when(textLoader.load(q1)).thenReturn("   ");
        invokeHandleOne(q1);

        ModerationQueueEntity q2 = queue(12L, QueueStage.RULE, QueueStatus.PENDING, ContentType.POST);
        stubCommon(q2, fallback(true), policy(Map.of("precheck", Map.of("rule", Map.of("enabled", true)))), step(12L));
        when(textLoader.load(q2)).thenReturn("text");
        when(rulesRepository.findAll()).thenReturn(null);
        invokeHandleOne(q2);

        ModerationQueueEntity q3 = queue(13L, QueueStage.RULE, QueueStatus.PENDING, ContentType.POST);
        stubCommon(q3, fallback(true), policy(Map.of("precheck", Map.of("rule", Map.of("enabled", true)))), step(13L));
        when(textLoader.load(q3)).thenReturn("text");
        when(rulesRepository.findAll()).thenReturn(List.of());
        invokeHandleOne(q3);
    }

    @Test
    void handleOne_should_cover_antispam_hit_and_eval_exception_paths() throws Exception {
        ModerationQueueEntity qHit = queue(20L, QueueStage.RULE, QueueStatus.PENDING, ContentType.COMMENT);
        stubCommon(qHit, fallback(true), policy(Map.of(
                "precheck", Map.of("rule", Map.of("enabled", true)),
                "anti_spam", Map.of("comment", Map.of("window_seconds", 60, "max_per_author_per_window", 1))
        )), step(20L));
        when(textLoader.load(qHit)).thenReturn("comment");
        var comment = new com.example.EnterpriseRagCommunity.entity.content.CommentsEntity();
        comment.setId(20L);
        comment.setAuthorId(99L);
        when(commentsRepository.findById(20L)).thenReturn(Optional.of(comment));
        when(commentsRepository.countByAuthorIdAndIsDeletedFalseAndCreatedAtAfter(eq(99L), any())).thenReturn(5L);
        invokeHandleOne(qHit);
        verify(queueRepository).updateStageAndStatusIfPendingOrReviewing(eq(20L), eq(QueueStage.HUMAN), eq(QueueStatus.HUMAN), any());

        ModerationQueueEntity qErr = queue(21L, QueueStage.RULE, QueueStatus.PENDING, ContentType.COMMENT);
        stubCommon(qErr, fallback(true), policy(Map.of(
                "precheck", Map.of("rule", Map.of("enabled", true)),
                "anti_spam", Map.of("comment", Map.of("window_seconds", 60, "max_per_author_per_window", 1))
        )), step(21L));
        when(textLoader.load(qErr)).thenReturn("comment");
        var comment2 = new com.example.EnterpriseRagCommunity.entity.content.CommentsEntity();
        comment2.setId(21L);
        comment2.setAuthorId(101L);
        when(commentsRepository.findById(21L)).thenReturn(Optional.of(comment2));
        when(commentsRepository.countByAuthorIdAndIsDeletedFalseAndCreatedAtAfter(eq(101L), any())).thenThrow(new RuntimeException("boom"));
        when(rulesRepository.findAll()).thenReturn(List.of());
        invokeHandleOne(qErr);
    }

    @Test
    void handleOne_should_cover_rule_loop_pass_and_reject_and_hit_paths() throws Exception {
        ModerationQueueEntity qPass = queue(30L, QueueStage.RULE, QueueStatus.PENDING, ContentType.POST);
        stubCommon(qPass, fallback(true), policy(Map.of("precheck", Map.of("rule", Map.of("enabled", true)))), step(30L));
        when(textLoader.load(qPass)).thenReturn("alpha beta");
        ModerationRulesEntity disabled = rule(1L, "r1", "alpha", Severity.HIGH, false);
        ModerationRulesEntity blank = rule(2L, "r2", " ", Severity.HIGH, true);
        ModerationRulesEntity badRegex = rule(3L, "r3", "*[", Severity.HIGH, true);
        ModerationRulesEntity miss = rule(4L, "r4", "gamma", Severity.HIGH, true);
        when(rulesRepository.findAll()).thenReturn(Arrays.asList(null, disabled, blank, badRegex, miss));
        invokeHandleOne(qPass);

        ModerationQueueEntity qReject = queue(31L, QueueStage.RULE, QueueStatus.PENDING, ContentType.POST);
        stubCommon(qReject, fallback(true), policy(Map.of(
                "precheck", Map.of(
                        "rule", Map.of("enabled", true, "high_action", "REJECT")
                )
        )), step(31L));
        when(textLoader.load(qReject)).thenReturn("hit-high");
        ModerationRulesEntity high = rule(5L, "r5", "hit-high", Severity.HIGH, true);
        doThrow(new RuntimeException("save")).when(ruleHitsRepository).save(any());
        when(rulesRepository.findAll()).thenReturn(List.of(high));
        invokeHandleOne(qReject);
        verify(queueService).autoReject(eq(31L), eq("规则命中自动拒绝（HIGH）"), eq("trace-31"));

        ModerationQueueEntity qHit = queue(32L, QueueStage.RULE, QueueStatus.PENDING, ContentType.POST);
        stubCommon(qHit, fallback(true), policy(Map.of(
                "precheck", Map.of(
                        "rule", Map.of("enabled", true, "medium_action", "VEC", "low_action", " ")
                )
        )), step(32L));
        when(textLoader.load(qHit)).thenReturn("low medium xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        ModerationRulesEntity low = rule(6L, "r6", "low", null, true);
        ModerationRulesEntity medium = rule(7L, "r7", "medium", Severity.MEDIUM, true);
        when(rulesRepository.findAll()).thenReturn(List.of(low, medium));
        invokeHandleOne(qHit);
        verify(queueRepository).updateStageIfLockedBy(eq(32L), eq(QueueStage.VEC), eq("RULE_AUTO"), any());
    }

    @Test
    void evaluateAntiSpam_private_should_cover_profile_branches() throws Exception {
        ModerationQueueEntity qProfile = queue(40L, QueueStage.RULE, QueueStatus.PENDING, ContentType.PROFILE);
        Object windowHit = invokePrivate(
                "evaluateAntiSpam",
                new Class[]{ModerationQueueEntity.class, Map.class},
                qProfile,
                Map.of("anti_spam", Map.of("profile", Map.of("window_minutes", 30, "max_updates_per_window", 1)))
        );
        assertNull(windowHit);

        when(moderationActionsRepository.countByQueueIdAndReasonAndCreatedAtAfter(eq(40L), eq("PROFILE_PENDING_SNAPSHOT"), any())).thenReturn(3L);
        Object windowExceeded = invokePrivate(
                "evaluateAntiSpam",
                new Class[]{ModerationQueueEntity.class, Map.class},
                qProfile,
                Map.of("anti_spam", Map.of("profile", Map.of("window_minutes", 30, "max_updates_per_window", 1)))
        );
        assertEquals(true, invokeRecordBoolean(windowExceeded, "hit"));

        when(moderationActionsRepository.countByQueueIdAndReasonAndCreatedAtBetween(eq(40L), eq("PROFILE_PENDING_SNAPSHOT"), any(), any())).thenReturn(2L);
        Object dayExceeded = invokePrivate(
                "evaluateAntiSpam",
                new Class[]{ModerationQueueEntity.class, Map.class},
                qProfile,
                Map.of("anti_spam", Map.of("profile", Map.of("max_updates_per_day", 1)))
        );
        assertEquals(true, invokeRecordBoolean(dayExceeded, "hit"));

        Object nullForUnknown = invokePrivate(
                "evaluateAntiSpam",
                new Class[]{ModerationQueueEntity.class, Map.class},
                queue(41L, QueueStage.RULE, QueueStatus.PENDING, ContentType.POST),
                Map.of()
        );
        assertNull(nullForUnknown);
    }

    @Test
    void handleOne_should_unlock_even_when_unlock_throws_and_policy_repo_throws() throws Exception {
        ModerationQueueEntity q = queue(50L, QueueStage.VEC, QueueStatus.PENDING, ContentType.POST);
        when(queueRepository.tryLockForAutoRun(anyLong(), eq(QueueStage.RULE), any(), eq(QueueStatus.REVIEWING), eq("RULE_AUTO"), any(), any())).thenReturn(1);
        when(queueRepository.findById(50L)).thenThrow(new RuntimeException("refetch failed"));
        when(pipelineTraceService.ensureRun(any())).thenReturn(run(50L, "trace-50"));
        when(pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step(50L));
        when(fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fallback(true)));
        when(policyConfigRepository.findByContentType(any())).thenThrow(new RuntimeException("policy failed"));
        when(textLoader.load(any())).thenReturn("x");
        when(rulesRepository.findAll()).thenReturn(List.of());
        doThrow(new RuntimeException("unlock fail")).when(queueRepository).unlockAutoRun(eq(50L), eq("RULE_AUTO"), any());
        invokeHandleOne(q);
        verify(queueRepository).updateStageIfLockedBy(eq(50L), eq(QueueStage.RULE), eq("RULE_AUTO"), any());
    }

    @Test
    void handleOne_should_throw_when_fallback_missing() {
        ModerationQueueEntity q = queue(60L, QueueStage.RULE, QueueStatus.PENDING, ContentType.POST);
        when(queueRepository.tryLockForAutoRun(anyLong(), eq(QueueStage.RULE), any(), eq(QueueStatus.REVIEWING), eq("RULE_AUTO"), any(), any())).thenReturn(1);
        when(queueRepository.findById(60L)).thenReturn(Optional.of(q));
        when(fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());
        when(pipelineTraceService.ensureRun(any())).thenReturn(run(60L, "trace-60"));
        when(pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step(60L));
        Exception ex = assertThrows(Exception.class, () -> invokeHandleOne(q));
        Throwable root = ex.getCause() == null ? ex : ex.getCause();
        assertEquals(IllegalStateException.class, root.getClass());
    }

    private void stubCommon(
            ModerationQueueEntity q,
            ModerationConfidenceFallbackConfigEntity fb,
            ModerationPolicyConfigEntity cfg,
            ModerationPipelineStepEntity step
    ) {
        when(queueRepository.tryLockForAutoRun(anyLong(), eq(QueueStage.RULE), any(), eq(QueueStatus.REVIEWING), eq("RULE_AUTO"), any(), any())).thenReturn(1);
        when(queueRepository.findById(q.getId())).thenReturn(Optional.of(q));
        when(pipelineTraceService.ensureRun(any())).thenReturn(run(q.getId(), "trace-" + q.getId()));
        when(pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step);
        when(fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));
        when(policyConfigRepository.findByContentType(q.getContentType())).thenReturn(Optional.ofNullable(cfg));
    }

    private static ModerationQueueEntity queue(Long id, QueueStage stage, QueueStatus status, ContentType contentType) {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(id);
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(contentType);
        q.setContentId(id);
        q.setCurrentStage(stage);
        q.setStatus(status);
        q.setCreatedAt(LocalDateTime.now());
        q.setUpdatedAt(LocalDateTime.now());
        q.setPriority(0);
        return q;
    }

    private static ModerationRulesEntity rule(Long id, String name, String pattern, Severity severity, boolean enabled) {
        ModerationRulesEntity r = new ModerationRulesEntity();
        r.setId(id);
        r.setName(name);
        r.setType(RuleType.REGEX);
        r.setPattern(pattern);
        r.setSeverity(severity);
        r.setEnabled(enabled);
        return r;
    }

    private static ModerationConfidenceFallbackConfigEntity fallback(boolean ruleEnabled) {
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        return fb;
    }

    private static ModerationPolicyConfigEntity policy(Map<String, Object> config) {
        ModerationPolicyConfigEntity e = new ModerationPolicyConfigEntity();
        e.setConfig(new LinkedHashMap<>(config));
        return e;
    }

    private static ModerationPipelineRunEntity run(Long id, String traceId) {
        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setId(id);
        run.setTraceId(traceId);
        return run;
    }

    private static ModerationPipelineStepEntity step(Long id) {
        ModerationPipelineStepEntity step = new ModerationPipelineStepEntity();
        step.setId(id);
        return step;
    }

    private void invokeHandleOne(ModerationQueueEntity q) throws Exception {
        Method m = ModerationRuleAutoRunner.class.getDeclaredMethod("handleOne", ModerationQueueEntity.class);
        m.setAccessible(true);
        m.invoke(runner, q);
    }

    private Object invokePrivate(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = ModerationRuleAutoRunner.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(runner, args);
    }

    private static Object invokeStatic(String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = ModerationRuleAutoRunner.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Object invokeRecordBoolean(Object record, String method) throws Exception {
        Method m = record.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(record);
    }
}
