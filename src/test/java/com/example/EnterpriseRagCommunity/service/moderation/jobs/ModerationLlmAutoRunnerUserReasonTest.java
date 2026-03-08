package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
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
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "security.access-refresh.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class ModerationLlmAutoRunnerUserReasonTest {

    @Autowired
    ModerationLlmAutoRunner runner;

    @Autowired
    ModerationQueueRepository queueRepository;

    @Autowired
    ModerationPipelineRunRepository runRepository;

    @Autowired
    ModerationPipelineStepRepository stepRepository;

    @Autowired
    ModerationPolicyConfigRepository policyConfigRepository;

    @Autowired
    ModerationConfidenceFallbackConfigRepository fallbackRepository;

    @MockitoBean
    AdminModerationLlmService llmService;

    @MockitoBean
    AdminModerationQueueService queueService;

    @MockitoBean
    RiskLabelingService riskLabelingService;

    @Test
    void handleOne_shouldPassConcreteReasonToAutoReject() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setCaseType(ModerationCaseType.CONTENT);
        q.setContentType(ContentType.COMMENT);
        q.setContentId(Math.abs(System.nanoTime()));
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.LLM);
        q.setPriority(0);
        q.setAssignedToId(null);
        q.setLockedBy(null);
        q.setLockedAt(null);
        q.setFinishedAt(null);
        q.setCreatedAt(now.minusMinutes(1));
        q.setUpdatedAt(now.minusMinutes(1));
        q = queueRepository.save(q);

        ModerationPolicyConfigEntity policy = policyConfigRepository.findByContentType(ContentType.COMMENT)
                .orElseGet(ModerationPolicyConfigEntity::new);
        if (policy.getId() == null) {
            policy.setContentType(ContentType.COMMENT);
            policy.setVersion(0);
        }
        policy.setPolicyVersion("test");
        policy.setConfig(Map.of(
                "thresholds", Map.of(
                        "default", Map.of(
                                "T_allow", 0.2,
                                "T_reject", 0.8
                        )
                )
        ));
        policy.setUpdatedAt(now);
        policy.setUpdatedBy(null);
        policyConfigRepository.save(policy);

        ModerationConfidenceFallbackConfigEntity fb = new ModerationConfidenceFallbackConfigEntity();
        fb.setRuleEnabled(true);
        fb.setRuleHighAction(ModerationConfidenceFallbackConfigEntity.Action.LLM);
        fb.setRuleMediumAction(ModerationConfidenceFallbackConfigEntity.Action.LLM);
        fb.setRuleLowAction(ModerationConfidenceFallbackConfigEntity.Action.LLM);
        fb.setVecEnabled(true);
        fb.setVecThreshold(0.5);
        fb.setVecHitAction(ModerationConfidenceFallbackConfigEntity.Action.LLM);
        fb.setVecMissAction(ModerationConfidenceFallbackConfigEntity.Action.LLM);
        fb.setLlmEnabled(true);
        fb.setLlmRejectThreshold(0.8);
        fb.setLlmHumanThreshold(0.6);
        fb.setChunkLlmRejectThreshold(0.8);
        fb.setChunkLlmHumanThreshold(0.6);
        fb.setLlmTextRiskThreshold(0.8);
        fb.setLlmImageRiskThreshold(0.8);
        fb.setLlmStrongRejectThreshold(0.95);
        fb.setLlmStrongPassThreshold(0.05);
        fb.setLlmCrossModalThreshold(0.8);
        fb.setReportHumanThreshold(1);
        fb.setChunkThresholdChars(800);
        fb.setThresholds(Map.of(
                "llm.text.upgrade.enable", false,
                "llm.text.upgrade.scoreMin", 0.45,
                "llm.text.upgrade.scoreMax", 0.55,
                "llm.text.upgrade.uncertaintyMin", 0.8
        ));
        fb.setVersion(0);
        fb.setUpdatedAt(now);
        fb.setUpdatedBy(null);
        fallbackRepository.save(fb);

        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setQueueId(q.getId());
        run.setContentType(q.getContentType());
        run.setContentId(q.getContentId());
        run.setStatus(ModerationPipelineRunEntity.RunStatus.RUNNING);
        run.setFinalDecision(null);
        run.setTraceId("trace_" + q.getId());
        run.setStartedAt(now.minusMinutes(1));
        run.setEndedAt(null);
        run.setTotalMs(null);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.setLlmModel(null);
        run.setCreatedAt(now.minusMinutes(1));
        run = runRepository.save(run);

        ModerationPipelineStepEntity ruleStep = new ModerationPipelineStepEntity();
        ruleStep.setRunId(run.getId());
        ruleStep.setStage(ModerationPipelineStepEntity.Stage.RULE);
        ruleStep.setStepOrder(1);
        ruleStep.setStartedAt(now.minusMinutes(1));
        stepRepository.save(ruleStep);

        ModerationPipelineStepEntity vecStep = new ModerationPipelineStepEntity();
        vecStep.setRunId(run.getId());
        vecStep.setStage(ModerationPipelineStepEntity.Stage.VEC);
        vecStep.setStepOrder(1);
        vecStep.setStartedAt(now.minusMinutes(1));
        stepRepository.save(vecStep);

        LlmModerationTestResponse resp = new LlmModerationTestResponse();
        resp.setDecision("REJECT");
        resp.setScore(0.99);
        resp.setReasons(List.of("包含具体原因"));
        resp.setRiskTags(List.of("r"));
        resp.setModel("gpt-test");
        when(llmService.test(any())).thenReturn(resp);
        when(queueService.autoReject(anyLong(), anyString(), anyString())).thenReturn(null);
        doNothing().when(riskLabelingService).replaceRiskTags(
                ArgumentMatchers.any(),
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.anyBoolean()
        );

        Method m = ModerationLlmAutoRunner.class.getDeclaredMethod("handleOne", ModerationQueueEntity.class, ModerationLlmConfigEntity.class);
        m.setAccessible(true);
        ModerationLlmConfigEntity cfg = new ModerationLlmConfigEntity();
        cfg.setAutoRun(true);
        m.invoke(runner, q, cfg);

        verify(queueService).autoReject(eq(q.getId()), eq("包含具体原因"), anyString());
    }
}
