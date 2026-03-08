package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.entity.access.AuditLogsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.access.AuditLogsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.security.Permissions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.access-refresh.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class AdminModerationReviewTraceControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ModerationQueueRepository moderationQueueRepository;

    @Autowired
    ModerationPipelineRunRepository runRepository;

    @Autowired
    ModerationPipelineStepRepository stepRepository;

    @Autowired
    ModerationChunkSetRepository chunkSetRepository;

    @Autowired
    AuditLogsRepository auditLogsRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void list_shouldDeny_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/moderation/review-trace/tasks")
                        .with(user("u@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAndDetail_shouldWork_withPermission() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
                "INSERT INTO users(id,email,username,password_hash,status,is_deleted,created_at,updated_at) VALUES (1,'u1@example.com','u1','x','ACTIVE',0,NOW(3),NOW(3)) ON DUPLICATE KEY UPDATE id=id"
        );

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.POST);
        q.setContentId(Math.abs(System.nanoTime()));
        q.setStatus(QueueStatus.HUMAN);
        q.setCurrentStage(QueueStage.HUMAN);
        q.setPriority(0);
        q.setAssignedToId(null);
        q.setLockedBy(null);
        q.setLockedAt(null);
        q.setFinishedAt(null);
        q.setCreatedAt(now.minusMinutes(3));
        q.setUpdatedAt(now.plusMinutes(10));
        q = moderationQueueRepository.save(q);

        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setQueueId(q.getId());
        run.setContentType(q.getContentType());
        run.setContentId(q.getContentId());
        run.setStatus(ModerationPipelineRunEntity.RunStatus.SUCCESS);
        run.setFinalDecision(ModerationPipelineRunEntity.FinalDecision.HUMAN);
        run.setTraceId("trace_" + q.getId());
        run.setStartedAt(now.minusMinutes(2));
        run.setEndedAt(now.minusMinutes(1));
        run.setTotalMs(60_000L);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.setLlmModel("gpt-test");
        run.setCreatedAt(now.minusMinutes(2));
        run = runRepository.save(run);

        ModerationPipelineStepEntity rule = new ModerationPipelineStepEntity();
        rule.setRunId(run.getId());
        rule.setStage(ModerationPipelineStepEntity.Stage.RULE);
        rule.setStepOrder(1);
        rule.setDecision("PASS");
        rule.setScore(null);
        rule.setThreshold(null);
        rule.setDetailsJson(Map.of(
                "antiSpamHit", true,
                "antiSpamType", "COMMENT_WINDOW_RATE",
                "actualCount", 5,
                "threshold", 3,
                "windowSeconds", 60
        ));
        rule.setStartedAt(now.minusMinutes(2));
        rule.setEndedAt(now.minusMinutes(2).plusSeconds(1));
        rule.setCostMs(1000L);
        stepRepository.save(rule);

        ModerationPipelineStepEntity vec = new ModerationPipelineStepEntity();
        vec.setRunId(run.getId());
        vec.setStage(ModerationPipelineStepEntity.Stage.VEC);
        vec.setStepOrder(2);
        vec.setDecision("MISS");
        vec.setScore(new BigDecimal("0.1234"));
        vec.setThreshold(new BigDecimal("0.2000"));
        vec.setDetailsJson(Map.of("distance", 0.33));
        vec.setStartedAt(now.minusMinutes(2).plusSeconds(1));
        vec.setEndedAt(now.minusMinutes(2).plusSeconds(2));
        vec.setCostMs(1000L);
        stepRepository.save(vec);

        ModerationPipelineStepEntity llm = new ModerationPipelineStepEntity();
        llm.setRunId(run.getId());
        llm.setStage(ModerationPipelineStepEntity.Stage.LLM);
        llm.setStepOrder(3);
        llm.setDecision("HUMAN");
        llm.setScore(new BigDecimal("0.88"));
        llm.setThreshold(new BigDecimal("0.75"));
        llm.setDetailsJson(Map.of("chunked", true, "chunkSetId", 1));
        llm.setStartedAt(now.minusMinutes(2).plusSeconds(2));
        llm.setEndedAt(now.minusMinutes(1));
        llm.setCostMs(58_000L);
        stepRepository.save(llm);

        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setQueueId(q.getId());
        set.setCaseType(q.getCaseType());
        set.setContentType(q.getContentType());
        set.setContentId(q.getContentId());
        set.setStatus(ChunkSetStatus.DONE);
        set.setTotalChunks(3);
        set.setCompletedChunks(3);
        set.setFailedChunks(0);
        set.setMemoryJson(Map.of("maxScore", 0.99));
        set.setConfigJson(Map.of("chunkSizeChars", 4000));
        set.setCancelledAt(null);
        set.setCreatedAt(now.minusMinutes(2));
        set.setUpdatedAt(now.minusMinutes(1));
        set.setVersion(0);
        chunkSetRepository.save(set);

        AuditLogsEntity manual = new AuditLogsEntity();
        manual.setTenantId(null);
        manual.setActorUserId(1L);
        manual.setAction("MODERATION_MANUAL_TO_HUMAN");
        manual.setEntityType("MODERATION_QUEUE");
        manual.setEntityId(q.getId());
        manual.setResult(AuditResult.SUCCESS);
        manual.setDetails(Map.of("actorName", "tester", "message", "manual"));
        manual.setCreatedAt(now.minusMinutes(1));
        auditLogsRepository.save(manual);

        String perm = Permissions.perm("admin_moderation_logs", "read");
        mockMvc.perform(get("/api/admin/moderation/review-trace/tasks")
                        .with(user("u@example.com").authorities(new SimpleGrantedAuthority(perm))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].queueId").value(q.getId()))
                .andExpect(jsonPath("$.content[0].latestRunId").value(run.getId()))
                .andExpect(jsonPath("$.content[0].rule.stage").value("RULE"))
                .andExpect(jsonPath("$.content[0].rule.details.antiSpamHit").value(true))
                .andExpect(jsonPath("$.content[0].rule.details.antiSpamType").value("COMMENT_WINDOW_RATE"))
                .andExpect(jsonPath("$.content[0].rule.details.actualCount").value(5))
                .andExpect(jsonPath("$.content[0].rule.details.threshold").value(3))
                .andExpect(jsonPath("$.content[0].vec.stage").value("VEC"))
                .andExpect(jsonPath("$.content[0].llm.stage").value("LLM"))
                .andExpect(jsonPath("$.content[0].chunk.chunked").value(true))
                .andExpect(jsonPath("$.content[0].manual.hasManual").value(true));

        mockMvc.perform(get("/api/admin/moderation/review-trace/tasks/{queueId}", q.getId())
                        .with(user("u@example.com").authorities(new SimpleGrantedAuthority(perm))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue.id").value(q.getId()))
                .andExpect(jsonPath("$.latestRun.run.id").value(run.getId()))
                                .andExpect(jsonPath("$.latestRun.steps[0].details.antiSpamType").value("COMMENT_WINDOW_RATE"))
                                .andExpect(jsonPath("$.latestRun.steps[0].details.actualCount").value(5))
                                .andExpect(jsonPath("$.latestRun.steps[0].details.threshold").value(3))
                .andExpect(jsonPath("$.chunkSet.queueId").value(q.getId()))
                .andExpect(jsonPath("$.auditLogs.length()").isNumber());
    }
}
