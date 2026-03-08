package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.access.AuditLogsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.access.AuditLogsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "security.access-refresh.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class ModerationLlmAutoRunnerChunkedAuditLogTest {

    @Autowired
    ModerationLlmAutoRunner runner;

    @Autowired
    ModerationQueueRepository queueRepository;

    @Autowired
    ModerationPipelineRunRepository runRepository;

    @Autowired
    ModerationChunkSetRepository chunkSetRepository;

    @Autowired
    AuditLogsRepository auditLogsRepository;

    @MockitoBean
    AdminModerationLlmService llmService;

    @MockitoBean
    AdminModerationQueueService queueService;

    @MockitoBean
    RiskLabelingService riskLabelingService;

    @Test
    void handleChunked_shouldWriteAuditLog_onGlobalReject() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(Math.abs(System.nanoTime()));
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.LLM);
        q.setPriority(0);
        q.setAssignedToId(null);
        q.setLockedBy(null);
        q.setLockedAt(null);
        q.setFinishedAt(null);
        q.setCreatedAt(now.minusMinutes(3));
        q.setUpdatedAt(now.minusMinutes(3));
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
        set.setMemoryJson(Map.of("maxScore", 0.99));
        set.setConfigJson(Map.of());
        set.setCancelledAt(null);
        set.setCreatedAt(now.minusMinutes(2));
        set.setUpdatedAt(now.minusMinutes(2));
        set.setVersion(0);
        set = chunkSetRepository.save(set);

        LlmModerationTestResponse resp = new LlmModerationTestResponse();
        resp.setDecision("REJECT");
        resp.setScore(0.99);
        resp.setReasons(List.of("x"));
        resp.setRiskTags(List.of("r"));
        resp.setModel("gpt-test");
        when(llmService.test(ArgumentMatchers.any())).thenReturn(resp);
        when(queueService.autoReject(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenReturn(null);
        doNothing().when(riskLabelingService).replaceRiskTags(ArgumentMatchers.any(), ArgumentMatchers.anyLong(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyBoolean());

        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod(
                "handleChunked",
                ModerationQueueEntity.class,
                ModerationPipelineRunEntity.class,
                com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity.class,
                Long.class,
                Map.class,
                Long.class,
                Map.class,
                String.class
        );
        m.setAccessible(true);

        com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity fb = new com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity();
        fb.setLlmRejectThreshold(0.75);
        fb.setLlmHumanThreshold(0.5);
        fb.setThresholds(Map.of("chunk.global.enable", true));

        m.invoke(runner, q, run, fb, null, Map.of(), set.getId(), Map.of(), "TEXT");

        List<AuditLogsEntity> logs = auditLogsRepository.findByEntityTypeAndEntityId("MODERATION_QUEUE", q.getId());
        assertThat(logs).isNotEmpty();
        assertThat(logs.stream().anyMatch(e -> "LLM_DECISION".equals(e.getAction()) && e.getDetails() != null && "chunked".equals(String.valueOf(e.getDetails().get("mode"))))).isTrue();
    }
}
