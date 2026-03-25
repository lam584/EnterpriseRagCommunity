package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.SimilarityCheckRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.SimilarityCheckResponse;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationSimilarityService;
import com.example.EnterpriseRagCommunity.service.moderation.trace.ModerationPipelineTraceService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ModerationVecAutoRunnerTest {

    @Test
    void runOnce_shouldReturnWhenScanFails() {
        Fixture f = fixture();
        when(f.queueRepository.findAllByCurrentStage(QueueStage.VEC)).thenThrow(new RuntimeException("scan-fail"));

        f.runner.runOnce();

        verify(f.queueRepository, never()).updateStageIfPendingOrReviewing(anyLong(), any(), any());
    }

    @Test
    void runOnce_shouldReturnWhenNoRunnableQueue() {
        Fixture f = fixture();
        ModerationQueueEntity ignored = queue(10L, QueueStage.VEC, QueueStatus.APPROVED, 1, LocalDateTime.now());
        when(f.queueRepository.findAllByCurrentStage(QueueStage.VEC)).thenReturn(null).thenReturn(List.of(ignored));

        f.runner.runOnce();
        f.runner.runOnce();

        verifyNoInteractions(f.fallbackRepository, f.pipelineTraceService, f.queueService, f.similarityService);
    }

    @Test
    void runOnce_shouldSkipInvalidAndContinueWhenOneQueueThrows() {
        Fixture f = fixture();
        ModerationQueueEntity q1 = queue(11L, QueueStage.VEC, QueueStatus.PENDING, 9, LocalDateTime.now().minusMinutes(3));
        ModerationQueueEntity q2 = queue(12L, QueueStage.VEC, QueueStatus.REVIEWING, null, null);
        ModerationQueueEntity ignoredStatus = queue(13L, QueueStage.VEC, QueueStatus.APPROVED, 1, LocalDateTime.now());

        when(f.queueRepository.findAllByCurrentStage(QueueStage.VEC)).thenReturn(Arrays.asList(null, ignoredStatus, q1, q2));
        when(f.queueRepository.findById(11L)).thenReturn(Optional.of(q1));
        when(f.queueRepository.findById(12L)).thenReturn(Optional.of(q2));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(null);
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc())
                .thenThrow(new IllegalStateException("cfg-missing"))
                .thenReturn(Optional.of(cfg(false, ModerationConfidenceFallbackConfigEntity.Action.LLM, ModerationConfidenceFallbackConfigEntity.Action.LLM, 0.4)));

        f.runner.runOnce();

        verify(f.queueRepository).updateStageIfPendingOrReviewing(eq(11L), eq(QueueStage.LLM), any());
    }

    @Test
    void runForQueueId_shouldApplyGuards() {
        Fixture f = fixture();
        when(f.queueRepository.findById(1L)).thenThrow(new RuntimeException("db"));
        when(f.queueRepository.findById(2L)).thenReturn(Optional.empty());
        when(f.queueRepository.findById(3L)).thenReturn(Optional.of(queue(3L, QueueStage.RULE, QueueStatus.PENDING, 1, LocalDateTime.now())));
        when(f.queueRepository.findById(4L)).thenReturn(Optional.of(queue(4L, QueueStage.VEC, QueueStatus.HUMAN, 1, LocalDateTime.now())));

        f.runner.runForQueueId(null);
        f.runner.runForQueueId(1L);
        f.runner.runForQueueId(2L);
        f.runner.runForQueueId(3L);
        f.runner.runForQueueId(4L);

        verifyNoInteractions(f.fallbackRepository, f.similarityService, f.textLoader, f.queueService);
    }

    @Test
    void runForQueueId_shouldReturnWhenReloadedStatusIsNotRunnable() {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(21L, QueueStage.VEC, QueueStatus.PENDING, 1, LocalDateTime.now());
        ModerationQueueEntity reloaded = queue(21L, QueueStage.VEC, QueueStatus.REJECTED, 1, LocalDateTime.now());

        when(f.queueRepository.findById(21L)).thenReturn(Optional.of(q), Optional.of(reloaded));

        f.runner.runForQueueId(21L);

        verifyNoInteractions(f.fallbackRepository, f.similarityService, f.textLoader, f.queueService);
    }

    @Test
    void runForQueueId_shouldThrowWhenFallbackConfigMissing() {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(31L, QueueStage.VEC, QueueStatus.PENDING, 1, LocalDateTime.now());

        when(f.queueRepository.findById(31L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(null);
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> f.runner.runForQueueId(31L));
        assertThat(ex.getMessage()).contains("moderation_confidence_fallback_config not initialized");
    }

    @Test
    void runForQueueId_shouldRejectWhenVecDisabledWithoutRun() {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(41L, QueueStage.VEC, QueueStatus.PENDING, 1, LocalDateTime.now());
        ModerationConfidenceFallbackConfigEntity cfg = cfg(false, ModerationConfidenceFallbackConfigEntity.Action.LLM, ModerationConfidenceFallbackConfigEntity.Action.REJECT, 0.7);
        ModerationPolicyConfigEntity policy = policy(Map.of(
            "precheck", Map.of(
                "vec", Map.of(
                    "enabled", false,
                    "miss_action", "REJECT"
                )
            )
        ));

        when(f.queueRepository.findById(41L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.pipelineTraceService.ensureRun(any())).thenThrow(new RuntimeException("trace down"));
        when(f.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy));
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(cfg));

        f.runner.runForQueueId(41L);

        verify(f.queueService).autoReject(41L, "相似检测关闭且未命中策略为拒绝", null);
        verify(f.pipelineTraceService, never()).finishRunSuccess(anyLong(), any());
        verifyNoInteractions(f.auditLogWriter, f.similarityService, f.textLoader);
    }

    @Test
    void runForQueueId_shouldRejectWhenVecDisabledWithRunAndStep() {
        Fixture f = fixture();
        ModerationQueueEntity first = queue(51L, QueueStage.VEC, QueueStatus.PENDING, 1, LocalDateTime.now());
        ModerationQueueEntity second = queue(51L, QueueStage.RULE, QueueStatus.PENDING, 1, LocalDateTime.now());
        ModerationPipelineRunEntity run = run(901L, "trace-901");
        ModerationPipelineStepEntity step = step(77L);
        ModerationConfidenceFallbackConfigEntity cfg = cfg(false, ModerationConfidenceFallbackConfigEntity.Action.HUMAN, ModerationConfidenceFallbackConfigEntity.Action.REJECT, 0.8);
        ModerationPolicyConfigEntity policy = policy(Map.of(
            "precheck", Map.of(
                "vec", Map.of(
                    "enabled", false,
                    "miss_action", "REJECT"
                )
            )
        ));

        when(f.queueRepository.findById(51L)).thenReturn(Optional.of(first), Optional.of(second));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run);
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step);
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(cfg));
        when(f.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy));

        f.runner.runForQueueId(51L);

        verify(f.queueRepository).updateStageIfPendingOrReviewing(eq(51L), eq(QueueStage.VEC), any());
        verify(f.pipelineTraceService).finishStepOk(eq(77L), eq("REJECT"), eq(null), any());
        verify(f.pipelineTraceService).finishRunSuccess(901L, ModerationPipelineRunEntity.FinalDecision.REJECT);
        verify(f.auditLogWriter).writeSystem(
                eq("VEC_DECISION"),
                eq("MODERATION_QUEUE"),
                eq(51L),
                any(),
                eq("VEC disabled -> REJECT"),
                eq("trace-901"),
                any()
        );
    }

    @Test
    void runForQueueId_shouldSkipWhenVecDisabledAndStepStartFails() {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(61L, QueueStage.VEC, QueueStatus.REVIEWING, 1, LocalDateTime.now());
        ModerationPipelineRunEntity run = run(902L, "trace-902");
        ModerationPolicyConfigEntity policy = policy(Map.of(
                "precheck", Map.of(
                        "vec", Map.of(
                                "enabled", "false",
                                "miss_action", "llm",
                                "hit_action", "   ",
                                "threshold", "0.55"
                        )
                )
        ));
        ModerationConfidenceFallbackConfigEntity cfg = cfg(true, ModerationConfidenceFallbackConfigEntity.Action.REJECT, ModerationConfidenceFallbackConfigEntity.Action.REJECT, 0.8);

        when(f.queueRepository.findById(61L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run);
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenThrow(new RuntimeException("step-fail"));
        when(f.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy));
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(cfg));

        f.runner.runForQueueId(61L);

        verify(f.queueRepository).updateStageIfPendingOrReviewing(eq(61L), eq(QueueStage.LLM), any());
        verify(f.pipelineTraceService, never()).finishStepOk(anyLong(), any(), any(), any());
        verify(f.pipelineTraceService, never()).finishRunSuccess(anyLong(), any());
        verify(f.auditLogWriter).writeSystem(
                eq("VEC_DECISION"),
                eq("MODERATION_QUEUE"),
                eq(61L),
                any(),
                eq("VEC skipped (disabled)"),
                eq("trace-902"),
                any()
        );
    }

    @Test
    void runForQueueId_shouldHandleEmptyTextRejectAndSkip() {
        Fixture rejectFixture = fixture();
        ModerationQueueEntity rejectQueue = queue(71L, QueueStage.VEC, QueueStatus.PENDING, 1, LocalDateTime.now());
        ModerationPipelineRunEntity run = run(903L, "trace-903");
        ModerationPipelineStepEntity step = step(88L);
        ModerationConfidenceFallbackConfigEntity rejectCfg = cfg(true, ModerationConfidenceFallbackConfigEntity.Action.HUMAN, ModerationConfidenceFallbackConfigEntity.Action.REJECT, 0.4);

        when(rejectFixture.queueRepository.findById(71L)).thenReturn(Optional.of(rejectQueue), Optional.of(rejectQueue));
        when(rejectFixture.pipelineTraceService.ensureRun(any())).thenReturn(run);
        when(rejectFixture.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step);
        when(rejectFixture.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(rejectCfg));
        when(rejectFixture.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(
            policy(Map.of(
                "precheck", Map.of(
                    "vec", Map.of(
                        "enabled", true,
                        "miss_action", "REJECT"
                    )
                )
            ))
        ));
        when(rejectFixture.textLoader.load(any())).thenReturn("   ");

        rejectFixture.runner.runForQueueId(71L);

        verify(rejectFixture.queueService).autoReject(71L, "相似检测空文本且未命中策略为拒绝", "trace-903");
        verify(rejectFixture.pipelineTraceService).finishStepOk(eq(88L), eq("REJECT"), eq(null), any());
        verify(rejectFixture.pipelineTraceService).finishRunSuccess(903L, ModerationPipelineRunEntity.FinalDecision.REJECT);

        Fixture skipFixture = fixture();
        ModerationQueueEntity skipQueue = queue(72L, QueueStage.VEC, QueueStatus.PENDING, 1, LocalDateTime.now());
        ModerationConfidenceFallbackConfigEntity skipCfg = cfg(true, ModerationConfidenceFallbackConfigEntity.Action.HUMAN, ModerationConfidenceFallbackConfigEntity.Action.LLM, 0.4);

        when(skipFixture.queueRepository.findById(72L)).thenReturn(Optional.of(skipQueue), Optional.of(skipQueue));
        when(skipFixture.pipelineTraceService.ensureRun(any())).thenThrow(new RuntimeException("no-run"));
        when(skipFixture.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(skipCfg));
        when(skipFixture.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.empty());
        when(skipFixture.textLoader.load(any())).thenReturn(null);

        skipFixture.runner.runForQueueId(72L);

        verify(skipFixture.queueRepository).updateStageIfPendingOrReviewing(eq(72L), eq(QueueStage.LLM), any());
        verifyNoInteractions(skipFixture.similarityService, skipFixture.auditLogWriter);
    }

    @Test
    void runForQueueId_shouldHandleSimilarityBranches() {
        Fixture missToVecFixture = fixture();
        ModerationQueueEntity missToVecQueue = queue(81L, QueueStage.VEC, QueueStatus.PENDING, 1, LocalDateTime.now());
        ModerationPipelineRunEntity run = run(904L, "trace-904");
        ModerationPipelineStepEntity step = step(99L);
        ModerationConfidenceFallbackConfigEntity cfg = cfg(true, ModerationConfidenceFallbackConfigEntity.Action.LLM, ModerationConfidenceFallbackConfigEntity.Action.HUMAN, null);
        ModerationPolicyConfigEntity missToVecPolicy = policy(Map.of(
                "precheck", Map.of(
                        "vec", Map.of(
                                "enabled", true,
                                "miss_action", "VEC"
                        )
                )
        ));

        when(missToVecFixture.queueRepository.findById(81L)).thenReturn(Optional.of(missToVecQueue), Optional.of(missToVecQueue));
        when(missToVecFixture.pipelineTraceService.ensureRun(any())).thenReturn(run);
        when(missToVecFixture.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step);
        when(missToVecFixture.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(cfg));
        when(missToVecFixture.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(missToVecPolicy));
        when(missToVecFixture.textLoader.load(any())).thenReturn("moderation text");
        when(missToVecFixture.similarityService.check(any())).thenReturn(null);

        missToVecFixture.runner.runForQueueId(81L);

        ArgumentCaptor<SimilarityCheckRequest> requestCaptor = ArgumentCaptor.forClass(SimilarityCheckRequest.class);
        verify(missToVecFixture.similarityService).check(requestCaptor.capture());
        assertEquals(0.2, requestCaptor.getValue().getThreshold());
        verify(missToVecFixture.queueRepository).updateStageIfPendingOrReviewing(eq(81L), eq(QueueStage.VEC), any());
        verify(missToVecFixture.pipelineTraceService).finishStepOk(eq(99L), eq("MISS"), eq(null), any());

        Fixture hitRejectFixture = fixture();
        ModerationQueueEntity hitRejectQueue = queue(82L, QueueStage.VEC, QueueStatus.PENDING, 1, LocalDateTime.now());
        ModerationConfidenceFallbackConfigEntity hitRejectCfg = cfg(true, ModerationConfidenceFallbackConfigEntity.Action.REJECT, ModerationConfidenceFallbackConfigEntity.Action.LLM, 0.33);
        SimilarityCheckResponse hitResp = new SimilarityCheckResponse();
        hitResp.setHit(true);
        hitResp.setBestDistance(0.2);
        hitResp.setThreshold(0.33);

        when(hitRejectFixture.queueRepository.findById(82L)).thenReturn(Optional.of(hitRejectQueue), Optional.of(hitRejectQueue));
        when(hitRejectFixture.pipelineTraceService.ensureRun(any())).thenReturn(run(905L, "trace-905"));
        when(hitRejectFixture.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step(101L));
        when(hitRejectFixture.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(hitRejectCfg));
        when(hitRejectFixture.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(
            policy(Map.of(
                "precheck", Map.of(
                    "vec", Map.of(
                        "enabled", true,
                        "threshold", 0.33
                    )
                )
            ))
        ));
        when(hitRejectFixture.textLoader.load(any())).thenReturn("txt");
        when(hitRejectFixture.similarityService.check(any())).thenReturn(hitResp);

        hitRejectFixture.runner.runForQueueId(82L);

        ArgumentCaptor<SimilarityCheckRequest> hitReq = ArgumentCaptor.forClass(SimilarityCheckRequest.class);
        verify(hitRejectFixture.similarityService).check(hitReq.capture());
        assertEquals(0.33, hitReq.getValue().getThreshold());
        verify(hitRejectFixture.queueService).autoReject(82L, "相似检测命中自动拒绝", "trace-905");

        Fixture missRejectFixture = fixture();
        ModerationQueueEntity missRejectQueue = queue(83L, QueueStage.VEC, QueueStatus.PENDING, 1, LocalDateTime.now());
        ModerationConfidenceFallbackConfigEntity missRejectCfg = cfg(true, ModerationConfidenceFallbackConfigEntity.Action.LLM, ModerationConfidenceFallbackConfigEntity.Action.REJECT, 0.33);
        SimilarityCheckResponse missResp = new SimilarityCheckResponse();
        missResp.setHit(false);
        missResp.setBestDistance(0.8);
        missResp.setThreshold(0.33);

        when(missRejectFixture.queueRepository.findById(83L)).thenReturn(Optional.of(missRejectQueue), Optional.of(missRejectQueue));
        when(missRejectFixture.pipelineTraceService.ensureRun(any())).thenThrow(new RuntimeException("no-run"));
        when(missRejectFixture.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(missRejectCfg));
        when(missRejectFixture.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(
            policy(Map.of(
                "precheck", Map.of(
                    "vec", Map.of(
                        "enabled", true,
                        "miss_action", "REJECT"
                    )
                )
            ))
        ));
        when(missRejectFixture.textLoader.load(any())).thenReturn("txt");
        when(missRejectFixture.similarityService.check(any())).thenReturn(missResp);

        missRejectFixture.runner.runForQueueId(83L);

        verify(missRejectFixture.queueService).autoReject(83L, "相似检测未命中自动拒绝", null);

        Fixture defaultActionFixture = fixture();
        ModerationQueueEntity defaultQueue = queue(84L, QueueStage.VEC, QueueStatus.PENDING, 1, LocalDateTime.now());
        ModerationPolicyConfigEntity policy = policy(Map.of(
                "precheck", Map.of(
                        "vec", Map.of(
                                "enabled", true,
                                "hit_action", "other",
                                "threshold", "bad-double"
                        )
                )
        ));
        SimilarityCheckResponse hitResp2 = new SimilarityCheckResponse();
        hitResp2.setHit(true);

        when(defaultActionFixture.queueRepository.findById(84L)).thenReturn(Optional.of(defaultQueue), Optional.of(defaultQueue));
        when(defaultActionFixture.pipelineTraceService.ensureRun(any())).thenThrow(new RuntimeException("no-run"));
        when(defaultActionFixture.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(cfg(true, ModerationConfidenceFallbackConfigEntity.Action.LLM, ModerationConfidenceFallbackConfigEntity.Action.HUMAN, 0.2)));
        when(defaultActionFixture.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy));
        when(defaultActionFixture.textLoader.load(any())).thenReturn("txt");
        when(defaultActionFixture.similarityService.check(any())).thenReturn(hitResp2);

        defaultActionFixture.runner.runForQueueId(84L);

        verify(defaultActionFixture.queueRepository).updateStageIfPendingOrReviewing(eq(84L), eq(QueueStage.HUMAN), any());
    }

    @Test
    void runForQueueId_shouldHandleHitToLlmWithRunAudit() {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(85L, QueueStage.VEC, QueueStatus.PENDING, 1, LocalDateTime.now());
        ModerationPipelineRunEntity run = run(906L, "trace-906");
        ModerationPipelineStepEntity step = step(102L);
        SimilarityCheckResponse resp = new SimilarityCheckResponse();
        resp.setHit(true);
        resp.setBestDistance(0.12);
        resp.setThreshold(0.2);
        SimilarityCheckResponse.Hit hit = new SimilarityCheckResponse.Hit();
        hit.setSampleId(1L);
        hit.setDistance(0.12);
        resp.setHits(List.of(hit));

        when(f.queueRepository.findById(85L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run);
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step);
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(
                cfg(true, ModerationConfidenceFallbackConfigEntity.Action.LLM, ModerationConfidenceFallbackConfigEntity.Action.HUMAN, 0.2)
        ));
        when(f.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(
            policy(Map.of(
                "precheck", Map.of(
                    "vec", Map.of(
                        "enabled", true,
                        "hit_action", "LLM"
                    )
                )
            ))
        ));
        when(f.textLoader.load(any())).thenReturn("vector text");
        when(f.similarityService.check(any())).thenReturn(resp);

        f.runner.runForQueueId(85L);

        verify(f.queueRepository).updateStageIfPendingOrReviewing(eq(85L), eq(QueueStage.LLM), any());
        verify(f.pipelineTraceService).finishStepOk(eq(102L), eq("HIT"), eq(null), any());
        verify(f.auditLogWriter).writeSystem(
                eq("VEC_DECISION"),
                eq("MODERATION_QUEUE"),
                eq(85L),
                any(),
                eq("VEC hit"),
                eq("trace-906"),
                any()
        );
    }

    @Test
    void privateHandleOne_shouldReturnForNullQueueOrNullId() throws Exception {
        Fixture f = fixture();
        Method handleOne = ModerationVecAutoRunner.class.getDeclaredMethod("handleOne", ModerationQueueEntity.class);
        handleOne.setAccessible(true);

        handleOne.invoke(f.runner, new Object[]{null});

        ModerationQueueEntity noId = queue(1L, QueueStage.VEC, QueueStatus.PENDING, 1, LocalDateTime.now());
        noId.setId(null);
        handleOne.invoke(f.runner, noId);

        verifyNoInteractions(f.queueRepository, f.fallbackRepository, f.pipelineTraceService, f.queueService, f.similarityService);
    }

    @Test
    void privateHelpers_shouldCoverAllBranches() throws Exception {
        Method mapAction = ModerationVecAutoRunner.class.getDeclaredMethod("mapAction", ModerationConfidenceFallbackConfigEntity.Action.class);
        mapAction.setAccessible(true);
        assertEquals(QueueStage.HUMAN, mapAction.invoke(null, new Object[]{null}));
        assertEquals(QueueStage.HUMAN, mapAction.invoke(null, ModerationConfidenceFallbackConfigEntity.Action.HUMAN));
        assertEquals(QueueStage.LLM, mapAction.invoke(null, ModerationConfidenceFallbackConfigEntity.Action.LLM));
        assertEquals(QueueStage.HUMAN, mapAction.invoke(null, ModerationConfidenceFallbackConfigEntity.Action.REJECT));

        Method mapNextStage = ModerationVecAutoRunner.class.getDeclaredMethod("mapNextStage", String.class);
        mapNextStage.setAccessible(true);
        assertEquals(QueueStage.HUMAN, mapNextStage.invoke(null, new Object[]{null}));
        assertEquals(QueueStage.HUMAN, mapNextStage.invoke(null, " "));
        assertEquals(QueueStage.LLM, mapNextStage.invoke(null, "llm"));
        assertEquals(QueueStage.VEC, mapNextStage.invoke(null, "VEC"));
        assertEquals(QueueStage.HUMAN, mapNextStage.invoke(null, "reject"));
        assertEquals(QueueStage.HUMAN, mapNextStage.invoke(null, "unknown"));

        Method normalizeAction = ModerationVecAutoRunner.class.getDeclaredMethod("normalizeAction", String.class);
        normalizeAction.setAccessible(true);
        assertNull(normalizeAction.invoke(null, new Object[]{null}));
        assertNull(normalizeAction.invoke(null, "  "));
        assertEquals("LLM", normalizeAction.invoke(null, " llm "));

        Method firstNonBlank = ModerationVecAutoRunner.class.getDeclaredMethod("firstNonBlank", String.class, String.class);
        firstNonBlank.setAccessible(true);
        assertEquals("a", firstNonBlank.invoke(null, " a ", "b"));
        assertEquals("b", firstNonBlank.invoke(null, " ", " b "));
        assertNull(firstNonBlank.invoke(null, " ", null));

        Method deepGet = ModerationVecAutoRunner.class.getDeclaredMethod("deepGet", Map.class, String.class);
        deepGet.setAccessible(true);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("precheck", Map.of("vec", Map.of("enabled", "true", "threshold", "0.25", "hit_action", "LLM")));
        map.put("nonMap", "x");
        assertNull(deepGet.invoke(null, null, "a.b"));
        assertNull(deepGet.invoke(null, map, " "));
        assertNull(deepGet.invoke(null, map, "missing.path"));
        assertNull(deepGet.invoke(null, map, "nonMap.inner"));
        assertEquals("true", deepGet.invoke(null, map, "precheck..vec.enabled"));

        Method deepGetBool = ModerationVecAutoRunner.class.getDeclaredMethod("deepGetBool", Map.class, String.class);
        deepGetBool.setAccessible(true);
        assertEquals(Boolean.TRUE, deepGetBool.invoke(null, map, "precheck.vec.enabled"));
        map.put("flagFalse", "false");
        map.put("flagInvalid", "x");
        map.put("flagBlank", " ");
        map.put("flagBool", Boolean.FALSE);
        assertEquals(Boolean.FALSE, deepGetBool.invoke(null, map, "flagFalse"));
        assertEquals(Boolean.FALSE, deepGetBool.invoke(null, map, "flagBool"));
        assertNull(deepGetBool.invoke(null, map, "flagInvalid"));
        assertNull(deepGetBool.invoke(null, map, "flagBlank"));

        Method deepGetString = ModerationVecAutoRunner.class.getDeclaredMethod("deepGetString", Map.class, String.class);
        deepGetString.setAccessible(true);
        map.put("textBlank", " ");
        map.put("text", "abc");
        assertNull(deepGetString.invoke(null, map, "none"));
        assertNull(deepGetString.invoke(null, map, "textBlank"));
        assertEquals("abc", deepGetString.invoke(null, map, "text"));

        Method deepGetDouble = ModerationVecAutoRunner.class.getDeclaredMethod("deepGetDouble", Map.class, String.class);
        deepGetDouble.setAccessible(true);
        map.put("num", 2);
        map.put("numString", "2.5");
        map.put("numBad", "x");
        assertNull(deepGetDouble.invoke(null, map, "none"));
        assertEquals(2.0, deepGetDouble.invoke(null, map, "num"));
        assertEquals(2.5, deepGetDouble.invoke(null, map, "numString"));
        assertNull(deepGetDouble.invoke(null, map, "numBad"));
    }

    private static Fixture fixture() {
        ModerationQueueRepository queueRepository = mock(ModerationQueueRepository.class);
        ModerationSimilarityService similarityService = mock(ModerationSimilarityService.class);
        ModerationConfidenceFallbackConfigRepository fallbackRepository = mock(ModerationConfidenceFallbackConfigRepository.class);
        ModerationPolicyConfigRepository policyConfigRepository = mock(ModerationPolicyConfigRepository.class);
        AdminModerationQueueService queueService = mock(AdminModerationQueueService.class);
        ModerationContentTextLoader textLoader = mock(ModerationContentTextLoader.class);
        ModerationPipelineTraceService pipelineTraceService = mock(ModerationPipelineTraceService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
        ModerationVecAutoRunner runner = new ModerationVecAutoRunner(
                queueRepository,
                similarityService,
                fallbackRepository,
                policyConfigRepository,
                queueService,
                textLoader,
                pipelineTraceService,
                auditLogWriter
        );
        return new Fixture(runner, queueRepository, similarityService, fallbackRepository, policyConfigRepository, queueService, textLoader, pipelineTraceService, auditLogWriter);
    }

    private static ModerationQueueEntity queue(Long id, QueueStage stage, QueueStatus status, Integer priority, LocalDateTime createdAt) {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(id);
        q.setCurrentStage(stage);
        q.setStatus(status);
        q.setContentType(ContentType.POST);
        q.setContentId(1000L + id);
        q.setPriority(priority);
        q.setCreatedAt(createdAt);
        q.setUpdatedAt(LocalDateTime.now());
        return q;
    }

    private static ModerationConfidenceFallbackConfigEntity cfg(
            Boolean enabled,
            ModerationConfidenceFallbackConfigEntity.Action hitAction,
            ModerationConfidenceFallbackConfigEntity.Action missAction,
            Double threshold
    ) {
        ModerationConfidenceFallbackConfigEntity cfg = new ModerationConfidenceFallbackConfigEntity();
        cfg.setLlmEnabled(enabled != null ? enabled : Boolean.TRUE);
        cfg.setLlmRejectThreshold(0.8);
        cfg.setLlmHumanThreshold(0.4);
        cfg.setChunkLlmRejectThreshold(0.8);
        cfg.setChunkLlmHumanThreshold(0.4);
        cfg.setLlmTextRiskThreshold(0.8);
        cfg.setLlmImageRiskThreshold(0.3);
        cfg.setLlmStrongRejectThreshold(0.95);
        cfg.setLlmStrongPassThreshold(0.1);
        cfg.setLlmCrossModalThreshold(0.75);
        cfg.setReportHumanThreshold(5);
        cfg.setChunkThresholdChars(20_000);
        cfg.setVersion(0);
        cfg.setUpdatedAt(LocalDateTime.now());
        return cfg;
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

    private static ModerationPolicyConfigEntity policy(Map<String, Object> config) {
        ModerationPolicyConfigEntity policy = new ModerationPolicyConfigEntity();
        policy.setConfig(config);
        policy.setContentType(ContentType.POST);
        policy.setPolicyVersion("test-v1");
        policy.setUpdatedAt(LocalDateTime.now());
        return policy;
    }

    private record Fixture(
            ModerationVecAutoRunner runner,
            ModerationQueueRepository queueRepository,
            ModerationSimilarityService similarityService,
            ModerationConfidenceFallbackConfigRepository fallbackRepository,
            ModerationPolicyConfigRepository policyConfigRepository,
            AdminModerationQueueService queueService,
            ModerationContentTextLoader textLoader,
            ModerationPipelineTraceService pipelineTraceService,
            AuditLogWriter auditLogWriter
    ) {
    }
}
