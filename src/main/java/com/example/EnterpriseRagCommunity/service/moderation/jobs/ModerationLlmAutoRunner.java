package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmDecisionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmDecisionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationFallbackDecisionService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.example.EnterpriseRagCommunity.service.moderation.trace.ModerationPipelineTraceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * LLM 自动审核 runner：
 * - 仅在 moderation_llm_config.auto_run=true 时启用
 * - decision=APPROVE/REJECT 自动落库并更新帖子/评论状态；decision=HUMAN 转人工
 * - 同时受“置信回退机制”控制：可关闭 LLM 或按配置阈值解释 score
 */
@Component
@RequiredArgsConstructor
public class ModerationLlmAutoRunner {

    private static final Logger log = LoggerFactory.getLogger(ModerationLlmAutoRunner.class);

    private final ModerationLlmConfigRepository llmConfigRepository;
    private final ModerationQueueRepository queueRepository;
    private final AdminModerationLlmService llmService;
    private final AdminModerationQueueService queueService;
    private final ModerationLlmDecisionsRepository llmDecisionsRepository;
    private final ModerationConfidenceFallbackConfigRepository fallbackRepository;

    private final ModerationPipelineTraceService pipelineTraceService;
    private final ModerationPipelineStepRepository pipelineStepRepository;
    private final AuditLogWriter auditLogWriter;

