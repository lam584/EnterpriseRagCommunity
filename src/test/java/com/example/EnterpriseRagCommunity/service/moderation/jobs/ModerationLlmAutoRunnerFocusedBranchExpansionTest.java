package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
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
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModerationLlmAutoRunnerFocusedBranchExpansionTest {

    @Test
    void runOnce_shouldReturnWhenConfigMissingOrDisabled() {
        Fixture f = fixture();

        when(f.llmConfigRepository.findAll()).thenReturn(List.of(), List.of(cfg(false)));

        f.runner.runOnce();
        f.runner.runOnce();

        verifyNoInteractions(f.queueRepository, f.queueService, f.llmService);
    }

    @Test
    void runOnce_shouldScanHumanStageWhenLlmPendingEmptyAndProcessUpTo20() {
        Fixture f = fixture();
        when(f.llmConfigRepository.findAll()).thenReturn(List.of(cfg(true)));

        List<ModerationQueueEntity> humanPending = new ArrayList<>();
        for (int i = 1; i <= 24; i++) {
            ModerationQueueEntity q = queue((long) i, QueueStage.HUMAN, (i % 2 == 0) ? QueueStatus.PENDING : QueueStatus.REVIEWING);
            q.setPriority(i % 3);
            q.setCreatedAt(LocalDateTime.now().minusSeconds(i));
            humanPending.add(q);
            when(f.queueRepository.findById((long) i)).thenReturn(Optional.of(q), Optional.of(q));
        }
        when(f.queueRepository.findAllByCurrentStage(QueueStage.LLM)).thenReturn(List.of());
        when(f.queueRepository.findAllByCurrentStage(QueueStage.HUMAN)).thenReturn(humanPending);
        when(f.queueRepository.tryLockForAutoRun(anyLong(), any(), any(), any(), anyString(), any(), any())).thenReturn(0);

        f.runner.runOnce();

        verify(f.queueRepository, atLeastOnce()).findAllByCurrentStage(QueueStage.HUMAN);
        verify(f.queueRepository, atLeastOnce()).tryLockForAutoRun(anyLong(), any(), any(), any(), anyString(), any(), any());
    }

    @Test
    void runOnce_shouldIgnoreScanError() {
        Fixture f = fixture();
        when(f.llmConfigRepository.findAll()).thenReturn(List.of(cfg(true)));
        when(f.queueRepository.findAllByCurrentStage(QueueStage.LLM)).thenThrow(new RuntimeException("scan failed"));

        f.runner.runOnce();

        verify(f.queueRepository).findAllByCurrentStage(QueueStage.LLM);
    }

    @Test
    void selectPostImageRefs_shouldFilterNonImageAndDeduplicateUrl() throws Exception {
        Fixture f = fixture();
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("selectPostImageRefs", Long.class);
        m.setAccessible(true);

        FileAssetsEntity img = new FileAssetsEntity();
        img.setMimeType("image/png");
        img.setUrl(" https://img/a.png ");
        FileAssetsEntity text = new FileAssetsEntity();
        text.setMimeType("text/plain");
        text.setUrl("https://text/a.txt");
        FileAssetsEntity dup = new FileAssetsEntity();
        dup.setMimeType("image/jpeg");
        dup.setUrl("https://img/a.png");

        PostAttachmentsEntity a1 = new PostAttachmentsEntity();
        a1.setFileAssetId(11L);
        a1.setFileAsset(img);
        PostAttachmentsEntity a2 = new PostAttachmentsEntity();
        a2.setFileAssetId(12L);
        a2.setFileAsset(text);
        PostAttachmentsEntity a3 = new PostAttachmentsEntity();
        a3.setFileAssetId(13L);
        a3.setFileAsset(dup);

        when(f.postAttachmentsRepository.findByPostId(anyLong(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(a1, a2, a3)));

        @SuppressWarnings("unchecked")
        List<ModerationLlmAutoRunner.ChunkImageRef> refs = (List<ModerationLlmAutoRunner.ChunkImageRef>) m.invoke(f.runner, 100L);

        assertEquals(1, refs.size());
        assertEquals("[[IMAGE_1]]", refs.getFirst().placeholder);
        assertEquals(11L, refs.getFirst().fileAssetId);
    }

    @Test
    void detectHardRejectFromPostFiles_shouldReturnCodeFromErrorOrMeta() throws Exception {
        Fixture f = fixture();
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("detectHardRejectFromPostFiles", Long.class);
        m.setAccessible(true);

        PostAttachmentsEntity a1 = new PostAttachmentsEntity();
        a1.setFileAssetId(21L);
        when(f.postAttachmentsRepository.findByPostId(anyLong(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(a1)));

        FileAssetExtractionsEntity failed = new FileAssetExtractionsEntity();
        failed.setFileAssetId(21L);
        failed.setExtractStatus(FileAssetExtractionStatus.FAILED);
        failed.setExtractedMetadataJson("{\"code\":\"ARCHIVE_NESTING_TOO_DEEP\"}");
        when(f.fileAssetExtractionsRepository.findAllById(List.of(21L))).thenReturn(List.of(failed));

        Object code = m.invoke(f.runner, 200L);
        assertEquals("ARCHIVE_NESTING_TOO_DEEP", code);
    }

    @Test
    void normalizeChunkEvidenceForLabels_shouldNormalizeAndDeduplicateAnchors() throws Exception {
        Fixture f = fixture();
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("normalizeChunkEvidenceForLabels", List.class, String.class, List.class);
        m.setAccessible(true);

        String e1 = "{\"before_context\":\"违规词\",\"after_context\":\"结束后文\",\"text\":\"x\"}";
        String e2 = "{\"before_context\":\"违规词\",\"after_context\":\"结束后文\",\"text\":\"x\"}";
        String chunk = "前文 违规词 这里是命中的片段 结束后文";

        @SuppressWarnings("unchecked")
        List<String> out = (List<String>) m.invoke(f.runner, List.of(e1, e2, "普通证据"), chunk, List.of());

        assertEquals(2, out.size());
        assertTrue(out.getFirst().contains("text"));
        assertTrue(out.getLast().contains("普通证据"));
    }

    @Test
    void ensureAnchorEvidenceContainsText_shouldKeepRawWhenInvalidJson() throws Exception {
        Fixture f = fixture();
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("ensureAnchorEvidenceContainsText", String.class, String.class);
        m.setAccessible(true);

        String raw = "{bad json}";
        String out = (String) m.invoke(f.runner, raw, "chunk");
        assertEquals(raw, out);
    }

    private static ModerationLlmConfigEntity cfg(boolean autoRun) {
        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setAutoRun(autoRun);
        return cfg;
    }

    private static ModerationQueueEntity queue(Long id, QueueStage stage, QueueStatus status) {
        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(id);
        q.setCurrentStage(stage);
        q.setStatus(status);
        q.setContentType(POST);
        q.setContentId(300L + id);
        q.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        q.setUpdatedAt(LocalDateTime.now().minusSeconds(30));
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
                postAttachmentsRepository,
                fileAssetExtractionsRepository
        );
    }

    private record Fixture(
            ModerationLlmAutoRunner runner,
            ModerationLlmConfigRepository llmConfigRepository,
            ModerationQueueRepository queueRepository,
            AdminModerationLlmService llmService,
            AdminModerationQueueService queueService,
            PostAttachmentsRepository postAttachmentsRepository,
            FileAssetExtractionsRepository fileAssetExtractionsRepository
    ) {
    }
}
