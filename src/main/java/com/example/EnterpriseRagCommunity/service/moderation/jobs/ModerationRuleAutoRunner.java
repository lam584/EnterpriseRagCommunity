package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.moderation.*;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.trace.ModerationPipelineTraceService;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationRuleHitsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationRulesRepository;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RULE auto runner:
 * - Run simple regex rules against content text.
 * - Persist hits into moderation_rule_hits.
 * - Decide next action by confidence fallback config.
 */
@Component
@RequiredArgsConstructor
public class ModerationRuleAutoRunner {

    private static final Logger log = LoggerFactory.getLogger(ModerationRuleAutoRunner.class);

    private final ModerationQueueRepository queueRepository;
    private final ModerationRulesRepository rulesRepository;
    private final ModerationRuleHitsRepository ruleHitsRepository;
    private final CommentsRepository commentsRepository;
    private final ModerationActionsRepository moderationActionsRepository;
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
            List<ModerationQueueEntity> ruleStage = queueRepository.findAllByCurrentStage(QueueStage.RULE);
            if (ruleStage != null) {
                for (ModerationQueueEntity q : ruleStage) {
                    if (q != null && q.getStatus() == QueueStatus.PENDING) pending.add(q);
                }
            }
        } catch (Exception e) {
            log.warn("RULE autorun scan failed: {}", e.getMessage());
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
                log.warn("RULE autorun handle queueId={} failed: {}", q.getId(), ex.getMessage());
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
        if (q.getCurrentStage() != QueueStage.RULE) return;
        if (q.getStatus() != QueueStatus.PENDING) return;
        handleOne(q);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static QueueStage mapNextStage(String action) {
        String a = normalizeAction(action);
        if (a == null) return QueueStage.HUMAN;
        return switch (a) {
            case "LLM" -> QueueStage.LLM;
            case "VEC" -> QueueStage.VEC;
            case "HUMAN", "REJECT" -> QueueStage.HUMAN;
            default -> QueueStage.HUMAN;
        };
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

    private static Boolean deepGetBool(Map<String, Object> m) {
        Object v = deepGet(m, "precheck.rule.enabled");
        if (v instanceof Boolean b) return b;
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
        return null;
    }

    private static Boolean deepGetBool(Map<String, Object> m, String path) {
        Object v = deepGet(m, path);
        if (v instanceof Boolean b) return b;
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
        return null;
    }

    private static String deepGetString(Map<String, Object> m, String path) {
        Object v = deepGet(m, path);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
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

    private void handleOne(ModerationQueueEntity q) {
        if (q == null || q.getId() == null) return;
        if (q.getStatus() != QueueStatus.PENDING) return;

        // Mark as in-progress as soon as RULE starts (and prevent concurrent runners from double-processing)
        // NOTE: we only allow locking from PENDING here because RULE is the first stage.
        LocalDateTime now = LocalDateTime.now();
        String locker = "RULE_AUTO";
        LocalDateTime lockExpiredBefore = now.minusMinutes(5);
        int locked = 0;
        try {
            locked = queueRepository.tryLockForAutoRun(
                    q.getId(),
                    QueueStage.RULE,
                    java.util.List.of(QueueStatus.PENDING),
                    QueueStatus.REVIEWING,
                    locker,
                    now,
                    lockExpiredBefore
            );
        } catch (Exception ignore) {
        }
        if (locked <= 0) return;

        final Long queueId = q.getId();
        try {
            try {
                q = queueRepository.findById(queueId).orElse(q);
            } catch (Exception ignore) {
            }

            ModerationPipelineRunEntity run = pipelineTraceService.ensureRun(q);

            long ruleStepId;
            try {
                ModerationPipelineStepEntity step = pipelineTraceService.startStep(
                        run.getId(),
                        ModerationPipelineStepEntity.Stage.RULE,
                        1,
                        null,
                        new LinkedHashMap<>(Map.of(
                                "queueId", q.getId(),
                                "contentType", String.valueOf(q.getContentType()),
                                "contentId", q.getContentId()
                        ))
                );
                ruleStepId = step.getId();
            } catch (Exception e) {
                ruleStepId = -1;
            }

            if (q.getCurrentStage() != QueueStage.RULE) {
                q.setCurrentStage(QueueStage.RULE);
                queueRepository.updateStageIfLockedBy(q.getId(), QueueStage.RULE, locker, LocalDateTime.now());
            }

            if (fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc().isEmpty()) {
                throw new IllegalStateException("moderation_confidence_fallback_config not initialized");
            }

            Map<String, Object> policyConfig = null;
            try {
                policyConfig = policyConfigRepository.findByContentType(q.getContentType()).map(ModerationPolicyConfigEntity::getConfig).orElse(null);
            } catch (Exception ignore) {
            }

            Boolean ruleEnabled = deepGetBool(policyConfig);
            if (ruleEnabled == null) ruleEnabled = true;

            if (!ruleEnabled) {
                skipToVec(q, locker, ruleStepId, run, "rule disabled", "RULE skipped (disabled)");
                return;
            }

            String text = textLoader.load(q);
            if (text == null || text.isBlank()) {
                skipToVec(q, locker, ruleStepId, run, "empty text", "RULE skipped (empty text)");
                return;
            }

            AntiSpamDecision antiSpamDecision = null;
            try {
                antiSpamDecision = evaluateAntiSpam(q, policyConfig);
            } catch (Exception e) {
                log.warn("RULE anti_spam check failed for queueId={}: {}", q.getId(), e.getMessage());
            }
            if (antiSpamDecision != null && antiSpamDecision.hit()) {
                q.setCurrentStage(QueueStage.HUMAN);
                q.setStatus(QueueStatus.HUMAN);
                q.setUpdatedAt(LocalDateTime.now());
                queueRepository.updateStageAndStatusIfPendingOrReviewing(q.getId(), QueueStage.HUMAN, QueueStatus.HUMAN, q.getUpdatedAt());

                if (ruleStepId > 0) {
                    Map<String, Object> antiSpamDetails = new LinkedHashMap<>();
                    antiSpamDetails.put("antiSpamHit", true);
                    antiSpamDetails.put("antiSpamType", antiSpamDecision.type());
                    antiSpamDetails.put("reason", antiSpamDecision.reason());
                    antiSpamDetails.put("actualCount", antiSpamDecision.actualCount());
                    antiSpamDetails.put("threshold", antiSpamDecision.threshold());
                    antiSpamDetails.put("windowSeconds", antiSpamDecision.windowSeconds());
                    antiSpamDetails.put("windowMinutes", antiSpamDecision.windowMinutes());
                    antiSpamDetails.put("nextStage", "HUMAN");
                    pipelineTraceService.finishStepOk(ruleStepId, "HIT", null, antiSpamDetails);
                }
                pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.HUMAN);
                auditLogWriter.writeSystem(
                        "RULE_DECISION",
                        "MODERATION_QUEUE",
                        q.getId(),
                        AuditResult.SUCCESS,
                        "RULE anti_spam hit -> HUMAN",
                        run.getTraceId(),
                        Map.of(
                                "runId", run.getId(),
                                "stage", "RULE",
                                "decision", "HUMAN",
                                "antiSpamHit", true,
                                "antiSpamType", antiSpamDecision.type(),
                                "reason", antiSpamDecision.reason(),
                                "actualCount", antiSpamDecision.actualCount(),
                                "threshold", antiSpamDecision.threshold()
                        )
                );
                return;
            }

            List<ModerationRulesEntity> rules = rulesRepository.findAll();
            if (rules.isEmpty()) {
                skipToVec(q, locker, ruleStepId, run, "no rules", "RULE skipped (no rules)");
                return;
            }

            Severity maxSeverity = null;
            LocalDateTime matchedAt = LocalDateTime.now();
            int hitCount = 0;
            List<Map<String, Object>> hitDetails = new ArrayList<>();

            for (ModerationRulesEntity r : rules) {
                if (r == null || !Boolean.TRUE.equals(r.getEnabled())) continue;
                if (r.getPattern() == null || r.getPattern().isBlank()) continue;

                Pattern p;
                try {
                    p = Pattern.compile(r.getPattern(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                } catch (Exception ignore) {
                    continue;
                }

                Matcher m = p.matcher(text);
                if (!m.find()) continue;

                String snippet;
                try {
                    int start = Math.max(0, m.start());
                    int end = Math.min(text.length(), m.end());
                    snippet = text.substring(start, end);
                    if (snippet.length() > 255) snippet = snippet.substring(0, 255);
                } catch (Exception e) {
                    snippet = null;
                }

                ModerationRuleHitsEntity hit = new ModerationRuleHitsEntity();
                hit.setContentType(q.getContentType());
                hit.setContentId(q.getContentId());
                hit.setRuleId(r.getId());
                hit.setSnippet(snippet);
                hit.setMatchedAt(matchedAt);
                try {
                    ruleHitsRepository.save(hit);
                } catch (Exception ignore) {
                }

                hitCount++;
                Map<String, Object> hd = new LinkedHashMap<>();
                hd.put("ruleId", r.getId());
                hd.put("severity", enumName(r.getSeverity()));
                hd.put("pattern", r.getPattern());
                hd.put("snippet", snippet);
                hitDetails.add(hd);

                Severity sev = r.getSeverity();
                if (sev == null) sev = Severity.LOW;
                if (maxSeverity == null || sev.ordinal() > maxSeverity.ordinal()) {
                    maxSeverity = sev;
                }
            }

            if (maxSeverity == null) {
                q.setCurrentStage(QueueStage.VEC);
                queueRepository.updateStageIfLockedBy(q.getId(), QueueStage.VEC, locker, LocalDateTime.now());

                if (ruleStepId > 0) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("hitCount", 0);
                    details.put("maxSeverity", null);
                    details.put("hits", hitDetails);
                    pipelineTraceService.finishStepOk(ruleStepId, "PASS", null, details);
                }
                auditLogWriter.writeSystem(
                        "RULE_DECISION",
                        "MODERATION_QUEUE",
                        q.getId(),
                        AuditResult.SUCCESS,
                        "RULE pass (no hit)",
                        run.getTraceId(),
                        Map.of("runId", run.getId(), "stage", "RULE", "decision", "PASS", "hitCount", 0)
                );
                return;
            }

            String action = switch (maxSeverity) {
                case HIGH -> firstNonBlank(deepGetString(policyConfig, "precheck.rule.high_action"), "REJECT");
                case MEDIUM -> firstNonBlank(deepGetString(policyConfig, "precheck.rule.medium_action"), "REJECT");
                case LOW -> firstNonBlank(deepGetString(policyConfig, "precheck.rule.low_action"), "HUMAN");
            };

            if ("REJECT".equals(normalizeAction(action))) {
                queueService.autoReject(q.getId(), "规则命中自动拒绝（" + maxSeverity.name() + "）", run.getTraceId());
                if (ruleStepId > 0) {
                    if (action != null) {
                        pipelineTraceService.finishStepOk(ruleStepId, "REJECT", null, Map.of(
                                "hitCount", hitCount,
                                "maxSeverity", maxSeverity.name(),
                                "hits", hitDetails,
                                "action", action
                        ));
                    }
                }
                pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.REJECT);
                if (action != null) {
                    auditLogWriter.writeSystem(
                            "RULE_DECISION",
                            "MODERATION_QUEUE",
                            q.getId(),
                            AuditResult.SUCCESS,
                            "RULE reject (maxSeverity=" + maxSeverity.name() + ")",
                            run.getTraceId(),
                            Map.of(
                                    "runId", run.getId(),
                                    "stage", "RULE",
                                    "decision", "REJECT",
                                    "hitCount", hitCount,
                                    "maxSeverity", maxSeverity.name(),
                                    "action", action
                            )
                    );
                }
                return;
            }

            QueueStage nextStage = mapNextStage(action);

            String decision = "HIT";

            q.setCurrentStage(nextStage);

            q.setUpdatedAt(LocalDateTime.now());
            queueRepository.updateStageIfLockedBy(q.getId(), q.getCurrentStage(), locker, q.getUpdatedAt());

            if (ruleStepId > 0) {
                if (action != null) {
                    pipelineTraceService.finishStepOk(ruleStepId, decision, null, Map.of(
                            "hitCount", hitCount,
                            "maxSeverity", maxSeverity.name(),
                            "hits", hitDetails,
                            "nextStage", String.valueOf(q.getCurrentStage()),
                            "action", action
                    ));
                }
            }
            if (action != null) {
                auditLogWriter.writeSystem(
                        "RULE_DECISION",
                        "MODERATION_QUEUE",
                        q.getId(),
                        AuditResult.SUCCESS,
                        "RULE hit (maxSeverity=" + maxSeverity.name() + ")",
                        run.getTraceId(),
                        Map.of(
                                "runId", run.getId(),
                                "stage", "RULE",
                                "decision", decision,
                                "hitCount", hitCount,
                                "maxSeverity", maxSeverity.name(),
                                "nextStage", String.valueOf(q.getCurrentStage()),
                                "action", action
                        )
                );
            }
        } finally {
            try {
                queueRepository.unlockAutoRun(queueId, locker, LocalDateTime.now());
            } catch (Exception ignore) {
            }
        }
    }

    private AntiSpamDecision evaluateAntiSpam(ModerationQueueEntity q, Map<String, Object> policyConfig) {
        if (q == null || q.getContentType() == null || q.getContentId() == null) return null;

        if (q.getContentType() == com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType.COMMENT) {
            Integer windowSeconds = deepGetInt(policyConfig, "anti_spam.comment.window_seconds");
            Integer maxPerWindow = deepGetInt(policyConfig, "anti_spam.comment.max_per_author_per_window");
            if (windowSeconds == null || maxPerWindow == null || windowSeconds <= 0 || maxPerWindow < 0) return null;

            var comment = commentsRepository.findById(q.getContentId()).orElse(null);
            if (comment == null || comment.getAuthorId() == null) return null;

            LocalDateTime after = LocalDateTime.now().minusSeconds(windowSeconds);
            long actual = commentsRepository.countByAuthorIdAndIsDeletedFalseAndCreatedAtAfter(comment.getAuthorId(), after);
            if (actual > maxPerWindow) {
                return new AntiSpamDecision(
                        true,
                        "COMMENT_WINDOW_RATE",
                        "comment_rate_exceeded",
                        actual,
                        maxPerWindow,
                        windowSeconds,
                        null
                );
            }
            return null;
        }

        if (q.getContentType() == com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType.PROFILE) {
            Integer windowMinutes = deepGetInt(policyConfig, "anti_spam.profile.window_minutes");
            Integer maxUpdatesPerWindow = deepGetInt(policyConfig, "anti_spam.profile.max_updates_per_window");
            Integer maxUpdatesPerDay = deepGetInt(policyConfig, "anti_spam.profile.max_updates_per_day");

            if (windowMinutes != null && maxUpdatesPerWindow != null && windowMinutes > 0 && maxUpdatesPerWindow >= 0) {
                LocalDateTime after = LocalDateTime.now().minusMinutes(windowMinutes);
                long actual = moderationActionsRepository.countByQueueIdAndReasonAndCreatedAtAfter(
                        q.getId(),
                        "PROFILE_PENDING_SNAPSHOT",
                        after
                );
                if (actual > maxUpdatesPerWindow) {
                    return new AntiSpamDecision(
                            true,
                            "PROFILE_WINDOW_RATE",
                            "profile_window_rate_exceeded",
                            actual,
                            maxUpdatesPerWindow,
                            null,
                            windowMinutes
                    );
                }
            }

            if (maxUpdatesPerDay != null && maxUpdatesPerDay >= 0) {
                LocalDateTime dayStart = LocalDate.now().atStartOfDay();
                LocalDateTime dayEnd = dayStart.plusDays(1);
                long actual = moderationActionsRepository.countByQueueIdAndReasonAndCreatedAtBetween(
                        q.getId(),
                        "PROFILE_PENDING_SNAPSHOT",
                        dayStart,
                        dayEnd
                );
                if (actual > maxUpdatesPerDay) {
                    return new AntiSpamDecision(
                            true,
                            "PROFILE_DAY_RATE",
                            "profile_day_rate_exceeded",
                            actual,
                            maxUpdatesPerDay,
                            null,
                            null
                    );
                }
            }
            return null;
        }

        return null;
    }

    private void skipToVec(ModerationQueueEntity q,
                           String locker,
                           long ruleStepId,
                           ModerationPipelineRunEntity run,
                           String reason,
                           String auditMessage) {
        q.setCurrentStage(QueueStage.VEC);
        queueRepository.updateStageIfLockedBy(q.getId(), QueueStage.VEC, locker, LocalDateTime.now());
        if (ruleStepId > 0) {
            pipelineTraceService.finishStepOk(ruleStepId, "SKIP", null, Map.of("reason", reason));
        }
        auditLogWriter.writeSystem(
                "RULE_DECISION",
                "MODERATION_QUEUE",
                q.getId(),
                AuditResult.SUCCESS,
                auditMessage,
                run.getTraceId(),
                Map.of("runId", run.getId(), "stage", "RULE", "decision", "SKIP")
        );
    }

    private static Integer deepGetInt(Map<String, Object> m, String path) {
        Object v = deepGet(m, path);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private record AntiSpamDecision(
            boolean hit,
            String type,
            String reason,
            long actualCount,
            long threshold,
            Integer windowSeconds,
            Integer windowMinutes
    ) {
    }
}
