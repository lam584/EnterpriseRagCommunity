package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmDecisionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmDecisionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
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
 * - 拉取 moderation_queue 中待处理记录，统一调用 LLM
 * - decision=APPROVE/REJECT 自动落库并更新帖子/评论状态；decision=HUMAN 转人工
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

        // 先把 stage 置为 LLM，避免多实例/多线程重复处理（轻量 best-effort）
        if (q.getCurrentStage() != QueueStage.LLM) {
            q.setCurrentStage(QueueStage.LLM);
            q.setUpdatedAt(LocalDateTime.now());
            queueRepository.save(q);
        }

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(q.getId());
        LlmModerationTestResponse res = llmService.test(req);

        String decision = res == null ? null : res.getDecision();
        decision = decision == null ? null : decision.trim().toUpperCase(Locale.ROOT);

        if ("APPROVE".equals(decision)) {
            queueService.approve(q.getId(), "LLM auto approve");
            saveDecision(q, res, Verdict.APPROVE);
            return;
        }
        if ("REJECT".equals(decision)) {
            queueService.reject(q.getId(), "LLM auto reject");
            saveDecision(q, res, Verdict.REJECT);
            return;
        }

        // HUMAN 或解析失败：转人工
        q.setCurrentStage(QueueStage.HUMAN);
        q.setUpdatedAt(LocalDateTime.now());
        queueRepository.save(q);
        saveDecision(q, res, Verdict.REVIEW);
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
}

