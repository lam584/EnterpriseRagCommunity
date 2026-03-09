package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkProgressDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModerationLlmAutoRunnerAuditAndSelectionMatrixTest {

    @SuppressWarnings("unchecked")
    @Test
    void writeChunkedDecisionAuditLog_shouldAggregateAllOptionalDetails() throws Exception {
        Fixture f = fixture();
        Method m = method("writeChunkedDecisionAuditLog",
                ModerationQueueEntity.class,
                ModerationPipelineRunEntity.class,
                Long.class,
                Long.class,
                String.class,
                LlmModerationTestResponse.class,
                Map.class
        );

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(501L);
        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setId(9001L);
        run.setTraceId("trace-9001");
        LlmModerationTestResponse global = new LlmModerationTestResponse();
        global.setModel("gpt-x");
        global.setDecision("REJECT");
        global.setScore(0.93);

        AdminModerationChunkProgressDTO progress = new AdminModerationChunkProgressDTO();
        progress.setStatus("RUNNING");
        progress.setTotalChunks(4);
        progress.setCompletedChunks(2);
        progress.setFailedChunks(1);
        progress.setRunningChunks(1);
        when(f.chunkReviewService.getProgress(501L, false, 0)).thenReturn(progress);

        ModerationPipelineStepEntity step = new ModerationPipelineStepEntity();
        step.setCostMs(1200L);
        when(f.pipelineStepRepository.findById(300L)).thenReturn(Optional.of(step));

        when(f.chunkReviewService.getMemory(700L)).thenReturn(Map.of("maxScore", 0.99));

        m.invoke(f.runner, q, run, 300L, 700L, "HUMAN", global, Map.of("scope", "chunks"));

        ArgumentCaptor<Map<String, Object>> detailsCap = ArgumentCaptor.forClass(Map.class);
        verify(f.auditLogWriter).writeSystem(
                eq("LLM_DECISION"),
                eq("MODERATION_QUEUE"),
                eq(501L),
                eq(AuditResult.SUCCESS),
                eq("LLM chunked decision: HUMAN"),
                eq("trace-9001"),
                detailsCap.capture()
        );
        Map<String, Object> details = detailsCap.getValue();
        assertEquals(9001L, details.get("runId"));
        assertEquals("chunked", details.get("mode"));
        assertEquals("REJECT", details.get("globalDecision"));
        assertEquals(300L, details.get("llmStepId"));
        assertEquals(700L, details.get("chunkSetId"));
        assertEquals(0.99, details.get("maxScore"));
        assertEquals("chunks", details.get("scope"));
        assertTrue(details.containsKey("avgLatencyMs"));
    }

    @Test
    void writeChunkedDecisionAuditLog_shouldSwallowInnerExceptionsAndStillWrite() throws Exception {
        Fixture f = fixture();
        Method m = method("writeChunkedDecisionAuditLog",
                ModerationQueueEntity.class,
                ModerationPipelineRunEntity.class,
                Long.class,
                Long.class,
                String.class,
                LlmModerationTestResponse.class,
                Map.class
        );

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(502L);
        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setId(9002L);
        run.setTraceId("trace-9002");

        when(f.chunkReviewService.getProgress(anyLong(), eq(false), eq(0))).thenThrow(new RuntimeException("p"));
        when(f.chunkReviewService.getMemory(anyLong())).thenThrow(new RuntimeException("m"));

        assertDoesNotThrow(() -> m.invoke(f.runner, q, run, 11L, 22L, "APPROVE", null, Map.of()));
        verify(f.auditLogWriter).writeSystem(eq("LLM_DECISION"), eq("MODERATION_QUEUE"), eq(502L), eq(AuditResult.SUCCESS), eq("LLM chunked decision: APPROVE"), eq("trace-9002"), anyMap());
    }

    @Test
    void writeChunkedDecisionAuditLog_shouldReturnEarlyWhenRequiredInputsMissing() throws Exception {
        Fixture f = fixture();
        Method m = method("writeChunkedDecisionAuditLog",
                ModerationQueueEntity.class,
                ModerationPipelineRunEntity.class,
                Long.class,
                Long.class,
                String.class,
                LlmModerationTestResponse.class,
                Map.class
        );

        ModerationQueueEntity qNoId = new ModerationQueueEntity();
        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setTraceId("trace");

        m.invoke(f.runner, null, run, null, null, "HUMAN", null, null);
        m.invoke(f.runner, qNoId, run, null, null, "HUMAN", null, null);
        m.invoke(f.runner, qNoId, null, null, null, "HUMAN", null, null);

        verifyNoInteractions(f.auditLogWriter);
    }

    @SuppressWarnings("unchecked")
    @Test
    void selectEvidenceDrivenChunkImages_shouldFilterByPastChunksAndPlaceholders() throws Exception {
        Method m = method("selectEvidenceDrivenChunkImages", Map.class, Integer.class, List.class);

        List<ModerationLlmAutoRunner.ChunkImageRef> candidates = List.of(
                new ModerationLlmAutoRunner.ChunkImageRef(1, "[[IMAGE_1]]", "https://img/1.png", "image/png", 1L),
                new ModerationLlmAutoRunner.ChunkImageRef(2, "[[IMAGE_2]]", "https://img/2.png", "image/png", 1L),
                new ModerationLlmAutoRunner.ChunkImageRef(3, "[[IMAGE_3]]", "https://img/3.png", "image/png", 1L),
                new ModerationLlmAutoRunner.ChunkImageRef(4, null, "https://img/4.png", "image/png", 1L)
        );
        Map<String, Object> mem = Map.of(
                "llmEvidenceByChunk", Map.of(
                        "1", List.of("{\"text\":\"见图 [[IMAGE_1]]\"}", "{\"text\":\"再次 [[IMAGE_2]]\"}"),
                        2, List.of("ignored [[IMAGE_3]]"),
                        "9", List.of("future [[IMAGE_2]]")
                )
        );

        Object selection = m.invoke(null, mem, 3, candidates);
        List<ModerationLlmAutoRunner.ChunkImageRef> selectedRefs = (List<ModerationLlmAutoRunner.ChunkImageRef>) field(selection, "selectedRefs");
        List<Integer> sourceChunkIndexes = (List<Integer>) field(selection, "sourceChunkIndexes");
        List<String> placeholders = (List<String>) field(selection, "placeholders");

        assertEquals(3, selectedRefs.size());
        assertEquals(List.of(2, 1), sourceChunkIndexes);
        assertTrue(placeholders.contains("[[IMAGE_1]]"));
        assertTrue(placeholders.contains("[[IMAGE_2]]"));
        assertTrue(placeholders.contains("[[IMAGE_3]]"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void selectEvidenceDrivenChunkImages_shouldReturnEmptyWhenNoMatchedPlaceholder() throws Exception {
        Method m = method("selectEvidenceDrivenChunkImages", Map.class, Integer.class, List.class);
        List<ModerationLlmAutoRunner.ChunkImageRef> candidates = List.of(
                new ModerationLlmAutoRunner.ChunkImageRef(1, "[[IMAGE_1]]", "https://img/1.png", "image/png", 1L)
        );
        Map<String, Object> mem = Map.of(
                "llmEvidenceByChunk", Map.of("1", List.of("no image token here"))
        );

        Object selection = m.invoke(null, mem, 2, candidates);
        List<ModerationLlmAutoRunner.ChunkImageRef> selectedRefs = (List<ModerationLlmAutoRunner.ChunkImageRef>) field(selection, "selectedRefs");
        List<Integer> sourceChunkIndexes = (List<Integer>) field(selection, "sourceChunkIndexes");
        List<String> placeholders = (List<String>) field(selection, "placeholders");

        assertTrue(selectedRefs.isEmpty());
        assertTrue(sourceChunkIndexes.isEmpty());
        assertTrue(placeholders.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "'',other",
            "'data:image/png;base64,abc',data_url",
            "/uploads/x.png,local_upload",
            "https://a.b/img.png,remote_url",
            "abc://x,other"
    })
    void classifyImageUrlKind_shouldCoverKindMatrix(String rawUrl, String expected) throws Exception {
        Method m = method("classifyImageUrlKind", String.class);
        String out = (String) m.invoke(null, rawUrl);
        assertEquals(expected, out);
    }

    @Test
    void parseUsedImageIndices_andFilterChunkEvidence_shouldCoverEdgeValues() {
        assertTrue(ModerationLlmAutoRunner.parseUsedImageIndices(null).isEmpty());
        assertEquals(2, ModerationLlmAutoRunner.parseUsedImageIndices("A [[IMAGE_2]] B [[IMAGE_5]] [[IMAGE_2]]").size());
        List<String> filtered = ModerationLlmAutoRunner.filterChunkEvidence(Arrays.asList("  a ", "", null, "b"));
        assertEquals(List.of("a", "b"), filtered);
        assertFalse(filtered.contains(""));
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static Method method(String name, Class<?>... types) throws Exception {
        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
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
        return new Fixture(runner, auditLogWriter, chunkReviewService, pipelineStepRepository);
    }

    private record Fixture(
            ModerationLlmAutoRunner runner,
            AuditLogWriter auditLogWriter,
            ModerationChunkReviewService chunkReviewService,
            ModerationPipelineStepRepository pipelineStepRepository
    ) {
    }
}
