package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.trace.ModerationPipelineTraceService;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationRuleHitsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationRulesEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Severity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationRuleHitsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationRulesRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    private final ModerationConfidenceFallbackConfigRepository fallbackRepository;

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

    private void handleOne(ModerationQueueEntity q) {
        if (q == null || q.getId() == null) return;
        if (q.getStatus() != QueueStatus.PENDING) return;

        // Ensure pipeline run exists
        ModerationPipelineRunEntity run = pipelineTraceService.ensureRun(q);

        long ruleStepId;
        try {
            // Start RULE step
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
            // If trace infra fails, keep main flow running.
            ruleStepId = -1;
        }

        // best-effort set stage to RULE
        if (q.getCurrentStage() != QueueStage.RULE) {
            q.setCurrentStage(QueueStage.RULE);
            q.setUpdatedAt(LocalDateTime.now());
            queueRepository.save(q);
        }

        ModerationConfidenceFallbackConfigEntity cfg = fallbackRepository.findAll().stream().findFirst().orElse(null);
        if (cfg == null) cfg = defaultFallback();

        if (!Boolean.TRUE.equals(cfg.getRuleEnabled())) {
            q.setCurrentStage(QueueStage.VEC);
            q.setUpdatedAt(LocalDateTime.now());
            queueRepository.save(q);

            if (ruleStepId > 0) {
                pipelineTraceService.finishStepOk(ruleStepId, "SKIP", null, Map.of("reason", "rule disabled"));
            }
            auditLogWriter.writeSystem(
                    "RULE_DECISION",
                    "MODERATION_QUEUE",
                    q.getId(),
                    AuditResult.SUCCESS,
                    "RULE skipped (disabled)",
                    run.getTraceId(),
                    Map.of("runId", run.getId(), "stage", "RULE", "decision", "SKIP")
            );
            return;
        }

        String text = textLoader.load(q);
        if (text == null || text.isBlank()) {
            q.setCurrentStage(QueueStage.VEC);
            q.setUpdatedAt(LocalDateTime.now());
            queueRepository.save(q);

            if (ruleStepId > 0) {
                pipelineTraceService.finishStepOk(ruleStepId, "SKIP", null, Map.of("reason", "empty text"));
            }
            auditLogWriter.writeSystem(
                    "RULE_DECISION",
                    "MODERATION_QUEUE",
                    q.getId(),
                    AuditResult.SUCCESS,
                    "RULE skipped (empty text)",
                    run.getTraceId(),
                    Map.of("runId", run.getId(), "stage", "RULE", "decision", "SKIP")
            );
            return;
        }

        List<ModerationRulesEntity> rules = rulesRepository.findAll();
        if (rules == null || rules.isEmpty()) {
            q.setCurrentStage(QueueStage.VEC);
            q.setUpdatedAt(LocalDateTime.now());
            queueRepository.save(q);

            if (ruleStepId > 0) {
                pipelineTraceService.finishStepOk(ruleStepId, "SKIP", null, Map.of("reason", "no rules"));
            }
            auditLogWriter.writeSystem(
                    "RULE_DECISION",
                    "MODERATION_QUEUE",
                    q.getId(),
                    AuditResult.SUCCESS,
                    "RULE skipped (no rules)",
                    run.getTraceId(),
                    Map.of("runId", run.getId(), "stage", "RULE", "decision", "SKIP")
            );
            return;
        }

        Severity maxSeverity = null;
        LocalDateTime now = LocalDateTime.now();
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
            hit.setMatchedAt(now);
            try {
                ruleHitsRepository.save(hit);
            } catch (Exception ignore) {
            }

            hitCount++;
            Map<String, Object> hd = new LinkedHashMap<>();
            hd.put("ruleId", r.getId());
            hd.put("severity", r.getSeverity() == null ? null : r.getSeverity().name());
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
            q.setUpdatedAt(LocalDateTime.now());
            queueRepository.save(q);

            if (ruleStepId > 0) {
                pipelineTraceService.finishStepOk(ruleStepId, "PASS", null, Map.of(
                        "hitCount", 0,
                        "maxSeverity", null,
                        "hits", hitDetails
                ));
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

        ModerationConfidenceFallbackConfigEntity.Action action = switch (maxSeverity) {
            case HIGH -> cfg.getRuleHighAction();
            case MEDIUM -> cfg.getRuleMediumAction();
            case LOW -> cfg.getRuleLowAction();
        };
        if (action == null) action = ModerationConfidenceFallbackConfigEntity.Action.HUMAN;

        String decision = "HIT";

        switch (action) {
            case REJECT -> q.setCurrentStage(QueueStage.HUMAN);
            case HUMAN -> q.setCurrentStage(QueueStage.HUMAN);
            case LLM -> q.setCurrentStage(QueueStage.LLM);
        }

        q.setUpdatedAt(LocalDateTime.now());
        queueRepository.save(q);

        if (ruleStepId > 0) {
            pipelineTraceService.finishStepOk(ruleStepId, decision, null, Map.of(
                    "hitCount", hitCount,
                    "maxSeverity", maxSeverity.name(),
                    "hits", hitDetails,
                    "nextStage", String.valueOf(q.getCurrentStage())
            ));
        }
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
                        "nextStage", String.valueOf(q.getCurrentStage())
                )
        );
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
