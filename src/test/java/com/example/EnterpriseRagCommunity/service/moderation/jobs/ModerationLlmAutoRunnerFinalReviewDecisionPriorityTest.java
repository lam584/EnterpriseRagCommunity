package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.*;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.RiskLabelingService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "security.access-refresh.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class ModerationLlmAutoRunnerFinalReviewDecisionPriorityTest {

    @Autowired
    ModerationLlmAutoRunner runner;

    @Autowired
    ModerationQueueRepository queueRepository;

    @Autowired
    ModerationPipelineRunRepository runRepository;

    @Autowired
    ModerationPipelineStepRepository stepRepository;

    @Autowired
    ModerationChunkSetRepository chunkSetRepository;

    @Autowired
    ModerationChunkRepository chunkRepository;

    @Autowired
    PromptsRepository promptsRepository;

    @MockitoBean
    AdminModerationLlmService llmService;

    @MockitoBean
    AdminModerationQueueService queueService;

    @MockitoBean
    RiskLabelingService riskLabelingService;

    @Test
    void handleChunked_finalReviewDecisionHumanShouldNotAutoApprove_whenScoreMissing() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        ensureFinalReviewPromptExists(now);

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(Math.abs(System.nanoTime()));
        q.setStatus(QueueStatus.REVIEWING);
        q.setCurrentStage(QueueStage.LLM);
        q.setPriority(0);
        q.setAssignedToId(null);
        q.setLockedBy(null);
        q.setLockedAt(null);
        q.setFinishedAt(null);
        q.setCreatedAt(now.minusMinutes(3));
        q.setUpdatedAt(now.minusMinutes(3));
        q.setVersion(0);
        q = queueRepository.save(q);

        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setQueueId(q.getId());
        run.setContentType(q.getContentType());
        run.setContentId(q.getContentId());
        run.setStatus(ModerationPipelineRunEntity.RunStatus.RUNNING);
        run.setFinalDecision(null);
        run.setTraceId("trace_" + q.getId());
        run.setStartedAt(now.minusMinutes(2));
        run.setEndedAt(null);
        run.setTotalMs(null);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.setCreatedAt(now.minusMinutes(2));
        run = runRepository.save(run);

        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setQueueId(q.getId());
        set.setCaseType(q.getCaseType());
        set.setContentType(q.getContentType());
        set.setContentId(q.getContentId());
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setTotalChunks(3);
        set.setCompletedChunks(0);
        set.setFailedChunks(0);
        set.setMemoryJson(Map.of("maxScore", 0.98, "riskTags", List.of("violence")));
        set.setConfigJson(Map.of());
        set.setCancelledAt(null);
        set.setCreatedAt(now.minusMinutes(2));
        set.setUpdatedAt(now.minusMinutes(2));
        set.setVersion(0);
        set = chunkSetRepository.save(set);

        ModerationChunkEntity c0 = mkChunk(set.getId(), "post", 0, ChunkStatus.SUCCESS, Verdict.REVIEW);
        ModerationChunkEntity c1 = mkChunk(set.getId(), "post", 1, ChunkStatus.SUCCESS, Verdict.REVIEW);
        ModerationChunkEntity c2 = mkChunk(set.getId(), "post", 2, ChunkStatus.SUCCESS, Verdict.REVIEW);
        chunkRepository.saveAll(List.of(c0, c1, c2));

        LlmModerationTestResponse finalReview = new LlmModerationTestResponse();
        finalReview.setDecision("HUMAN");
        finalReview.setScore(null);
        finalReview.setReasons(List.of("Text moderation output invalid, routed to HUMAN"));
        finalReview.setEvidence(List.of("[[IMAGE_99]]"));
        finalReview.setModel("gpt-test");

        when(llmService.test(ArgumentMatchers.any())).thenReturn(finalReview);
        doNothing().when(riskLabelingService).replaceRiskTags(ArgumentMatchers.any(), ArgumentMatchers.anyLong(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());

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

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setLlmRejectThreshold(0.75);
        fb.setLlmHumanThreshold(0.5);
        fb.setChunkLlmRejectThreshold(0.85);
        fb.setChunkLlmHumanThreshold(0.6);
        fb.setThresholds(Map.of(
                "chunk.imageStage.enable", false,
                "chunk.global.enable", false,
                "chunk.finalReview.enable", true,
                "chunk.finalReview.triggerScoreMin", 0.2,
                "chunk.finalReview.triggerRiskTagCount", 0L,
                "chunk.finalReview.triggerOpenQuestions", false
        ));

        m.invoke(runner, q, run, fb, null, Map.of(), set.getId(), Map.of(), "TEXT");

        verify(queueService, never()).autoApprove(anyLong(), anyString(), anyString());
        verify(queueService, never()).autoReject(anyLong(), anyString(), anyString());

        ModerationQueueEntity refreshed = queueRepository.findById(q.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(QueueStatus.HUMAN);
        assertThat(refreshed.getCurrentStage()).isEqualTo(QueueStage.HUMAN);

        ModerationPipelineStepEntity llmFinalStep = stepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(run.getId(), ModerationPipelineStepEntity.Stage.LLM)
            .stream()
            .reduce((first, second) -> second)
            .orElseThrow();
        Object evidenceObj = llmFinalStep.getDetailsJson() == null ? null : llmFinalStep.getDetailsJson().get("evidence");
        assertThat(evidenceObj).isInstanceOf(Collection.class);
        @SuppressWarnings("unchecked")
        Collection<Object> evidence = (Collection<Object>) evidenceObj;
        assertThat(evidence)
            .isNotEmpty()
            .anySatisfy(v -> assertThat(String.valueOf(v)).contains("[[IMAGE_99]]"));
    }

    private void ensureFinalReviewPromptExists(LocalDateTime now) {
        boolean exists = promptsRepository.findByPromptCode("MODERATION_JUDGE").isPresent();
        if (exists) return;

        PromptsEntity p = new PromptsEntity();
        p.setName("MODERATION_JUDGE");
        p.setPromptCode("MODERATION_JUDGE");
        p.setUserPromptTemplate("Return JSON.");
        p.setSystemPrompt(null);
        p.setModelName(null);
        p.setProviderId(null);
        p.setTemperature(null);
        p.setTopP(null);
        p.setMaxTokens(null);
        p.setEnableDeepThinking(null);
        p.setVariables(Map.of());
        p.setVersion(0);
        p.setIsActive(true);
        p.setCreatedAt(now);
        p.setUpdatedBy(null);
        promptsRepository.save(p);
    }

    private static ModerationChunkEntity mkChunk(Long chunkSetId, String sourceKey, int idx, ChunkStatus st, Verdict verdict) {
        LocalDateTime now = LocalDateTime.now();
        ModerationChunkEntity c = new ModerationChunkEntity();
        c.setChunkSetId(chunkSetId);
        c.setSourceType(ChunkSourceType.POST_TEXT);
        c.setSourceKey(sourceKey);
        c.setChunkIndex(idx);
        c.setStartOffset(idx * 100);
        c.setEndOffset(idx * 100 + 99);
        c.setStatus(st);
        c.setAttempts(1);
        c.setVerdict(verdict);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        c.setVersion(0);
        return c;
    }
}

