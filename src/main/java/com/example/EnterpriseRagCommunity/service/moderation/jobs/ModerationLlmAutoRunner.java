package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import static com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunnerSupport.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkProgressDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineStepEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPolicyConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Source;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetExtractionStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineStepRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPolicyConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import com.example.EnterpriseRagCommunity.service.ai.TokenCountService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationFallbackDecisionService;
import com.example.EnterpriseRagCommunity.service.moderation.RiskLabelingService;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationLlmService;
import com.example.EnterpriseRagCommunity.service.moderation.trace.ModerationPipelineTraceService;
import com.example.EnterpriseRagCommunity.service.monitor.FileAssetExtractionService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunnerSupport.ChunkedVerdict;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunnerSupport.PolicyEval;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunnerSupport.Thresholds;

/**
 * LLM 闂備胶鍘ч〃搴㈢濠婂嫭鍙忛柍鍝勫暊閸嬫捇宕楁径濠傗拤闂?runner闂? * - 濠电偛顕慨鎾箠鎼粹槄鑰?moderation_llm_config.auto_run=true 闂備礁鎼崯鍐测枖濞戙垹绠氶幖娣妽閸? * - decision=APPROVE/REJECT 闂備胶鍘ч〃搴㈢濠婂嫭鍙忛柍鍝勬噺婵即鏌﹀Ο渚Ш婵ǜ鍔嶉〃銉╂倷鐠鸿櫣鍘┑鐐村絻濞尖€愁嚕椤掑嫬绾ч悹鎭掑妿閹虫劙鏌?闂佽崵濮村ú銈団偓姘煎灦椤㈡瑩骞嬮敂钘変汗闂佺厧鎽滈。浠嬪磻閹惧鐟瑰┑鐘插閼扮笩ecision=HUMAN 闂佸搫顦遍崕鎰板礂濞戞褎寰勬繝搴℃櫊? * - 闂備礁鎲￠懝楣冨嫉椤掆偓椤啴宕掗悙鑼厬濠碉紕鍋熼崕鐢稿磻閹炬剚鐓ラ柛娑卞灡閻庮厽绻涢敐鍛闁逞屽墲濞呮洜绮堥埀顒勬⒒娓氬洤浜愰柛瀣崌閺岋繝宕煎┑鍥ㄥ創闂佺硶鏅滈〃濠囧垂閹€鏀介柛銉戝倻甯涢梻浣告啞鐢喖顢欓幇顔筋潟濞村吋娼欓惌妤併亜閺嶃劏澹樼憸鎵█濮?LLM 闂備胶鎳撻悺銊╂偋濡ゅ啠鍋撻崹顐ょ煉闁哄苯锕ら濂稿炊閳哄倻鈧參姊婚崒姘殭闁绘妫濋崺鈧い鎺戝暙椤ュ秶鎮▎鎾粹拺?score
 */
@Component
@RequiredArgsConstructor
public class ModerationLlmAutoRunner {

    private static final Logger log = LoggerFactory.getLogger(ModerationLlmAutoRunner.class);
    private final ModerationLlmConfigRepository llmConfigRepository;
    private final ModerationQueueRepository queueRepository;
    private final AdminModerationLlmService llmService;
    private final AdminModerationQueueService queueService;
    private final ModerationConfidenceFallbackConfigRepository fallbackRepository;
    private final ModerationPolicyConfigRepository policyConfigRepository;
    private final TagsRepository tagsRepository;

    private final ModerationPipelineTraceService pipelineTraceService;
    private final ModerationPipelineStepRepository pipelineStepRepository;
    private final com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository promptsRepository;
    private final AuditLogWriter auditLogWriter;
    private final RiskLabelingService riskLabelingService;
    private final TokenCountService tokenCountService;
    private final ModerationChunkReviewService chunkReviewService;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final FileAssetExtractionsRepository fileAssetExtractionsRepository;
    private final FileAssetExtractionService fileAssetExtractionService;
    private final LlmQueueProperties llmQueueProperties;
    private final LlmCallQueueService llmCallQueueService;
    private final ObjectMapper objectMapper;

    private volatile long riskTagThresholdsLoadedAtMs = 0L;
    private volatile Map<String, Double> riskTagThresholdsCache = Map.of();

