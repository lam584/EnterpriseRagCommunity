package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.SimilarityCheckRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.SimilarityCheckResponse;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
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
    private final ModerationPolicyConfigRepository policyConfigRepository;
    private final AdminModerationQueueService queueService;
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
                log.warn("VEC autorun handle queueId={} failed", q.getId(), ex);
            }
        }
    }

    public void runForQueueId(Long queueId) {
        if (queueId == null) return;
        ModerationQueueEntity q;
        try {
            q = queueRepository.findById(queueId).orElse(null);
        } catch (Exception e) {
            return;
        }
        if (q == null) return;
        if (q.getCurrentStage() != QueueStage.VEC) return;
        if (q.getStatus() != QueueStatus.PENDING && q.getStatus() != QueueStatus.REVIEWING) return;
        handleOne(q);
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
            }
        }

        // best-effort set stage to VEC (avoid double processing)
        if (q.getCurrentStage() != QueueStage.VEC) {
            q.setCurrentStage(QueueStage.VEC);
            queueRepository.updateStageIfPendingOrReviewing(q.getId(), QueueStage.VEC, LocalDateTime.now());
        }

        if (fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc().isEmpty()) {
            throw new IllegalStateException("moderation_confidence_fallback_config not initialized");
        }

        Map<String, Object> policyConfig = null;
        try {
            ModerationPolicyConfigEntity policy = policyConfigRepository.findByContentType(q.getContentType()).orElse(null);
            policyConfig = policy == null ? null : policy.getConfig();
        } catch (Exception ignore) {
        }

        Boolean vecEnabled = deepGetVecEnabled(policyConfig);
        if (vecEnabled == null) vecEnabled = true;

        String vecHitAction = firstNonBlank(deepGetString(policyConfig, "precheck.vec.hit_action"), "REJECT");
        String vecMissAction = firstNonBlank(deepGetString(policyConfig, "precheck.vec.miss_action"), "LLM");
        Double vecThreshold = deepGetVecThreshold(policyConfig);
        if (vecThreshold == null) vecThreshold = 0.2;

        if (!vecEnabled) {
            handleMissAction(q, run, vecStepId, vecMissAction,
                    "vec disabled",
                    "相似检测关闭且未命中策略为拒绝",
                    "VEC disabled -> REJECT",
                    "VEC skipped (disabled)");
            return;
        }

        String text = textLoader.load(q);
        if (text == null || text.isBlank()) {
            handleMissAction(q, run, vecStepId, vecMissAction,
                    "empty text",
                    "相似检测空文本且未命中策略为拒绝",
                    "VEC empty text -> REJECT",
                    "VEC skipped (empty text)");
            return;
        }

        SimilarityCheckRequest req = new SimilarityCheckRequest();
        req.setText(text);
        req.setContentType(q.getContentType());
        req.setContentId(q.getContentId());
        req.setThreshold(vecThreshold);

        SimilarityCheckResponse resp = similarityService.check(req);

        boolean hit = resp != null && resp.isHit();
        String decidedAction = hit ? vecHitAction : vecMissAction;
        if ("REJECT".equals(normalizeAction(decidedAction))) {
            queueService.autoReject(q.getId(), hit ? "相似检测命中自动拒绝" : "相似检测未命中自动拒绝", run == null ? null : run.getTraceId());

            if (vecStepId > 0) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("hit", hit);
                details.put("bestDistance", resp == null ? null : resp.getBestDistance());
                details.put("threshold", resp == null ? null : resp.getThreshold());
                details.put("hits", resp == null ? List.of() : resp.getHits());
                details.put("action", decidedAction);
                pipelineTraceService.finishStepOk(vecStepId, "REJECT", null, details);
            }

            if (run != null) {
                pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.REJECT);
                Map<String, Object> auditDetails = new LinkedHashMap<>();
                auditDetails.put("runId", run.getId());
                auditDetails.put("stage", "VEC");
                auditDetails.put("decision", "REJECT");
                auditDetails.put("bestDistance", resp == null ? null : resp.getBestDistance());
                auditDetails.put("threshold", resp == null ? null : resp.getThreshold());
                auditDetails.put("action", decidedAction);

                auditLogWriter.writeSystem(
                        "VEC_DECISION",
                        "MODERATION_QUEUE",
                        q.getId(),
                        AuditResult.SUCCESS,
                        hit ? "VEC hit -> REJECT" : "VEC miss -> REJECT",
                        run.getTraceId(),
                        auditDetails
                );
            }
            return;
        }

        QueueStage next = mapNextStage(decidedAction);

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
            Map<String, Object> auditDetails = new LinkedHashMap<>();
            auditDetails.put("runId", run.getId());
            auditDetails.put("stage", "VEC");
            auditDetails.put("decision", hit ? "HIT" : "MISS");
            auditDetails.put("bestDistance", resp == null ? null : resp.getBestDistance());
            auditDetails.put("threshold", resp == null ? null : resp.getThreshold());
            auditDetails.put("nextStage", String.valueOf(next));

            auditLogWriter.writeSystem(
                    "VEC_DECISION",
                    "MODERATION_QUEUE",
                    q.getId(),
                    AuditResult.SUCCESS,
                    hit ? "VEC hit" : "VEC miss",
                    run.getTraceId(),
                    auditDetails
            );
        }
    }

    private static QueueStage mapNextStage(String action) {
        String a = normalizeAction(action);
        if (a == null) return QueueStage.HUMAN;
        return switch (a) {
            case "LLM" -> QueueStage.LLM;
            case "VEC" -> QueueStage.VEC;
            case "HUMAN" -> QueueStage.HUMAN;
            default -> QueueStage.HUMAN;
        };
    }

    private static QueueStage mapAction(ModerationConfidenceFallbackConfigEntity.Action action) {
        if (action == null) return QueueStage.HUMAN;
        return switch (action) {
            case LLM -> QueueStage.LLM;
            case HUMAN, REJECT -> QueueStage.HUMAN;
        };
    }

    private void handleMissAction(ModerationQueueEntity q,
                                  ModerationPipelineRunEntity run,
                                  long vecStepId,
                                  String vecMissAction,
                                  String reason,
                                  String rejectReason,
                                  String rejectMessage,
                                  String skipMessage) {
        if ("REJECT".equals(normalizeAction(vecMissAction))) {
            queueService.autoReject(q.getId(), rejectReason, run == null ? null : run.getTraceId());
            if (vecStepId > 0 && vecMissAction != null) {
                pipelineTraceService.finishStepOk(vecStepId, "REJECT", null, Map.of("reason", reason, "action", vecMissAction));
            }
            if (run != null) {
                pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.REJECT);
                if (vecMissAction != null) {
                    auditLogWriter.writeSystem(
                            "VEC_DECISION",
                            "MODERATION_QUEUE",
                            q.getId(),
                            AuditResult.SUCCESS,
                            rejectMessage,
                            run.getTraceId(),
                            Map.of("runId", run.getId(), "stage", "VEC", "decision", "REJECT", "action", vecMissAction)
                    );
                }
            }
            return;
        }

        QueueStage next = mapNextStage(vecMissAction);
        q.setCurrentStage(next);
        queueRepository.updateStageIfPendingOrReviewing(q.getId(), next, LocalDateTime.now());
        if (vecStepId > 0) {
            pipelineTraceService.finishStepOk(vecStepId, "SKIP", null, Map.of("reason", reason, "nextStage", String.valueOf(next)));
        }
        if (run != null) {
            auditLogWriter.writeSystem(
                    "VEC_DECISION",
                    "MODERATION_QUEUE",
                    q.getId(),
                    AuditResult.SUCCESS,
                    skipMessage,
                    run.getTraceId(),
                    Map.of("runId", run.getId(), "stage", "VEC", "decision", "SKIP", "nextStage", String.valueOf(next))
            );
        }
    }

    private static String normalizeAction(String action) {
        String a = action == null ? null : action.trim().toUpperCase(Locale.ROOT);
        if (a == null || a.isBlank()) return null;
        return a;
    }

    private static String firstNonBlank(String a, String b) {
        String x = a == null ? null : a.trim();
        if (x != null && !x.isBlank()) return x;
        String y = b == null ? null : b.trim();
        if (y != null && !y.isBlank()) return y;
        return null;
    }

    private static Boolean deepGetVecEnabled(Map<String, Object> m) {
        return deepGetBool(m, "precheck.vec.enabled");
    }

    private static String deepGetString(Map<String, Object> m, String path) {
        Object v = deepGet(m, path);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static Double deepGetVecThreshold(Map<String, Object> m) {
        return deepGetDouble(m, "precheck.vec.threshold");
    }

    private static Boolean deepGetBool(Map<String, Object> m, String path) {
        Object v = deepGet(m, path);
        if (v instanceof Boolean bb) return bb;
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
        return null;
    }

    private static Double deepGetDouble(Map<String, Object> m, String path) {
        Object v = deepGet(m, path);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Object deepGet(Map<String, Object> m, String path) {
        if (m == null || path == null || path.isBlank()) return null;
        String[] segs = path.split("\\.");
        Object cur = m;
        for (String seg : segs) {
            if (seg == null || seg.isBlank()) continue;
            if (!(cur instanceof Map<?, ?> mm)) return null;
            cur = mm.get(seg);
        }
        return cur;
    }

}
