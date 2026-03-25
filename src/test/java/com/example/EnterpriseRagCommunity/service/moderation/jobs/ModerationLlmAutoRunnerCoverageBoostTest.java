package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
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
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService;
import com.example.EnterpriseRagCommunity.service.moderation.RiskLabelingService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.example.EnterpriseRagCommunity.service.moderation.trace.ModerationPipelineTraceService;
import com.example.EnterpriseRagCommunity.service.monitor.FileAssetExtractionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationLlmAutoRunnerCoverageBoostTest {

    @SuppressWarnings("unchecked")
    @Test
    void summarizeMethods_shouldCoverNullAndRichPayload() throws Exception {
        Method summarizeStage = method("summarizeLlmStage", LlmModerationTestResponse.Stage.class);
        Method summarizeRes = method("summarizeLlmRes", LlmModerationTestResponse.class);

        assertTrue(((Map<String, Object>) summarizeStage.invoke(null, new Object[]{null})).isEmpty());
        assertTrue(((Map<String, Object>) summarizeRes.invoke(null, new Object[]{null})).isEmpty());

        LlmModerationTestResponse.Stage stage = new LlmModerationTestResponse.Stage();
        stage.setDecisionSuggestion("ALLOW");
        stage.setDecision("APPROVE");
        stage.setRiskScore(0.1);
        stage.setScore(0.2);
        stage.setSeverity("LOW");
        stage.setUncertainty(0.01);
        stage.setLabels(List.of("safe"));
        stage.setRiskTags(List.of("safeTag"));
        stage.setReasons(List.of("r1", "r2", "r3"));
        stage.setEvidence(List.of("e1", "e2", "e3"));
        stage.setInputMode("text");
        stage.setModel("m1");
        stage.setLatencyMs(12L);
        stage.setDescription("desc");
        stage.setRawModelOutput("x".repeat(1500));
        Map<String, Object> stageMap = (Map<String, Object>) summarizeStage.invoke(null, stage);
        assertEquals("ALLOW", stageMap.get("decision_suggestion"));
        assertTrue(String.valueOf(stageMap.get("rawModelOutput")).length() <= 1000);

        LlmModerationTestResponse res = new LlmModerationTestResponse();
        res.setDecisionSuggestion("REJECT");
        res.setDecision("REJECT");
        res.setRiskScore(0.8);
        res.setScore(0.9);
        res.setSeverity("HIGH");
        res.setUncertainty(0.11);
        res.setLabels(List.of("l1"));
        res.setRiskTags(List.of("t1"));
        res.setReasons(List.of("a", "b"));
        res.setEvidence(List.of("ev"));
        res.setInputMode("multimodal");
        res.setModel("m2");
        res.setLatencyMs(19L);
        LlmModerationTestResponse.Usage usage = new LlmModerationTestResponse.Usage();
        usage.setPromptTokens(1);
        res.setUsage(usage);
        LlmModerationTestResponse.Stages stages = new LlmModerationTestResponse.Stages();
        stages.setText(new LlmModerationTestResponse.Stage());
        stages.setImage(new LlmModerationTestResponse.Stage());
        res.setStages(stages);
        res.setRawModelOutput("y".repeat(1800));
        Map<String, Object> resMap = (Map<String, Object>) summarizeRes.invoke(null, res);
        assertEquals(true, resMap.get("hasTextStage"));
        assertEquals(false, resMap.get("hasJudgeStage"));
        assertTrue(String.valueOf(resMap.get("rawModelOutput")).length() <= 1000);
    }

    @Test
    void boolAndVerdictHelpers_shouldCoverKeyBranches() throws Exception {
        Method asBooleanRequired = method("asBooleanRequired", Object.class, String.class);
        Method asBooleanOrDefault = method("asBooleanOrDefault", Object.class, boolean.class);
        Method verdictFromDecisionAndScore = method("verdictFromDecisionAndScore", String.class, Double.class, double.class, double.class);
        Method stricterVerdict = method("stricterVerdict", Verdict.class, Verdict.class);
        Method hasIntersection = method("hasIntersection", List.class, List.class);

        assertEquals(true, asBooleanRequired.invoke(null, "yes", "k"));
        assertEquals(false, asBooleanRequired.invoke(null, 0, "k"));
        assertIllegalState(() -> asBooleanRequired.invoke(null, "maybe", "k"));
        assertEquals(true, asBooleanOrDefault.invoke(null, "1", false));
        assertEquals(false, asBooleanOrDefault.invoke(null, "invalid", false));

        assertEquals(Verdict.REJECT, verdictFromDecisionAndScore.invoke(null, "REJECT", null, 0.7, 0.4));
        assertEquals(Verdict.REVIEW, verdictFromDecisionAndScore.invoke(null, "HUMAN", null, 0.7, 0.4));
        assertEquals(Verdict.APPROVE, verdictFromDecisionAndScore.invoke(null, "APPROVE", null, 0.7, 0.4));
        assertEquals(Verdict.REVIEW, verdictFromDecisionAndScore.invoke(null, "UNKNOWN", null, 0.7, 0.4));
        assertEquals(Verdict.REJECT, stricterVerdict.invoke(null, Verdict.REVIEW, Verdict.REJECT));
        assertEquals(Verdict.REVIEW, stricterVerdict.invoke(null, Verdict.APPROVE, Verdict.REVIEW));
        assertEquals(Verdict.APPROVE, stricterVerdict.invoke(null, Verdict.APPROVE, Verdict.APPROVE));
        assertEquals(false, hasIntersection.invoke(null, List.of("a"), List.of("b")));
        assertEquals(true, hasIntersection.invoke(null, List.of(" a "), List.of("a")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void fileRelatedMethods_shouldCoverReadinessHardRejectAndImageRefs() throws Exception {
        Fixture f = fixture();
        Method checkPostFilesReadiness = method("checkPostFilesReadiness", ModerationQueueEntity.class);
        Method detectHardRejectFromPostFiles = method("detectHardRejectFromPostFiles", Long.class);
        Method selectPostImageRefs = method("selectPostImageRefs", Long.class);

        ModerationQueueEntity wrongType = queue(1L);
        wrongType.setContentType(ContentType.COMMENT);
        Object r0 = checkPostFilesReadiness.invoke(f.runner, wrongType);
        assertEquals(false, readField(r0, "hasAttachments"));

        ModerationQueueEntity q = queue(2L);
        q.setContentType(ContentType.POST);
        q.setContentId(22L);
        when(f.postAttachmentsRepository.findByPostId(anyLong(), any())).thenReturn(new PageImpl<>(List.of(
                attachment(101L, "image/png", "/uploads/a.png"),
                attachment(102L, "application/pdf", "/uploads/a.pdf"),
                attachment(101L, "image/png", "/uploads/a.png"),
                new PostAttachmentsEntity()
        )));
        FileAssetExtractionsEntity pending = extraction(101L, FileAssetExtractionStatus.PENDING, null, null);
        when(f.fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(pending));

        Object r1 = checkPostFilesReadiness.invoke(f.runner, q);
        assertEquals(true, readField(r1, "hasAttachments"));
        List<Long> pendingIds = (List<Long>) readField(r1, "pendingFileAssetIds");
        assertEquals(List.of(101L), pendingIds);

        when(f.fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(
                extraction(102L, FileAssetExtractionStatus.FAILED, "ARCHIVE_NESTING_TOO_DEEP", null)
        ));
        assertEquals("ARCHIVE_NESTING_TOO_DEEP", detectHardRejectFromPostFiles.invoke(f.runner, 22L));

        when(f.fileAssetExtractionsRepository.findAllById(any())).thenReturn(List.of(
                extraction(102L, FileAssetExtractionStatus.FAILED, "x", "{\"code\":\"ARCHIVE_NESTING_TOO_DEEP\"}")
        ));
        assertEquals("ARCHIVE_NESTING_TOO_DEEP", detectHardRejectFromPostFiles.invoke(f.runner, 22L));
        assertNull(detectHardRejectFromPostFiles.invoke(f.runner, new Object[]{null}));

        List<ModerationLlmAutoRunner.ChunkImageRef> refs =
                (List<ModerationLlmAutoRunner.ChunkImageRef>) selectPostImageRefs.invoke(f.runner, 22L);
        assertEquals(1, refs.size());
        assertEquals("[[IMAGE_1]]", refs.get(0).placeholder);
        assertEquals("/uploads/a.png", refs.get(0).url);
    }

    @SuppressWarnings("unchecked")
    @Test
    void evidenceMethods_shouldCoverNormalizationFingerprintAndAnchorFallback() throws Exception {
        Fixture f = fixture();
        Method normalizeChunkEvidenceForLabels = method("normalizeChunkEvidenceForLabels", List.class, String.class, List.class);
        Method ensureAnchorEvidenceContainsText = method("ensureAnchorEvidenceContainsText", String.class, String.class);
        Method evidenceFingerprint = method("evidenceFingerprint", String.class);
        Method fallbackViolationSnippet = method("fallbackViolationSnippet", String.class, int.class);
        Method summarizeEvidenceMemory = method("summarizeEvidenceMemory", Map.class, Integer.class, int.class);
        Method collectChunkEvidenceForStepDetail = method("collectChunkEvidenceForStepDetail", Map.class, int.class);
        Method fingerprintAggregateEvidenceItem = method("fingerprintAggregateEvidenceItem", String.class);

        String evidenceJson = "{\"before_context\":\"违规词\",\"after_context\":\"结束后文\"}";
        String chunkText = "前文 违规词 命中的文本 [[IMAGE_1]] 结束后文";
        String normalized = (String) ensureAnchorEvidenceContainsText.invoke(f.runner, evidenceJson, chunkText);
        assertTrue(normalized.contains("text"));

        List<String> out = (List<String>) normalizeChunkEvidenceForLabels.invoke(
                f.runner,
                List.of(" ", evidenceJson, evidenceJson, "raw evidence"),
            chunkText,
            List.of()
        );
        assertEquals(2, out.size());

        assertTrue(String.valueOf(evidenceFingerprint.invoke(f.runner, normalized)).startsWith("text|"));
        assertTrue(String.valueOf(evidenceFingerprint.invoke(f.runner, "plain text")).startsWith("raw|"));

        String s = (String) fallbackViolationSnippet.invoke(null, "abc [[IMAGE_1]]\n[SEC]\nmore", 0);
        assertNotNull(s);

        Map<String, Object> mem = Map.of(
                "llmEvidenceByChunk",
                Map.of(
                        "1", List.of("{\"text\":\"x\"}", "{\"before_context\":\"A\",\"after_context\":\"B\"}"),
                        2, List.of("line-2", "line-2")
                )
        );
        List<String> sum = (List<String>) summarizeEvidenceMemory.invoke(null, mem, 9, 3);
        assertFalse(sum.isEmpty());
        List<String> detail = (List<String>) collectChunkEvidenceForStepDetail.invoke(null, mem, 3);
        assertEquals(3, detail.size());
        assertTrue(String.valueOf(fingerprintAggregateEvidenceItem.invoke(null, "{\"text\":\"abc\"}")).startsWith("text|"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void thresholdAndTagMethods_shouldCoverPreferredAndApplyPaths() throws Exception {
        Fixture f = fixture();
        Method resolveThresholdsRequired = method("resolveThresholdsRequired", Map.class, String.class, List.class);
        Method resolveThresholdsPreferred = method("resolveThresholdsPreferred", Map.class, String.class, List.class, ModerationConfidenceFallbackConfigEntity.class);
        Method verdictFromLlm = method("verdictFromLlm", LlmModerationTestResponse.class, ModerationConfidenceFallbackConfigEntity.class);
        Method applyRiskTags = method("applyRiskTags", ModerationQueueEntity.class, LlmModerationTestResponse.class);
        Method applyChunkedRiskTags = method("applyChunkedRiskTags", ModerationQueueEntity.class, Long.class, LlmModerationTestResponse.class);
        Method resolveWaitFilesSeconds = method("resolveWaitFilesSeconds", ModerationLlmConfigEntity.class);

        Map<String, Object> policy = Map.of(
                "thresholds", Map.of(
                        "default", Map.of("T_allow", 0.3, "T_reject", 0.8),
                        "by_review_stage", Map.of("reported", Map.of("T_allow", 0.2, "T_reject", 0.6)),
                        "by_label", Map.of("abuse", Map.of("T_allow", 0.1, "T_reject", 0.5))
                )
        );
        Object th = resolveThresholdsRequired.invoke(null, policy, "reported", List.of("abuse"));
        assertEquals("policy.by_review_stage", readField(th, "source"));

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setChunkLlmRejectThreshold(0.9);
        fb.setChunkLlmHumanThreshold(0.4);
        fb.setLlmRejectThreshold(0.8);
        fb.setLlmHumanThreshold(0.3);
        Object preferred = resolveThresholdsPreferred.invoke(null, Map.of(), "default", List.of("x"), fb);
        assertEquals("fallback.chunk_config", readField(preferred, "source"));

        LlmModerationTestResponse v1 = new LlmModerationTestResponse();
        v1.setDecision("REJECT");
        assertEquals(Verdict.REJECT, verdictFromLlm.invoke(null, v1, fb));
        LlmModerationTestResponse v2 = new LlmModerationTestResponse();
        v2.setDecision("HUMAN");
        assertNotNull(verdictFromLlm.invoke(null, v2, fb));

        ModerationQueueEntity q = queue(99L);
        LlmModerationTestResponse tagRes = new LlmModerationTestResponse();
        tagRes.setScore(1.2);
        tagRes.setRiskTags(List.of(" r1 ", "", "r1"));
        tagRes.setLabels(List.of("l1", " "));
        applyRiskTags.invoke(f.runner, q, tagRes);
        verify(f.riskLabelingService).replaceRiskTags(eq(ContentType.POST), eq(9900L), any(), any(), eq(BigDecimal.valueOf(1.0)), eq(false));

        when(f.chunkReviewService.getMemory(77L)).thenReturn(Map.of("riskTags", List.of("m1"), "maxScore", 0.95));
        LlmModerationTestResponse chunkRes = new LlmModerationTestResponse();
        chunkRes.setScore(-1.0);
        chunkRes.setRiskTags(List.of("r2"));
        applyChunkedRiskTags.invoke(f.runner, q, 77L, chunkRes);
        verify(f.riskLabelingService).replaceRiskTags(eq(ContentType.POST), eq(9900L), any(), any(), eq(BigDecimal.valueOf(0.95)), eq(false));

        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setMultimodalPromptCode("VISION_PROMPT");
        PromptsEntity prompt = new PromptsEntity();
        prompt.setWaitFilesSeconds(7200);
        when(f.promptsRepository.findByPromptCode("VISION_PROMPT")).thenReturn(Optional.of(prompt));
        assertEquals(3600, resolveWaitFilesSeconds.invoke(f.runner, cfg));

        prompt.setWaitFilesSeconds(-1);
        assertEquals(0, resolveWaitFilesSeconds.invoke(f.runner, cfg));
        assertEquals(60, resolveWaitFilesSeconds.invoke(f.runner, new Object[]{null}));
    }

    private static Method method(String name, Class<?>... types) throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

    private static void assertIllegalState(ThrowingCall c) {
        try {
            c.call();
            fail("expected IllegalStateException");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        } catch (Exception e) {
            fail("unexpected exception: " + e.getClass().getName());
        }
    }

    private static Object readField(Object obj, String field) throws Exception {
        var f = obj.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(obj);
    }

    private static PostAttachmentsEntity attachment(Long fileAssetId, String mimeType, String url) {
        FileAssetsEntity fa = new FileAssetsEntity();
        fa.setId(fileAssetId);
        fa.setMimeType(mimeType);
        fa.setUrl(url);
        PostAttachmentsEntity a = new PostAttachmentsEntity();
        a.setPostId(22L);
        a.setFileAssetId(fileAssetId);
        a.setFileAsset(fa);
        return a;
    }

    private static FileAssetExtractionsEntity extraction(Long fileAssetId, FileAssetExtractionStatus status, String err, String meta) {
        FileAssetExtractionsEntity e = new FileAssetExtractionsEntity();
        e.setFileAssetId(fileAssetId);
        e.setExtractStatus(status);
        e.setErrorMessage(err);
        e.setExtractedMetadataJson(meta);
        return e;
    }

    private static ModerationQueueEntity queue(Long id) {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(id);
        q.setCurrentStage(QueueStage.LLM);
        q.setStatus(QueueStatus.PENDING);
        q.setContentType(ContentType.POST);
        q.setContentId(id * 100);
        q.setCreatedAt(LocalDateTime.now().minusMinutes(20));
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
                new ObjectMapper()
        );
        return new Fixture(runner, postAttachmentsRepository, fileAssetExtractionsRepository, promptsRepository, riskLabelingService, chunkReviewService);
    }

    private record Fixture(
            ModerationLlmAutoRunner runner,
            PostAttachmentsRepository postAttachmentsRepository,
            FileAssetExtractionsRepository fileAssetExtractionsRepository,
            com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository promptsRepository,
            RiskLabelingService riskLabelingService,
            ModerationChunkReviewService chunkReviewService
    ) {
    }

    private interface ThrowingCall {
        void call() throws Exception;
    }
}