    /**
     * 婵?15 缂傚倷绀侀ˇ鎵暜濡や胶鐝堕柛灞剧〒閳绘棃鏌嶈閸撴瑩鍩㈡惔銊ョ叀闁告侗鍨抽ˇ顕€姊洪崨濠庢畷婵炲弶锕㈤、鏇熺附缁嬪灝鐝橀梺閫炲苯澧€垫澘瀚～婵嬵敆婢跺瑩鐔兼⒑?20 闂備礁鎼ˇ顐⑽ｉ崟顓燁潟婵犻潧顑嗛悞濂告煕椤垵鏋涢悽顖涘▕閹鎮界化鏇炰壕婵炴垶姘ㄩ幉顏勨攽閻愬瓨灏柟鐚溿倕鏆為梻浣规偠閸斿矂顢栨径鎰劦妞ゆ帒鍊告牎闂佹悶鍔嶅畝鎼佸箚婢舵劦鏁嗗ù锝堫嚃濡差垶姊洪崨濞掝亪顢栭崨顔藉弿闊洦绋戣繚?     */
    @Scheduled(fixedDelay = 15_000)
    public void runOnce() {
        ModerationLlmConfigEntity cfg = llmConfigRepository.findAll().stream().findFirst().orElse(null);
        if (cfg == null || !Boolean.TRUE.equals(cfg.getAutoRun())) return;

        List<ModerationQueueEntity> pending = new ArrayList<>();

        // 闁荤喐绮庢晶妤呭箰閸涘﹥娅犻柣妯煎仺娴滄粓鏌涢敂璇插箺缂佹劖顨婂娲箵閹烘洖顏梺鍛婄懃濡繂顫忔總鍛婂亜闁稿繐鐨烽弸鍡椻攽閻愯尙澧㈤柡浣藉吹閸掓帡顢涘鍕€涢梺闈涚墕濡瑧鐟?HUMAN闂備線娼уΛ妤呭磹妞嬪孩瀚婚柣鏂挎憸椤╂煡鏌涢埄鍐炬當闁诡喗鍨块幃妤呮偡閻楀牐纭€濡ょ姷鍋涘ú顓㈠箖閳哄啯濯奸柛锔诲幘閻?
        // - 闂備胶顭堢换鎰版偋閹扮増鍎婃い鏍仜閻愬﹪鏌熼鍡楁湰琚╅梻?LLM stage闂?        // - 濠电姷顣介埀顒€鍟块埀顒€缍婇幃妯诲緞婵炵偓鐓㈤梺鏂ユ櫅閸燁垳绮婚幒妤佺叆婵炴垶顭堢€氫即鏌涢悢鎻掍壕闂備胶顭堢换鎴犲垝瀹€鈧懞閬嶆惞椤愩垻绐為梺鍛婃处閸樹粙宕?HUMAN stage闂備焦瀵х粙鎴︽偋閸℃哎浜圭憸搴☆嚗閸曨垰绀嬫い鎾跺枎閳?PENDING闂備焦瀵х粙鎴λ囬銏犵劦?
        try {
            List<ModerationQueueEntity> llmStage = queueRepository.findAllByCurrentStage(QueueStage.LLM);
            if (llmStage != null) {
                for (ModerationQueueEntity q : llmStage) {
                    if (q != null && (q.getStatus() == QueueStatus.PENDING || q.getStatus() == QueueStatus.REVIEWING)) pending.add(q);
                }
            }

            if (pending.isEmpty()) {
                List<ModerationQueueEntity> humanStage = queueRepository.findAllByCurrentStage(QueueStage.HUMAN);
                if (humanStage != null) {
                    for (ModerationQueueEntity q : humanStage) {
                        if (q != null && (q.getStatus() == QueueStatus.PENDING || q.getStatus() == QueueStatus.REVIEWING)) pending.add(q);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("LLM autorun scan failed: {}", e.getMessage());
            return;
        }

        if (pending.isEmpty()) return;

        // priority DESC, createdAt ASC (闂?list() 闂備焦鐪归崝宀€鈧凹鍘鹃弫顕€骞樼€涙ê鍔呴棅顐㈡搐濞寸兘顢旈柆宥嗙厾?
        pending.sort(Comparator
                .comparing(ModerationQueueEntity::getPriority, Comparator.nullsFirst(Comparator.reverseOrder()))
                .thenComparing(ModerationQueueEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        int limit = Math.min(pending.size(), 20);
        for (int i = 0; i < limit; i++) {
            ModerationQueueEntity q = pending.get(i);
            try {
                handleOne(q, cfg);
            } catch (Exception ex) {
                // 濠电姰鍨洪崕鑲╁垝閸撗勫枂闁挎梻鏅埢鏃傗偓骞垮劚椤︿即鍩㈡惔銊︾厸闁稿被鍊曢獮鏍煙椤旂瓔娈樼紒杈ㄥ浮椤㈡ê鈹戦崶褍绨ラ梻浣告啞閺屸€澄ｉ崟顓燁潟閺夊牄鍔庨埢鏃堟倵閿濆骸浜介柛瀣斿厾褰掑礂閸忚偐娈ら梺瑙勬尦椤ユ挾妲愰幒妤婃晣闁绘劕鐏氶娑㈡煟?                log.warn("LLM autorun handle queueId={} failed: {}", q.getId(), ex.getMessage());
            }
        }
    }

    public void runForQueueId(Long queueId) {
        if (queueId == null) return;
        ModerationLlmConfigEntity cfg = llmConfigRepository.findAll().stream().findFirst().orElse(null);
        if (cfg == null || !Boolean.TRUE.equals(cfg.getAutoRun())) return;
        ModerationQueueEntity q;
        try {
            q = queueRepository.findById(queueId).orElse(null);
        } catch (Exception e) {
            return;
        }
        if (q == null) return;
        if (q.getCurrentStage() != QueueStage.LLM && q.getCurrentStage() != QueueStage.HUMAN) return;
        if (q.getStatus() != QueueStatus.PENDING && q.getStatus() != QueueStatus.REVIEWING) return;
        handleOne(q, cfg);
    }

    private void handleOne(ModerationQueueEntity q, ModerationLlmConfigEntity llmCfg) {
        if (q == null || q.getId() == null) return;
        Long queueId = q.getId();
        try {
            q = queueRepository.findById(queueId).orElse(q);
        } catch (Exception ignore) {
        }
        if (q.getStatus() != QueueStatus.PENDING && q.getStatus() != QueueStatus.REVIEWING) return;

        LocalDateTime lockedAt = LocalDateTime.now();
        String locker = "LLM_AUTO";
        LocalDateTime lockExpiredBefore = lockedAt.minusMinutes(5);
        QueueStage lockStage = q.getCurrentStage() == null ? QueueStage.LLM : q.getCurrentStage();
        int locked = 0;
        try {
            locked = queueRepository.tryLockForAutoRun(
                    queueId,
                    lockStage,
                    java.util.List.of(QueueStatus.PENDING, QueueStatus.REVIEWING),
                    QueueStatus.REVIEWING,
                    locker,
                    lockedAt,
                    lockExpiredBefore
            );
        } catch (Exception ignore) {
            locked = 0;
        }
        if (locked <= 0) return;

        try {
            try {
                q = queueRepository.findById(queueId).orElse(q);
            } catch (Exception ignore) {
            }

        String policyVersion;
        Map<String, Object> policyConfig;
        try {
            ModerationPolicyConfigEntity policy = policyConfigRepository.findByContentType(q.getContentType()).orElse(null);
            policyVersion = policy == null ? null : policy.getPolicyVersion();
            policyConfig = policy == null ? null : policy.getConfig();
        } catch (Exception e) {
            policyVersion = null;
            policyConfig = null;
        }
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalStateException("moderation_policy_config not initialized for contentType=" + q.getContentType());
        }
        if (policyConfig == null) policyConfig = Map.of();
        String reviewStage = resolveReviewStage(q);

        ModerationPipelineRunEntity run;
        try {
            run = pipelineTraceService.ensureRun(q);
        } catch (Exception e) {
            run = null;
        }
        if (run == null) {
            q.setCurrentStage(QueueStage.RULE);
            queueRepository.updateStageIfPendingOrReviewing(q.getId(), QueueStage.RULE, LocalDateTime.now());
            return;
        }

        boolean hasRule = !pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(run.getId(), ModerationPipelineStepEntity.Stage.RULE).isEmpty();
        boolean hasVec = !pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(run.getId(), ModerationPipelineStepEntity.Stage.VEC).isEmpty();
        if (!hasRule || !hasVec) {
            q.setCurrentStage(QueueStage.RULE);
            queueRepository.updateStageIfPendingOrReviewing(q.getId(), QueueStage.RULE, LocalDateTime.now());

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

        ModerationConfidenceFallbackConfigEntity fb = fallbackRepository.findFirstByOrderByUpdatedAtDescIdDesc()
                .orElseThrow(() -> new IllegalStateException("moderation_confidence_fallback_config not initialized"));

        FilesReadiness files = checkPostFilesReadiness(q);
        if (files.hasAttachments) {
            String hardReject = detectHardRejectFromPostFiles(q.getContentId());
            if (hardReject != null) {
                Long llmStepId = null;
                try {
                    ModerationPipelineStepEntity step = pipelineTraceService.startStep(
                            run.getId(),
                            ModerationPipelineStepEntity.Stage.LLM,
                            7,
                            fb.getLlmRejectThreshold(),
                            Map.of("hardRejectReason", hardReject, "source", "FILES")
                    );
                    llmStepId = step.getId();
                } catch (Exception ignore) {
                }
                try {
                    if (llmStepId != null) {
                        pipelineTraceService.finishStepOk(llmStepId, "REJECT", 1.0, Map.of("hardRejectReason", hardReject, "source", "FILES"));
                    }
                } catch (Exception ignore) {
                }

                queueService.autoReject(q.getId(), normalizeOneLine("Attachment scan failed: " + hardReject), run.getTraceId());
                pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.REJECT);

                auditLogWriter.writeSystem(
                        "LLM_DECISION",
                        "MODERATION_QUEUE",
                        q.getId(),
                        AuditResult.SUCCESS,
                        "Hard reject by files: " + hardReject,
                        run.getTraceId(),
                        Map.of("runId", run.getId(), "stage", "LLM", "decision", "REJECT", "hardRejectReason", hardReject, "source", "FILES")
                );
                return;
            }
        }
        if (files.hasAttachments && !files.pendingFileAssetIds.isEmpty()) {
            int waitFilesSeconds = resolveWaitFilesSeconds(llmCfg);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime base = q.getCreatedAt() != null ? q.getCreatedAt() : (q.getUpdatedAt() != null ? q.getUpdatedAt() : now);
            long ageSeconds = Math.max(0L, Duration.between(base, now).getSeconds());
            if (ageSeconds <= waitFilesSeconds) {
                queueRepository.updateStageIfPendingOrReviewing(q.getId(), QueueStage.LLM, now);
                auditLogWriter.writeSystem(
                        "LLM_DECISION",
                        "MODERATION_QUEUE",
                        q.getId(),
                        AuditResult.SUCCESS,
                        "LLM waiting files extraction",
                        run.getTraceId(),
                        Map.of(
                                "runId", run.getId(),
                                "stage", "LLM",
                                "decision", "WAIT_FILES",
                                "waitFilesSeconds", waitFilesSeconds,
                                "ageSeconds", ageSeconds,
                                "pendingFileAssetIds", files.pendingFileAssetIds
                        )
                );
                return;
            }

            Long llmStepId = null;
            try {
                ModerationPipelineStepEntity step = pipelineTraceService.startStep(
                        run.getId(),
                        ModerationPipelineStepEntity.Stage.LLM,
                        7,
                        fb.getLlmRejectThreshold(),
                        Map.of(
                                "waitFilesSeconds", waitFilesSeconds,
                                "ageSeconds", ageSeconds,
                                "pendingFileAssetIds", files.pendingFileAssetIds,
                                "timeout", true
                        )
                );
                llmStepId = step.getId();
            } catch (Exception ignore) {
            }
            try {
                if (llmStepId != null) {
                    pipelineTraceService.finishStepOk(llmStepId, "HUMAN", null, Map.of("timeout", true));
                }
            } catch (Exception ignore) {
            }
            q.setCurrentStage(QueueStage.HUMAN);
            q.setStatus(QueueStatus.HUMAN);
            queueRepository.updateStageAndStatusIfPendingOrReviewing(q.getId(), QueueStage.HUMAN, QueueStatus.HUMAN, now);
            pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.HUMAN);

            auditLogWriter.writeSystem(
                    "LLM_DECISION",
                    "MODERATION_QUEUE",
                    q.getId(),
                    AuditResult.SUCCESS,
                    "LLM files extraction timeout -> HUMAN",
                    run.getTraceId(),
                    Map.of(
                            "runId", run.getId(),
                            "stage", "LLM",
                            "decision", "HUMAN",
                            "waitFilesSeconds", waitFilesSeconds,
                            "ageSeconds", ageSeconds,
                            "pendingFileAssetIds", files.pendingFileAssetIds
                    )
            );
            return;
        }

        if (!Boolean.TRUE.equals(fb.getLlmEnabled())) {
            List<ModerationPipelineStepEntity> rules = pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(run.getId(), ModerationPipelineStepEntity.Stage.RULE);
            List<ModerationPipelineStepEntity> vecs = pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(run.getId(), ModerationPipelineStepEntity.Stage.VEC);
            ModerationPipelineStepEntity rule = rules.isEmpty() ? null : rules.get(rules.size() - 1);
            ModerationPipelineStepEntity vec = vecs.isEmpty() ? null : vecs.get(vecs.size() - 1);
            String ruleDecision = rule == null ? null : rule.getDecision();
            String vecDecision = vec == null ? null : vec.getDecision();

            boolean lowRisk = "PASS".equalsIgnoreCase(ruleDecision) && "MISS".equalsIgnoreCase(vecDecision);
            if (lowRisk) {
                Long llmStepId = null;
                try {
                    ModerationPipelineStepEntity step = pipelineTraceService.startStep(
                            run.getId(),
                            ModerationPipelineStepEntity.Stage.LLM,
                            7,
                            fb.getLlmRejectThreshold(),
                            Map.of("llmEnabled", false, "autoApproved", true)
                    );
                    llmStepId = step.getId();
                } catch (Exception ignore) {
                }
                try {
                    if (llmStepId != null) {
                        pipelineTraceService.finishStepOk(llmStepId, "SKIP", null, Map.of("llmEnabled", false, "autoApproved", true));
                    }
                } catch (Exception ignore) {
                }

                queueService.autoApprove(q.getId(), "", run.getTraceId());
                pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.APPROVE);

                auditLogWriter.writeSystem(
                        "LLM_DECISION",
                        "MODERATION_QUEUE",
                        q.getId(),
                        AuditResult.SUCCESS,
                        "LLM skipped (disabled) -> auto approve",
                        run.getTraceId(),
                        Map.of("runId", run.getId(), "stage", "LLM", "decision", "APPROVE", "llmEnabled", false, "ruleDecision", String.valueOf(ruleDecision), "vecDecision", String.valueOf(vecDecision))
                );
                return;
            }

            q.setCurrentStage(QueueStage.HUMAN);
            q.setStatus(QueueStatus.HUMAN);
            queueRepository.updateStageAndStatusIfPendingOrReviewing(q.getId(), QueueStage.HUMAN, QueueStatus.HUMAN, LocalDateTime.now());

            auditLogWriter.writeSystem(
                    "LLM_DECISION",
                    "MODERATION_QUEUE",
                    q.getId(),
                    AuditResult.SUCCESS,
                    "LLM skipped (disabled)",
                    run.getTraceId(),
                    Map.of("runId", run.getId(), "stage", "LLM", "decision", "SKIP", "ruleDecision", String.valueOf(ruleDecision), "vecDecision", String.valueOf(vecDecision))
            );
            return;
        }

        // best-effort set stage to LLM
        if (q.getCurrentStage() != QueueStage.LLM) {
            q.setCurrentStage(QueueStage.LLM);
            queueRepository.updateStageIfPendingOrReviewing(q.getId(), QueueStage.LLM, LocalDateTime.now());
        }

        // Start LLM step with prior results summary
        Long llmStepId = null;
        Map<String, Object> prior = new LinkedHashMap<>();
        {
            List<ModerationPipelineStepEntity> rules = pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(run.getId(), ModerationPipelineStepEntity.Stage.RULE);
            List<ModerationPipelineStepEntity> vecs = pipelineStepRepository.findAllByRunIdAndStageOrderByStepOrderAsc(run.getId(), ModerationPipelineStepEntity.Stage.VEC);
            ModerationPipelineStepEntity rule = rules.isEmpty() ? null : rules.get(rules.size() - 1);
            ModerationPipelineStepEntity vec = vecs.isEmpty() ? null : vecs.get(vecs.size() - 1);
            if (rule != null) prior.put("rule", rule.getDetailsJson());
            if (vec != null) prior.put("vec", vec.getDetailsJson());
        }

        try {
            ModerationPipelineStepEntity step = pipelineTraceService.startStep(
                    run.getId(),
                    ModerationPipelineStepEntity.Stage.LLM,
                    7,
                    fb.getLlmRejectThreshold(),
                    Map.of("prior", prior)
            );
            llmStepId = step.getId();
        } catch (Exception ignore) {
        }

        ModerationChunkReviewService.ChunkWorkResult chunkWork;
        try {
            chunkWork = chunkReviewService.prepareChunksIfNeeded(q);
        } catch (Exception e) {
            chunkWork = null;
        }

        String inputMode;
        if (chunkWork != null && chunkWork.chunked) {
            inputMode = "chunk";
        } else if (files.hasAttachments) {
            inputMode = "multimodal";
        } else {
            inputMode = "text";
        }
        try {
            pipelineTraceService.ensureRun(q);
        } catch (Exception ignore) {
        }

        if (chunkWork != null && chunkWork.enabled && chunkWork.chunked && !chunkWork.cancelled && chunkWork.chunkSetId != null) {
            handleChunked(q, run, fb, llmStepId, prior, chunkWork.chunkSetId, policyConfig, reviewStage);
            return;
        }

        LlmModerationTestRequest req = new LlmModerationTestRequest();
        req.setQueueId(q.getId());
        req.setReviewStage(reviewStage);
        LlmModerationTestResponse res;
        LlmModerationTestResponse upgradeRes = null;
        boolean upgraded = false;
        Long textStepId = null;
        Long visionStepId = null;
        Long judgeStepId = null;
        try {
            ModerationPipelineStepEntity t = pipelineTraceService.startStep(
                    run.getId(),
                    ModerationPipelineStepEntity.Stage.TEXT,
                    3,
                    fb.getLlmTextRiskThreshold(),
                    Map.of("inputMode", inputMode)
            );
            textStepId = t.getId();
        } catch (Exception ignore) {
        }
        if (files.hasAttachments) {
            try {
                ModerationPipelineStepEntity t = pipelineTraceService.startStep(
                        run.getId(),
                        ModerationPipelineStepEntity.Stage.VISION,
                        4,
                        fb.getLlmImageRiskThreshold(),
                        Map.of("inputMode", inputMode)
                );
                visionStepId = t.getId();
            } catch (Exception ignore) {
            }
            try {
                ModerationPipelineStepEntity t = pipelineTraceService.startStep(
                        run.getId(),
                        ModerationPipelineStepEntity.Stage.JUDGE,
                        5,
                        fb.getLlmCrossModalThreshold(),
                        Map.of("inputMode", inputMode)
                );
                judgeStepId = t.getId();
            } catch (Exception ignore) {
            }
        }
        try {
            res = llmService.test(req);
        } catch (Exception ex) {
            // On upstream error: mark run fail + route to HUMAN
            q.setCurrentStage(QueueStage.HUMAN);
            q.setStatus(QueueStatus.HUMAN);
            queueRepository.updateStageAndStatusIfPendingOrReviewing(q.getId(), QueueStage.HUMAN, QueueStatus.HUMAN, LocalDateTime.now());

            if (llmStepId != null) {
                pipelineTraceService.finishStepError(llmStepId, "LLM_CALL_FAILED", ex.getMessage(), Map.of());
            }
            if (textStepId != null) pipelineTraceService.finishStepError(textStepId, "LLM_CALL_FAILED", ex.getMessage(), Map.of());
            if (visionStepId != null) pipelineTraceService.finishStepError(visionStepId, "LLM_CALL_FAILED", ex.getMessage(), Map.of());
            if (judgeStepId != null) pipelineTraceService.finishStepError(judgeStepId, "LLM_CALL_FAILED", ex.getMessage(), Map.of());
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

        try {
            LlmModerationTestResponse.Stages ss = res == null ? null : res.getStages();
            LlmModerationTestResponse.Stage sText = ss == null ? null : ss.getText();
            LlmModerationTestResponse.Stage sVision = ss == null ? null : ss.getImage();
            LlmModerationTestResponse.Stage sJudge = ss == null ? null : ss.getJudge();
            LlmModerationTestResponse.Stage sUpgrade = ss == null ? null : ss.getUpgrade();
            Long upgradeStepId = null;
            if (sUpgrade != null) {
                try {
                    ModerationPipelineStepEntity t = pipelineTraceService.startStep(
                            run.getId(),
                            ModerationPipelineStepEntity.Stage.UPGRADE,
                            6,
                            null,
                            Map.of("reason", "llm_upgrade_stage", "inputMode", inputMode)
                    );
                    upgradeStepId = t.getId();
                } catch (Exception ignore) {
                }
            }

            if (textStepId != null) {
                if (sText == null) pipelineTraceService.finishStepOk(textStepId, "SKIP", null, Map.of("reason", "no_text_stage"));
                else pipelineTraceService.finishStepOk(textStepId, normalizeDecisionOrNull(sText.getDecision()), sText.getScore(), summarizeLlmStage(sText));
            }
            if (visionStepId != null) {
                if (sVision == null) pipelineTraceService.finishStepOk(visionStepId, "SKIP", null, Map.of("reason", "no_vision_stage"));
                else pipelineTraceService.finishStepOk(visionStepId, normalizeDecisionOrNull(sVision.getDecision()), sVision.getScore(), summarizeLlmStage(sVision));
            }
            if (judgeStepId != null) {
                if (sJudge == null) pipelineTraceService.finishStepOk(judgeStepId, "SKIP", null, Map.of("reason", "no_judge_stage"));
                else pipelineTraceService.finishStepOk(judgeStepId, normalizeDecisionOrNull(sJudge.getDecision()), sJudge.getScore(), summarizeLlmStage(sJudge));
            }
            if (upgradeStepId != null) {
                pipelineTraceService.finishStepOk(upgradeStepId, normalizeDecisionOrNull(sUpgrade.getDecision()), sUpgrade.getScore(), summarizeLlmStage(sUpgrade));
            }
        } catch (Exception ignore) {
        }

        try {
            Map<String, Object> thresholds = fb == null ? null : fb.getThresholds();
            boolean upgradeEnable = asBooleanRequired(thresholds == null ? null : thresholds.get("llm.text.upgrade.enable"), "llm.text.upgrade.enable");
            double grayMin = clamp01Strict(asDoubleRequired(thresholds == null ? null : thresholds.get("llm.text.upgrade.scoreMin"), "llm.text.upgrade.scoreMin"));
            double grayMax = clamp01Strict(asDoubleRequired(thresholds == null ? null : thresholds.get("llm.text.upgrade.scoreMax"), "llm.text.upgrade.scoreMax"));
            double uncertaintyMin = clamp01Strict(asDoubleRequired(thresholds == null ? null : thresholds.get("llm.text.upgrade.uncertaintyMin"), "llm.text.upgrade.uncertaintyMin"));
            if (grayMin > grayMax) grayMin = grayMax;

            boolean textOnly = res == null || res.getImages() == null || res.getImages().isEmpty();
            Double sc0 = res == null ? null : res.getScore();
            double score0 = sc0 == null ? 0.0 : clamp01(sc0, 0.0);
            Double un0 = res == null ? null : res.getUncertainty();
            double uncertainty0 = (un0 == null || !Double.isFinite(un0)) ? 0.0 : clamp01(un0, 0.0);

            boolean inGrayZone = score0 >= grayMin && score0 <= grayMax;
            boolean uncertain = uncertainty0 >= uncertaintyMin;
            if (upgradeEnable && textOnly && (inGrayZone || uncertain)) {
                Long upgradeStepId = null;
                try {
                    ModerationPipelineStepEntity s = pipelineTraceService.startStep(
                            run.getId(),
                            ModerationPipelineStepEntity.Stage.UPGRADE,
                            6,
                            uncertaintyMin,
                            Map.of("reason", "text_upgrade", "grayMin", grayMin, "grayMax", grayMax, "uncertaintyMin", uncertaintyMin)
                    );
                    upgradeStepId = s.getId();
                } catch (Exception ignore) {
                }
                LlmModerationTestRequest req2 = new LlmModerationTestRequest();
                req2.setQueueId(q.getId());
                req2.setUseQueue(Boolean.FALSE);
                LlmModerationTestRequest.LlmModerationConfigOverrideDTO ov = new LlmModerationTestRequest.LlmModerationConfigOverrideDTO();
                ov.setPromptTemplate(judgePromptTemplate(llmCfg));
                req2.setConfigOverride(ov);
                try {
                    upgradeRes = llmService.test(req2);
                    if (upgradeStepId != null) {
                        pipelineTraceService.finishStepOk(upgradeStepId, normalizeDecisionOrNull(upgradeRes.getDecision()), upgradeRes.getScore(), summarizeLlmRes(upgradeRes));
                    }
                } catch (Exception ex) {
                    if (upgradeStepId != null) {
                        pipelineTraceService.finishStepError(upgradeStepId, "LLM_CALL_FAILED", ex.getMessage(), Map.of());
                    }
                    throw ex;
                }
                upgraded = true;
            }
        } catch (Exception ignore) {
            upgraded = true;
            upgradeRes = null;
        }

        LlmModerationTestResponse effectiveRes = upgradeRes != null ? upgradeRes : res;
        Map<String, Double> tagThresholds = resolveRiskTagThresholdsCached();
        PolicyEval policyEval = evaluatePolicyVerdict(policyConfig, reviewStage, effectiveRes, tagThresholds, upgraded, upgradeRes == null);
        Verdict verdict = policyEval.verdict;

        // Save step details
        if (llmStepId != null) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("upgraded", upgraded);
            details.put("reviewStage", reviewStage);
            details.put("policyVersion", policyVersion);
            if (policyEval.details != null && !policyEval.details.isEmpty()) details.put("policyEval", policyEval.details);
            if (res != null) details.put("primary", summarizeLlmRes(res));
            if (upgradeRes != null) details.put("upgrade", summarizeLlmRes(upgradeRes));
            if (effectiveRes != null) {
                details.put("model", effectiveRes.getModel());
                details.put("decision", effectiveRes.getDecision());
                details.put("decision_suggestion", effectiveRes.getDecisionSuggestion());
                details.put("score", effectiveRes.getScore());
                details.put("risk_score", effectiveRes.getRiskScore());
                details.put("reasons", effectiveRes.getReasons());
                details.put("riskTags", effectiveRes.getRiskTags());
                details.put("labels", effectiveRes.getLabels());
                details.put("severity", effectiveRes.getSeverity());
                details.put("uncertainty", effectiveRes.getUncertainty());
                if (effectiveRes.getEvidence() != null && !effectiveRes.getEvidence().isEmpty()) {
                    int take = Math.min(10, effectiveRes.getEvidence().size());
                    details.put("evidence", effectiveRes.getEvidence().subList(0, take));
                }
                details.put("inputMode", effectiveRes.getInputMode());
                if (effectiveRes.getStages() != null) {
                    details.put("stages", effectiveRes.getStages());
                }
                if (effectiveRes.getImages() != null) {
                    details.put("imageCount", effectiveRes.getImages().size());
                    int take = Math.min(5, effectiveRes.getImages().size());
                    details.put("images", effectiveRes.getImages().subList(0, take));
                }
                // raw output may be big; still store but truncate
                String raw = effectiveRes.getRawModelOutput();
                if (raw != null && raw.length() > 2000) raw = raw.substring(0, 2000);
                details.put("rawModelOutput", raw);
                details.put("latencyMs", effectiveRes.getLatencyMs());
                details.put("usage", effectiveRes.getUsage());
            }
            pipelineTraceService.finishStepOk(
                    llmStepId,
                    verdict == Verdict.APPROVE ? "APPROVE" : (verdict == Verdict.REJECT ? "REJECT" : "HUMAN"),
                    effectiveRes == null ? null : (effectiveRes.getRiskScore() == null ? effectiveRes.getScore() : effectiveRes.getRiskScore()),
                    details
            );
        }

        // Update run decision
        if (verdict == Verdict.APPROVE) {
            queueService.autoApprove(q.getId(), "", run.getTraceId());
            applyRiskTags(q, effectiveRes);
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
            queueService.autoReject(q.getId(), buildUserFacingRejectReason(effectiveRes, "Content violates policy"), run.getTraceId());
            applyRiskTags(q, effectiveRes);
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
        q.setStatus(QueueStatus.HUMAN);
        queueRepository.updateStageAndStatusIfPendingOrReviewing(q.getId(), QueueStage.HUMAN, QueueStatus.HUMAN, LocalDateTime.now());
        applyRiskTags(q, effectiveRes != null ? effectiveRes : res);
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
        } finally {
            try {
                queueRepository.unlockAutoRun(queueId, locker, LocalDateTime.now());
            } catch (Exception ignore) {
            }
        }
    }

    static Object sanitizeModerationResponseForQueueOutput(ObjectMapper objectMapper, LlmModerationTestResponse res) {
        return ModerationLlmAutoRunnerSupport.sanitizeModerationResponseForQueueOutput(objectMapper, res);
    }


    private String judgePromptTemplate(ModerationLlmConfigEntity cfg) {
        ModerationLlmConfigEntity effective = cfg;
        if (effective == null) {
            effective = llmConfigRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("moderation_llm_config not initialized"));
        }
        String promptCode = toStr(effective.getJudgePromptCode());
        if (promptCode == null) {
            throw new IllegalStateException("moderation_llm_config.judge_prompt_code is required");
        }
        return promptsRepository.findByPromptCode(promptCode)
                .map(com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity::getUserPromptTemplate)
                .orElseThrow(() -> new IllegalStateException("Prompt " + promptCode + " not found"));
    }

    private static class FilesReadiness {
        final boolean hasAttachments;
        final List<Long> pendingFileAssetIds;

        private FilesReadiness(boolean hasAttachments, List<Long> pendingFileAssetIds) {
            this.hasAttachments = hasAttachments;
            this.pendingFileAssetIds = pendingFileAssetIds == null ? List.of() : pendingFileAssetIds;
        }
    }

    private int resolveWaitFilesSeconds(ModerationLlmConfigEntity cfg) {
        if (cfg != null && cfg.getMultimodalPromptCode() != null) {
            try {
                var prompt = promptsRepository.findByPromptCode(cfg.getMultimodalPromptCode()).orElse(null);
                if (prompt != null && prompt.getWaitFilesSeconds() != null) {
                    int n = prompt.getWaitFilesSeconds();
                    if (n < 0) n = 0;
                    if (n > 3600) n = 3600;
                    return n;
                }
            } catch (Exception ignore) {
            }
        }
        return 60;
    }

    private FilesReadiness checkPostFilesReadiness(ModerationQueueEntity q) {
        if (q == null || q.getContentType() != com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType.POST) {
            return new FilesReadiness(false, List.of());
        }
        Long postId = q.getContentId();
        if (postId == null) return new FilesReadiness(false, List.of());

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                0,
                200,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.asc("createdAt"), org.springframework.data.domain.Sort.Order.asc("id"))
        );
        List<Long> fileAssetIds = new ArrayList<>();
        try {
            var page = postAttachmentsRepository.findByPostId(postId, pageable);
            if (page != null && page.getContent() != null) {
                for (PostAttachmentsEntity a : page.getContent()) {
                    if (a == null || a.getFileAssetId() == null) continue;
                    if (!fileAssetIds.contains(a.getFileAssetId())) fileAssetIds.add(a.getFileAssetId());
                }
            }
        } catch (Exception ignore) {
        }
        if (fileAssetIds.isEmpty()) return new FilesReadiness(false, List.of());

        Map<Long, FileAssetExtractionsEntity> extById = new HashMap<>();
        try {
            List<FileAssetExtractionsEntity> exts = fileAssetExtractionsRepository.findAllById(fileAssetIds);
            if (exts != null) {
                for (FileAssetExtractionsEntity e : exts) {
                    if (e == null || e.getFileAssetId() == null) continue;
                    extById.put(e.getFileAssetId(), e);
                }
            }
        } catch (Exception ignore) {
        }

        List<Long> pending = new ArrayList<>();
        for (Long id : fileAssetIds) {
            if (id == null) continue;
            FileAssetExtractionsEntity e = extById.get(id);
            if (e == null) {
                try {
                    fileAssetExtractionService.requestExtractionIfEnabled(id);
                    e = fileAssetExtractionsRepository.findById(id).orElse(null);
                } catch (Exception ignore) {
                }
            }
            if (e == null) continue;
            if (e.getExtractStatus() == FileAssetExtractionStatus.PENDING) {
                pending.add(id);
            }
        }
        return new FilesReadiness(true, pending);
    }

