package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkProgressDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
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
import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService;
import com.example.EnterpriseRagCommunity.service.moderation.RiskLabelingService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.example.EnterpriseRagCommunity.service.moderation.trace.ModerationPipelineTraceService;
import com.example.EnterpriseRagCommunity.service.monitor.FileAssetExtractionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.PageImpl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModerationLlmAutoRunnerChunkedLambdaBranchUnitTest {

    @Test
    void handleOne_shouldReturnWhenLockNotAcquired() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(301L, QueueStage.LLM, QueueStatus.PENDING);
        when(f.queueRepository.findById(301L)).thenReturn(Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        invokeHandleOne(f.runner, q, llmCfg());

        verify(f.queueRepository, never()).unlockAutoRun(anyLong(), anyString(), any());
        verify(f.fallbackRepository, never()).findFirstByOrderByUpdatedAtDescIdDesc();
    }

    @Test
    void handleChunked_shouldRouteHumanWhenAnyChunkFailed() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(302L, QueueStage.LLM, QueueStatus.REVIEWING);
        ModerationPipelineRunEntity run = run(9302L, "trace-9302");
        ModerationPipelineStepEntity step = step(93021L);
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step);
        when(f.chunkReviewService.countPendingOrFailed(66L)).thenReturn(0L);
        when(f.chunkReviewService.getProgress(302L, true, 300)).thenReturn(progress("DONE", 2, 1, 1, 0));

        invokeHandleChunked(f.runner, q, run, fb(), 93021L, Map.of(), 66L, Map.of(), "TEXT");

        verify(f.queueRepository).updateStageAndStatusIfPendingOrReviewing(eq(302L), eq(QueueStage.HUMAN), eq(QueueStatus.HUMAN), any());
        verify(f.pipelineTraceService).finishRunSuccess(9302L, com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity.FinalDecision.HUMAN);
    }

    @Test
    void handleChunked_lambda_shouldCoverClaimEmptyAndFailureBranches() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(303L, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(9303L, "trace-9303");
        ModerationPipelineStepEntity step = step(93031L);
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step);
        when(f.chunkReviewService.countPendingOrFailed(77L)).thenReturn(1L);
        when(f.chunkReviewService.getConfig()).thenReturn(chunkCfg(false));
        when(f.chunkReviewService.listEligibleChunks(77L)).thenReturn(List.of(
                new ModerationChunkReviewService.ChunkCandidate(7001L, ChunkSourceType.FILE_TEXT, null, "a.txt", 2, 0, 40, 0),
                new ModerationChunkReviewService.ChunkCandidate(7002L, ChunkSourceType.FILE_TEXT, null, "b.txt", 3, 0, 50, 0)
        ));
        when(f.chunkReviewService.claimChunkById(7001L)).thenReturn(Optional.empty());
        when(f.chunkReviewService.claimChunkById(7002L)).thenReturn(Optional.of(
                new ModerationChunkReviewService.ChunkToProcess(7002L, ChunkSourceType.FILE_TEXT, null, "b.txt", 3, 0, 50)
        ));
        when(f.chunkReviewService.loadChunkText(eq(303L), eq(ChunkSourceType.FILE_TEXT), eq(null), eq(0), eq(50))).thenReturn(Optional.of("chunk-body"));
        when(f.llmService.test(any())).thenThrow(new RuntimeException("llm-fail"));

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<Object> supplier =
                    (LlmCallQueueService.CheckedTaskSupplier<Object>) invocation.getArgument(6);
            try {
                supplier.get(task);
            } catch (Exception ignore) {
            }
            return CompletableFuture.completedFuture(null);
        }).when(f.llmCallQueueService).submitDedup(any(), any(), any(), anyInt(), anyString(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any());

        invokeHandleChunked(f.runner, q, run, fb(), 93031L, Map.of(), 77L, Map.of(), "TEXT");

        verify(f.llmCallQueueService, times(2)).submitDedup(eq(LlmQueueTaskType.MODERATION_CHUNK), eq(null), eq(null), eq(0), anyString(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any());
        verify(f.chunkReviewService).markChunkFailed(eq(7002L), eq("llm-fail"), eq(false));
        verify(f.chunkReviewService).refreshSetCountersDebounced(eq(77L), eq(1000L));
        verify(task, atLeastOnce()).reportInput(anyString());
        verify(task, atLeastOnce()).reportOutput(anyString());
    }

    @Test
    void handleChunked_metricsExtractor_shouldReturnUsageMetrics() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(304L, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(9304L, "trace-9304");
        ModerationPipelineStepEntity step = step(93041L);
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step);
        when(f.chunkReviewService.countPendingOrFailed(88L)).thenReturn(1L);
        when(f.chunkReviewService.getConfig()).thenReturn(chunkCfg(false));
        when(f.chunkReviewService.listEligibleChunks(88L)).thenReturn(List.of(
                new ModerationChunkReviewService.ChunkCandidate(8001L, ChunkSourceType.FILE_TEXT, null, "c.txt", 1, 0, 10, 0)
        ));
        when(f.chunkReviewService.claimChunkById(8001L)).thenReturn(Optional.empty());

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<Object> supplier =
                    (LlmCallQueueService.CheckedTaskSupplier<Object>) invocation.getArgument(6);
            @SuppressWarnings("unchecked")
            LlmCallQueueService.ResultMetricsExtractor<Object> extractor =
                    (LlmCallQueueService.ResultMetricsExtractor<Object>) invocation.getArgument(7);
            assertDoesNotThrow(() -> supplier.get(task));
            com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse r = new com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse();
            com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse.Usage u = new com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse.Usage();
            u.setPromptTokens(11);
            u.setCompletionTokens(7);
            u.setTotalTokens(18);
            r.setUsage(u);
            LlmCallQueueService.UsageMetrics metrics = extractor.extract(r);
            org.junit.jupiter.api.Assertions.assertEquals(11, metrics.promptTokens());
            org.junit.jupiter.api.Assertions.assertEquals(7, metrics.completionTokens());
            org.junit.jupiter.api.Assertions.assertEquals(18, metrics.totalTokens());
            return CompletableFuture.completedFuture(null);
        }).when(f.llmCallQueueService).submitDedup(any(), any(), any(), anyInt(), anyString(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any());

        invokeHandleChunked(f.runner, q, run, fb(), 93041L, Map.of(), 88L, Map.of(), "TEXT");
    }

    @Test
    void handleChunked_lambda_shouldCoverSuccessWritebackPath() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(305L, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(9305L, "trace-9305");
        ModerationPipelineStepEntity step = step(93051L);
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step);
        when(f.chunkReviewService.countPendingOrFailed(99L)).thenReturn(1L);
        ModerationChunkReviewConfigDTO cfg = chunkCfg(true);
        cfg.setSendImagesOnlyWhenInEvidence(false);
        cfg.setIncludeImagesBlockOnlyForEvidenceMatches(false);
        when(f.chunkReviewService.getConfig()).thenReturn(cfg);
        when(f.chunkReviewService.listEligibleChunks(99L)).thenReturn(List.of(
                new ModerationChunkReviewService.ChunkCandidate(9001L, ChunkSourceType.FILE_TEXT, null, "ok.txt", null, 0, 60, 1)
        ));
        when(f.chunkReviewService.claimChunkById(9001L)).thenReturn(Optional.of(
                new ModerationChunkReviewService.ChunkToProcess(9001L, ChunkSourceType.FILE_TEXT, null, "ok.txt", null, 0, 60)
        ));
        when(f.chunkReviewService.loadChunkText(eq(305L), eq(ChunkSourceType.FILE_TEXT), eq(null), eq(0), eq(60)))
                .thenReturn(Optional.of("正文 [[IMAGE_1]] 示例"));
        when(f.chunkReviewService.getMemory(99L)).thenReturn(Map.of("riskTags", List.of("safe")));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(any())).thenReturn(List.of());

        LlmModerationTestResponse res = new LlmModerationTestResponse();
        res.setModel("m-task5");
        res.setDecision("APPROVE");
        res.setDecisionSuggestion("ALLOW");
        res.setScore(0.2);
        res.setRiskScore(0.2);
        res.setEvidence(List.of("ev1"));
        LlmModerationTestResponse.Usage usage = new LlmModerationTestResponse.Usage();
        usage.setPromptTokens(12);
        usage.setCompletionTokens(5);
        usage.setTotalTokens(17);
        res.setUsage(usage);
        when(f.llmService.test(any())).thenReturn(res);

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<Object> supplier =
                    (LlmCallQueueService.CheckedTaskSupplier<Object>) invocation.getArgument(6);
            @SuppressWarnings("unchecked")
            LlmCallQueueService.ResultMetricsExtractor<Object> extractor =
                    (LlmCallQueueService.ResultMetricsExtractor<Object>) invocation.getArgument(7);
            Object out = supplier.get(task);
            assertDoesNotThrow(() -> extractor.extract(out));
            return CompletableFuture.completedFuture(null);
        }).when(f.llmCallQueueService).submitDedup(any(), any(), any(), anyInt(), anyString(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any());

        invokeHandleChunked(f.runner, q, run, fb(), 93051L, Map.of(), 99L, policyConfig(), "TEXT");

        verify(f.chunkReviewService).markChunkSuccess(eq(9001L), eq("m-task5"), any(), eq(0.2), any(), eq(12), eq(5), eq(false));
        verify(task, atLeastOnce()).reportModel("m-task5");
        verify(task, atLeastOnce()).reportOutput(anyString());
    }

    @Test
    void handleChunked_shouldSendCurrentChunkImagesEvenWhenEvidenceOnlyModeHasNoPastImageEvidence() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(3051L, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(19305L, "trace-19305");
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step(193051L));
        when(f.chunkReviewService.countPendingOrFailed(199L)).thenReturn(1L);
        when(f.chunkReviewService.getConfig()).thenReturn(chunkCfg(true));
        when(f.chunkReviewService.listEligibleChunks(199L)).thenReturn(List.of(
                new ModerationChunkReviewService.ChunkCandidate(9051L, ChunkSourceType.FILE_TEXT, 8811L, "guide.docx", 6, 0, 120, 0)
        ));
        when(f.chunkReviewService.claimChunkById(9051L)).thenReturn(Optional.of(
                new ModerationChunkReviewService.ChunkToProcess(9051L, ChunkSourceType.FILE_TEXT, 8811L, "guide.docx", 6, 0, 120)
        ));
        when(f.chunkReviewService.loadChunkText(eq(3051L), eq(ChunkSourceType.FILE_TEXT), eq(8811L), eq(0), eq(120)))
                .thenReturn(Optional.of("正文开始 [[IMAGE_19]] 正文结束"));
        when(f.chunkReviewService.getMemory(199L)).thenReturn(Map.of("riskTags", List.of("safe")));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(any())).thenReturn(List.of());

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(8811L);
        ex.setExtractedMetadataJson("""
                {
                  "extractedImages": [
                    {"index": 18, "placeholder": "[[IMAGE_18]]", "url": "https://img/unit-18.jpg", "mimeType": "image/jpeg"},
                    {"index": 19, "placeholder": "[[IMAGE_19]]", "url": "https://img/unit-19.png", "mimeType": "image/png"},
                    {"index": 20, "placeholder": "[[IMAGE_20]]", "url": "https://img/unit-20.jpg", "mimeType": "image/jpeg"}
                  ]
                }
                """);
        when(f.fileAssetExtractionsRepository.findById(8811L)).thenReturn(Optional.of(ex));

        LlmModerationTestResponse res = new LlmModerationTestResponse();
        res.setModel("m-image");
        res.setDecision("REJECT");
        res.setScore(0.98);
        when(f.llmService.test(any())).thenReturn(res);

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<Object> supplier =
                    (LlmCallQueueService.CheckedTaskSupplier<Object>) invocation.getArgument(6);
            supplier.get(task);
            return CompletableFuture.completedFuture(null);
        }).when(f.llmCallQueueService).submitDedup(any(), any(), any(), anyInt(), anyString(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any());

        invokeHandleChunked(f.runner, q, run, fb(), 193051L, Map.of(), 199L, policyConfig(), "TEXT");

        ArgumentCaptor<com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest> reqCaptor =
                ArgumentCaptor.forClass(com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest.class);
        verify(f.llmService).test(reqCaptor.capture());
        var sentReq = reqCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(1, sentReq.getImages().size());
        org.junit.jupiter.api.Assertions.assertEquals("https://img/unit-19.png", sentReq.getImages().get(0).getUrl());
        org.junit.jupiter.api.Assertions.assertEquals(8811L, sentReq.getImages().get(0).getFileAssetId());
    }

    @Test
    void handleChunked_shouldRejectEarlyWhenImageStageIsStrongReject() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(306L, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(9306L, "trace-9306");
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step(93061L));
        LlmModerationTestResponse imageRes = new LlmModerationTestResponse();
        imageRes.setModel("img-model");
        imageRes.setDecision("REJECT");
        imageRes.setScore(0.99);
        LlmModerationTestResponse.Stages stages = new LlmModerationTestResponse.Stages();
        LlmModerationTestResponse.Stage image = new LlmModerationTestResponse.Stage();
        image.setScore(0.99);
        stages.setImage(image);
        imageRes.setStages(stages);
        when(f.llmService.testImageOnly(any())).thenReturn(imageRes);

        ModerationConfidenceFallbackConfigEntity fb = fb();
        fb.setThresholds(Map.of(
                "chunk.global.enable", false,
                "chunk.imageStage.enable", true,
                "chunk.finalReview.enable", false,
                "chunk.withImages.imageStrongRejectThreshold", 0.9
        ));

        invokeHandleChunked(f.runner, q, run, fb, 93061L, Map.of(), 100L, Map.of(), "TEXT");

        verify(f.queueService).autoReject(eq(306L), anyString(), eq("trace-9306"));
        verify(f.pipelineTraceService).finishRunSuccess(9306L, ModerationPipelineRunEntity.FinalDecision.REJECT);
    }

    @ParameterizedTest
    @CsvSource({
            "REJECT,0.95,REJECT",
            "HUMAN,0.55,HUMAN"
    })
    void handleChunked_shouldFinalizeByGlobalVerdict(String decision, double score, String expectedFinal) throws Exception {
        Fixture f = fixture();
        long queueId = "REJECT".equalsIgnoreCase(decision) ? 311L : 312L;
        long runId = "REJECT".equalsIgnoreCase(decision) ? 9311L : 9312L;
        ModerationQueueEntity q = queue(queueId, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(runId, "trace-" + runId);
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step(runId * 10 + 1));

        ModerationConfidenceFallbackConfigEntity fb = fb();
        fb.setThresholds(Map.of(
                "chunk.global.enable", true,
                "chunk.imageStage.enable", false,
                "chunk.finalReview.enable", false,
                "chunk.finalReview.triggerScoreMin", 0.8,
                "chunk.finalReview.triggerRiskTagCount", 2,
                "chunk.finalReview.triggerOpenQuestions", false
        ));

        LlmModerationTestResponse globalRes = new LlmModerationTestResponse();
        globalRes.setDecision(decision);
        globalRes.setScore(score);
        globalRes.setModel("global-model");
        globalRes.setRiskTags(List.of("r1"));
        when(f.llmService.test(any())).thenReturn(globalRes);

        invokeHandleChunked(f.runner, q, run, fb, runId * 10 + 1, Map.of(), 1311L, policyConfig(), "TEXT");

        if ("REJECT".equals(expectedFinal)) {
            verify(f.queueService).autoReject(eq(queueId), anyString(), eq("trace-" + runId));
            verify(f.pipelineTraceService).finishRunSuccess(runId, ModerationPipelineRunEntity.FinalDecision.REJECT);
        } else {
            verify(f.queueRepository).updateStageAndStatusIfPendingOrReviewing(eq(queueId), eq(QueueStage.HUMAN), eq(QueueStatus.HUMAN), any());
            verify(f.pipelineTraceService).finishRunSuccess(runId, ModerationPipelineRunEntity.FinalDecision.HUMAN);
            verifyNoInteractions(f.queueService);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "APPROVE,0.15,APPROVE",
            "REJECT,0.99,REJECT",
            "HUMAN,0.55,HUMAN"
    })
    void handleChunked_shouldFinalizeByFinalReviewVerdict(String decision, double score, String expectedFinal) throws Exception {
        Fixture f = fixture();
        long queueId = 320L;
        long runId = 9320L + ("REJECT".equals(decision) ? 1 : "HUMAN".equals(decision) ? 2 : 0);
        ModerationQueueEntity q = queue(queueId + runId % 10, QueueStage.LLM, QueueStatus.REVIEWING);
        ModerationPipelineRunEntity run = run(runId, "trace-" + runId);
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step(runId * 10 + 1));
        when(f.chunkReviewService.countPendingOrFailed(1320L + runId % 10)).thenReturn(0L);
        when(f.chunkReviewService.getProgress(q.getId(), true, 300)).thenReturn(progress("DONE", 2, 2, 0, 0));
        when(f.chunkReviewService.getMemory(1320L + runId % 10)).thenReturn(Map.of(
                "maxScore", 0.98,
                "riskTags", List.of("risk-a", "risk-b"),
                "openQuestions", List.of("q1"),
                "llmEvidenceByChunk", Map.of("1", List.of("ev1"))
        ));

        ModerationConfidenceFallbackConfigEntity fb = fb();
        fb.setThresholds(Map.of(
                "chunk.global.enable", false,
                "chunk.imageStage.enable", false,
                "chunk.finalReview.enable", true,
                "chunk.finalReview.triggerScoreMin", 0.4,
                "chunk.finalReview.triggerRiskTagCount", 1,
                "chunk.finalReview.triggerOpenQuestions", true
        ));

        LlmModerationTestResponse finalReviewRes = new LlmModerationTestResponse();
        finalReviewRes.setDecision(decision);
        finalReviewRes.setScore(score);
        finalReviewRes.setEvidence(List.of("e1", "e2"));
        finalReviewRes.setRiskTags(List.of("tag-x"));
        finalReviewRes.setModel("final-model");
        when(f.llmService.test(any())).thenReturn(finalReviewRes);
        ModerationLlmConfigEntity llmConfig = llmCfg();
        llmConfig.setJudgePromptCode("MODERATION_JUDGE");
        when(f.llmConfigRepository.findAll()).thenReturn(List.of(llmConfig));
        PromptsEntity prompt = new PromptsEntity();
        prompt.setUserPromptTemplate("Return JSON only.");
        when(f.promptsRepository.findByPromptCode("MODERATION_JUDGE")).thenReturn(Optional.of(prompt));

        invokeHandleChunked(f.runner, q, run, fb, runId * 10 + 1, Map.of(), 1320L + runId % 10, policyConfig(), "TEXT");

        if ("APPROVE".equals(expectedFinal)) {
            verify(f.queueService).autoApprove(eq(q.getId()), eq(""), eq("trace-" + runId));
            verify(f.pipelineTraceService).finishRunSuccess(runId, ModerationPipelineRunEntity.FinalDecision.APPROVE);
        } else if ("REJECT".equals(expectedFinal)) {
            verify(f.queueService).autoReject(eq(q.getId()), anyString(), eq("trace-" + runId));
            verify(f.pipelineTraceService).finishRunSuccess(runId, ModerationPipelineRunEntity.FinalDecision.REJECT);
        } else {
            verify(f.queueRepository).updateStageAndStatusIfPendingOrReviewing(eq(q.getId()), eq(QueueStage.HUMAN), eq(QueueStatus.HUMAN), any());
            verify(f.pipelineTraceService).finishRunSuccess(runId, ModerationPipelineRunEntity.FinalDecision.HUMAN);
        }
    }

    @ParameterizedTest
    @MethodSource("aggregateVerdictCases")
    void handleChunked_shouldFinalizeByAggregateVerdict_whenFinalReviewBypassed(
            String chunkVerdict,
            Double chunkScore,
            double memoryMaxScore,
            String expectedFinal
    ) throws Exception {
        Fixture f = fixture();
        long queueId = 360L;
        long runId = 9360L + ("REJECT".equals(chunkVerdict) ? 1 : "REVIEW".equals(chunkVerdict) ? 2 : (memoryMaxScore >= 0.5 ? 3 : 0));
        ModerationQueueEntity q = queue(queueId + runId % 10, QueueStage.LLM, QueueStatus.REVIEWING);
        ModerationPipelineRunEntity run = run(runId, "trace-" + runId);
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step(runId * 10 + 1));
        when(f.chunkReviewService.countPendingOrFailed(1360L + runId % 10)).thenReturn(0L);

        AdminModerationChunkProgressDTO p = progress("DONE", 2, 2, 0, 0);
        p.setChunks(List.of(
                chunkItem(chunkVerdict, chunkScore),
                chunkItem("APPROVE", 0.1)
        ));
        when(f.chunkReviewService.getProgress(q.getId(), true, 300)).thenReturn(p);
        when(f.chunkReviewService.getMemory(1360L + runId % 10)).thenReturn(Map.of(
                "maxScore", memoryMaxScore,
                "riskTags", List.of("r-low")
        ));

        ModerationConfidenceFallbackConfigEntity fb = fb();
        fb.setThresholds(Map.of(
                "chunk.global.enable", false,
                "chunk.imageStage.enable", false,
                "chunk.finalReview.enable", true,
                "chunk.finalReview.triggerScoreMin", 0.99,
                "chunk.finalReview.triggerRiskTagCount", 99,
                "chunk.finalReview.triggerOpenQuestions", false
        ));

        invokeHandleChunked(f.runner, q, run, fb, runId * 10 + 1, Map.of(), 1360L + runId % 10, policyConfig(), "TEXT");

        if ("APPROVE".equals(expectedFinal)) {
            verify(f.queueService).autoApprove(eq(q.getId()), eq(""), eq("trace-" + runId));
            verify(f.pipelineTraceService).finishRunSuccess(runId, ModerationPipelineRunEntity.FinalDecision.APPROVE);
        } else if ("REJECT".equals(expectedFinal)) {
            verify(f.queueService).autoReject(eq(q.getId()), anyString(), eq("trace-" + runId));
            verify(f.pipelineTraceService).finishRunSuccess(runId, ModerationPipelineRunEntity.FinalDecision.REJECT);
        } else {
            verify(f.queueRepository).updateStageAndStatusIfPendingOrReviewing(eq(q.getId()), eq(QueueStage.HUMAN), eq(QueueStatus.HUMAN), any());
            verify(f.pipelineTraceService).finishRunSuccess(runId, ModerationPipelineRunEntity.FinalDecision.HUMAN);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "POST_TEXT,true,true",
            "FILE_TEXT,false,false"
    })
    void handleChunked_lambda_shouldCoverImageSelectionModeMatrix(
            String sourceType,
            boolean sendImagesOnlyWhenInEvidence,
            boolean includeImagesBlockOnlyForEvidenceMatches
    ) throws Exception {
        Fixture f = fixture();
        long queueId = "POST_TEXT".equals(sourceType) ? 370L : 371L;
        long runId = "POST_TEXT".equals(sourceType) ? 9370L : 9371L;
        long chunkSetId = "POST_TEXT".equals(sourceType) ? 1370L : 1371L;
        long chunkId = "POST_TEXT".equals(sourceType) ? 9170L : 9171L;
        Long fileAssetId = "POST_TEXT".equals(sourceType) ? null : 8811L;
        ChunkSourceType st = ChunkSourceType.valueOf(sourceType);

        ModerationQueueEntity q = queue(queueId, QueueStage.LLM, QueueStatus.PENDING);
        q.setContentType(ContentType.POST);
        q.setContentId(77001L + runId % 10);
        ModerationPipelineRunEntity run = run(runId, "trace-" + runId);
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step(runId * 10 + 1));
        when(f.chunkReviewService.countPendingOrFailed(chunkSetId)).thenReturn(1L);
        ModerationChunkReviewConfigDTO cfg = chunkCfg(true);
        cfg.setSendImagesOnlyWhenInEvidence(sendImagesOnlyWhenInEvidence);
        cfg.setIncludeImagesBlockOnlyForEvidenceMatches(includeImagesBlockOnlyForEvidenceMatches);
        when(f.chunkReviewService.getConfig()).thenReturn(cfg);
        when(f.chunkReviewService.listEligibleChunks(chunkSetId)).thenReturn(List.of(
                new ModerationChunkReviewService.ChunkCandidate(chunkId, st, fileAssetId, "mx.txt", 1, 0, 80, 0)
        ));
        when(f.chunkReviewService.claimChunkById(chunkId)).thenReturn(Optional.of(
                new ModerationChunkReviewService.ChunkToProcess(chunkId, st, fileAssetId, "mx.txt", 1, 0, 80)
        ));
        when(f.chunkReviewService.loadChunkText(eq(queueId), eq(st), eq(fileAssetId), eq(0), eq(80)))
                .thenReturn(Optional.of("正文 [[IMAGE_1]] 命中内容"));
        when(f.chunkReviewService.getMemory(chunkSetId)).thenReturn(Map.of(
                "riskTags", List.of("tag-x"),
                "llmEvidenceByChunk", Map.of("1", List.of("[[IMAGE_1]]"))
        ));
        when(f.tagsRepository.findByTypeAndIsActiveTrue(any())).thenReturn(List.of());

        FileAssetsEntity imageAsset = new FileAssetsEntity();
        imageAsset.setMimeType("image/png");
        imageAsset.setUrl("https://img/unit-1.png");
        PostAttachmentsEntity postImage = new PostAttachmentsEntity();
        postImage.setFileAssetId(551L);
        postImage.setFileAsset(imageAsset);
        when(f.postAttachmentsRepository.findByPostId(eq(q.getContentId()), any()))
                .thenReturn(new PageImpl<>(List.of(postImage)));

        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(8811L);
        ex.setExtractedMetadataJson("{\"images\":[{\"index\":1,\"placeholder\":\"[[IMAGE_1]]\",\"url\":\"https://img/meta-1.png\"}]}");
        when(f.fileAssetExtractionsRepository.findById(8811L)).thenReturn(Optional.of(ex));

        LlmModerationTestResponse res = new LlmModerationTestResponse();
        res.setDecision("HUMAN");
        res.setScore(0.55);
        res.setModel("m-matrix");
        res.setEvidence(List.of("{\"before_context\":\"正文\",\"after_context\":\"命中内容\",\"text\":\"[[IMAGE_1]]\"}", "普通证据"));
        LlmModerationTestResponse.Stages stages = new LlmModerationTestResponse.Stages();
        stages.setImage(new LlmModerationTestResponse.Stage());
        stages.setJudge(new LlmModerationTestResponse.Stage());
        stages.setUpgrade(new LlmModerationTestResponse.Stage());
        res.setStages(stages);
        when(f.llmService.test(any())).thenReturn(res);

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<Object> supplier =
                    (LlmCallQueueService.CheckedTaskSupplier<Object>) invocation.getArgument(6);
            supplier.get(task);
            return CompletableFuture.completedFuture(null);
        }).when(f.llmCallQueueService).submitDedup(any(), any(), any(), anyInt(), anyString(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any());

        invokeHandleChunked(f.runner, q, run, fb(), runId * 10 + 1, Map.of(), chunkSetId, policyConfig(), "TEXT");

        verify(f.chunkReviewService).markChunkSuccess(eq(chunkId), eq("m-matrix"), any(), eq(0.55), any(), eq(null), eq(null), eq(false));
        verify(f.chunkReviewService).refreshSetCountersDebounced(eq(chunkSetId), eq(1000L));
    }

    @ParameterizedTest
    @CsvSource({
            "true,true,true",
            "true,true,false",
            "false,true,true"
    })
    void handleOne_shouldFallbackToNormalLlmPathWhenChunkWorkCannotEnterChunked(
            boolean enabled,
            boolean chunked,
            boolean cancelled
    ) throws Exception {
        Fixture f = fixture();
        long queueId = 380L + (enabled ? 1 : 0) + (cancelled ? 10 : 0);
        long runId = 9380L + (enabled ? 1 : 0) + (cancelled ? 10 : 0);
        ModerationQueueEntity q = queue(queueId, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(runId, "trace-" + runId);
        ModerationPipelineStepEntity rule = step(runId * 10 + 1);
        rule.setDecision("PASS");
        ModerationPipelineStepEntity vec = step(runId * 10 + 2);
        vec.setDecision("MISS");
        ModerationChunkReviewService.ChunkWorkResult chunkWork = new ModerationChunkReviewService.ChunkWorkResult();
        chunkWork.enabled = enabled;
        chunkWork.chunked = chunked;
        chunkWork.cancelled = cancelled;
        chunkWork.chunkSetId = cancelled ? 1880L : null;

        LlmModerationTestResponse res = new LlmModerationTestResponse();
        res.setDecisionSuggestion("ALLOW");
        res.setScore(0.1);
        res.setRiskScore(0.1);

        when(f.queueRepository.findById(queueId)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(f.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy("v1")));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run, run);
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(runId, ModerationPipelineStepEntity.Stage.RULE))
                .thenReturn(List.of(rule), List.of(rule));
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(runId, ModerationPipelineStepEntity.Stage.VEC))
                .thenReturn(List.of(vec), List.of(vec));
        ModerationConfidenceFallbackConfigEntity fb = fb();
        fb.setLlmEnabled(true);
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));
        when(f.chunkReviewService.prepareChunksIfNeeded(q)).thenReturn(chunkWork);
        when(f.postAttachmentsRepository.findByPostId(eq(q.getContentId()), any())).thenReturn(new PageImpl<>(List.of()));
        when(f.llmService.test(any())).thenReturn(res);

        invokeHandleOne(f.runner, q, llmCfg());

        verify(f.llmCallQueueService, never()).submitDedup(any(), any(), any(), anyInt(), anyString(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any());
    }

    @Test
    void handleChunked_lambda_shouldFallbackToFailedWhenSuccessWritebackThrows() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(313L, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(9313L, "trace-9313");
        when(f.pipelineTraceService.startStep(anyLong(), any(), anyInt(), any(), any())).thenReturn(step(93131L));
        when(f.chunkReviewService.countPendingOrFailed(1313L)).thenReturn(1L);
        when(f.chunkReviewService.getConfig()).thenReturn(chunkCfg(false));
        when(f.chunkReviewService.listEligibleChunks(1313L)).thenReturn(List.of(
                new ModerationChunkReviewService.ChunkCandidate(9131L, ChunkSourceType.FILE_TEXT, null, "w.txt", 9, 0, 80, 0)
        ));
        when(f.chunkReviewService.claimChunkById(9131L)).thenReturn(Optional.of(
                new ModerationChunkReviewService.ChunkToProcess(9131L, ChunkSourceType.FILE_TEXT, null, "w.txt", 9, 0, 80)
        ));
        when(f.chunkReviewService.loadChunkText(eq(313L), eq(ChunkSourceType.FILE_TEXT), eq(null), eq(0), eq(80)))
                .thenThrow(new RuntimeException("read-fail"));

        LlmModerationTestResponse res = new LlmModerationTestResponse();
        res.setDecision("APPROVE");
        res.setScore(0.2);
        res.setModel("m-fallback");
        when(f.llmService.test(any())).thenReturn(res);
        doAnswer(invocation -> {
            throw new RuntimeException("writeback-fail");
        }).when(f.chunkReviewService).markChunkSuccess(eq(9131L), any(), any(), any(), any(), any(), any(), eq(false));

        LlmCallQueueService.TaskHandle task = mock(LlmCallQueueService.TaskHandle.class);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            LlmCallQueueService.CheckedTaskSupplier<Object> supplier =
                    (LlmCallQueueService.CheckedTaskSupplier<Object>) invocation.getArgument(6);
            try {
                supplier.get(task);
            } catch (Exception ignore) {
            }
            return CompletableFuture.completedFuture(null);
        }).when(f.llmCallQueueService).submitDedup(any(), any(), any(), anyInt(), anyString(), anyString(), any(LlmCallQueueService.CheckedTaskSupplier.class), any());

        invokeHandleChunked(f.runner, q, run, fb(), 93131L, Map.of(), 1313L, policyConfig(), "TEXT");

        verify(f.chunkReviewService).markChunkFailed(eq(9131L), eq("writeback-fail"), eq(false));
        verify(f.chunkReviewService).refreshSetCountersDebounced(eq(1313L), eq(1000L));
    }

    @Test
    void handleOne_shouldHardRejectWhenAttachmentExtractionFailed() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(307L, QueueStage.LLM, QueueStatus.PENDING);
        q.setContentType(ContentType.POST);
        q.setContentId(70001L);
        ModerationPipelineRunEntity run = run(9307L, "trace-9307");
        ModerationPipelineStepEntity rule = step(93071L);
        rule.setDecision("PASS");
        ModerationPipelineStepEntity vec = step(93072L);
        vec.setDecision("MISS");
        PostAttachmentsEntity att = new PostAttachmentsEntity();
        att.setPostId(70001L);
        att.setFileAssetId(5001L);
        FileAssetExtractionsEntity ex = new FileAssetExtractionsEntity();
        ex.setFileAssetId(5001L);
        ex.setExtractStatus(FileAssetExtractionStatus.FAILED);
        ex.setErrorMessage("ARCHIVE_NESTING_TOO_DEEP");

        when(f.queueRepository.findById(307L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(f.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy("v1")));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run);
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(9307L, ModerationPipelineStepEntity.Stage.RULE)).thenReturn(List.of(rule));
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(9307L, ModerationPipelineStepEntity.Stage.VEC)).thenReturn(List.of(vec));
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb()));
        when(f.postAttachmentsRepository.findByPostId(eq(70001L), any())).thenReturn(new PageImpl<>(List.of(att)));
        when(f.fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(ex));

        invokeHandleOne(f.runner, q, llmCfg());

        verify(f.queueService).autoReject(eq(307L), anyString(), eq("trace-9307"));
        verify(f.pipelineTraceService).finishRunSuccess(9307L, ModerationPipelineRunEntity.FinalDecision.REJECT);
    }

    @Test
    void handleOne_shouldFallbackUpgradeFailureToHuman() throws Exception {
        Fixture f = fixture();
        ModerationQueueEntity q = queue(308L, QueueStage.LLM, QueueStatus.PENDING);
        ModerationPipelineRunEntity run = run(9308L, "trace-9308");
        ModerationPipelineStepEntity rule = step(93081L);
        rule.setDecision("PASS");
        ModerationPipelineStepEntity vec = step(93082L);
        vec.setDecision("MISS");
        LlmModerationTestResponse res = new LlmModerationTestResponse();
        res.setDecisionSuggestion("ALLOW");
        res.setScore(0.2);
        res.setRiskScore(0.2);

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setLlmEnabled(true);
        fb.setLlmRejectThreshold(0.75);
        fb.setLlmHumanThreshold(0.4);
        fb.setThresholds(Map.of());

        when(f.queueRepository.findById(308L)).thenReturn(Optional.of(q), Optional.of(q));
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(f.policyConfigRepository.findByContentType(ContentType.POST)).thenReturn(Optional.of(policy("v1")));
        when(f.pipelineTraceService.ensureRun(any())).thenReturn(run, run);
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(9308L, ModerationPipelineStepEntity.Stage.RULE))
                .thenReturn(List.of(rule), List.of(rule));
        when(f.pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(9308L, ModerationPipelineStepEntity.Stage.VEC))
                .thenReturn(List.of(vec), List.of(vec));
        when(f.fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(fb));
        when(f.llmService.test(any())).thenReturn(res);
        when(f.postAttachmentsRepository.findByPostId(eq(30800L), any())).thenReturn(new PageImpl<>(List.of()));

        invokeHandleOne(f.runner, q, llmCfg());

        verify(f.queueRepository).updateStageAndStatusIfPendingOrReviewing(eq(308L), eq(QueueStage.HUMAN), eq(QueueStatus.HUMAN), any());
        verify(f.pipelineTraceService).finishRunSuccess(9308L, ModerationPipelineRunEntity.FinalDecision.HUMAN);
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

    private static void invokeHandleChunked(
            ModerationLlmAutoRunner runner,
            ModerationQueueEntity q,
            ModerationPipelineRunEntity run,
            ModerationConfidenceFallbackConfigEntity fb,
            Long llmStepId,
            Map<String, Object> prior,
            Long chunkSetId,
            Map<String, Object> policyConfig,
            String reviewStage
    ) throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(
                "handleChunked",
                ModerationQueueEntity.class,
                ModerationPipelineRunEntity.class,
                ModerationConfidenceFallbackConfigEntity.class,
                Long.class,
                Map.class,
                Long.class,
                Map.class,
                String.class
        );
        m.setAccessible(true);
        m.invoke(runner, q, run, fb, llmStepId, prior, chunkSetId, policyConfig, reviewStage);
    }

    private static ModerationChunkReviewConfigDTO chunkCfg(boolean enableGlobalMemory) {
        ModerationChunkReviewConfigDTO cfg = new ModerationChunkReviewConfigDTO();
        cfg.setEnableGlobalMemory(enableGlobalMemory);
        cfg.setSendImagesOnlyWhenInEvidence(true);
        cfg.setIncludeImagesBlockOnlyForEvidenceMatches(true);
        return cfg;
    }

    private static AdminModerationChunkProgressDTO progress(String status, Integer total, Integer completed, Integer failed, Integer running) {
        AdminModerationChunkProgressDTO p = new AdminModerationChunkProgressDTO();
        p.setStatus(status);
        p.setTotalChunks(total);
        p.setCompletedChunks(completed);
        p.setFailedChunks(failed);
        p.setRunningChunks(running);
        return p;
    }

    private static AdminModerationChunkProgressDTO.ChunkItem chunkItem(String verdict, Double score) {
        AdminModerationChunkProgressDTO.ChunkItem i = new AdminModerationChunkProgressDTO.ChunkItem();
        i.setVerdict(verdict);
        i.setScore(score);
        return i;
    }

    private static Stream<Arguments> aggregateVerdictCases() {
        return Stream.of(
                Arguments.of("APPROVE", 0.1, 0.1, "APPROVE"),
                Arguments.of("APPROVE", 0.1, 0.6, "HUMAN"),
                Arguments.of("REJECT", 0.9, 0.9, "REJECT"),
                Arguments.of("REVIEW", null, 0.2, "HUMAN")
        );
    }

    private static ModerationLlmConfigEntity llmCfg() {
        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setAutoRun(true);
        return cfg;
    }

    private static ModerationConfidenceFallbackConfigEntity fb() {
        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setLlmRejectThreshold(0.75);
        fb.setLlmHumanThreshold(0.4);
        fb.setChunkLlmRejectThreshold(0.75);
        fb.setChunkLlmHumanThreshold(0.4);
        fb.setLlmStrongRejectThreshold(0.95);
        fb.setThresholds(Map.of(
                "chunk.global.enable", false,
                "chunk.imageStage.enable", false,
                "chunk.finalReview.enable", false,
                "chunk.finalReview.triggerScoreMin", 0.8,
                "chunk.finalReview.triggerRiskTagCount", 2,
                "chunk.finalReview.triggerOpenQuestions", false
        ));
        return fb;
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

    private static ModerationQueueEntity queue(Long id, QueueStage stage, QueueStatus status) {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(id);
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(id * 100);
        q.setCurrentStage(stage);
        q.setStatus(status);
        q.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        q.setUpdatedAt(LocalDateTime.now().minusMinutes(2));
        return q;
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
                chunkReviewService,
                llmCallQueueService,
                postAttachmentsRepository,
                fileAssetExtractionsRepository,
                promptsRepository,
                tagsRepository
        );
    }

    private static ModerationPolicyConfigEntity policy(String version) {
        ModerationPolicyConfigEntity p = new ModerationPolicyConfigEntity();
        p.setPolicyVersion(version);
        p.setConfig(policyConfig());
        return p;
    }

    private static Map<String, Object> policyConfig() {
        return Map.of(
                "thresholds", Map.of(
                        "default", Map.of("T_allow", 0.3, "T_reject", 0.7)
                ),
                "escalate_rules", Map.of("require_evidence", false)
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
            ModerationChunkReviewService chunkReviewService,
            LlmCallQueueService llmCallQueueService,
            PostAttachmentsRepository postAttachmentsRepository,
            FileAssetExtractionsRepository fileAssetExtractionsRepository,
            com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository promptsRepository,
            TagsRepository tagsRepository
    ) {
    }
}
