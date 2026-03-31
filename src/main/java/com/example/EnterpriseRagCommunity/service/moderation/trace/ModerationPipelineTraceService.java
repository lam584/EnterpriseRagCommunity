package com.example.EnterpriseRagCommunity.service.moderation.trace;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ModerationPipelineTraceService {

    private final ModerationPipelineRunRepository runRepository;
    private final ModerationPipelineStepRepository stepRepository;
    private final AuditLogWriter auditLogWriter;

    @Transactional
    public ModerationPipelineRunEntity ensureRun(ModerationQueueEntity q) {
        if (q == null || q.getId() == null) throw new IllegalArgumentException("queue is null");

        // If there's a running run, reuse it.
        Optional<ModerationPipelineRunEntity> latest = runRepository.findFirstByQueueIdOrderByCreatedAtDesc(q.getId());
        if (latest.isPresent() && latest.get().getStatus() == ModerationPipelineRunEntity.RunStatus.RUNNING) {
            return latest.get();
        }

        LocalDateTime now = LocalDateTime.now();
        ModerationPipelineRunEntity run = new ModerationPipelineRunEntity();
        run.setQueueId(q.getId());
        run.setContentType(q.getContentType());
        run.setContentId(q.getContentId());
        run.setStatus(ModerationPipelineRunEntity.RunStatus.RUNNING);
        run.setFinalDecision(null);
        run.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        run.setStartedAt(now);
        run.setEndedAt(null);
        run.setTotalMs(null);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.setLlmModel(null);
        run.setCreatedAt(now);
        return runRepository.save(run);
    }

    @Transactional
    public ModerationPipelineStepEntity startStep(Long runId, ModerationPipelineStepEntity.Stage stage, int stepOrder, Double threshold, Map<String, Object> details) {
        if (runId == null) throw new IllegalArgumentException("runId is null");
        if (stage == null) throw new IllegalArgumentException("stage is null");

        ModerationPipelineStepEntity step = stepRepository.findByRunIdAndStageAndStepOrder(runId, stage, stepOrder)
                .orElseGet(ModerationPipelineStepEntity::new);
        if (step.getId() != null && step.getEndedAt() != null) {
            return step;
        }

        step.setRunId(runId);
        step.setStage(stage);
        step.setStepOrder(stepOrder);
        step.setThreshold(toBigDecimal(threshold));
        step.setStartedAt(step.getStartedAt() != null ? step.getStartedAt() : LocalDateTime.now());
        step.setEndedAt(null);
        step.setCostMs(null);
        step.setDecision(step.getDecision());
        step.setScore(step.getScore());
        step.setErrorCode(null);
        step.setErrorMessage(null);

        if (details != null) {
            Map<String, Object> d = step.getDetailsJson() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(step.getDetailsJson());
            d.putAll(details);
            step.setDetailsJson(d);
        }

        return stepRepository.save(step);
    }

    @Transactional
    public void finishStepOk(Long stepId, String decision, Double score, Map<String, Object> details) {
        ModerationPipelineStepEntity step = stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("step not found: " + stepId));

        LocalDateTime now = LocalDateTime.now();
        step.setDecision(decision);
        step.setScore(toBigDecimal(score));
        step.setEndedAt(now);
        if (step.getStartedAt() != null) {
            step.setCostMs(Duration.between(step.getStartedAt(), now).toMillis());
        }

        if (details != null) {
            Map<String, Object> d = step.getDetailsJson() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(step.getDetailsJson());
            d.putAll(details);
            step.setDetailsJson(d);
        }

        stepRepository.save(step);
    }

    @Transactional
    public void finishStepError(Long stepId, String errorCode, String errorMessage, Map<String, Object> details) {
        ModerationPipelineStepEntity step = stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("step not found: " + stepId));

        LocalDateTime now = LocalDateTime.now();
        step.setDecision("ERROR");
        step.setErrorCode(errorCode);
        step.setErrorMessage(safeMsg(errorMessage));
        step.setEndedAt(now);
        if (step.getStartedAt() != null) {
            step.setCostMs(Duration.between(step.getStartedAt(), now).toMillis());
        }

        if (details != null) {
            Map<String, Object> d = step.getDetailsJson() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(step.getDetailsJson());
            d.putAll(details);
            step.setDetailsJson(d);
        }

        stepRepository.save(step);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    @Transactional
    public void finishRunFail(Long runId, String errorCode, String errorMessage) {
        ModerationPipelineRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("run not found: " + runId));

        LocalDateTime now = LocalDateTime.now();
        run.setStatus(ModerationPipelineRunEntity.RunStatus.FAIL);
        run.setFinalDecision(ModerationPipelineRunEntity.FinalDecision.HUMAN);
        run.setErrorCode(errorCode);
        run.setErrorMessage(safeMsg(errorMessage));
        run.setEndedAt(now);
        run.setTotalMs(Duration.between(run.getStartedAt(), now).toMillis());
        runRepository.save(run);

        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("runId", run.getId());
            details.put("finalDecision", "HUMAN");
            details.put("errorCode", errorCode);
            details.put("totalMs", run.getTotalMs());
            auditLogWriter.writeSystem(
                    "QUEUE_DECISION",
                    "MODERATION_QUEUE",
                    run.getQueueId(),
                    AuditResult.FAIL,
                    safeMsg(errorMessage),
                    run.getTraceId(),
                    details
            );
        } catch (Exception ignore) {
        }
    }

    private static String safeMsg(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() > 512) t = t.substring(0, 512);
        return t;
    }

    private static BigDecimal toBigDecimal(Double v) {
        if (v == null) return null;
        // valueOf avoids many binary floating point surprises compared to new BigDecimal(double)
        return BigDecimal.valueOf(v);
    }

    @Transactional
    public void finishRunSuccess(Long runId, ModerationPipelineRunEntity.FinalDecision finalDecision) {
        ModerationPipelineRunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("run not found: " + runId));

        LocalDateTime now = LocalDateTime.now();
        run.setStatus(ModerationPipelineRunEntity.RunStatus.SUCCESS);
        run.setFinalDecision(finalDecision);
        run.setEndedAt(now);
        run.setTotalMs(Duration.between(run.getStartedAt(), now).toMillis());
        runRepository.save(run);

        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("runId", run.getId());
            details.put("finalDecision", enumName(finalDecision));
            details.put("totalMs", run.getTotalMs());
            auditLogWriter.writeSystem(
                    "QUEUE_DECISION",
                    "MODERATION_QUEUE",
                    run.getQueueId(),
                    AuditResult.SUCCESS,
                    "审核流程完成：" + (enumName(finalDecision) == null ? "UNKNOWN" : enumName(finalDecision)),
                    run.getTraceId(),
                    details
            );
        } catch (Exception ignore) {
        }
    }
}