    private String detectHardRejectFromPostFiles(Long postId) {
        if (postId == null) return null;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                0,
                200,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.asc("createdAt"), org.springframework.data.domain.Sort.Order.asc("id"))
        );
        List<Long> fileAssetIds = new ArrayList<>();
        try {
            var page = postAttachmentsRepository.findByPostId(postId, pageable);
            if (page != null && page.getContent() != null) {
                for (PostAttachmentsEntity a : page.getContent()) {
                    if (a == null || a.getFileAssetId() == null) continue;
                    if (!fileAssetIds.contains(a.getFileAssetId())) fileAssetIds.add(a.getFileAssetId());
                }
            }
        } catch (Exception ignore) {
        }
        if (fileAssetIds.isEmpty()) return null;
        try {
            for (FileAssetExtractionsEntity e : fileAssetExtractionsRepository.findAllById(fileAssetIds)) {
                if (e == null) continue;
                if (e.getExtractStatus() != FileAssetExtractionStatus.FAILED) continue;
                String msg = e.getErrorMessage();
                if (msg != null && msg.contains("ARCHIVE_NESTING_TOO_DEEP")) return "ARCHIVE_NESTING_TOO_DEEP";
                String meta = e.getExtractedMetadataJson();
                if (meta != null && meta.contains("ARCHIVE_NESTING_TOO_DEEP")) return "ARCHIVE_NESTING_TOO_DEEP";
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private Map<String, Double> resolveRiskTagThresholdsCached() {
        long now = System.currentTimeMillis();
        long loadedAt = riskTagThresholdsLoadedAtMs;
        if (loadedAt > 0 && (now - loadedAt) <= 10_000) return riskTagThresholdsCache;
        synchronized (this) {
            now = System.currentTimeMillis();
            loadedAt = riskTagThresholdsLoadedAtMs;
            if (loadedAt > 0 && (now - loadedAt) <= 10_000) return riskTagThresholdsCache;
            HashMap<String, Double> map = new HashMap<>();
            try {
                tagsRepository.findByTypeAndIsActiveTrue(TagType.RISK).forEach(t -> {
                    if (t == null) return;
                    String slug = t.getSlug();
                    Double th = t.getThreshold();
                    if (slug == null || slug.isBlank()) return;
                    if (th == null || !Double.isFinite(th)) return;
                    map.put(slug.trim(), clamp01(th, 0.0));
                });
            } catch (Exception ignore) {
            }
            riskTagThresholdsCache = map.isEmpty() ? Map.of() : Map.copyOf(map);
            riskTagThresholdsLoadedAtMs = now;
            return riskTagThresholdsCache;
        }
    }

    private void writeChunkedDecisionAuditLog(
            ModerationQueueEntity q,
            ModerationPipelineRunEntity run,
            Long llmStepId,
            Long chunkSetId,
            String finalDecision,
            LlmModerationTestResponse globalRes,
            Map<String, Object> extraDetails
    ) {
        if (q == null || q.getId() == null || run == null) return;
        try {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("runId", run.getId());
            details.put("stage", "LLM");
            details.put("mode", "chunked");
            details.put("decision", finalDecision);
            if (llmStepId != null) details.put("llmStepId", llmStepId);
            if (chunkSetId != null) details.put("chunkSetId", chunkSetId);

            if (globalRes != null) {
                if (globalRes.getModel() != null) details.put("model", globalRes.getModel());
                if (globalRes.getDecision() != null) details.put("globalDecision", globalRes.getDecision());
                if (globalRes.getScore() != null) details.put("globalScore", globalRes.getScore());
            }

            try {
                AdminModerationChunkProgressDTO p = chunkReviewService.getProgress(q.getId(), false, 0);
                if (p != null) {
                    details.put("chunkProgress", Map.of(
                            "status", p.getStatus(),
                            "total", p.getTotalChunks(),
                            "completed", p.getCompletedChunks(),
                            "failed", p.getFailedChunks(),
                            "running", p.getRunningChunks()
                    ));
                    if (llmStepId != null && p.getTotalChunks() != null && p.getTotalChunks() > 0) {
                        ModerationPipelineStepEntity step = pipelineStepRepository.findById(llmStepId).orElse(null);
                        if (step != null && step.getCostMs() != null) {
                            details.put("avgLatencyMs", Math.max(0L, step.getCostMs() / Math.max(1, (long) p.getTotalChunks())));
                        }
                    }
                }
            } catch (Exception ignore) {
            }

            try {
                if (chunkSetId != null) {
                    Map<String, Object> mem = chunkReviewService.getMemory(chunkSetId);
                    if (mem != null) {
                        Object ms = mem.get("maxScore");
                        if (ms != null) details.put("maxScore", ms);
                    }
                }
            } catch (Exception ignore) {
            }

            if (extraDetails != null && !extraDetails.isEmpty()) details.putAll(extraDetails);

            String msg = "LLM chunked decision: " + finalDecision;
            auditLogWriter.writeSystem("LLM_DECISION", "MODERATION_QUEUE", q.getId(), AuditResult.SUCCESS, msg, run.getTraceId(), details);
        } catch (Exception ignore) {
        }
    }

    private List<ChunkImageRef> selectPostImageRefs(Long postId) {
        if (postId == null) return List.of();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                0,
                200,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.asc("createdAt"), org.springframework.data.domain.Sort.Order.asc("id"))
        );
        List<PostAttachmentsEntity> atts;
        try {
            var page = postAttachmentsRepository.findByPostId(postId, pageable);
            atts = page == null ? List.of() : (page.getContent() == null ? List.of() : page.getContent());
        } catch (Exception ignore) {
            atts = List.of();
        }
        if (atts.isEmpty()) return List.of();
        ArrayList<ChunkImageRef> out = new ArrayList<>();
        LinkedHashSet<String> seenUrl = new LinkedHashSet<>();
        int idx = 0;
        for (PostAttachmentsEntity a : atts) {
            if (a == null || a.getFileAsset() == null) continue;
            String mt0 = a.getFileAsset().getMimeType() == null ? "" : a.getFileAsset().getMimeType().trim().toLowerCase(Locale.ROOT);
            if (!mt0.startsWith("image/")) continue;
            String url = a.getFileAsset().getUrl();
            if (url == null || url.isBlank()) continue;
            String u = url.trim();
            if (!seenUrl.add(u)) continue;
            idx += 1;
            String ph = "[[IMAGE_" + idx + "]]";
            out.add(new ChunkImageRef(idx, ph, u, a.getFileAsset().getMimeType(), a.getFileAssetId()));
        }
        return out.isEmpty() ? List.of() : out;
    }

    private void applyRiskTags(ModerationQueueEntity q, LlmModerationTestResponse res) {
        if (q == null || res == null) return;
        try {
            Double score0 = res.getScore();
            double score = score0 == null ? 0.0 : score0;
            if (!Double.isFinite(score)) score = 0.0;
            if (score < 0) score = 0.0;
            if (score > 1) score = 1.0;
            LinkedHashSet<String> set = new LinkedHashSet<>();
            if (res.getRiskTags() != null) {
                for (String t : res.getRiskTags()) {
                    if (t == null) continue;
                    String s = t.trim();
                    if (!s.isEmpty()) set.add(s);
                }
            }
            if (res.getLabels() != null) {
                for (String t : res.getLabels()) {
                    if (t == null) continue;
                    String s = t.trim();
                    if (!s.isEmpty()) set.add(s);
                }
            }
            List<String> tags = set.isEmpty() ? List.of() : new ArrayList<>(set);
            riskLabelingService.replaceRiskTags(q.getContentType(), q.getContentId(), Source.LLM, tags, BigDecimal.valueOf(score), false);
        } catch (Exception ignore) {
        }
    }

    private void applyChunkedRiskTags(ModerationQueueEntity q, Long chunkSetId, LlmModerationTestResponse res) {
        if (q == null) return;
        try {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            double score = 0.0;

            if (res != null) {
                Double score0 = res.getScore();
                double s = score0 == null ? 0.0 : score0;
                if (!Double.isFinite(s)) s = 0.0;
                if (s < 0) s = 0.0;
                if (s > 1) s = 1.0;
                score = Math.max(score, s);

                if (res.getRiskTags() != null) {
                    for (String t : res.getRiskTags()) {
                        if (t == null) continue;
                        String v = t.trim();
                        if (!v.isEmpty()) set.add(v);
                    }
                }
                if (res.getLabels() != null) {
                    for (String t : res.getLabels()) {
                        if (t == null) continue;
                        String v = t.trim();
                        if (!v.isEmpty()) set.add(v);
                    }
                }
            }

            if (chunkSetId != null) {
                Map<String, Object> mem = chunkReviewService.getMemory(chunkSetId);
                Object r0 = mem == null ? null : mem.get("riskTags");
                if (r0 instanceof Collection<?> col) {
                    for (Object o : col) {
                        if (o == null) continue;
                        String v = String.valueOf(o).trim();
                        if (!v.isEmpty()) set.add(v);
                    }
                }
                double memScore = clamp01(asDoubleOrDefault(mem == null ? null : mem.get("maxScore"), asDoubleOrDefault(mem == null ? null : mem.get("imageScore"), 0.0)), 0.0);
                score = Math.max(score, memScore);
            }

            if (set.isEmpty()) return;
            riskLabelingService.replaceRiskTags(q.getContentType(), q.getContentId(), Source.LLM, new ArrayList<>(set), BigDecimal.valueOf(score), false);
        } catch (Exception ignore) {
        }
    }

    static List<LlmModerationTestRequest.ImageInput> selectChunkImageInputs(ObjectMapper objectMapper,
                                                                            String chunkText,
                                                                            Long fileAssetId,
                                                                            String extractedMetadataJson) {
        return refsToImageInputs(selectChunkImageRefs(objectMapper, chunkText, fileAssetId, extractedMetadataJson), fileAssetId);
    }

    static List<ChunkImageRef> selectChunkImageRefs(ObjectMapper objectMapper,
                                                    String chunkText,
                                                    Long fileAssetId,
                                                    String extractedMetadataJson) {
        return fromSupportRefs(ModerationLlmAutoRunnerSupport.selectChunkImageRefs(objectMapper, chunkText, fileAssetId, extractedMetadataJson));
    }

    static Set<Integer> parseUsedImageIndices(String text) {
        return ModerationLlmAutoRunnerSupport.parseUsedImageIndices(text);
    }

    static List<String> filterChunkEvidence(List<String> evidence) {
        return ModerationLlmAutoRunnerSupport.filterChunkEvidence(evidence);
    }

    static String extractByContextAnchors(Map<String, Object> node, String chunkText) {
        if (node == null || chunkText == null || chunkText.isBlank()) return null;
        Object beforeObj = node.get("before_context");
        String before = beforeObj == null ? null : String.valueOf(beforeObj).trim();
        if (before == null || before.isBlank()) return null;
        Object afterObj = node.get("after_context");
        String after = afterObj == null ? null : String.valueOf(afterObj).trim();
        String extracted = extractBetweenAnchorsByRegex(chunkText, before, after, 500);
        if (extracted == null || extracted.isBlank()) return null;
        return extracted;
    }

    static List<String> buildEvidenceNormalizeReplay(List<String> before, List<String> after, int maxItems) {
        return ModerationLlmAutoRunnerSupport.buildEvidenceNormalizeReplay(before, after, maxItems);
    }

    static List<String> filterChunkImageEvidence(List<String> evidence, List<ChunkImageRef> refs) {
        return ModerationLlmAutoRunnerSupport.filterChunkImageEvidence(evidence, toSupportRefs(refs));
    }

    static String normalizeSuggestion(String suggestion, String decisionFallback) {
        return ModerationLlmAutoRunnerSupport.normalizeSuggestion(suggestion, decisionFallback);
    }

    static List<String> asStringList(Object v) {
        return ModerationLlmAutoRunnerSupport.asStringList(v);
    }

    static Object deepGet(Map<String, Object> root, String path) {
        return ModerationLlmAutoRunnerSupport.deepGet(root, path);
    }

    static String normalizeImageEvidenceToTextAnchor(String evidenceItem, String chunkText) {
        return ModerationLlmAutoRunnerSupport.normalizeImageEvidenceToTextAnchor(evidenceItem, chunkText);
    }

    static String normalizeChunkImageEvidenceId(String evidenceItem, List<ChunkImageRef> actualChunkImageRefs) {
        return ModerationLlmAutoRunnerSupport.normalizeChunkImageEvidenceId(evidenceItem, toSupportRefs(actualChunkImageRefs));
    }

    static Integer parseImageIndexFromPlaceholder(String placeholder) {
        return ModerationLlmAutoRunnerSupport.parseImageIndexFromPlaceholder(placeholder);
    }

    static boolean containsNormalizedText(String text, String needle) {
        return ModerationLlmAutoRunnerSupport.containsNormalizedText(text, needle);
    }

    static List<ChunkImageRef> filterChunkImageRefsByIndices(List<ChunkImageRef> refs, Set<Integer> indices) {
        return fromSupportRefs(ModerationLlmAutoRunnerSupport.filterChunkImageRefsByIndices(toSupportRefs(refs), indices));
    }

    static List<ChunkImageRef> mergeChunkImageRefs(List<ChunkImageRef> primaryRefs, List<ChunkImageRef> secondaryRefs) {
        return fromSupportRefs(ModerationLlmAutoRunnerSupport.mergeChunkImageRefs(toSupportRefs(primaryRefs), toSupportRefs(secondaryRefs)));
    }

    static List<String> extractPlaceholdersFromRefs(List<ChunkImageRef> refs) {
        return ModerationLlmAutoRunnerSupport.extractPlaceholdersFromRefs(toSupportRefs(refs));
    }

    static EvidenceImageSelection selectEvidenceDrivenChunkImages(Map<String, Object> mem,
                                                                  Integer chunkIndex,
                                                                  List<ChunkImageRef> candidateRefs) {
        ModerationLlmAutoRunnerSupport.EvidenceImageSelection selection =
                ModerationLlmAutoRunnerSupport.selectEvidenceDrivenChunkImages(mem, chunkIndex, toSupportRefs(candidateRefs));
        if (selection == null) {
            return new EvidenceImageSelection(List.of(), List.of(), List.of());
        }
        return new EvidenceImageSelection(
                fromSupportRefs(selection.selectedRefs),
                selection.sourceChunkIndexes,
                selection.placeholders
        );
    }

    static List<LlmModerationTestRequest.ImageInput> refsToImageInputs(List<ChunkImageRef> refs, Long fileAssetId) {
        return ModerationLlmAutoRunnerSupport.refsToImageInputs(toSupportRefs(refs), fileAssetId);
    }

    static List<String> normalizeChunkEvidenceForLabels(ObjectMapper objectMapper,
                                                        List<String> evidence,
                                                        String chunkText,
                                                        List<ChunkImageRef> actualChunkImageRefs) {
        return ModerationLlmAutoRunnerSupport.normalizeChunkEvidenceForLabels(
                objectMapper,
                evidence,
                chunkText,
                toSupportRefs(actualChunkImageRefs)
        );
    }

    static String buildChunkPromptText(ModerationQueueEntity q,
                                       ModerationChunkReviewService.ChunkToProcess c,
                                       String raw,
                                       com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO cfg,
                                       Map<String, Object> mem,
                                       List<ChunkImageRef> imageRefs) {
        return ModerationLlmAutoRunnerSupport.buildChunkPromptText(
                q,
                c,
                raw,
                cfg,
                mem,
                toSupportRefs(imageRefs)
        );
    }

    static PolicyEval evaluatePolicyVerdict(Map<String, Object> policyConfig,
                                            String reviewStage,
                                            LlmModerationTestResponse res,
                                            Map<String, Double> tagThresholds,
                                            boolean upgraded,
                                            boolean upgradeFailed) {
        return ModerationLlmAutoRunnerSupport.evaluatePolicyVerdict(
                policyConfig,
                reviewStage,
                res,
                tagThresholds,
                upgraded,
                upgradeFailed
        );
    }

    static Thresholds resolveThresholdsRequired(Map<String, Object> policyConfig, String reviewStage, List<String> labels) {
        return ModerationLlmAutoRunnerSupport.resolveThresholdsRequired(policyConfig, reviewStage, labels);
    }

    static Thresholds resolveThresholdsPreferred(Map<String, Object> policyConfig,
                                                 String reviewStage,
                                                 List<String> labels,
                                                 ModerationConfidenceFallbackConfigEntity fb) {
        return ModerationLlmAutoRunnerSupport.resolveThresholdsPreferred(policyConfig, reviewStage, labels, fb);
    }

    static String resolveReviewStage(ModerationQueueEntity q) {
        return ModerationLlmAutoRunnerSupport.resolveReviewStage(q);
    }

    static String normalizeReviewStage(String reviewStage) {
        return ModerationLlmAutoRunnerSupport.normalizeReviewStage(reviewStage);
    }

    static boolean hasIntersection(List<String> a, List<String> b) {
        return ModerationLlmAutoRunnerSupport.hasIntersection(a, b);
    }

    static Verdict aggregateChunkVerdict(AdminModerationChunkProgressDTO progress,
                                         ModerationConfidenceFallbackConfigEntity fb,
                                         Map<String, Object> policyConfig,
                                         String reviewStage,
                                         List<String> labels) {
        return ModerationLlmAutoRunnerSupport.aggregateChunkVerdict(progress, fb, policyConfig, reviewStage, labels);
    }

    static Verdict guardChunkedAggregateByMemory(Verdict v,
                                                 Map<String, Object> mem,
                                                 ModerationConfidenceFallbackConfigEntity fb,
                                                 Map<String, Object> policyConfig,
                                                 String reviewStage,
                                                 List<String> labels) {
        return ModerationLlmAutoRunnerSupport.guardChunkedAggregateByMemory(v, mem, fb, policyConfig, reviewStage, labels);
    }

    static Verdict verdictFromLlm(LlmModerationTestResponse res, ModerationConfidenceFallbackConfigEntity fb) {
        return ModerationLlmAutoRunnerSupport.verdictFromLlm(res, fb);
    }

    static Verdict verdictFromScore(double score, double rejectThreshold, double humanThreshold) {
        return ModerationLlmAutoRunnerSupport.verdictFromScore(score, rejectThreshold, humanThreshold);
    }

    static Verdict verdictFromDecisionAndScore(String decision, Double score, double rejectThreshold, double humanThreshold) {
        return ModerationLlmAutoRunnerSupport.verdictFromDecisionAndScore(decision, score, rejectThreshold, humanThreshold);
    }

    static Verdict stricterVerdict(Verdict a, Verdict b) {
        return ModerationLlmAutoRunnerSupport.stricterVerdict(a, b);
    }

    static String normalizeDecisionOrNull(String decision) {
        return ModerationLlmAutoRunnerSupport.normalizeDecisionOrNull(decision);
    }

    static Map<String, Object> summarizeLlmStage(LlmModerationTestResponse.Stage stage) {
        return ModerationLlmAutoRunnerSupport.summarizeLlmStage(stage);
    }

    static Map<String, Object> summarizeLlmRes(LlmModerationTestResponse res) {
        return ModerationLlmAutoRunnerSupport.summarizeLlmRes(res);
    }

    static List<Map<String, Object>> extractEntitiesFromText(String text, int chunkIndex, int max) {
        return ModerationLlmAutoRunnerSupport.extractEntitiesFromText(text, chunkIndex, max);
    }

    static Map<String, Object> buildTokenDiagnostics(String promptText,
                                                     List<LlmModerationTestRequest.ImageInput> images,
                                                     LlmModerationTestResponse res,
                                                     com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity visionPrompt,
                                                     int imagesCandidate,
                                                     List<Integer> evidenceSourceChunks,
                                                     List<String> evidencePlaceholdersUsed) {
        return ModerationLlmAutoRunnerSupport.buildTokenDiagnostics(
                promptText,
                images,
                res,
                visionPrompt,
                imagesCandidate,
                evidenceSourceChunks,
                evidencePlaceholdersUsed
        );
    }

    static String classifyImageUrlKind(String rawUrl) {
        return ModerationLlmAutoRunnerSupport.classifyImageUrlKind(rawUrl);
    }

    static String buildUserFacingRejectReason(LlmModerationTestResponse res, String fallback) {
        return ModerationLlmAutoRunnerSupport.buildUserFacingRejectReason(res, fallback);
    }

    static String buildFinalReviewInput(Map<String, Object> mem) {
        return ModerationLlmAutoRunnerSupport.buildFinalReviewInput(mem);
    }

    static boolean asBooleanRequired(Object value, String key) {
        return ModerationLlmAutoRunnerSupport.asBooleanRequired(value, key);
    }

    static boolean asBooleanOrDefault(Object value, boolean def) {
        return ModerationLlmAutoRunnerSupport.asBooleanOrDefault(value, def);
    }

    static double asDoubleRequired(Object value, String key) {
        return ModerationLlmAutoRunnerSupport.asDoubleRequired(value, key);
    }

    static long asLongRequired(Object value, String key) {
        return ModerationLlmAutoRunnerSupport.asLongRequired(value, key);
    }

    static double clamp01Strict(double value) {
        return ModerationLlmAutoRunnerSupport.clamp01Strict(value);
    }

    static List<String> summarizeEvidenceMemory(Map<String, Object> mem, Integer chunkIndex, int maxLines) {
        return ModerationLlmAutoRunnerSupport.summarizeEvidenceMemory(mem, chunkIndex, maxLines);
    }

    static List<String> collectChunkEvidenceForStepDetail(Map<String, Object> mem, int maxItems) {
        return ModerationLlmAutoRunnerSupport.collectChunkEvidenceForStepDetail(mem, maxItems);
    }

    static String fingerprintAggregateEvidenceItem(String item) {
        return ModerationLlmAutoRunnerSupport.fingerprintAggregateEvidenceItem(item);
    }

    static String canonicalEvidenceValue(Object value) {
        return ModerationLlmAutoRunnerSupport.canonicalEvidenceValue(value);
    }

    static String normalizeForAnchorRegex(String value) {
        if (value == null) return "";
        String out = value
                .replace('\u201c', '"')
                .replace('\u201d', '"')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'');
        out = out.replaceAll(" ?\\\" ?", "\\\"")
                .replaceAll(" ?' ?", "'")
                .replaceAll("\\s+", " ")
                .trim();
        return out;
    }

    static String anchorToRegex(String anchor) {
        if (anchor == null) return "";
        String text = normalizeForAnchorRegex(anchor);
        if (text.isBlank()) return "";
        String[] parts = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (!sb.isEmpty()) sb.append("\\s+");
            sb.append(java.util.regex.Pattern.quote(part));
        }
        return sb.toString();
    }

    static String extractBetweenAnchorsByRegex(String text, String before, String after, int maxLen) {
        if (text == null || text.isEmpty() || before == null || before.isBlank()) return null;
        int cap = Math.max(20, Math.min(2000, maxLen));

        String normText = normalizeForAnchorRegex(text);
        String normBefore = normalizeForAnchorRegex(before);
        String normAfter = after == null || after.isBlank() ? null : normalizeForAnchorRegex(after);
        if (normBefore.isBlank() || normText.isBlank()) return null;

        String best = null;
        int bestLen = Integer.MAX_VALUE;
        boolean matchedWindow = false;

        int from = 0;
        while (from >= 0 && from < normText.length()) {
            int bIdx = normText.indexOf(normBefore, from);
            if (bIdx < 0) break;
            int start = bIdx + normBefore.length();

            if (normAfter != null && !normAfter.isBlank()) {
                int aIdx = normText.indexOf(normAfter, start);
                if (aIdx >= 0) {
                    matchedWindow = true;
                    String mid = normText.substring(start, aIdx);
                    if (mid.length() > cap) mid = mid.substring(0, cap);
                    String cleaned = cleanExtractedSnippet(mid);
                    cleaned = cleaned.replaceFirst("\\s*\\[[A-Z_]{2,}\\].*$", "").trim();
                    if (!cleaned.isBlank()) {
                        int len = cleaned.length();
                        if (len < bestLen) {
                            best = cleaned;
                            bestLen = len;
                        }
                    }
                }
            } else {
                matchedWindow = true;
                String tail = normText.substring(start);
                String candidate = fallbackViolationSnippet(tail, 0);
                String cleaned = cleanExtractedSnippet(candidate);
                cleaned = cleaned.replaceFirst("\\s*\\[[A-Z_]{2,}\\].*$", "").trim();
                if (!cleaned.isBlank()) {
                    if (cleaned.length() > cap) cleaned = cleaned.substring(0, cap);
                    int len = cleaned.length();
                    if (len < bestLen) {
                        best = cleaned;
                        bestLen = len;
                    }
                }
            }

            from = Math.max(start, bIdx + 1);
        }

        if (best != null) return best;
        if (matchedWindow) return null;

        int fallbackStart = normText.indexOf(normBefore);
        if (fallbackStart < 0) return null;
        String candidate = fallbackViolationSnippet(normText, fallbackStart + normBefore.length());
        String cleaned = cleanExtractedSnippet(candidate);
        cleaned = cleaned.replaceFirst("\\s*\\[[A-Z_]{2,}\\].*$", "").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    static boolean isSuspiciousEvidenceText(String text, String chunkText) {
        return ModerationLlmAutoRunnerSupport.isSuspiciousEvidenceText(text, chunkText);
    }

    static String clipTextForEvidenceJson(String text, int maxChars) {
        return ModerationLlmAutoRunnerSupport.clipTextForEvidenceJson(text, maxChars);
    }

    static String cleanExtractedSnippet(String snippet) {
        return ModerationLlmAutoRunnerSupport.cleanExtractedSnippet(snippet);
    }

    static int findBoundaryEnd(String text, int start, int maxEnd) {
        return ModerationLlmAutoRunnerSupport.findBoundaryEnd(text, start, maxEnd);
    }

    static String fallbackViolationSnippet(String text, int violationStart) {
        return ModerationLlmAutoRunnerSupport.fallbackViolationSnippet(text, violationStart);
    }

    private static List<ModerationLlmAutoRunnerSupport.ChunkImageRef> toSupportRefs(List<ChunkImageRef> refs) {
        if (refs == null || refs.isEmpty()) return List.of();
        List<ModerationLlmAutoRunnerSupport.ChunkImageRef> out = new ArrayList<>();
        for (ChunkImageRef ref : refs) {
            if (ref == null) continue;
            out.add(new ModerationLlmAutoRunnerSupport.ChunkImageRef(
                    ref.index,
                    ref.placeholder,
                    ref.url,
                    ref.mimeType,
                    ref.fileAssetId
            ));
        }
        return out;
    }

    private static List<ChunkImageRef> fromSupportRefs(List<ModerationLlmAutoRunnerSupport.ChunkImageRef> refs) {
        if (refs == null || refs.isEmpty()) return List.of();
        List<ChunkImageRef> out = new ArrayList<>();
        for (ModerationLlmAutoRunnerSupport.ChunkImageRef ref : refs) {
            if (ref == null) continue;
            out.add(new ChunkImageRef(ref.index, ref.placeholder, ref.url, ref.mimeType, ref.fileAssetId));
        }
        return out;
    }

    private void handleChunked(
            ModerationQueueEntity q,
            ModerationPipelineRunEntity run,
            ModerationConfidenceFallbackConfigEntity fb,
            Long llmStepId,
            Map<String, Object> prior,
            Long chunkSetId,
            Map<String, Object> policyConfig,
            String reviewStage
    ) {
        if (q.getStatus() == QueueStatus.PENDING) {
            q.setStatus(QueueStatus.REVIEWING);
            queueRepository.updateStageAndStatusIfPendingOrReviewing(q.getId(), QueueStage.LLM, QueueStatus.REVIEWING, LocalDateTime.now());
        } else if (q.getCurrentStage() != QueueStage.LLM) {
            q.setCurrentStage(QueueStage.LLM);
            queueRepository.updateStageIfPendingOrReviewing(q.getId(), QueueStage.LLM, LocalDateTime.now());
        }

        ModerationPipelineStepEntity step;
        try {
            step = pipelineTraceService.startStep(
                    run.getId(),
                    ModerationPipelineStepEntity.Stage.LLM,
                    7,
                    fb.getLlmRejectThreshold(),
                    Map.of("prior", prior, "chunked", true, "chunkSetId", chunkSetId)
            );
            llmStepId = step.getId();
        } catch (Exception e) {
            step = null;
        }

        Map<String, Object> fbThresholds = fb == null ? null : fb.getThresholds();
        boolean enableChunkImageStage = asBooleanOrDefault(fbThresholds == null ? null : fbThresholds.get("chunk.imageStage.enable"), false);
        boolean enableChunkGlobal = asBooleanOrDefault(fbThresholds == null ? null : fbThresholds.get("chunk.global.enable"), false);

        LlmModerationTestResponse imageRes = null;
        if (enableChunkImageStage) {
            try {
                boolean hasImageStage = false;
                if (step != null && step.getDetailsJson() != null) {
                    Object g = step.getDetailsJson().get("chunkedImageStage");
                    hasImageStage = g != null;
                }
                if (!hasImageStage) {
                    LlmModerationTestRequest imgReq = new LlmModerationTestRequest();
                    imgReq.setQueueId(q.getId());
                    imgReq.setReviewStage(reviewStage);
                    imgReq.setUseQueue(false);
                    imageRes = llmService.testImageOnly(imgReq);
                    if (imageRes != null && imageRes.getStages() != null && imageRes.getStages().getImage() != null) {
                        try {
                            chunkReviewService.updateImageStageMemory(
                                    chunkSetId,
                                    imageRes.getStages().getImage().getScore(),
                                    imageRes.getStages().getImage().getRiskTags(),
                                    imageRes.getStages().getImage().getDescription()
                            );
                        } catch (Exception ignore) {
                        }
                    }
                    Map<String, Object> g = new LinkedHashMap<>();
                    if (imageRes != null) {
                        g.put("model", imageRes.getModel());
                        g.put("decision", imageRes.getDecision());
                        g.put("score", imageRes.getScore());
                        g.put("reasons", imageRes.getReasons());
                        g.put("riskTags", imageRes.getRiskTags());
                        if (imageRes.getStages() != null && imageRes.getStages().getImage() != null) {
                            g.put("imageDescription", imageRes.getStages().getImage().getDescription());
                        }
                    }
                    double imageStrongRejectThreshold = clamp01Strict(fb.getLlmStrongRejectThreshold());
                    try {
                        Object v = fb.getThresholds() == null ? null : fb.getThresholds().get("chunk.withImages.imageStrongRejectThreshold");
                        if (v != null) imageStrongRejectThreshold = clamp01Strict(asDoubleRequired(v, "chunk.withImages.imageStrongRejectThreshold"));
                    } catch (Exception ignore) {
                    }
                    if (imageRes != null && imageRes.getScore() != null) {
                        double s = clamp01(imageRes.getScore(), 0.0);
                        if (s >= imageStrongRejectThreshold) {
                            if (llmStepId != null) {
                                pipelineTraceService.finishStepOk(llmStepId, "REJECT", s, Map.of("chunkedFinalFromImageStage", true, "imageStrongRejectThreshold", imageStrongRejectThreshold, "chunkedImageStage", g));
                            }
            queueService.autoReject(q.getId(), buildUserFacingRejectReason(imageRes, "Content violates policy"), run.getTraceId());
                            applyRiskTags(q, imageRes);
                            pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.REJECT);
                            writeChunkedDecisionAuditLog(q, run, llmStepId, chunkSetId, "REJECT", imageRes, Map.of("scope", "image", "chunkedFinalFromImageStage", true));
                            return;
                        }
                    }
                }
            } catch (Exception ignore) {
                imageRes = null;
            }
        }

        LlmModerationTestResponse globalRes = null;
        if (enableChunkGlobal) {
            try {
                boolean hasGlobal = false;
                if (step != null && step.getDetailsJson() != null) {
                    Object g = step.getDetailsJson().get("chunkedGlobal");
                    hasGlobal = g != null;
                }
                if (!hasGlobal) {
                    LlmModerationTestRequest globalReq = new LlmModerationTestRequest();
                    globalReq.setQueueId(q.getId());
                    globalReq.setReviewStage(reviewStage);
                    globalRes = llmService.test(globalReq);
                    Map<String, Object> g = new LinkedHashMap<>();
                    if (globalRes != null) {
                        g.put("model", globalRes.getModel());
                        g.put("decision", globalRes.getDecision());
                        g.put("score", globalRes.getScore());
                        g.put("reasons", globalRes.getReasons());
                        g.put("riskTags", globalRes.getRiskTags());
                        g.put("inputMode", globalRes.getInputMode());
                    }
                    ChunkedVerdict chunkedVerdict = verdictFromLlmInChunked(globalRes, fb, policyConfig, reviewStage);
                    Verdict v = chunkedVerdict.verdict;
                    Thresholds vth = chunkedVerdict.thresholds;
                    if (v == Verdict.REJECT) {
                        if (llmStepId != null) {
                            Map<String, Object> d = new LinkedHashMap<>();
                            d.put("chunkedFinalFromGlobal", true);
                            d.put("chunkedGlobal", g);
                            d.put("thresholdSource", vth.source);
                            d.put("thresholdsEffective", Map.of("T_allow", vth.tAllow, "T_reject", vth.tReject));
                            try {
                                AdminModerationChunkProgressDTO p = chunkReviewService.getProgress(q.getId(), false, 0);
                                if (p != null) {
                                    d.put("chunkProgressFinal", Map.of(
                                            "status", p.getStatus(),
                                            "total", p.getTotalChunks(),
                                            "completed", p.getCompletedChunks(),
                                            "failed", p.getFailedChunks(),
                                            "running", p.getRunningChunks()
                                    ));
                                }
                            } catch (Exception ignore) {
                            }
                            pipelineTraceService.finishStepOk(llmStepId, "REJECT", globalRes == null ? null : globalRes.getScore(), d);
                        }
            queueService.autoReject(q.getId(), buildUserFacingRejectReason(globalRes, "Content violates policy"), run.getTraceId());
                        applyRiskTags(q, globalRes);
                        pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.REJECT);
                        Map<String, Object> auditDetails = new LinkedHashMap<>();
                        auditDetails.put("scope", "global");
                        auditDetails.put("chunkedFinalFromGlobal", true);
                        auditDetails.put("thresholdSource", vth.source);
                        auditDetails.put("thresholdsEffective", Map.of("T_allow", vth.tAllow, "T_reject", vth.tReject));
                        writeChunkedDecisionAuditLog(q, run, llmStepId, chunkSetId, "REJECT", globalRes, auditDetails);
                        return;
                    }
                    if (v == Verdict.REVIEW) {
                        if (llmStepId != null) {
                            Map<String, Object> d = new LinkedHashMap<>();
                            d.put("chunkedFinalFromGlobal", true);
                            d.put("chunkedGlobal", g);
                            d.put("thresholdSource", vth.source);
                            d.put("thresholdsEffective", Map.of("T_allow", vth.tAllow, "T_reject", vth.tReject));
                            try {
                                AdminModerationChunkProgressDTO p = chunkReviewService.getProgress(q.getId(), false, 0);
                                if (p != null) {
                                    d.put("chunkProgressFinal", Map.of(
                                            "status", p.getStatus(),
                                            "total", p.getTotalChunks(),
                                            "completed", p.getCompletedChunks(),
                                            "failed", p.getFailedChunks(),
                                            "running", p.getRunningChunks()
                                    ));
                                }
                            } catch (Exception ignore) {
                            }
                            pipelineTraceService.finishStepOk(llmStepId, "HUMAN", globalRes == null ? null : globalRes.getScore(), d);
                        }
                        q.setCurrentStage(QueueStage.HUMAN);
                        q.setStatus(QueueStatus.HUMAN);
                        queueRepository.updateStageAndStatusIfPendingOrReviewing(q.getId(), QueueStage.HUMAN, QueueStatus.HUMAN, LocalDateTime.now());
                        applyRiskTags(q, globalRes);
                        pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.HUMAN);
                        Map<String, Object> auditDetails = new LinkedHashMap<>();
                        auditDetails.put("scope", "global");
                        auditDetails.put("chunkedFinalFromGlobal", true);
                        auditDetails.put("thresholdSource", vth.source);
                        auditDetails.put("thresholdsEffective", Map.of("T_allow", vth.tAllow, "T_reject", vth.tReject));
                        writeChunkedDecisionAuditLog(q, run, llmStepId, chunkSetId, "HUMAN", globalRes, auditDetails);
                        return;
                    }
                }
            } catch (Exception ignore) {
            }
        }

        long pendingOrFailed = 0L;
        try {
            pendingOrFailed = chunkReviewService.countPendingOrFailed(chunkSetId);
        } catch (Exception ignore) {
            pendingOrFailed = 0L;
        }
        if (pendingOrFailed > 0L) {
            var chunkCfg0 = chunkReviewService.getConfig();
            List<ModerationChunkReviewService.ChunkCandidate> candidates = List.of();
            try {
                candidates = chunkReviewService.listEligibleChunks(chunkSetId);
            } catch (Exception ignore) {
                candidates = List.of();
            }
            for (ModerationChunkReviewService.ChunkCandidate cand : candidates) {
                if (cand == null || cand.chunkId() == null) continue;
                ModerationChunkReviewService.ChunkCandidate cand0 = cand;
                long chunkId = cand.chunkId();
                int attempts = cand.attempts() == null ? 0 : Math.max(0, cand.attempts());
                int nextAttempt = attempts + 1;
                String dedupKey = "chunk:" + chunkId + ":" + nextAttempt + ":v3";
                String label = "queue#" + q.getId() + " chunk#" + chunkId + " a" + nextAttempt;

                llmCallQueueService.submitDedup(
                        com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType.MODERATION_CHUNK,
                        null,
                        null,
                        0,
                        label,
                        dedupKey,
                        (task) -> {
                            Map<String, Object> metaMap = new LinkedHashMap<>();
                            metaMap.put("kind", "MODERATION_CHUNK");
                            metaMap.put("queueId", q.getId());
                            metaMap.put("chunkSetId", chunkSetId);
                            metaMap.put("chunkId", chunkId);
                            metaMap.put("attempt", nextAttempt);
                            metaMap.put("debug_trace", "v3_fixed_" + System.currentTimeMillis());
                            metaMap.put("trace_id", UUID.randomUUID().toString());
                            metaMap.put("sourceType", String.valueOf(cand0.sourceType()));
                            if (cand0.fileAssetId() != null) metaMap.put("fileAssetId", cand0.fileAssetId());
                            if (cand0.chunkIndex() != null) metaMap.put("chunkIndex", cand0.chunkIndex());

                            String meta = "";
                            try {
                                meta = objectMapper.writeValueAsString(metaMap);
                                task.reportInput(meta);
                            } catch (Exception e) {
                                task.reportInput("{\"error\":\"Meta build failed: " + e.getMessage() + "\"}");
                            }

                            Optional<ModerationChunkReviewService.ChunkToProcess> claimed = Optional.empty();
                            try {
                                claimed = chunkReviewService.claimChunkById(chunkId);
                            } catch (Exception ignore) {
                                claimed = Optional.empty();
                            }
                            if (claimed.isEmpty()) return null;
                            ModerationChunkReviewService.ChunkToProcess c = claimed.get();

                            String raw = "";
                            try {
                                raw = chunkReviewService.loadChunkText(q.getId(), c.sourceType(), c.fileAssetId(), c.startOffset(), c.endOffset()).orElse("");
                            } catch (Exception ignore) {
                                raw = "";
                            }
                            Integer chunkIndex = c.chunkIndex() == null ? 0 : c.chunkIndex();
                            int prevSummaryMaxChars = 200;
                            try {
                                Object v = fb == null || fb.getThresholds() == null ? null : fb.getThresholds().get("chunk.prevSummary.maxChars");
                                prevSummaryMaxChars = (int) clampLong(asLongOrDefault(v, prevSummaryMaxChars), 0L, 2000L);
                            } catch (Exception ignore) {
                                prevSummaryMaxChars = 200;
                            }
                            String normalizedRaw = normalizeForPrompt(raw);
                            String summaryForNext = normalizedRaw;
                            if (summaryForNext.length() > prevSummaryMaxChars) summaryForNext = summaryForNext.substring(0, prevSummaryMaxChars);
                            String snippet = normalizedRaw;
                            if (snippet.length() > 240) snippet = snippet.substring(0, 240);
                            List<Map<String, Object>> entities = extractEntitiesFromText(normalizedRaw, chunkIndex, 20);
                            Map<String, Object> mem = Map.of();
                            try {
                                if (chunkCfg0 != null && Boolean.TRUE.equals(chunkCfg0.getEnableGlobalMemory())) {
                                    mem = chunkReviewService.getMemory(chunkSetId);
                                }
                            } catch (Exception ignore) {
                                mem = Map.of();
                            }

                            try {
                                LlmModerationTestRequest req = new LlmModerationTestRequest();
                                req.setReviewStage(reviewStage);
                                req.setUseQueue(false);

                                List<LlmModerationTestRequest.ImageInput> chunkImages = List.of();
                                List<ChunkImageRef> chunkImageRefs = List.of();
                                List<ChunkImageRef> candidateChunkImageRefs = List.of();
                                List<Integer> evidenceSourceChunks = List.of();
                                List<String> evidencePlaceholdersUsed = List.of();
                                try {
                                    if (c.sourceType() == ChunkSourceType.FILE_TEXT && c.fileAssetId() != null) {
                                        FileAssetExtractionsEntity ex = fileAssetExtractionsRepository.findById(c.fileAssetId()).orElse(null);
                                        String metaJson = ex == null ? null : ex.getExtractedMetadataJson();
                                        candidateChunkImageRefs = selectChunkImageRefs(objectMapper, raw, c.fileAssetId(), metaJson);
                                    } else if (c.sourceType() == ChunkSourceType.POST_TEXT
                                            && q != null
                                            && q.getContentType() == com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType.POST
                                            && q.getContentId() != null) {
                                        candidateChunkImageRefs = selectPostImageRefs(q.getContentId());
                                    }
                                } catch (Exception ignore) {
                                    candidateChunkImageRefs = List.of();
                                }

                                Set<Integer> currentChunkImageIndices = parseUsedImageIndices(raw);
                                List<ChunkImageRef> currentChunkImageRefs = filterChunkImageRefsByIndices(candidateChunkImageRefs, currentChunkImageIndices);

                                boolean sendImagesOnlyWhenInEvidence = chunkCfg0 == null || !Boolean.FALSE.equals(chunkCfg0.getSendImagesOnlyWhenInEvidence());
                                if (sendImagesOnlyWhenInEvidence) {
                                    EvidenceImageSelection selection = selectEvidenceDrivenChunkImages(mem, c.chunkIndex(), candidateChunkImageRefs);
                                    chunkImageRefs = selection.selectedRefs;
                                    evidenceSourceChunks = selection.sourceChunkIndexes;
                                    evidencePlaceholdersUsed = selection.placeholders;
                                } else {
                                    chunkImageRefs = candidateChunkImageRefs;
                                }
                                List<ChunkImageRef> actualChunkImageRefs = sendImagesOnlyWhenInEvidence
                                        ? mergeChunkImageRefs(chunkImageRefs, currentChunkImageRefs)
                                        : chunkImageRefs;
                                chunkImages = refsToImageInputs(actualChunkImageRefs, c.fileAssetId());

                                // promptImageRefs: images listed as text URLs in the prompt (low token cost).
                                // When sendImagesOnlyWhenInEvidence, also include current-chunk images
                                // in the prompt text so LLM can see the placeholders, without sending
                                // them as expensive base64 vision inputs via chunkImageRefs.
                                boolean includeImagesBlockOnlyForEvidenceMatches = chunkCfg0 == null
                                        || !Boolean.FALSE.equals(chunkCfg0.getIncludeImagesBlockOnlyForEvidenceMatches());
                                List<ChunkImageRef> promptImageRefs;
                                if (!includeImagesBlockOnlyForEvidenceMatches) {
                                    promptImageRefs = candidateChunkImageRefs;
                                } else if (sendImagesOnlyWhenInEvidence) {
                                    // Keep evidence-driven prompt refs, but also list images explicitly referenced in the current chunk.
                                    promptImageRefs = mergeChunkImageRefs(chunkImageRefs, currentChunkImageRefs);
                                } else {
                                    promptImageRefs = chunkImageRefs;
                                }

                                if (chunkImageRefs == null) chunkImageRefs = List.of();
                                if (chunkImages == null) chunkImages = List.of();
                                if (promptImageRefs == null) promptImageRefs = List.of();
                                if (candidateChunkImageRefs == null) candidateChunkImageRefs = List.of();
                                if (evidenceSourceChunks == null) evidenceSourceChunks = List.of();
                                if (evidencePlaceholdersUsed == null) evidencePlaceholdersUsed = List.of();

                                try {
                                    if (!sendImagesOnlyWhenInEvidence) {
                                        evidencePlaceholdersUsed = extractPlaceholdersFromRefs(chunkImageRefs);
                                    }
                                } catch (Exception ignore) {
                                    chunkImageRefs = List.of();
                                }
                                String promptText = buildChunkPromptText(q, c, raw, chunkCfg0, mem, promptImageRefs);
                                req.setText(promptText);
                                if (chunkImages != null && !chunkImages.isEmpty()) {
                                    req.setImages(chunkImages);
                                }

                                // Merge meta and request for detailed input
                                try {
                                    Map<String, Object> combinedInput = new LinkedHashMap<>();
                                    combinedInput.put("meta", metaMap);
                                    combinedInput.put("request", req);
                                    if (req != null && req.getText() != null) {
                                        combinedInput.put("prompt", req.getText());
                                    }
                                    combinedInput.put("trace_id", metaMap.get("trace_id"));
                                    task.reportInput(objectMapper.writeValueAsString(combinedInput));
                                } catch (Exception e) {
                                    task.reportInput("{\"error\":\"Input merge failed: " + e.getMessage() + "\", \"meta\": " + meta + "}");
                                }

                                LlmModerationTestResponse res = llmService.test(req);

                                if (res != null && res.getModel() != null && !res.getModel().isBlank()) {
                                    task.reportModel(res.getModel());
                                }
                                Verdict v = verdictFromLlm(res, fb);
                                Map<String, Object> labels = new LinkedHashMap<>();
                                if (res != null) {
                                    if (res.getDecision() != null) labels.put("decision", res.getDecision());
                                    if (res.getScore() != null) labels.put("score", res.getScore());
                                    if (res.getReasons() != null) labels.put("reasons", res.getReasons());
                                    if (res.getRiskTags() != null) labels.put("riskTags", res.getRiskTags());
                                    if (res.getLabels() != null) labels.put("labels", res.getLabels());
                                    if (res.getSeverity() != null) labels.put("severity", res.getSeverity());
                                    if (res.getUncertainty() != null) labels.put("uncertainty", res.getUncertainty());
                                    PolicyEval policyEval = evaluatePolicyVerdict(policyConfig, reviewStage, res, resolveRiskTagThresholdsCached(), false, false);
                                    if (policyEval != null && policyEval.verdict != null) {
                                        labels.put("policyVerdict", policyEval.verdict.name());
                                    }
                                    Verdict policyVerdict = policyEval == null ? null : policyEval.verdict;
                                        boolean keepEvidence = policyVerdict == Verdict.REJECT || policyVerdict == Verdict.REVIEW;
                                        List<String> evidenceBeforeNormalize = filterChunkEvidence(res == null ? null : res.getEvidence());
                                        List<String> evidence = keepEvidence
                                                ? normalizeChunkEvidenceForLabels(objectMapper, res == null ? null : res.getEvidence(), raw, actualChunkImageRefs)
                                            : List.of();
                                    if (evidence != null && !evidence.isEmpty()) {
                                        int take = Math.min(10, evidence.size());
                                        labels.put("evidence", evidence.subList(0, take));
                                    }
                                    if (keepEvidence) {
                                        List<String> replay = buildEvidenceNormalizeReplay(evidenceBeforeNormalize, evidence, 3);
                                        if (!replay.isEmpty()) {
                                            labels.put("evidenceNormalizeReplay", replay);
                                            log.info("chunk evidence normalized: queueId={}, chunkId={}, replay={}",
                                                    q == null ? null : q.getId(),
                                                    c == null ? null : c.chunkId(),
                                                    replay);
                                        }
                                    }
                                    if (res.getInputMode() != null) labels.put("inputMode", res.getInputMode());
                                    if (res.getStages() != null) {
                                        labels.put("hasImageStage", res.getStages().getImage() != null);
                                        labels.put("hasJudgeStage", res.getStages().getJudge() != null);
                                        labels.put("hasUpgradeStage", res.getStages().getUpgrade() != null);
                                    }
                                }
                                if (chunkImages != null && !chunkImages.isEmpty()) labels.put("imagesSent", chunkImages.size());
                                if (candidateChunkImageRefs != null && !candidateChunkImageRefs.isEmpty()) {
                                    labels.put("imagesCandidate", candidateChunkImageRefs.size());
                                }
                                if (evidenceSourceChunks != null && !evidenceSourceChunks.isEmpty()) {
                                    labels.put("evidenceSourceChunks", evidenceSourceChunks);
                                }
                                if (evidencePlaceholdersUsed != null && !evidencePlaceholdersUsed.isEmpty()) {
                                    labels.put("evidencePlaceholdersUsed", evidencePlaceholdersUsed);
                                }
                                labels.put("tokenDiagnostics", buildTokenDiagnostics(
                                        promptText,
                                        chunkImages,
                                        res,
                                        null,
                                        candidateChunkImageRefs == null ? 0 : candidateChunkImageRefs.size(),
                                        evidenceSourceChunks,
                                        evidencePlaceholdersUsed
                                ));
                                labels.put("chunkIndex", chunkIndex);
                                labels.put("summaryForNext", summaryForNext);
                                labels.put("chunkTextSnippet", snippet);
                                if (entities != null && !entities.isEmpty()) labels.put("entities", entities);
                                Integer tokensIn = res == null || res.getUsage() == null ? null : res.getUsage().getPromptTokens();
                                Integer tokensOut = res == null || res.getUsage() == null ? null : res.getUsage().getCompletionTokens();
                                chunkReviewService.markChunkSuccess(c.chunkId(), res == null ? null : res.getModel(), v, res == null ? null : res.getScore(), labels, tokensIn, tokensOut, false);

                                try {
                                    Map<String, Object> simpleOutput = new LinkedHashMap<>();
                                    simpleOutput.put("decision", String.valueOf(v));
                                    simpleOutput.put("model", String.valueOf(res == null ? null : res.getModel()));

                                    Map<String, Object> combinedOutput = new LinkedHashMap<>();
                                    combinedOutput.put("summary", simpleOutput);
                                    combinedOutput.put("full_response", sanitizeModerationResponseForQueueOutput(objectMapper, res));
                                    combinedOutput.put("debug_trace", "v3_fixed_" + System.currentTimeMillis());
                                    if (res != null) {
                                        combinedOutput.put("raw_output", res.getRawModelOutput());
                                        combinedOutput.put("decision", res.getDecision());
                                    }
                                    task.reportOutput(objectMapper.writeValueAsString(combinedOutput));
                                } catch (Exception e) {
                                    task.reportOutput("{\"error\":\"Output merge failed: " + e.getMessage() + "\"}");
                                }

                                return res;
                            } catch (Exception ex) {
                                chunkReviewService.markChunkFailed(c.chunkId(), ex.getMessage(), false);
                                task.reportOutput("{\"error\":\"" + String.valueOf(ex.getMessage()) + "\"}");
                                throw ex;
                            } finally {
                                try {
                                    chunkReviewService.refreshSetCountersDebounced(chunkSetId, 1000);
                                } catch (Exception ignore) {
                                }
                            }
                        },
                        (res) -> {
                            if (res == null || res.getUsage() == null) return null;
                            Integer p = res.getUsage().getPromptTokens();
                            Integer c = res.getUsage().getCompletionTokens();
                            Integer t = res.getUsage().getTotalTokens();
                            return new LlmCallQueueService.UsageMetrics(p, c, t, null);
                        }
                );
            }
            return;
        }

        AdminModerationChunkProgressDTO progress;
        try {
            progress = chunkReviewService.getProgress(q.getId(), true, 300);
        } catch (Exception e) {
            progress = null;
        }
        if (progress == null || progress.getTotalChunks() == null || progress.getTotalChunks() <= 0) return;
        int total = progress.getTotalChunks() == null ? 0 : progress.getTotalChunks();
        int completed = progress.getCompletedChunks() == null ? 0 : progress.getCompletedChunks();
        int failed = progress.getFailedChunks() == null ? 0 : progress.getFailedChunks();
        int running = progress.getRunningChunks() == null ? 0 : progress.getRunningChunks();
        if (completed + failed < total || running > 0) return;
        try {
            chunkReviewService.refreshSetCountersNow(chunkSetId);
        } catch (Exception ignore) {
        }
        Map<String, Object> chunkProgressFinal = Map.of(
                "status", progress.getStatus(),
                "total", progress.getTotalChunks(),
                "completed", progress.getCompletedChunks(),
                "failed", progress.getFailedChunks(),
                "running", progress.getRunningChunks()
        );
        if (failed > 0) {
            if (llmStepId != null) {
                pipelineTraceService.finishStepOk(llmStepId, "HUMAN", null, Map.of("chunkedFinal", "HUMAN", "reason", "chunk_failed", "chunkProgressFinal", chunkProgressFinal));
            }
            q.setCurrentStage(QueueStage.HUMAN);
            q.setStatus(QueueStatus.HUMAN);
            queueRepository.updateStageAndStatusIfPendingOrReviewing(q.getId(), QueueStage.HUMAN, QueueStatus.HUMAN, LocalDateTime.now());
            applyChunkedRiskTags(q, chunkSetId, globalRes);
            pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.HUMAN);
            writeChunkedDecisionAuditLog(q, run, llmStepId, chunkSetId, "HUMAN", globalRes, Map.of("scope", "chunks", "reason", "chunk_failed", "failedChunks", failed, "chunkProgressFinal", chunkProgressFinal));
            return;
        }

        Verdict finalReviewVerdict = null;
        LlmModerationTestResponse finalReviewRes = null;
        try {
            Map<String, Object> thresholds = fb == null ? null : fb.getThresholds();
            boolean enableFinalReview = asBooleanRequired(thresholds == null ? null : thresholds.get("chunk.finalReview.enable"), "chunk.finalReview.enable");
            double triggerScoreMin = clamp01Strict(asDoubleRequired(thresholds == null ? null : thresholds.get("chunk.finalReview.triggerScoreMin"), "chunk.finalReview.triggerScoreMin"));
            long triggerRiskTagCount = clampLong(asLongRequired(thresholds == null ? null : thresholds.get("chunk.finalReview.triggerRiskTagCount"), "chunk.finalReview.triggerRiskTagCount"), 0L, 1000L);
            boolean triggerOpenQuestions = asBooleanRequired(thresholds == null ? null : thresholds.get("chunk.finalReview.triggerOpenQuestions"), "chunk.finalReview.triggerOpenQuestions");

            Map<String, Object> mem = Map.of();
            try {
                mem = chunkReviewService.getMemory(chunkSetId);
            } catch (Exception ignore) {
                mem = Map.of();
            }
            double maxScore = asDoubleOrDefault(mem.get("maxScore"), 0.0);
            int riskTagCount = mem.get("riskTags") instanceof Collection<?> col ? col.size() : 0;
            boolean hasOpenQuestions = mem.get("openQuestions") instanceof Collection<?> col && !col.isEmpty();
            boolean shouldFinalReview = enableFinalReview && (maxScore >= triggerScoreMin || riskTagCount >= (int) triggerRiskTagCount || (triggerOpenQuestions && hasOpenQuestions));
            if (shouldFinalReview) {
                String input = buildFinalReviewInput(mem);
                LlmModerationTestRequest req = new LlmModerationTestRequest();
                req.setReviewStage(reviewStage);
                req.setText(input);
                req.setUseQueue(false);
                LlmModerationTestRequest.LlmModerationConfigOverrideDTO ov = new LlmModerationTestRequest.LlmModerationConfigOverrideDTO();
                ov.setPromptTemplate(judgePromptTemplate(null));
                req.setConfigOverride(ov);
                finalReviewRes = llmService.test(req);

                Thresholds finalTh = resolveThresholdsPreferred(policyConfig, reviewStage, coalesceLabels(finalReviewRes), fb);
                double rejectT = finalTh.tReject;
                double humanT = finalTh.tAllow;
                String d = normalizeDecisionOrNull(finalReviewRes == null ? null : finalReviewRes.getDecision());
                Double sc = finalReviewRes == null ? null : finalReviewRes.getScore();
                finalReviewVerdict = verdictFromDecisionAndScore(d, sc, rejectT, humanT);
            }
        } catch (Exception ignore) {
            finalReviewVerdict = null;
            finalReviewRes = null;
        }

        if (finalReviewVerdict != null) {
            List<String> finalReviewEvidence = filterChunkEvidence(finalReviewRes == null ? null : finalReviewRes.getEvidence());
            if (finalReviewVerdict == Verdict.APPROVE) {
                if (llmStepId != null) {
                    Thresholds finalTh = resolveThresholdsPreferred(policyConfig, reviewStage, coalesceLabels(finalReviewRes), fb);
                    Map<String, Object> stepDetails = new LinkedHashMap<>();
                    stepDetails.put("chunkedFinal", "APPROVE");
                    stepDetails.put("scope", "finalReview");
                    stepDetails.put("chunkProgressFinal", chunkProgressFinal);
                    stepDetails.put("thresholdSource", finalTh.source);
                    stepDetails.put("thresholdsEffective", Map.of("T_allow", finalTh.tAllow, "T_reject", finalTh.tReject));
                    if (!finalReviewEvidence.isEmpty()) stepDetails.put("evidence", finalReviewEvidence);
                    pipelineTraceService.finishStepOk(llmStepId, "APPROVE", finalReviewRes == null ? null : finalReviewRes.getScore(), stepDetails);
                }
                queueService.autoApprove(q.getId(), "", run.getTraceId());
                applyChunkedRiskTags(q, chunkSetId, finalReviewRes);
                pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.APPROVE);
                writeChunkedDecisionAuditLog(q, run, llmStepId, chunkSetId, "APPROVE", globalRes, buildFinalReviewAuditDetails(chunkProgressFinal, finalReviewRes));
                return;
            }
            if (finalReviewVerdict == Verdict.REJECT) {
                if (llmStepId != null) {
                    Thresholds finalTh = resolveThresholdsPreferred(policyConfig, reviewStage, coalesceLabels(finalReviewRes), fb);
                    Map<String, Object> stepDetails = new LinkedHashMap<>();
                    stepDetails.put("chunkedFinal", "REJECT");
                    stepDetails.put("scope", "finalReview");
                    stepDetails.put("chunkProgressFinal", chunkProgressFinal);
                    stepDetails.put("thresholdSource", finalTh.source);
                    stepDetails.put("thresholdsEffective", Map.of("T_allow", finalTh.tAllow, "T_reject", finalTh.tReject));
                    if (!finalReviewEvidence.isEmpty()) stepDetails.put("evidence", finalReviewEvidence);
                    pipelineTraceService.finishStepOk(llmStepId, "REJECT", finalReviewRes == null ? null : finalReviewRes.getScore(), stepDetails);
                }
            queueService.autoReject(q.getId(), buildUserFacingRejectReason(finalReviewRes, "Content violates policy"), run.getTraceId());
                applyChunkedRiskTags(q, chunkSetId, finalReviewRes);
                pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.REJECT);
                writeChunkedDecisionAuditLog(q, run, llmStepId, chunkSetId, "REJECT", globalRes, buildFinalReviewAuditDetails(chunkProgressFinal, finalReviewRes));
                return;
            }
            if (llmStepId != null) {
                Thresholds finalTh = resolveThresholdsPreferred(policyConfig, reviewStage, coalesceLabels(finalReviewRes), fb);
                Map<String, Object> stepDetails = new LinkedHashMap<>();
                stepDetails.put("chunkedFinal", "HUMAN");
                stepDetails.put("scope", "finalReview");
                stepDetails.put("chunkProgressFinal", chunkProgressFinal);
                stepDetails.put("thresholdSource", finalTh.source);
                stepDetails.put("thresholdsEffective", Map.of("T_allow", finalTh.tAllow, "T_reject", finalTh.tReject));
                if (!finalReviewEvidence.isEmpty()) stepDetails.put("evidence", finalReviewEvidence);
                pipelineTraceService.finishStepOk(llmStepId, "HUMAN", finalReviewRes == null ? null : finalReviewRes.getScore(), stepDetails);
            }
            q.setCurrentStage(QueueStage.HUMAN);
            q.setStatus(QueueStatus.HUMAN);
            queueRepository.updateStageAndStatusIfPendingOrReviewing(q.getId(), QueueStage.HUMAN, QueueStatus.HUMAN, LocalDateTime.now());
            applyChunkedRiskTags(q, chunkSetId, finalReviewRes);
            pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.HUMAN);
            writeChunkedDecisionAuditLog(q, run, llmStepId, chunkSetId, "HUMAN", globalRes, buildFinalReviewAuditDetails(chunkProgressFinal, finalReviewRes));
            return;
        }

        Map<String, Object> aggregateMem = Map.of();
        try {
            aggregateMem = chunkReviewService.getMemory(chunkSetId);
        } catch (Exception ignore) {
            aggregateMem = Map.of();
        }
        List<String> aggregateLabels = asStringList(aggregateMem.get("riskTags"));
        List<String> aggregateEvidence = collectChunkEvidenceForStepDetail(aggregateMem, 30);
        Thresholds aggregateThresholds = resolveThresholdsPreferred(policyConfig, reviewStage, aggregateLabels, fb);

        Verdict finalVerdict = aggregateChunkVerdict(progress, fb, policyConfig, reviewStage, aggregateLabels);
        Map<String, Object> memoryGuardDetails = Map.of();
        if (finalVerdict == Verdict.APPROVE) {
            Verdict guarded = guardChunkedAggregateByMemory(finalVerdict, aggregateMem, fb, policyConfig, reviewStage, aggregateLabels);
            if (guarded != finalVerdict) {
                finalVerdict = guarded;
                memoryGuardDetails = Map.of(
                        "memoryGuard", true,
                        "memoryMaxScore", clamp01(asDoubleOrDefault(aggregateMem.get("maxScore"), 0.0), 0.0)
                );
            }
        }
        if (finalVerdict == Verdict.APPROVE) {
            if (llmStepId != null) {
                Map<String, Object> stepDetails = new LinkedHashMap<>();
                stepDetails.put("chunkedFinal", "APPROVE");
                stepDetails.put("chunkProgressFinal", chunkProgressFinal);
                stepDetails.put("thresholdSource", aggregateThresholds.source);
                stepDetails.put("thresholdsEffective", Map.of("T_allow", aggregateThresholds.tAllow, "T_reject", aggregateThresholds.tReject));
                if (!aggregateEvidence.isEmpty()) stepDetails.put("evidence", aggregateEvidence);
                pipelineTraceService.finishStepOk(llmStepId, "APPROVE", null, stepDetails);
            }
            queueService.autoApprove(q.getId(), "", run.getTraceId());
            applyChunkedRiskTags(q, chunkSetId, globalRes);
            pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.APPROVE);
            writeChunkedDecisionAuditLog(q, run, llmStepId, chunkSetId, "APPROVE", globalRes, Map.of("scope", "chunks", "chunkProgressFinal", chunkProgressFinal, "thresholdSource", aggregateThresholds.source, "thresholdsEffective", Map.of("T_allow", aggregateThresholds.tAllow, "T_reject", aggregateThresholds.tReject)));
            return;
        }
        if (finalVerdict == Verdict.REJECT) {
            if (llmStepId != null) {
                Map<String, Object> stepDetails = new LinkedHashMap<>();
                stepDetails.put("chunkedFinal", "REJECT");
                stepDetails.put("chunkProgressFinal", chunkProgressFinal);
                stepDetails.put("thresholdSource", aggregateThresholds.source);
                stepDetails.put("thresholdsEffective", Map.of("T_allow", aggregateThresholds.tAllow, "T_reject", aggregateThresholds.tReject));
                if (!aggregateEvidence.isEmpty()) stepDetails.put("evidence", aggregateEvidence);
                pipelineTraceService.finishStepOk(llmStepId, "REJECT", null, stepDetails);
            }
            queueService.autoReject(q.getId(), buildUserFacingRejectReason(globalRes, "Content violates policy"), run.getTraceId());
            applyChunkedRiskTags(q, chunkSetId, globalRes);
            pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.REJECT);
            writeChunkedDecisionAuditLog(q, run, llmStepId, chunkSetId, "REJECT", globalRes, Map.of("scope", "chunks", "chunkProgressFinal", chunkProgressFinal, "thresholdSource", aggregateThresholds.source, "thresholdsEffective", Map.of("T_allow", aggregateThresholds.tAllow, "T_reject", aggregateThresholds.tReject)));
            return;
        }

        if (llmStepId != null) {
            Map<String, Object> stepDetails = new LinkedHashMap<>();
            stepDetails.put("chunkedFinal", "HUMAN");
            stepDetails.put("chunkProgressFinal", chunkProgressFinal);
            stepDetails.put("thresholdSource", aggregateThresholds.source);
            stepDetails.put("thresholdsEffective", Map.of("T_allow", aggregateThresholds.tAllow, "T_reject", aggregateThresholds.tReject));
            if (!aggregateEvidence.isEmpty()) stepDetails.put("evidence", aggregateEvidence);
            pipelineTraceService.finishStepOk(llmStepId, "HUMAN", null, stepDetails);
        }
        q.setCurrentStage(QueueStage.HUMAN);
        q.setStatus(QueueStatus.HUMAN);
        queueRepository.updateStageAndStatusIfPendingOrReviewing(q.getId(), QueueStage.HUMAN, QueueStatus.HUMAN, LocalDateTime.now());
        applyChunkedRiskTags(q, chunkSetId, globalRes);
        pipelineTraceService.finishRunSuccess(run.getId(), ModerationPipelineRunEntity.FinalDecision.HUMAN);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("scope", "chunks");
        details.put("chunkProgressFinal", chunkProgressFinal);
        details.put("thresholdSource", aggregateThresholds.source);
        details.put("thresholdsEffective", Map.of("T_allow", aggregateThresholds.tAllow, "T_reject", aggregateThresholds.tReject));
        if (memoryGuardDetails != null && !memoryGuardDetails.isEmpty()) details.putAll(memoryGuardDetails);
        writeChunkedDecisionAuditLog(q, run, llmStepId, chunkSetId, "HUMAN", globalRes, details);
    }

    private String ensureAnchorEvidenceContainsText(String evidenceItem, String chunkText) {
        return ModerationLlmAutoRunnerSupport.ensureAnchorEvidenceContainsText(objectMapper, evidenceItem, chunkText);
    }

    private String evidenceFingerprint(String item) {
        return ModerationLlmAutoRunnerSupport.evidenceFingerprint(objectMapper, item);
    }

    private List<String> normalizeChunkEvidenceForLabels(List<String> evidence,
                                                         String chunkText,
                                                         List<ChunkImageRef> actualChunkImageRefs) {
        return normalizeChunkEvidenceForLabels(objectMapper, evidence, chunkText, actualChunkImageRefs);
    }

    // Backward-compatible static helpers for existing tests/callers.
    public static final class ChunkImageRef {
        final Integer index;
        final String placeholder;
        final String url;
        final String mimeType;
        final Long fileAssetId;

        public ChunkImageRef(Integer index, String placeholder, String url, String mimeType, Long fileAssetId) {
            this.index = index;
            this.placeholder = placeholder;
            this.url = url;
            this.mimeType = mimeType;
            this.fileAssetId = fileAssetId;
        }
    }

    static final class EvidenceImageSelection {
        final List<ChunkImageRef> selectedRefs;
        final List<Integer> sourceChunkIndexes;
        final List<String> placeholders;

        EvidenceImageSelection(List<ChunkImageRef> selectedRefs, List<Integer> sourceChunkIndexes, List<String> placeholders) {
            this.selectedRefs = selectedRefs == null ? List.of() : selectedRefs;
            this.sourceChunkIndexes = sourceChunkIndexes == null ? List.of() : sourceChunkIndexes;
            this.placeholders = placeholders == null ? List.of() : placeholders;
        }
    }

}




