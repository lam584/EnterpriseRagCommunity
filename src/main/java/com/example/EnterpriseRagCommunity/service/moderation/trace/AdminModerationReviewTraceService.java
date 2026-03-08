package com.example.EnterpriseRagCommunity.service.moderation.trace;

import com.example.EnterpriseRagCommunity.dto.access.AuditLogsViewDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.*;
import com.example.EnterpriseRagCommunity.entity.access.AuditLogsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.access.AuditLogsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogsService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminModerationReviewTraceService {

    private final ModerationQueueRepository queueRepository;
    private final ModerationPipelineRunRepository runRepository;
    private final ModerationPipelineStepRepository stepRepository;
    private final ModerationChunkSetRepository chunkSetRepository;
    private final AuditLogsRepository auditLogsRepository;

    private final AdminModerationQueueService moderationQueueService;
    private final AdminModerationPipelineTraceService pipelineTraceService;
    private final ModerationChunkReviewService chunkReviewService;
    private final AuditLogsService auditLogsService;

    @Transactional(readOnly = true)
    public AdminModerationReviewTraceTaskPageDTO listTasks(
            Long queueId,
            ContentType contentType,
            Long contentId,
            String traceId,
            QueueStatus status,
            LocalDateTime updatedFrom,
            LocalDateTime updatedTo,
            Integer page,
            Integer pageSize
    ) {
        Long resolvedQueueId = queueId;
        String traceKw = StringUtils.hasText(traceId) ? traceId.trim() : null;
        if (resolvedQueueId == null && traceKw != null) {
            ModerationPipelineRunEntity run = runRepository.findByTraceId(traceKw).orElse(null);
            if (run != null) resolvedQueueId = run.getQueueId();
            else {
                return new AdminModerationReviewTraceTaskPageDTO(List.of(), 0, 0, page == null ? 1 : Math.max(1, page), pageSize == null ? 20 : Math.max(1, pageSize));
            }
        }

        int p = page == null ? 1 : Math.max(1, page);
        int ps = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 200);

        Pageable pageable = PageRequest.of(p - 1, ps, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Long finalQueueId = resolvedQueueId;
        Specification<ModerationQueueEntity> spec = (root, q, cb) -> {
            List<Predicate> ps0 = new ArrayList<>();
            if (finalQueueId != null) ps0.add(cb.equal(root.get("id"), finalQueueId));
            if (contentType != null) ps0.add(cb.equal(root.get("contentType"), contentType));
            if (contentId != null) ps0.add(cb.equal(root.get("contentId"), contentId));
            if (status != null) ps0.add(cb.equal(root.get("status"), status));
            if (updatedFrom != null || updatedTo != null) {
                LocalDateTime start = updatedFrom == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : updatedFrom;
                LocalDateTime end = updatedTo == null ? LocalDateTime.now().plusYears(100) : updatedTo;
                ps0.add(cb.between(root.get("updatedAt"), start, end));
            }
            return cb.and(ps0.toArray(new Predicate[0]));
        };

        Page<ModerationQueueEntity> queues = queueRepository.findAll(spec, pageable);
        List<ModerationQueueEntity> items = queues.getContent() == null ? List.of() : queues.getContent();
        if (items.isEmpty()) {
            return new AdminModerationReviewTraceTaskPageDTO(List.of(), queues.getTotalPages(), queues.getTotalElements(), p, ps);
        }

        List<Long> queueIds = items.stream().map(ModerationQueueEntity::getId).filter(Objects::nonNull).toList();

        Map<Long, ModerationChunkSetEntity> chunkSetByQueueId = new HashMap<>();
        try {
            for (ModerationChunkSetEntity s : chunkSetRepository.findAllByQueueIds(queueIds)) {
                if (s == null || s.getQueueId() == null) continue;
                chunkSetByQueueId.put(s.getQueueId(), s);
            }
        } catch (Exception ignore) {
        }

        Map<Long, ModerationPipelineRunEntity> latestRunByQueueId = new HashMap<>();
        List<ModerationPipelineRunEntity> runs = List.of();
        try {
            runs = runRepository.findAllByQueueIdInOrderByCreatedAtDesc(queueIds);
        } catch (Exception ignore) {
        }
        if (runs != null) {
            for (ModerationPipelineRunEntity r : runs) {
                if (r == null || r.getQueueId() == null || r.getId() == null) continue;
                latestRunByQueueId.putIfAbsent(r.getQueueId(), r);
            }
        }

        List<Long> runIds = latestRunByQueueId.values().stream().map(ModerationPipelineRunEntity::getId).filter(Objects::nonNull).toList();

        Map<Long, List<ModerationPipelineStepEntity>> stepsByRunId = new HashMap<>();
        if (!runIds.isEmpty()) {
            try {
                List<ModerationPipelineStepEntity> steps = stepRepository.findAllByRunIdIn(runIds);
                if (steps != null) {
                    for (ModerationPipelineStepEntity s : steps) {
                        if (s == null || s.getRunId() == null) continue;
                        stepsByRunId.computeIfAbsent(s.getRunId(), k -> new ArrayList<>()).add(s);
                    }
                }
            } catch (Exception ignore) {
            }
        }

        for (List<ModerationPipelineStepEntity> ss : stepsByRunId.values()) {
            ss.sort(Comparator.comparing(ModerationPipelineStepEntity::getStepOrder, Comparator.nullsLast(Comparator.naturalOrder())));
        }

        Map<Long, AdminModerationReviewTraceManualSummaryDTO> manualByQueueId = new HashMap<>();
        try {
            List<AuditLogsEntity> manualLogs = auditLogsRepository.findTop2000ByEntityTypeAndEntityIdInAndActionStartingWithOrderByCreatedAtDesc(
                    "MODERATION_QUEUE",
                    queueIds,
                    "MODERATION_MANUAL_"
            );
            if (manualLogs != null) {
                for (AuditLogsEntity e : manualLogs) {
                    if (e == null || e.getEntityId() == null) continue;
                    if (manualByQueueId.containsKey(e.getEntityId())) continue;
                    Map<String, Object> d = e.getDetails();
                    manualByQueueId.put(
                            e.getEntityId(),
                            new AdminModerationReviewTraceManualSummaryDTO(
                                    true,
                                    e.getAction(),
                                    d == null ? null : (d.get("actorName") == null ? null : String.valueOf(d.get("actorName"))),
                                    e.getActorUserId(),
                                    e.getCreatedAt()
                            )
                    );
                }
            }
        } catch (Exception ignore) {
        }

        List<AdminModerationReviewTraceTaskItemDTO> out = new ArrayList<>();
        for (ModerationQueueEntity q : items) {
            if (q == null || q.getId() == null) continue;
            ModerationPipelineRunEntity run = latestRunByQueueId.get(q.getId());
            List<ModerationPipelineStepEntity> steps = run == null ? List.of() : (stepsByRunId.getOrDefault(run.getId(), List.of()));

            ModerationPipelineStepEntity rule = findStage(steps, ModerationPipelineStepEntity.Stage.RULE);
            ModerationPipelineStepEntity vec = findStage(steps, ModerationPipelineStepEntity.Stage.VEC);
            ModerationPipelineStepEntity llm = findStage(steps, ModerationPipelineStepEntity.Stage.LLM);

            AdminModerationReviewTraceStageSummaryDTO ruleDto = toStageSummary("RULE", rule, shouldIncludeRuleDetails(rule));
            AdminModerationReviewTraceStageSummaryDTO vecDto = toStageSummary("VEC", vec, false);
            AdminModerationReviewTraceStageSummaryDTO llmDto = toStageSummary("LLM", llm, true);

            ModerationChunkSetEntity set = chunkSetByQueueId.get(q.getId());
            AdminModerationReviewTraceChunkSummaryDTO chunkDto = toChunkSummary(llm, set);

            AdminModerationReviewTraceManualSummaryDTO manual = manualByQueueId.getOrDefault(q.getId(), new AdminModerationReviewTraceManualSummaryDTO(false, null, null, null, null));

            out.add(new AdminModerationReviewTraceTaskItemDTO(
                    q.getId(),
                    q.getContentType(),
                    q.getContentId(),
                    q.getStatus() == null ? null : q.getStatus().name(),
                    q.getCurrentStage() == null ? null : q.getCurrentStage().name(),
                    q.getUpdatedAt(),
                    run == null ? null : run.getId(),
                    run == null || run.getStatus() == null ? null : run.getStatus().name(),
                    run == null || run.getFinalDecision() == null ? null : run.getFinalDecision().name(),
                    run == null ? null : run.getTraceId(),
                    run == null ? null : run.getStartedAt(),
                    run == null ? null : run.getEndedAt(),
                    run == null ? null : run.getTotalMs(),
                    ruleDto,
                    vecDto,
                    llmDto,
                    chunkDto,
                    manual
            ));
        }

        return new AdminModerationReviewTraceTaskPageDTO(out, queues.getTotalPages(), queues.getTotalElements(), p, ps);
    }

    @Transactional(readOnly = true)
    public AdminModerationReviewTraceTaskDetailDTO getTaskDetail(Long queueId) {
        if (queueId == null) throw new IllegalArgumentException("queueId is null");

        AdminModerationQueueDetailDTO queue = moderationQueueService.getDetail(queueId);
        AdminModerationPipelineRunDetailDTO latestRun = pipelineTraceService.getLatestByQueueId(queueId);
        AdminModerationPipelineRunHistoryPageDTO history = pipelineTraceService.history(queueId, null, null, 1, 50);

        ModerationChunkSetEntity set = chunkSetRepository.findByQueueId(queueId).orElse(null);
        AdminModerationReviewTraceChunkSetDTO chunkSet = toChunkSetDto(set);

        AdminModerationChunkProgressDTO progress;
        try {
            progress = chunkReviewService.getProgress(queueId, true, 300);
        } catch (Exception e) {
            progress = null;
        }

        List<AuditLogsViewDTO> auditLogs;
        try {
            var page = auditLogsService.query(1, 200, null, null, null, null, null, "MODERATION_QUEUE", queueId, null, null, null, null, "createdAt,desc");
            auditLogs = page == null || page.getContent() == null ? List.of() : page.getContent();
        } catch (Exception e) {
            auditLogs = List.of();
        }

        return new AdminModerationReviewTraceTaskDetailDTO(queue, latestRun, history, chunkSet, progress, auditLogs);
    }

    public static LocalDateTime parseLocalDateTimeOrNull(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String t = raw.trim();
        try {
            return LocalDateTime.parse(t);
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }

    private static ModerationPipelineStepEntity findStage(List<ModerationPipelineStepEntity> steps, ModerationPipelineStepEntity.Stage stage) {
        if (steps == null || steps.isEmpty() || stage == null) return null;
        for (ModerationPipelineStepEntity s : steps) {
            if (s != null && stage.equals(s.getStage())) return s;
        }
        return null;
    }

    private static boolean shouldIncludeRuleDetails(ModerationPipelineStepEntity step) {
        if (step == null || step.getDetailsJson() == null) return false;
        Object hit = step.getDetailsJson().get("antiSpamHit");
        if (hit instanceof Boolean b) return b;
        return hit != null && "true".equalsIgnoreCase(String.valueOf(hit));
    }

    private static AdminModerationReviewTraceStageSummaryDTO toStageSummary(String stage, ModerationPipelineStepEntity step, boolean includeDetails) {
        if (step == null) return new AdminModerationReviewTraceStageSummaryDTO(stage, null, null, null, null, null);
        return new AdminModerationReviewTraceStageSummaryDTO(
                stage,
                step.getDecision(),
                step.getScore(),
                step.getThreshold(),
                step.getCostMs(),
                includeDetails ? step.getDetailsJson() : null
        );
    }

    private static AdminModerationReviewTraceChunkSummaryDTO toChunkSummary(ModerationPipelineStepEntity llmStep, ModerationChunkSetEntity set) {
        boolean chunked = false;
        Long chunkSetId = null;
        Integer total = null;
        Integer completed = null;
        Integer failed = null;
        BigDecimal maxScore = null;

        if (llmStep != null && llmStep.getDetailsJson() != null) {
            Object c = llmStep.getDetailsJson().get("chunked");
            if (c instanceof Boolean b && b) chunked = true;
            else if (c != null && "true".equalsIgnoreCase(String.valueOf(c))) chunked = true;
            Object cs = llmStep.getDetailsJson().get("chunkSetId");
            if (cs instanceof Number n) chunkSetId = n.longValue();
            else if (cs != null) {
                try {
                    chunkSetId = Long.parseLong(String.valueOf(cs));
                } catch (Exception ignore) {
                }
            }
        }

        if (set != null) {
            if (chunkSetId == null) chunkSetId = set.getId();
            total = set.getTotalChunks();
            completed = set.getCompletedChunks();
            failed = set.getFailedChunks();
            maxScore = readBigDecimal(set.getMemoryJson(), "maxScore");
        }

        Long avgMs = null;
        if (Boolean.TRUE.equals(chunked) && llmStep != null && llmStep.getCostMs() != null) {
            int denom = total == null || total <= 0 ? 0 : total;
            if (denom > 0) avgMs = Math.max(0L, llmStep.getCostMs() / denom);
        }

        return new AdminModerationReviewTraceChunkSummaryDTO(chunked, chunkSetId, total, completed, failed, maxScore, avgMs);
    }

    private static BigDecimal readBigDecimal(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception ignore) {
            return null;
        }
    }

    private static AdminModerationReviewTraceChunkSetDTO toChunkSetDto(ModerationChunkSetEntity s) {
        if (s == null) return null;
        return new AdminModerationReviewTraceChunkSetDTO(
                s.getId(),
                s.getQueueId(),
                s.getCaseType() == null ? null : s.getCaseType().name(),
                s.getContentType() == null ? null : s.getContentType().name(),
                s.getContentId(),
                s.getStatus() == null ? null : s.getStatus().name(),
                s.getChunkThresholdChars(),
                s.getChunkSizeChars(),
                s.getOverlapChars(),
                s.getTotalChunks(),
                s.getCompletedChunks(),
                s.getFailedChunks(),
                s.getConfigJson(),
                s.getMemoryJson(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}

