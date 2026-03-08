package com.example.EnterpriseRagCommunity.service.safety;

import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContentSafetyAutoTriggerJob {
    private static final Logger log = LoggerFactory.getLogger(ContentSafetyAutoTriggerJob.class);

    private final ContentSafetyCircuitBreakerService circuitBreakerService;
    private final ModerationPipelineStepRepository pipelineStepRepository;
    private final AuditLogWriter auditLogWriter;

    private volatile Instant lastAutoTriggeredAt = Instant.EPOCH;
    private volatile Instant lastAutoEnabledAt = null;

    @Scheduled(fixedDelayString = "${app.safety.circuit-breaker.autoTrigger.intervalMs:10000}")
    public void tick() {
        ContentSafetyCircuitBreakerConfigDTO cfg0 = circuitBreakerService.getConfig();
        ContentSafetyCircuitBreakerConfigDTO cfg = ContentSafetyCircuitBreakerService.normalize(cfg0);
        ContentSafetyCircuitBreakerConfigDTO.AutoTrigger at = cfg.getAutoTrigger();

        Instant now = Instant.now();
        if (Boolean.TRUE.equals(cfg.getEnabled())) {
            maybeAutoRecover(cfg, at, now);
            return;
        }

        if (at == null || !Boolean.TRUE.equals(at.getEnabled())) return;

        int cd = at.getCoolDownSeconds() == null ? 0 : Math.max(0, at.getCoolDownSeconds());
        if (cd > 0 && lastAutoTriggeredAt.plusSeconds(cd).isAfter(now)) return;

        int windowSeconds = at.getWindowSeconds() == null ? 60 : Math.max(5, at.getWindowSeconds());
        int thresholdCount = at.getThresholdCount() == null ? 10 : Math.max(1, at.getThresholdCount());
        double minConf = at.getMinConfidence() == null ? 0.90 : at.getMinConfidence();

        List<Verdict> verdicts = parseVerdicts(at.getVerdicts());
        if (verdicts.isEmpty()) verdicts = List.of(Verdict.REJECT, Verdict.REVIEW);
        List<String> decisions = new ArrayList<>();
        for (Verdict v : verdicts) {
            if (v == null) continue;
            if (v == Verdict.REVIEW) decisions.add("HUMAN");
            else decisions.add(v.name());
        }
        if (decisions.isEmpty()) decisions = List.of("REJECT", "HUMAN");

        LocalDateTime since = LocalDateTime.ofInstant(now.minusSeconds(windowSeconds), ZoneId.systemDefault());

        long c;
        try {
            c = pipelineStepRepository.countByEndedAtAfterAndStageAndDecisionInAndScoreGreaterThanEqual(
                    since,
                    ModerationPipelineStepEntity.Stage.LLM,
                    decisions,
                    BigDecimal.valueOf(minConf)
            );
        } catch (Exception e) {
            log.warn("Auto-trigger query failed. err={}", e.getMessage());
            return;
        }

        if (c < thresholdCount) return;

        ContentSafetyCircuitBreakerConfigDTO next = ContentSafetyCircuitBreakerService.normalize(cfg);
        next.setEnabled(true);

        String triggerMode = at.getTriggerMode();
        if (triggerMode != null && !triggerMode.isBlank()) {
            next.setMode(triggerMode.trim().toUpperCase(Locale.ROOT));
        }

        String reason = "AUTO_TRIGGER moderation_pipeline_step(LLM)>=threshold"
                + " windowSeconds=" + windowSeconds
                + " thresholdCount=" + thresholdCount
                + " minConfidence=" + minConf
                + " decisions=" + decisions;

        circuitBreakerService.update(next, null, "SYSTEM", reason);
        lastAutoTriggeredAt = now;
        lastAutoEnabledAt = now;

        auditLogWriter.write(
                null,
                "SYSTEM",
                "CONTENT_SAFETY_CIRCUIT_BREAKER_AUTO_TRIGGER",
                "SAFETY",
                null,
                AuditResult.SUCCESS,
                "自动触发内容安全熔断",
                null,
                Map.of(
                        "count", c,
                        "windowSeconds", windowSeconds,
                        "thresholdCount", thresholdCount,
                        "minConfidence", minConf,
                        "verdicts", verdicts.toString(),
                        "mode", next.getMode()
                )
        );
        log.warn("Content safety circuit breaker auto-triggered. count={} windowSeconds={} thresholdCount={} mode={}", c, windowSeconds, thresholdCount, next.getMode());
    }

    private void maybeAutoRecover(ContentSafetyCircuitBreakerConfigDTO cfg, ContentSafetyCircuitBreakerConfigDTO.AutoTrigger at, Instant now) {
        if (cfg == null) return;
        if (at == null) return;
        Integer ar0 = at.getAutoRecoverSeconds();
        int ar = ar0 == null ? 0 : Math.max(0, ar0);
        if (ar <= 0) return;
        Instant enabledAt = lastAutoEnabledAt;
        if (enabledAt == null) return;
        if (enabledAt.plusSeconds(ar).isAfter(now)) return;

        ContentSafetyCircuitBreakerConfigDTO next = ContentSafetyCircuitBreakerService.normalize(cfg);
        next.setEnabled(false);

        String reason = "AUTO_RECOVER seconds=" + ar;
        circuitBreakerService.update(next, null, "SYSTEM", reason);
        lastAutoEnabledAt = null;

        try {
            auditLogWriter.write(
                    null,
                    "SYSTEM",
                    "CONTENT_SAFETY_CIRCUIT_BREAKER_AUTO_RECOVER",
                    "SAFETY",
                    null,
                    AuditResult.SUCCESS,
                    "自动解除内容安全熔断",
                    null,
                    Map.of(
                            "autoRecoverSeconds", ar,
                            "mode", cfg.getMode()
                    )
            );
        } catch (Exception ignored) {
        }
        log.warn("Content safety circuit breaker auto-recovered. autoRecoverSeconds={}", ar);
    }

    private static List<Verdict> parseVerdicts(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<Verdict> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            try {
                out.add(Verdict.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
            }
        }
        return out;
    }
}
