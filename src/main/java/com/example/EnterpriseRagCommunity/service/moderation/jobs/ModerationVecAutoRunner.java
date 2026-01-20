package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.SimilarityCheckRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.SimilarityCheckResponse;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationSimilarityService;
import com.example.EnterpriseRagCommunity.service.moderation.trace.ModerationPipelineTraceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * VEC (similarity) auto runner:
 * - Before LLM, run similarity check against historical violation samples.
 * - Next action is controlled by confidence fallback config.
 */
@Component
@RequiredArgsConstructor
public class ModerationVecAutoRunner {

    private static final Logger log = LoggerFactory.getLogger(ModerationVecAutoRunner.class);

    private final ModerationQueueRepository queueRepository;
    private final ModerationSimilarityService similarityService;
    private final ModerationConfidenceFallbackConfigRepository fallbackRepository;
    private final ModerationContentTextLoader textLoader;

    private final ModerationPipelineTraceService pipelineTraceService;
    private final AuditLogWriter auditLogWriter;

    @Scheduled(fixedDelay = 15_000)
    public void runOnce() {
        List<ModerationQueueEntity> pending = new ArrayList<>();
        try {
            List<ModerationQueueEntity> vecStage = queueRepository.findAllByCurrentStage(QueueStage.VEC);
            if (vecStage != null) {
                for (ModerationQueueEntity q : vecStage) {
                    // After RULE marks status=REVIEWING, downstream stages should still proceed.
                    if (q != null && (q.getStatus() == QueueStatus.PENDING || q.getStatus() == QueueStatus.REVIEWING)) pending.add(q);
                }
            }
        } catch (Exception e) {
            log.warn("VEC autorun scan failed: {}", e.getMessage());
            return;
        }

        if (pending.isEmpty()) return;

        pending.sort(Comparator
                .comparing(ModerationQueueEntity::getPriority, Comparator.nullsFirst(Comparator.reverseOrder()))
                .thenComparing(ModerationQueueEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        int limit = Math.min(pending.size(), 50);
        for (int i = 0; i < limit; i++) {
            ModerationQueueEntity q = pending.get(i);
            try {
                handleOne(q);
            } catch (Exception ex) {
                log.warn("VEC autorun handle queueId={} failed: {}", q.getId(), ex.getMessage());
            }
        }
    }

    private void handleOne(ModerationQueueEntity q) {
        if (q == null || q.getId() == null) return;
        try {
            q = queueRepository.findById(q.getId()).orElse(q);
        } catch (Exception ignore) {
        }
        if (q.getStatus() != QueueStatus.PENDING && q.getStatus() != QueueStatus.REVIEWING) return;

        // Require pipeline run (created in RULE stage). If missing, force back to RULE.
        ModerationPipelineRunEntity run;
        try {
            run = pipelineTraceService.ensureRun(q);
        } catch (Exception e) {
            run = null;
        }

        long vecStepId = -1;
        if (run != null) {
            try {
                ModerationPipelineStepEntity step = pipelineTraceService.startStep(
                        run.getId(),
                        ModerationPipelineStepEntity.Stage.VEC,
                        2,
                        null,
                        new LinkedHashMap<>(Map.of(
                                "queueId", q.getId(),
                                "contentType", String.valueOf(q.getContentType()),
                                "contentId", q.getContentId()
                        ))
                );
                vecStepId = step.getId();
            } catch (Exception ignore) {
                vecStepId = -1;
            }
        }

        // best-effort set stage to VEC (avoid double processing)
        if (q.getCurrentStage() != QueueStage.VEC) {
            q.setCurrentStage(QueueStage.VEC);
            queueRepository.updateStageIfPendingOrReviewing(q.getId(), QueueStage.VEC, LocalDateTime.now());
        }

        ModerationConfidenceFallbackConfigEntity cfg = fallbackRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"))
                .stream().findFirst().orElse(null);
        if (cfg == null) cfg = defaultFallback();

        if (!Boolean.TRUE.equals(cfg.getVecEnabled())) {
            // skip VEC -> decide miss action
            QueueStage next = mapAction(cfg.getVecMissAction());
            q.setCurrentStage(next);
            queueRepository.updateStageIfPendingOrReviewing(q.getId(), next, LocalDateTime.now());

            if (vecStepId > 0) {
                pipelineTraceService.finishStepOk(vecStepId, "SKIP", null, Map.of("reason", "vec disabled", "nextStage", String.valueOf(next)));
            }
            if (run != null) {
                auditLogWriter.writeSystem(
                        "VEC_DECISION",
                        "MODERATION_QUEUE",
                        q.getId(),
                        AuditResult.SUCCESS,
                        "VEC skipped (disabled)",
                        run.getTraceId(),
                        Map.of("runId", run.getId(), "stage", "VEC", "decision", "SKIP", "nextStage", String.valueOf(next))
                );
            }
            return;
        }

        String text = textLoader.load(q);
        if (text == null || text.isBlank()) {
            // If no text, treat as miss
            QueueStage next = mapAction(cfg.getVecMissAction());
            q.setCurrentStage(next);
            queueRepository.updateStageIfPendingOrReviewing(q.getId(), next, LocalDateTime.now());

            if (vecStepId > 0) {
                pipelineTraceService.finishStepOk(vecStepId, "SKIP", null, Map.of("reason", "empty text", "nextStage", String.valueOf(next)));
            }
            if (run != null) {
                auditLogWriter.writeSystem(
                        "VEC_DECISION",
                        "MODERATION_QUEUE",
                        q.getId(),
                        AuditResult.SUCCESS,
                        "VEC skipped (empty text)",
                        run.getTraceId(),
                        Map.of("runId", run.getId(), "stage", "VEC", "decision", "SKIP", "nextStage", String.valueOf(next))
                );
            }
            return;
        }

        SimilarityCheckRequest req = new SimilarityCheckRequest();
        req.setText(text);
        req.setContentType(q.getContentType());
        req.setContentId(q.getContentId());
        if (cfg.getVecThreshold() != null) req.setThreshold(cfg.getVecThreshold());

        SimilarityCheckResponse resp = similarityService.check(req);

        boolean hit = resp != null && resp.isHit();
        QueueStage next = hit ? mapAction(cfg.getVecHitAction()) : mapAction(cfg.getVecMissAction());

        q.setCurrentStage(next);
        queueRepository.updateStageIfPendingOrReviewing(q.getId(), next, LocalDateTime.now());

        if (vecStepId > 0) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("hit", hit);
            details.put("bestDistance", resp == null ? null : resp.getBestDistance());
            details.put("threshold", resp == null ? null : resp.getThreshold());
            details.put("hits", resp == null ? List.of() : resp.getHits());
            details.put("nextStage", String.valueOf(next));
            pipelineTraceService.finishStepOk(vecStepId, hit ? "HIT" : "MISS", null, details);
        }

        if (run != null) {
            auditLogWriter.writeSystem(
                    "VEC_DECISION",
                    "MODERATION_QUEUE",
                    q.getId(),
                    AuditResult.SUCCESS,
                    hit ? "VEC hit" : "VEC miss",
                    run.getTraceId(),
                    Map.of(
                            "runId", run.getId(),
                            "stage", "VEC",
                            "decision", hit ? "HIT" : "MISS",
                            "bestDistance", resp == null ? null : resp.getBestDistance(),
                            "threshold", resp == null ? null : resp.getThreshold(),
                            "nextStage", String.valueOf(next)
                    )
            );
        }
    }

    private static QueueStage mapAction(ModerationConfidenceFallbackConfigEntity.Action a) {
        if (a == null) return QueueStage.HUMAN;
        return switch (a) {
            case HUMAN -> QueueStage.HUMAN;
            case LLM -> QueueStage.LLM;
            case REJECT -> QueueStage.HUMAN; // immediate reject needs actor/audit; fallback to HUMAN stage
        };
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