    /**
     * 每 15 秒扫一次，单次最多处理 20 条，避免对上游模型造成瞬时压力。
     */
    @Scheduled(fixedDelay = 15_000)
    public void runOnce() {
        ModerationLlmConfigEntity cfg = llmConfigRepository.findAll().stream().findFirst().orElse(null);
        if (cfg == null || !Boolean.TRUE.equals(cfg.getAutoRun())) return;

        List<ModerationQueueEntity> pending = new ArrayList<>();

        // 当前项目里入队阶段默认是 HUMAN。为了兼容现状：
        // - 先试图处理 LLM stage；
        // - 如果没有，再兜底处理 HUMAN stage（只处理 PENDING）。
        try {
            List<ModerationQueueEntity> llmStage = queueRepository.findAllByCurrentStage(QueueStage.LLM);
            if (llmStage != null) {
                for (ModerationQueueEntity q : llmStage) {
                    if (q != null && q.getStatus() == QueueStatus.PENDING) pending.add(q);
                }
            }

            if (pending.isEmpty()) {
                List<ModerationQueueEntity> humanStage = queueRepository.findAllByCurrentStage(QueueStage.HUMAN);
                if (humanStage != null) {
                    for (ModerationQueueEntity q : humanStage) {
                        if (q != null && q.getStatus() == QueueStatus.PENDING) pending.add(q);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("LLM autorun scan failed: {}", e.getMessage());
            return;
        }

        if (pending.isEmpty()) return;

        // priority DESC, createdAt ASC (和 list() 的排序一致)
        pending.sort(Comparator
                .comparing(ModerationQueueEntity::getPriority, Comparator.nullsFirst(Comparator.reverseOrder()))
                .thenComparing(ModerationQueueEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        int limit = Math.min(pending.size(), 20);
        for (int i = 0; i < limit; i++) {
            ModerationQueueEntity q = pending.get(i);
            try {
                handleOne(q);
            } catch (Exception ex) {
                // 失败不阻断后续任务；下次会继续尝试
                log.warn("LLM autorun handle queueId={} failed: {}", q.getId(), ex.getMessage());
            }
        }
    }

    private void handleOne(ModerationQueueEntity q) {
        if (q == null || q.getId() == null) return;
        if (q.getStatus() != QueueStatus.PENDING) return;

        // Enforce pipeline order:
        // if no previous step records, drop back to RULE.
        ModerationPipelineRunEntity run;
        try {
            run = pipelineTraceService.ensureRun(q);
        } catch (Exception e) {
            run = null;
        }
        if (run == null) {
            q.setCurrentStage(QueueStage.RULE);
            q.setUpdatedAt(LocalDateTime.now());
            queueRepository.save(q);
            return;
        }

        boolean hasRule = pipelineStepRepository.findByRunIdAndStage(run.getId(), ModerationPipelineStepEntity.Stage.RULE).isPresent();
        boolean hasVec = pipelineStepRepository.findByRunIdAndStage(run.getId(), ModerationPipelineStepEntity.Stage.VEC).isPresent();
        if (!hasRule || !hasVec) {
            q.setCurrentStage(QueueStage.RULE);
            q.setUpdatedAt(LocalDateTime.now());
            queueRepository.save(q);

            auditLogWriter.writeSystem(
                    "LLM_DECISION",
                    "MODERATION_QUEUE",
                    q.getId(),
                    AuditResult.SUCCESS,
                    "LLM skipped: missing prev steps (RULE=" + hasRule + ", VEC=" + hasVec + ")",
                    run.getTraceId(),
                    Map.of("runId", run.getId(), "stage", "LLM", "decision", "SKIP", "missingRule", !hasRule, "missingVec", !hasVec)
            );
            return;
        }

        ModerationConfidenceFallbackConfigEntity fb = fallbackRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"))
                .stream().findFirst().orElse(null);
        if (fb == null) fb = defaultFallback();

        if (!Boolean.TRUE.equals(fb.getLlmEnabled())) {
            q.setCurrentStage(QueueStage.HUMAN);
            q.setUpdatedAt(LocalDateTime.now());
            queueRepository.save(q);

            auditLogWriter.writeSystem(
                    "LLM_DECISION",
                    "MODERATION_QUEUE",
                    q.getId(),
                    AuditResult.SUCCESS,
                    "LLM skipped (disabled)",
                    run.getTraceId(),
                    Map.of("runId", run.getId(), "stage", "LLM", "decision", "SKIP")
            );
            return;
        }

        // best-effort set stage to LLM
        if (q.getCurrentStage() != QueueStage.LLM) {
            q.setCurrentStage(QueueStage.LLM);
            q.setUpdatedAt(LocalDateTime.now());
            queueRepository.save(q);
        }

        // Start LLM step with prior results summary
        Long llmStepId = null;
        Map<String, Object> prior = new LinkedHashMap<>();
        pipelineStepRepository.findByRunIdAndStage(run.getId(), ModerationPipelineStepEntity.Stage.RULE)
                .ifPresent(s -> prior.put("rule", s.getDetailsJson()));
        pipelineStepRepository.findByRunIdAndStage(run.getId(), ModerationPipelineStepEntity.Stage.VEC)
                .ifPresent(s -> prior.put("vec", s.getDetailsJson()));

        try {
            ModerationPipelineStepEntity step = pipelineTraceService.startStep(
                    run.getId(),
                    ModerationPipelineStepEntity.Stage.LLM,
                    3,
                    fb.getLlmRejectThreshold(),
                    Map.of("prior", prior)
            );
            llmStepId = step.getId();
        } catch (Exception ignore) {
        }

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(q.getId());
        LlmModerationTestResponse res;
        try {
            res = llmService.test(req);
        } catch (Exception ex) {
            // On upstream error: mark run fail + route to HUMAN
            q.setCurrentStage(QueueStage.HUMAN);
            q.setUpdatedAt(LocalDateTime.now());
            queueRepository.save(q);

            if (llmStepId != null) {
                pipelineTraceService.finishStepError(llmStepId, "LLM_CALL_FAILED", ex.getMessage(), Map.of());
            }
            pipelineTraceService.finishRunFail(run.getId(), "LLM_CALL_FAILED", ex.getMessage());

            auditLogWriter.writeSystem(
                    "LLM_DECISION",
                    "MODERATION_QUEUE",
                    q.getId(),
                    AuditResult.FAIL,
                    "LLM call failed: " + ex.getMessage(),
                    run.getTraceId(),
                    Map.of("runId", run.getId(), "stage", "LLM", "decision", "ERROR")
            );
            return;
        }

        String decision = res == null ? null : ModerationFallbackDecisionService.normalizeDecision(res.getDecision());

        Verdict verdict;
        if ("APPROVE".equals(decision)) {
            verdict = Verdict.APPROVE;
        } else if ("REJECT".equals(decision)) {
            verdict = Verdict.REJECT;
        } else if ("HUMAN".equals(decision)) {
            verdict = Verdict.REVIEW;
        } else {
            verdict = ModerationFallbackDecisionService.verdictFromLlmScore(
                    res == null ? null : res.getScore(),
                    fb.getLlmRejectThreshold(),
                    fb.getLlmHumanThreshold()
            );
        }

        // Save step details
        if (llmStepId != null) {
            Map<String, Object> details = new LinkedHashMap<>();
            if (res != null) {
                details.put("model", res.getModel());
                details.put("decision", res.getDecision());
                details.put("score", res.getScore());
                details.put("reasons", res.getReasons());
                details.put("riskTags", res.getRiskTags());
                // raw output may be big; still store but truncate
                String raw = res.getRawModelOutput();
                if (raw != null && raw.length() > 2000) raw = raw.substring(0, 2000);
                details.put("rawModelOutput", raw);
                details.put("latencyMs", res.getLatencyMs());
                details.put("usage", res.getUsage());
            }
            pipelineTraceService.finishStepOk(
                    llmStepId,
                    verdict == Verdict.APPROVE ? "APPROVE" : (verdict == Verdict.REJECT ? "REJECT" : "HUMAN"),
                    res == null ? null : res.getScore(),
                    details
            );
        }

        // Update run decision
        if (verdict == Verdict.APPROVE) {
            queueService.approve(q.getId(), "LLM auto approve");
            saveDecision(q, res, Verdict.APPROVE);
            pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.APPROVE);

            auditLogWriter.writeSystem(
                    "LLM_DECISION",
                    "MODERATION_QUEUE",
                    q.getId(),
                    AuditResult.SUCCESS,
                    "LLM auto approve",
                    run.getTraceId(),
                    Map.of("runId", run.getId(), "stage", "LLM", "decision", "APPROVE")
            );
            return;
        }
        if (verdict == Verdict.REJECT) {
            queueService.reject(q.getId(), "LLM auto reject");
            saveDecision(q, res, Verdict.REJECT);
            pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.REJECT);

            auditLogWriter.writeSystem(
                    "LLM_DECISION",
                    "MODERATION_QUEUE",
                    q.getId(),
                    AuditResult.SUCCESS,
                    "LLM auto reject",
                    run.getTraceId(),
                    Map.of("runId", run.getId(), "stage", "LLM", "decision", "REJECT")
            );
            return;
        }

        // REVIEW: go HUMAN
        q.setCurrentStage(QueueStage.HUMAN);
        q.setUpdatedAt(LocalDateTime.now());
        queueRepository.save(q);
        saveDecision(q, res, Verdict.REVIEW);
        pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.HUMAN);

        auditLogWriter.writeSystem(
                "LLM_DECISION",
                "MODERATION_QUEUE",
                q.getId(),
                AuditResult.SUCCESS,
                "LLM routed to HUMAN",
                run.getTraceId(),
                Map.of("runId", run.getId(), "stage", "LLM", "decision", "HUMAN")
        );
    }

    private void saveDecision(ModerationQueueEntity q, LlmModerationTestResponse res, Verdict verdict) {
        try {
            ModerationLlmDecisionsEntity e = new ModerationLlmDecisionsEntity();
            e.setContentType(q.getContentType());
            e.setContentId(q.getContentId());
            e.setModel((res != null && res.getModel() != null && !res.getModel().isBlank()) ? res.getModel() : "unknown");

            Map<String, Object> labels = new LinkedHashMap<>();
            if (res != null) {
                if (res.getDecision() != null) labels.put("decision", res.getDecision());
                if (res.getScore() != null) labels.put("score", res.getScore());
                if (res.getReasons() != null) labels.put("reasons", res.getReasons());
                if (res.getRiskTags() != null) labels.put("riskTags", res.getRiskTags());
                if (res.getRawModelOutput() != null) labels.put("rawModelOutput", res.getRawModelOutput());
            }
            e.setLabels(labels);

            Double score = res == null ? null : res.getScore();
            if (score == null) score = 0.0;
            if (score < 0) score = 0.0;
            if (score > 1) score = 1.0;
            e.setConfidence(BigDecimal.valueOf(score));

            e.setVerdict(verdict);
            e.setPromptId(null);
            e.setTokensIn(res == null || res.getUsage() == null ? null : res.getUsage().getPromptTokens());
            e.setTokensOut(res == null || res.getUsage() == null ? null : res.getUsage().getCompletionTokens());
            e.setDecidedAt(LocalDateTime.now());

            llmDecisionsRepository.save(e);
        } catch (Exception ignore) {
            // decision 落库失败不影响主流程
        }
    }

    private static ModerationConfidenceFallbackConfigEntity defaultFallback() {
        ModerationConfidenceFallbackConfigEntity e = new ModerationConfidenceFallbackConfigEntity();
        e.setRuleEnabled(Boolean.TRUE);
        e.setRuleHighAction(ModerationConfidenceFallbackConfigEntity.Action.HUMAN);
        e.setRuleMediumAction(ModerationConfidenceFallbackConfigEntity.Action.LLM);
        e.setRuleLowAction(ModerationConfidenceFallbackConfigEntity.Action.LLM);
        e.setVecEnabled(Boolean.TRUE);
        e.setVecThreshold(0.2);
        e.setVecHitAction(ModerationConfidenceFallbackConfigEntity.Action.HUMAN);
        e.setVecMissAction(ModerationConfidenceFallbackConfigEntity.Action.LLM);
        e.setLlmEnabled(Boolean.TRUE);
        e.setLlmRejectThreshold(0.75);
        e.setLlmHumanThreshold(0.5);
        e.setVersion(0);
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }
}

