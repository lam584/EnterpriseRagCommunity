package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService;
import com.example.EnterpriseRagCommunity.service.moderation.RiskLabelingService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.example.EnterpriseRagCommunity.service.moderation.trace.ModerationPipelineTraceService;
import com.example.EnterpriseRagCommunity.service.monitor.FileAssetExtractionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModerationLlmAutoRunnerBranchUnitTest {

    @Test
    void runForQueueId_shouldApplyGuardsAndSkip() {
        Fixture f = fixture();
        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setAutoRun(true);

        when(f.llmConfigRepository.findAll()).thenReturn(List.of(cfg));
        when(f.queueRepository.findById(1L)).thenThrow(new RuntimeException("db"));
        when(f.queueRepository.findById(2L)).thenReturn(Optional.empty());
        when(f.queueRepository.findById(3L)).thenReturn(Optional.of(queue(3L, QueueStage.RULE, QueueStatus.PENDING)));
        when(f.queueRepository.findById(4L)).thenReturn(Optional.of(queue(4L, QueueStage.LLM, QueueStatus.APPROVED)));

        f.runner.runForQueueId(null);
        f.runner.runForQueueId(1L);
        f.runner.runForQueueId(2L);
        f.runner.runForQueueId(3L);
        f.runner.runForQueueId(4L);

        verifyNoInteractions(f.fallbackRepository, f.llmService, f.queueService);
    }

    @Test
    void handleOne_shouldRouteToRuleWhenRunMissing() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(11L, QueueStage.LLM, QueueStatus.PENDING);

        when(f.queueRepository.findById(11L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(f.policyConfigRepository.findByContentType(ContentType.COMMENT)).thenReturn(Optional.of(policy("v1")));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(null);

        invokeHandleOne(f.runner, q, llmCfg());

        verify(f.queueRepository).updateStageIfPendingOrReviewing(eq(11L), eq(QueueStage.RULE), any());
    }

    @Test
    void handleOne_shouldSkipWhenPreviousRuleOrVecMissing() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(12L, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(901L, "trace-901");
        ModerationPipelineStepEntity onlyRule = new ModerationPipelineStepEntity();
        onlyRule.setDecision("PASS");

        when(f.queueRepository.findById(12L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(f.policyConfigRepository.findByContentType(ContentType.COMMENT)).thenReturn(Optional.of(policy("v1")));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run);
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(901L, ModerationPipelineStepEntity.Stage.RULE))
                .thenReturn(List.of(onlyRule));
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(901L, ModerationPipelineStepEntity.Stage.VEC))
                .thenReturn(List.of());

        invokeHandleOne(f.runner, q, llmCfg());

        verify(f.queueRepository).updateStageIfPendingOrReviewing(eq(12L), eq(QueueStage.RULE), any());
        verify(f.auditLogWriter).writeSystem(eq("LLM_DECISION"), eq("MODERATION_QUEUE"), eq(12L), any(), eq("LLM skipped: missing prev steps (RULE=true, VEC=false)"), eq("trace-901"), any());
    }

    @Test
    void handleOne_shouldAutoApproveWhenLlmDisabledAndLowRisk() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(13L, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(902L, "trace-902");
        ModerationPipelineStepEntity rule = new ModerationPipelineStepEntity();
        rule.setDecision("PASS");
        ModerationPipelineStepEntity vec = new ModerationPipelineStepEntity();
        vec.setDecision("MISS");

        when(f.queueRepository.findById(13L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(f.policyConfigRepository.findByContentType(ContentType.COMMENT)).thenReturn(Optional.of(policy("v1")));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run);
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(902L, ModerationPipelineStepEntity.Stage.RULE))
                .thenReturn(List.of(rule));
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(902L, ModerationPipelineStepEntity.Stage.VEC))
                .thenReturn(List.of(vec), List.of(vec));
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fbDisabled()));

        invokeHandleOne(f.runner, q, llmCfg());

        verify(f.queueService).autoApprove(13L, "", "trace-902");
        verify(f.pipelineTraceService).finishRunSuccess(902L, ModerationPipelineRunEntity.FinalDecision.APPROVE);
    }

    @Test
    void handleOne_shouldRouteHumanWhenLlmDisabledAndNotLowRisk() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(14L, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(903L, "trace-903");
        ModerationPipelineStepEntity rule = new ModerationPipelineStepEntity();
        rule.setDecision("PASS");
        ModerationPipelineStepEntity vec = new ModerationPipelineStepEntity();
        vec.setDecision("HIT");

        when(f.queueRepository.findById(14L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(f.policyConfigRepository.findByContentType(ContentType.COMMENT)).thenReturn(Optional.of(policy("v1")));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run);
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(903L, ModerationPipelineStepEntity.Stage.RULE))
                .thenReturn(List.of(rule));
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(903L, ModerationPipelineStepEntity.Stage.VEC))
                .thenReturn(List.of(vec), List.of(vec));
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fbDisabled()));

        invokeHandleOne(f.runner, q, llmCfg());

        verify(f.queueRepository).updateStageAndStatusIfPendingOrReviewing(eq(14L), eq(QueueStage.HUMAN), eq(QueueStatus.HUMAN), any());
        verify(f.queueService, never()).autoApprove(anyLong(), any(), any());
    }

    @Test
    void handleOne_shouldRouteHumanWhenLlmCallFails() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(15L, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(904L, "trace-904");
        ModerationPipelineStepEntity rule = new ModerationPipelineStepEntity();
        rule.setDecision("PASS");
        ModerationPipelineStepEntity vec = new ModerationPipelineStepEntity();
        vec.setDecision("MISS");

        when(f.queueRepository.findById(15L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(f.policyConfigRepository.findByContentType(ContentType.COMMENT)).thenReturn(Optional.of(policy("v1")));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run, run);
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(904L, ModerationPipelineStepEntity.Stage.RULE))
                .thenReturn(List.of(rule), List.of(rule));
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(904L, ModerationPipelineStepEntity.Stage.VEC))
                .thenReturn(List.of(vec), List.of(vec));
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fbEnabled()));
        when(f.llmService.test(any())).thenThrow(new RuntimeException("upstream down"));

        invokeHandleOne(f.runner, q, llmCfg());

        verify(f.queueRepository).updateStageAndStatusIfPendingOrReviewing(eq(15L), eq(QueueStage.HUMAN), eq(QueueStatus.HUMAN), any());
        verify(f.pipelineTraceService).finishRunFail(904L, "LLM_CALL_FAILED", "upstream down");
    }

    @Test
    void handleOne_shouldApproveRejectAndHumanByPolicy() throws Exception {
        Fixture f = fixture();
        ModerationPipelineStepEntity rule = new ModerationPipelineStepEntity();
        rule.setDecision("PASS");
        ModerationPipelineStepEntity vec = new ModerationPipelineStepEntity();
        vec.setDecision("MISS");
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fbEnabled()));
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(anyLong(), eq(ModerationPipelineStepEntity.Stage.RULE)))
                .thenReturn(List.of(rule), List.of(rule), List.of(rule), List.of(rule), List.of(rule), List.of(rule));
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(anyLong(), eq(ModerationPipelineStepEntity.Stage.VEC)))
                .thenReturn(List.of(vec), List.of(vec), List.of(vec), List.of(vec), List.of(vec), List.of(vec));

        runOneWithResponse(f, 16L, 905L, llmResponse("ALLOW", 0.2, List.of("safe")));
        runOneWithResponse(f, 17L, 906L, llmResponse("REJECT", 0.9, List.of("abuse")));
        runOneWithResponse(f, 18L, 907L, llmResponse("ESCALATE", 0.5, List.of("risk")));

        verify(f.queueService).autoApprove(eq(16L), eq(""), eq("trace-905"));
        verify(f.queueService).autoReject(eq(17L), anyString(), eq("trace-906"));
        verify(f.queueRepository).updateStageAndStatusIfPendingOrReviewing(eq(18L), eq(QueueStage.HUMAN), eq(QueueStatus.HUMAN), any());
    }

    @Test
    void handleOne_shouldThrowWhenPolicyMissing() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(19L, QueueStage.LLM, QueueStatus.PENDING);

        when(f.queueRepository.findById(19L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(f.policyConfigRepository.findByContentType(ContentType.COMMENT)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> invokeHandleOne(f.runner, q, llmCfg()));
        verify(f.queueRepository).unlockAutoRun(eq(19L), eq("LLM_AUTO"), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void handleOne_shouldThrowAndUnlockWhenPolicyUnavailable(boolean repositoryThrows) throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(repositoryThrows ? 191L : 192L, QueueStage.LLM, QueueStatus.PENDING);

        when(f.queueRepository.findById(q.getId())).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        if (repositoryThrows) {
            when(f.policyConfigRepository.findByContentType(ContentType.COMMENT)).thenThrow(new RuntimeException("policy-db-fail"));
        } else {
            when(f.policyConfigRepository.findByContentType(ContentType.COMMENT)).thenReturn(Optional.empty());
        }

        assertThrows(IllegalStateException.class, () -> invokeHandleOne(f.runner, q, llmCfg()));
        verify(f.queueRepository).unlockAutoRun(eq(q.getId()), eq("LLM_AUTO"), any());
    }

    @Test
    void handleOne_shouldWaitFilesWhenExtractionStillPendingAndWithinWindow() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(21L, QueueStage.LLM, QueueStatus.PENDING);
        q.setContentType(ContentType.POST);
        q.setContentId(2100L);
        q.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        ModerationPipelineRunEntity run = run(921L, "trace-921");
        ModerationPipelineStepEntity rule = new ModerationPipelineStepEntity();
        rule.setDecision("PASS");
        ModerationPipelineStepEntity vec = new ModerationPipelineStepEntity();
        vec.setDecision("MISS");

        when(f.queueRepository.findById(21L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(f.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy("v1")));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run);
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(921L, ModerationPipelineStepEntity.Stage.RULE))
                .thenReturn(List.of(rule));
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(921L, ModerationPipelineStepEntity.Stage.VEC))
                .thenReturn(List.of(vec));
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fbEnabled()));
        when(f.postAttachmentsRepository.findByPostId(anyLong(), any())).thenReturn(new PageImpl<>(List.of(imageAttachment(3001L))));
        when(f.fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(extraction(3001L, FileAssetExtractionStatus.PENDING, null)));

        invokeHandleOne(f.runner, q, llmCfg());

        verify(f.queueRepository).updateStageIfPendingOrReviewing(eq(21L), eq(QueueStage.LLM), any());
        verify(f.llmService, never()).test(any());
    }

    @Test
    void handleOne_shouldTimeoutFilesToHumanWhenPendingTooLong() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(22L, QueueStage.LLM, QueueStatus.PENDING);
        q.setContentType(ContentType.POST);
        q.setContentId(2200L);
        q.setCreatedAt(LocalDateTime.now().minusHours(2));
        ModerationPipelineRunEntity run = run(922L, "trace-922");
        ModerationPipelineStepEntity rule = new ModerationPipelineStepEntity();
        rule.setDecision("PASS");
        ModerationPipelineStepEntity vec = new ModerationPipelineStepEntity();
        vec.setDecision("MISS");

        when(f.queueRepository.findById(22L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(f.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy("v1")));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run);
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(922L, ModerationPipelineStepEntity.Stage.RULE))
                .thenReturn(List.of(rule));
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(922L, ModerationPipelineStepEntity.Stage.VEC))
                .thenReturn(List.of(vec));
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fbEnabled()));
        when(f.postAttachmentsRepository.findByPostId(anyLong(), any())).thenReturn(new PageImpl<>(List.of(imageAttachment(3002L))));
        when(f.fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(extraction(3002L, FileAssetExtractionStatus.PENDING, null)));

        invokeHandleOne(f.runner, q, llmCfg());

        verify(f.queueRepository).updateStageAndStatusIfPendingOrReviewing(eq(22L), eq(QueueStage.HUMAN), eq(QueueStatus.HUMAN), any());
        verify(f.pipelineTraceService).finishRunSuccess(922L, ModerationPipelineRunEntity.FinalDecision.HUMAN);
        verify(f.llmService, never()).test(any());
    }

    @Test
    void handleOne_shouldUseUpgradeResultWhenTextGrayZone() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(23L, QueueStage.LLM, QueueStatus.PENDING);
        q.setContentType(ContentType.COMMENT);
        q.setContentId(2300L);
        ModerationPipelineRunEntity run = run(923L, "trace-923");
        ModerationPipelineStepEntity rule = new ModerationPipelineStepEntity();
        rule.setDecision("PASS");
        ModerationPipelineStepEntity vec = new ModerationPipelineStepEntity();
        vec.setDecision("MISS");

        ModerationConfidenceFallbackConfigEntity fb = fbEnabled();
        fb.setThresholds(Map.of(
                "llm.text.upgrade.enable", true,
                "llm.text.upgrade.scoreMin", 0.4,
                "llm.text.upgrade.scoreMax", 0.6,
                "llm.text.upgrade.uncertaintyMin", 0.9
        ));

        LlmModerationTestResponse primary = llmResponse("ALLOW", 0.5, List.of("safe"));
        primary.setUncertainty(0.95);
        LlmModerationTestResponse upgraded = llmResponse("REJECT", 0.95, List.of("abuse"));
        upgraded.setDecision("REJECT");
        upgraded.setDecisionSuggestion("REJECT");

        when(f.queueRepository.findById(23L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(f.policyConfigRepository.findByContentType(ContentType.COMMENT)).thenReturn(Optional.of(policy("v1")));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run, run);
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(923L, ModerationPipelineStepEntity.Stage.RULE))
                .thenReturn(List.of(rule), List.of(rule));
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(923L, ModerationPipelineStepEntity.Stage.VEC))
                .thenReturn(List.of(vec), List.of(vec));
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));
        when(f.llmService.test(any())).thenReturn(primary, upgraded);
        com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity p = new com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity();
        p.setUserPromptTemplate("judge-template");
        when(f.promptsRepository.findByPromptCode("JP")).thenReturn(Optional.of(p));
        ModerationLlmConfigEntity cfg = llmCfg();
        cfg.setJudgePromptCode("JP");

        invokeHandleOne(f.runner, q, cfg);

        verify(f.llmService, atLeastOnce()).test(any());
    }

    private static void runOneWithResponse(Fixture f, Long queueId, Long runId, LlmModerationTestResponse response) throws Exception {
        ModerationQueueEntity q = queue(queueId, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(runId, "trace-" + runId);
        when(f.queueRepository.findById(queueId)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(f.policyConfigRepository.findByContentType(ContentType.COMMENT)).thenReturn(Optional.of(policy("v1")));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run, run);
        when(f.llmService.test(any())).thenReturn(response);
        invokeHandleOne(f.runner, q, llmCfg());
    }

    private static void invokeHandleOne(ModerationLlmAutoRunner runner, ModerationQueueEntity q, ModerationLlmConfigEntity cfg) throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("handleOne", ModerationQueueEntity.class, ModerationLlmConfigEntity.class);
        m.setAccessible(true);
        try {
            m.invoke(runner, q, cfg);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception e) throw e;
            throw ex;
        }
    }

    private static ModerationLlmConfigEntity llmCfg() {
        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setAutoRun(true);
        return cfg;
    }

    private static ModerationConfidenceFallbackConfigEntity fbEnabled() {
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setLlmEnabled(true);
        fb.setLlmRejectThreshold(0.75);
        fb.setLlmHumanThreshold(0.4);
        fb.setThresholds(Map.of(
                "llm.text.upgrade.enable", false,
                "llm.text.upgrade.scoreMin", 0.4,
                "llm.text.upgrade.scoreMax", 0.6,
                "llm.text.upgrade.uncertaintyMin", 0.5
        ));
        return fb;
    }

    private static ModerationConfidenceFallbackConfigEntity fbDisabled() {
        ModerationConfidenceFallbackConfigEntity fb = fbEnabled();
        fb.setLlmEnabled(false);
        return fb;
    }

    private static ModerationPolicyConfigEntity policy(String version) {
        ModerationPolicyConfigEntity p = new ModerationPolicyConfigEntity();
        p.setPolicyVersion(version);
        p.setConfig(Map.of(
                "thresholds", Map.of(
                        "default", Map.of("T_allow", 0.3, "T_reject", 0.7)
                ),
                "escalate_rules", Map.of("require_evidence", false)
        ));
        return p;
    }

    private static ModerationPipelineRunEntity run(Long id, String traceId) {
        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setId(id);
        run.setTraceId(traceId);
        return run;
    }

    private static ModerationQueueEntity queue(Long id, QueueStage stage, QueueStatus status) {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(id);
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.COMMENT);
        q.setContentId(id * 100);
        q.setCurrentStage(stage);
        q.setStatus(status);
        q.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        q.setUpdatedAt(LocalDateTime.now().minusMinutes(2));
        return q;
    }

    private static LlmModerationTestResponse llmResponse(String suggestion, double score, List<String> riskTags) {
        LlmModerationTestResponse res = new LlmModerationTestResponse();
        res.setDecisionSuggestion(suggestion);
        res.setScore(score);
        res.setRiskScore(score);
        res.setRiskTags(riskTags);
        res.setEvidence(List.of("证据"));
        return res;
    }

    private static PostAttachmentsEntity imageAttachment(Long fileAssetId) {
        FileAssetsEntity file = new FileAssetsEntity();
        file.setId(fileAssetId);
        file.setMimeType("image/png");
        file.setUrl("/uploads/" + fileAssetId + ".png");
        PostAttachmentsEntity att = new PostAttachmentsEntity();
        att.setFileAssetId(fileAssetId);
        att.setFileAsset(file);
        return att;
    }

    private static FileAssetExtractionsEntity extraction(Long fileAssetId, FileAssetExtractionStatus status, String errorMessage) {
        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(fileAssetId);
        ex.setExtractStatus(status);
        ex.setErrorMessage(errorMessage);
        return ex;
    }

    private static Fixture fixture() {
        ModerationLlmConfigRepository llmConfigRepository = mock(ModerationLlmConfigRepository.class);
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        AdminModerationLlmService llmService = mock(AdminModerationLlmService.class);
        AdminModerationQueueService queueService = mock(AdminModerationQueueService.class);
        ModerationConfidenceFallbackConfigRepository fallbackRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        TagsRepository tagsRepository = mock(TagsRepository.class);
        ModerationPipelineTraceService pipelineTraceService = mock(ModerationPipelineTraceService.class);
        ModerationPipelineStepRepository pipelineStepRepository = mock(ModerationPipelineStepRepository.class);
        com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository promptsRepository = mock(com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        RiskLabelingService riskLabelingService = mock(RiskLabelingService.class);
        TokenCountService tokenCountService = mock(TokenCountService.class);
        ModerationChunkReviewService chunkReviewService = mock(ModerationChunkReviewService.class);
        PostAttachmentsRepository postAttachmentsRepository = mock(PostAttachmentsRepository.class);
        FileAssetExtractionsRepository fileAssetExtractionsRepository = mock(FileAssetExtractionsRepository.class);
        FileAssetExtractionService fileAssetExtractionService = mock(FileAssetExtractionService.class);
        LlmQueueProperties llmQueueProperties = mock(LlmQueueProperties.class);
        LlmCallQueueService llmCallQueueService = mock(LlmCallQueueService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        ModerationLlmAutoRunner runner = new ModerationLlmAutoRunner(
                llmConfigRepository,
                queueRepository,
                llmService,
                queueService,
                fallbackRepository,
                policyConfigRepository,
                tagsRepository,
                pipelineTraceService,
                pipelineStepRepository,
                promptsRepository,
                auditLogWriter,
                riskLabelingService,
                tokenCountService,
                chunkReviewService,
                postAttachmentsRepository,
                fileAssetExtractionsRepository,
                fileAssetExtractionService,
                llmQueueProperties,
                llmCallQueueService,
                objectMapper
        );
        return new Fixture(
                runner,
                llmConfigRepository,
                queueRepository,
                llmService,
                queueService,
                fallbackRepository,
                policyConfigRepository,
                pipelineTraceService,
                pipelineStepRepository,
                auditLogWriter,
                postAttachmentsRepository,
                fileAssetExtractionsRepository,
                promptsRepository
        );
    }

    private record Fixture(
            ModerationLlmAutoRunner runner,
            ModerationLlmConfigRepository llmConfigRepository,
            ModerationQueueRepository queueRepository,
            AdminModerationLlmService llmService,
            AdminModerationQueueService queueService,
            ModerationConfidenceFallbackConfigRepository fallbackRepository,
            ModerationPolicyConfigRepository policyConfigRepository,
            ModerationPipelineTraceService pipelineTraceService,
            ModerationPipelineStepRepository pipelineStepRepository,
            AuditLogWriter auditLogWriter,
            PostAttachmentsRepository postAttachmentsRepository,
            FileAssetExtractionsRepository fileAssetExtractionsRepository,
            com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository promptsRepository
    ) {}
}
