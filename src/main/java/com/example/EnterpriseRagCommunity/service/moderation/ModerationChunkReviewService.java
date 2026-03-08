package com.example.EnterpriseRagCommunity.service.moderation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkProgressDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationChunkReviewConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationChunkSetEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationConfidenceFallbackConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationLlmConfigEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSetStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationChunkSetRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationConfidenceFallbackConfigRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationLlmConfigRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModerationChunkReviewService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_EVIDENCE_ITEM_CHARS = 400;
    private final ModerationChunkReviewConfigService configService;
    private final ModerationConfidenceFallbackConfigRepository fallbackConfigRepository;
    private final ModerationLlmConfigRepository llmConfigRepository;
    private final PromptsRepository promptsRepository;
    private final ModerationChunkSetRepository chunkSetRepository;
    private final ModerationChunkRepository chunkRepository;
    private final PostsRepository postsRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final FileAssetExtractionsRepository fileAssetExtractionsRepository;
    private final PlatformTransactionManager transactionManager;

    private final ConcurrentHashMap<Long, AtomicLong> lastSetRefreshMs = new ConcurrentHashMap<>();
    private volatile TransactionTemplate cachedRequiresNewTx;

    @Transactional(readOnly = true)
    public Optional<ModerationChunkSetEntity> findSetByQueueId(Long queueId) {
        if (queueId == null) return Optional.empty();
        return chunkSetRepository.findByQueueId(queueId);
    }

    @Transactional(readOnly = true)
    public int chunksPerRun() {
        ModerationChunkReviewConfigDTO cfg = configService.getConfig();
        Integer v = cfg == null ? null : cfg.getChunksPerRun();
        int x = v == null ? 3 : v;
        if (x < 1) x = 1;
        if (x > 50) x = 50;
        return x;
    }

    @Transactional(readOnly = true)
    public ModerationChunkReviewConfigDTO getConfig() {
        return configService.getConfig();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMemory(Long chunkSetId) {
        if (chunkSetId == null) return Map.of();
        ModerationChunkSetEntity set = chunkSetRepository.findById(chunkSetId).orElse(null);
        if (set == null || set.getMemoryJson() == null) return Map.of();
        return set.getMemoryJson();
    }

    public void updateImageStageMemory(Long chunkSetId, Double imageScore, List<String> imageRiskTags, String imageDescription) {
        if (chunkSetId == null) return;
        for (int i = 0; i < 3; i++) {
            try {
                Boolean done = requiresNewTx().execute((status) -> {
                    ModerationChunkSetEntity set = chunkSetRepository.findById(chunkSetId).orElse(null);
                    if (set == null || set.getStatus() == ChunkSetStatus.CANCELLED) return Boolean.TRUE;
                    Map<String, Object> mem = set.getMemoryJson() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(set.getMemoryJson());
                    if (imageScore != null) mem.put("imageScore", clamp01(imageScore));
                    if (imageRiskTags != null && !imageRiskTags.isEmpty()) {
                        mem.put("imageRiskTags", imageRiskTags);
                        LinkedHashSet<String> risk = new LinkedHashSet<>();
                        Object r0 = mem.get("riskTags");
                        if (r0 instanceof Collection<?> col) {
                            for (Object o : col) {
                                if (o == null) continue;
                                String s = String.valueOf(o).trim();
                                if (!s.isEmpty()) risk.add(s);
                            }
                        }
                        for (String tag : imageRiskTags) {
                            if (tag == null) continue;
                            String s = tag.trim();
                            if (!s.isEmpty()) risk.add(s);
                        }
                        if (!risk.isEmpty()) mem.put("riskTags", new ArrayList<>(risk));
                    }
                    if (imageDescription != null && !imageDescription.isBlank()) {
                        String t = imageDescription.trim();
                        if (t.length() > 2000) t = t.substring(0, 2000);
                        mem.put("imageDescription", t);
                    }
                    mem.put("updatedAt", LocalDateTime.now().toString());
                    set.setMemoryJson(mem);
                    set.setUpdatedAt(LocalDateTime.now());
                    chunkSetRepository.saveAndFlush(set);
                    return Boolean.TRUE;
                });
                if (Boolean.TRUE.equals(done)) return;
                return;
            } catch (OptimisticLockingFailureException e) {
                sleepQuietly(10L + (long) (Math.random() * 40));
            } catch (Exception e) {
                return;
            }
        }
    }

    @Transactional
    public ModerationChunkSetEntity ensureChunkSetForQueue(ModerationQueueEntity q) {
        if (q == null || q.getId() == null) throw new IllegalArgumentException("queue is required");
        return chunkSetRepository.findByQueueId(q.getId()).orElseGet(() -> {
            ModerationChunkSetEntity created = null;
            try {
                created = requiresNewTx().execute((status) -> {
                    try {
                        return createChunkSet(q);
                    } catch (DataIntegrityViolationException e) {
                        return chunkSetRepository.findByQueueId(q.getId()).orElse(null);
                    }
                });
            } catch (DataIntegrityViolationException e) {
                created = chunkSetRepository.findByQueueId(q.getId()).orElse(null);
            }
            if (created != null) return created;
            return chunkSetRepository.findByQueueId(q.getId()).orElseThrow(() -> new IllegalStateException("chunkSet create failed"));
        });
    }

    @Transactional
    public void cancelByQueueId(Long queueId) {
        if (queueId == null) return;
        ModerationChunkSetEntity set = chunkSetRepository.findByQueueId(queueId).orElse(null);
        if (set == null) return;
        if (set.getStatus() == ChunkSetStatus.CANCELLED || set.getStatus() == ChunkSetStatus.DONE) return;
        LocalDateTime now = LocalDateTime.now();
        set.setStatus(ChunkSetStatus.CANCELLED);
        set.setCancelledAt(now);
        set.setUpdatedAt(now);
        chunkSetRepository.save(set);
        List<ModerationChunkEntity> chunks = chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(set.getId());
        for (ModerationChunkEntity c : chunks) {
            if (c == null) continue;
            if (c.getStatus() == ChunkStatus.SUCCESS) continue;
            c.setStatus(ChunkStatus.CANCELLED);
            c.setUpdatedAt(now);
        }
        if (!chunks.isEmpty()) chunkRepository.saveAll(chunks);
    }

    @Transactional(readOnly = true)
    public Map<Long, ProgressSummary> loadProgressSummaries(Collection<Long> queueIds) {
        if (queueIds == null || queueIds.isEmpty()) return Map.of();
        List<ModerationChunkSetEntity> sets = chunkSetRepository.findAllByQueueIds(queueIds);
        if (sets == null || sets.isEmpty()) return Map.of();
        Map<Long, ProgressSummary> map = new HashMap<>();
        for (ModerationChunkSetEntity s : sets) {
            if (s == null || s.getQueueId() == null) continue;
            ProgressSummary ps = new ProgressSummary();
            ps.queueId = s.getQueueId();
            ps.status = s.getStatus() == null ? null : s.getStatus().name();
            ps.total = safeInt(s.getTotalChunks());
            ps.completed = safeInt(s.getCompletedChunks());
            ps.failed = safeInt(s.getFailedChunks());
            ps.updatedAt = s.getUpdatedAt();
            map.put(s.getQueueId(), ps);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public AdminModerationChunkProgressDTO getProgress(Long queueId, boolean includeChunks, int chunkLimit) {
        if (queueId == null) throw new IllegalArgumentException("queueId 不能为空");
        ModerationChunkSetEntity set = chunkSetRepository.findByQueueId(queueId).orElse(null);
        if (set == null) {
            AdminModerationChunkProgressDTO dto = new AdminModerationChunkProgressDTO();
            dto.setQueueId(queueId);
            dto.setStatus("NONE");
            dto.setTotalChunks(0);
            dto.setCompletedChunks(0);
            dto.setFailedChunks(0);
            dto.setRunningChunks(0);
            dto.setUpdatedAt(null);
            dto.setChunks(List.of());
            return dto;
        }

        AdminModerationChunkProgressDTO dto = new AdminModerationChunkProgressDTO();
        dto.setQueueId(queueId);
        int total = safeInt(set.getTotalChunks());
        long done = chunkRepository.countByChunkSetIdAndStatusIn(set.getId(), List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED));
        long failed = chunkRepository.countByChunkSetIdAndStatusIn(set.getId(), List.of(ChunkStatus.FAILED));
        long running = chunkRepository.countByChunkSetIdAndStatusIn(set.getId(), List.of(ChunkStatus.RUNNING));

        dto.setTotalChunks(total);
        dto.setCompletedChunks((int) Math.min(Integer.MAX_VALUE, done));
        dto.setFailedChunks((int) Math.min(Integer.MAX_VALUE, failed));
        dto.setUpdatedAt(set.getUpdatedAt());

        dto.setRunningChunks((int) Math.min(Integer.MAX_VALUE, running));
        if (set.getStatus() == ChunkSetStatus.CANCELLED) {
            dto.setStatus(ChunkSetStatus.CANCELLED.name());
        } else if (total > 0 && (done + failed) >= (long) total) {
            dto.setStatus(ChunkSetStatus.DONE.name());
        } else {
            dto.setStatus(ChunkSetStatus.RUNNING.name());
        }

        if (!includeChunks) {
            dto.setChunks(List.of());
            return dto;
        }

        List<ModerationChunkEntity> chunks = chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(set.getId());
        int take = Math.max(0, Math.min(chunkLimit, chunks == null ? 0 : chunks.size()));
        List<AdminModerationChunkProgressDTO.ChunkItem> items = new ArrayList<>();
        for (int i = 0; i < take; i++) {
            ModerationChunkEntity c = chunks.get(i);
            if (c == null) continue;
            AdminModerationChunkProgressDTO.ChunkItem it = new AdminModerationChunkProgressDTO.ChunkItem();
            it.setId(c.getId());
            it.setSourceType(c.getSourceType() == null ? null : c.getSourceType().name());
            it.setFileAssetId(c.getFileAssetId());
            it.setFileName(c.getFileName());
            it.setChunkIndex(c.getChunkIndex());
            it.setStartOffset(c.getStartOffset());
            it.setEndOffset(c.getEndOffset());
            it.setStatus(c.getStatus() == null ? null : c.getStatus().name());
            it.setVerdict(c.getVerdict() == null ? null : c.getVerdict().name());
            Double conf = c.getConfidence() == null ? null : c.getConfidence().doubleValue();
            it.setConfidence(conf);
            it.setScore(conf);
            it.setRiskScore(conf);
            it.setAttempts(c.getAttempts());
            it.setLastError(c.getLastError());
            it.setDecidedAt(c.getDecidedAt());
            java.time.LocalDateTime start = c.getCreatedAt();
            java.time.LocalDateTime end = c.getDecidedAt() != null ? c.getDecidedAt() : c.getUpdatedAt();
            Long elapsed = (start != null && end != null) ? java.time.Duration.between(start, end).toMillis() : null;
            it.setElapsedMs(elapsed);
            items.add(it);
        }
        dto.setChunks(items);
        return dto;
    }

    @Transactional
    public ChunkWorkResult prepareChunksIfNeeded(ModerationQueueEntity q) {
        if (q == null || q.getId() == null) throw new IllegalArgumentException("queue is required");

        ModerationChunkReviewConfigDTO cfg = configService.getConfig();
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) return ChunkWorkResult.disabled();

        if (q.getContentType() != ContentType.POST) return ChunkWorkResult.notChunked();

        PostsEntity p = postsRepository.findById(q.getContentId()).orElse(null);
        if (p == null || Boolean.TRUE.equals(p.getIsDeleted())) return ChunkWorkResult.notChunked();

        ModerationConfidenceFallbackConfigEntity fb = fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc().orElse(null);
        int threshold = resolveChunkThreshold(cfg, fb);
        boolean postChunked = p.getContentLength() != null && p.getContentLength() > threshold;
        if (Boolean.TRUE.equals(p.getIsChunkedReview())) postChunked = true;

        List<PostAttachmentsEntity> atts = safeLoadAttachments(p.getId());
        List<FileAssetExtractionsEntity> exts = safeLoadExtractions(atts);

        boolean anyFileChunked = false;
        for (FileAssetExtractionsEntity e : exts) {
            if (e == null) continue;
            if (!"READY".equalsIgnoreCase(String.valueOf(e.getExtractStatus()))) continue;
            String t = e.getExtractedText();
            if (t != null && t.length() > threshold) {
                anyFileChunked = true;
                break;
            }
        }

        boolean need = postChunked || anyFileChunked;
        if (!need) return ChunkWorkResult.notChunked();

        ModerationChunkSetEntity set = ensureChunkSetForQueue(q);
        if (set == null) return ChunkWorkResult.notChunked();
        if (set.getStatus() == ChunkSetStatus.CANCELLED) return ChunkWorkResult.cancelled(set);
        if (set.getTotalChunks() != null && set.getTotalChunks() > 0) return ChunkWorkResult.ready(set);
        if (set.getId() != null) {
            List<ModerationChunkSetEntity> locked = chunkSetRepository.findByIdForUpdate(set.getId());
            if (locked != null && !locked.isEmpty() && locked.get(0) != null) {
                set = locked.get(0);
            }
        }
        if (set == null) return ChunkWorkResult.notChunked();
        if (set.getStatus() == ChunkSetStatus.CANCELLED) return ChunkWorkResult.cancelled(set);
        if (set.getTotalChunks() != null && set.getTotalChunks() > 0) return ChunkWorkResult.ready(set);

        LocalDateTime now = LocalDateTime.now();
        ChunkSizingDecision sizingDecision = resolveChunkSizingDecision(cfg);
        int effectiveChunkSizeChars = sizingDecision.effectiveChunkSizeChars();
        int effectiveOverlapChars = cfg.getOverlapChars() == null ? 0 : Math.max(0, cfg.getOverlapChars());
        if (effectiveOverlapChars >= effectiveChunkSizeChars) effectiveOverlapChars = Math.max(0, effectiveChunkSizeChars / 10);

        set.setChunkThresholdChars(threshold);
        set.setChunkSizeChars(effectiveChunkSizeChars);
        set.setOverlapChars(effectiveOverlapChars);
        set.setStatus(ChunkSetStatus.RUNNING);
        set.setUpdatedAt(now);
        set.setConfigJson(configSnapshot(cfg, effectiveChunkSizeChars, effectiveOverlapChars, sizingDecision.budgetConvergenceLog()));
        chunkSetRepository.save(set);

        List<ModerationChunkEntity> chunks = new ArrayList<>();
        
        boolean semantic = "SEMANTIC".equalsIgnoreCase(cfg.getChunkMode());

        String postContent = p.getContent() == null ? "" : p.getContent();
        if (!postContent.isBlank() && postContent.length() > threshold) {
            List<Span> spans = semantic
                    ? chunkSemantic(postContent, effectiveChunkSizeChars, effectiveOverlapChars, cfg.getMaxChunksTotal())
                    : chunkSpans(postContent.length(), effectiveChunkSizeChars, effectiveOverlapChars, cfg.getMaxChunksTotal());

            for (int i = 0; i < spans.size(); i++) {
                Span sp = spans.get(i);
                ModerationChunkEntity c = new ModerationChunkEntity();
                c.setChunkSetId(set.getId());
                c.setSourceType(ChunkSourceType.POST_TEXT);
                c.setSourceKey("POST");
                c.setFileAssetId(null);
                c.setFileName(null);
                c.setChunkIndex(i);
                c.setStartOffset(sp.start);
                c.setEndOffset(sp.end);
                c.setStatus(ChunkStatus.PENDING);
                c.setAttempts(0);
                c.setCreatedAt(now);
                c.setUpdatedAt(now);
                c.setVersion(0);
                chunks.add(c);
            }
        }

        Map<Long, String> fileNameById = new HashMap<>();
        for (PostAttachmentsEntity a : atts) {
            if (a == null || a.getFileAssetId() == null) continue;
            String fn = a.getFileAsset() != null ? a.getFileAsset().getOriginalName() : null;
            fileNameById.putIfAbsent(a.getFileAssetId(), fn);
        }

        for (FileAssetExtractionsEntity e : exts) {
            if (e == null || e.getFileAssetId() == null) continue;
            if (!"READY".equalsIgnoreCase(String.valueOf(e.getExtractStatus()))) continue;
            String t = e.getExtractedText();
            if (t == null) t = "";
            if (t.isBlank() || t.length() <= threshold) continue;
            
            List<Span> spans = semantic
                    ? chunkSemantic(t, effectiveChunkSizeChars, effectiveOverlapChars, cfg.getMaxChunksTotal())
                    : chunkSpans(t.length(), effectiveChunkSizeChars, effectiveOverlapChars, cfg.getMaxChunksTotal());

            for (int i = 0; i < spans.size(); i++) {
                Span sp = spans.get(i);
                ModerationChunkEntity c = new ModerationChunkEntity();
                c.setChunkSetId(set.getId());
                c.setSourceType(ChunkSourceType.FILE_TEXT);
                c.setSourceKey("FILE:" + e.getFileAssetId());
                c.setFileAssetId(e.getFileAssetId());
                c.setFileName(fileNameById.get(e.getFileAssetId()));
                c.setChunkIndex(i);
                c.setStartOffset(sp.start);
                c.setEndOffset(sp.end);
                c.setStatus(ChunkStatus.PENDING);
                c.setAttempts(0);
                c.setCreatedAt(now);
                c.setUpdatedAt(now);
                c.setVersion(0);
                chunks.add(c);
            }
        }

        if (!chunks.isEmpty()) {
            chunkRepository.saveAll(chunks);
        }

        int total = chunks.size();
        set.setTotalChunks(total);
        set.setCompletedChunks(0);
        set.setFailedChunks(0);
        set.setUpdatedAt(now);
        chunkSetRepository.save(set);

        return ChunkWorkResult.ready(set);
    }

    static int resolveChunkThreshold(ModerationChunkReviewConfigDTO cfg, ModerationConfidenceFallbackConfigEntity fb) {
        Integer fbT = fb == null ? null : fb.getChunkThresholdChars();
        Integer cfgT = cfg == null ? null : cfg.getChunkThresholdChars();
        int t = fbT != null ? fbT : (cfgT != null ? cfgT : 20_000);
        if (t < 1000) t = 1000;
        if (t > 5_000_000) t = 5_000_000;
        return t;
    }

    private ChunkSizingDecision resolveChunkSizingDecision(ModerationChunkReviewConfigDTO cfg) {
        int baseChunkChars = cfg == null || cfg.getChunkSizeChars() == null ? 4000 : Math.max(500, cfg.getChunkSizeChars());

        ModerationLlmConfigEntity llm = null;
        try {
            llm = llmConfigRepository.findAll().stream().findFirst().orElse(null);
        } catch (Exception ignore) {
            llm = null;
        }

        PromptsEntity visionPrompt = null;
        if (llm != null && llm.getVisionPromptCode() != null) {
            try {
                visionPrompt = promptsRepository.findByPromptCode(llm.getVisionPromptCode()).orElse(null);
            } catch (Exception ignore) {
                visionPrompt = null;
            }
        }

        int imageTokenBudget = visionPrompt == null || visionPrompt.getVisionImageTokenBudget() == null ? 50_000 : clampInt(visionPrompt.getVisionImageTokenBudget(), 1, 300_000);
        int maxImagesPerRequest = visionPrompt == null || visionPrompt.getVisionMaxImagesPerRequest() == null ? 10 : clampInt(visionPrompt.getVisionMaxImagesPerRequest(), 1, 50);
        boolean highRes = visionPrompt != null && Boolean.TRUE.equals(visionPrompt.getVisionHighResolutionImages());
        int maxPixels = visionPrompt == null || visionPrompt.getVisionMaxPixels() == null ? 2_621_440 : Math.max(1, visionPrompt.getVisionMaxPixels());

        int tokenGridSide = 32;
        int tokenPixels = tokenGridSide * tokenGridSide;
        long maxPixelsEffective = highRes ? (16384L * tokenPixels) : (long) maxPixels;
        int perImageTokens = (int) Math.max(4L, Math.min(16386L, (maxPixelsEffective / tokenPixels) + 2L));
        long estimatedImageTokens = (long) perImageTokens * (long) maxImagesPerRequest;

        int charsPerToken = 4;
        int baseTextTokens = Math.max(128, baseChunkChars / charsPerToken);
        long totalBudget = (long) baseTextTokens + (long) imageTokenBudget;

        int effectiveTextTokens = baseTextTokens;
        int minTextTokens = 128;
        List<Map<String, Object>> rounds = new ArrayList<>();
        rounds.add(Map.of(
                "round", 0,
                "textTokenBudget", effectiveTextTokens,
                "chunkSizeChars", baseChunkChars,
                "estimatedTotalRequestTokens", effectiveTextTokens + estimatedImageTokens
        ));
        int round = 0;
        while (((long) effectiveTextTokens + estimatedImageTokens) > totalBudget && effectiveTextTokens > minTextTokens) {
            round += 1;
            int next = (int) Math.floor(effectiveTextTokens * 0.8);
            effectiveTextTokens = Math.max(minTextTokens, next);
            int nextChunkChars = Math.max(500, effectiveTextTokens * charsPerToken);
            rounds.add(Map.of(
                    "round", round,
                    "textTokenBudget", effectiveTextTokens,
                    "chunkSizeChars", nextChunkChars,
                    "estimatedTotalRequestTokens", effectiveTextTokens + estimatedImageTokens
            ));
        }

        int effectiveChars = Math.max(500, effectiveTextTokens * charsPerToken);
        int finalChunkChars = Math.min(baseChunkChars, effectiveChars);

        Map<String, Object> budgetConvergenceLog = new LinkedHashMap<>();
        budgetConvergenceLog.put("baseChunkSizeChars", baseChunkChars);
        budgetConvergenceLog.put("effectiveChunkSizeChars", finalChunkChars);
        budgetConvergenceLog.put("baseTextTokenBudget", baseTextTokens);
        budgetConvergenceLog.put("effectiveTextTokenBudget", effectiveTextTokens);
        budgetConvergenceLog.put("totalBudgetTokens", totalBudget);
        budgetConvergenceLog.put("imageTokenBudget", imageTokenBudget);
        budgetConvergenceLog.put("estimatedImageTokens", estimatedImageTokens);
        budgetConvergenceLog.put("maxImagesPerRequest", maxImagesPerRequest);
        budgetConvergenceLog.put("perImageTokenEstimate", perImageTokens);
        budgetConvergenceLog.put("highResolutionImages", highRes);
        budgetConvergenceLog.put("maxPixels", maxPixels);
        budgetConvergenceLog.put("triggeredResharding", finalChunkChars < baseChunkChars);
        budgetConvergenceLog.put("rounds", rounds);

        return new ChunkSizingDecision(finalChunkChars, budgetConvergenceLog);
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    @Transactional
    public Optional<ChunkToProcess> claimNextChunk(Long chunkSetId) {
        if (chunkSetId == null) return Optional.empty();
        ModerationChunkReviewConfigDTO cfg = configService.getConfig();
        int maxAttempts = cfg.getMaxAttempts() == null ? 3 : cfg.getMaxAttempts();
        List<ModerationChunkEntity> list = chunkRepository.findNextEligibleForUpdate(
                chunkSetId,
                ChunkStatus.PENDING,
                ChunkStatus.FAILED,
                Math.max(1, maxAttempts),
                PageRequest.of(0, 1, Sort.by(Sort.Order.asc("chunkIndex"), Sort.Order.asc("id")))
        );
        if (list == null || list.isEmpty()) return Optional.empty();
        ModerationChunkEntity c = list.get(0);
        if (c == null) return Optional.empty();
        int attempts = c.getAttempts() == null ? 0 : c.getAttempts();
        if (attempts >= maxAttempts) return Optional.empty();
        c.setAttempts(attempts + 1);
        c.setStatus(ChunkStatus.RUNNING);
        c.setLastError(null);
        c.setUpdatedAt(LocalDateTime.now());
        chunkRepository.save(c);
        return Optional.of(new ChunkToProcess(c.getId(), c.getSourceType(), c.getFileAssetId(), c.getFileName(), c.getChunkIndex(), c.getStartOffset(), c.getEndOffset()));
    }

    public record ChunkCandidate(
            Long chunkId,
            ChunkSourceType sourceType,
            Long fileAssetId,
            String fileName,
            Integer chunkIndex,
            Integer startOffset,
            Integer endOffset,
            Integer attempts
    ) {
    }

    @Transactional(readOnly = true)
    public List<ChunkCandidate> listEligibleChunks(Long chunkSetId) {
        if (chunkSetId == null) return List.of();
        ModerationChunkReviewConfigDTO cfg = configService.getConfig();
        int maxAttempts = cfg == null || cfg.getMaxAttempts() == null ? 3 : cfg.getMaxAttempts();
        List<ModerationChunkEntity> chunks = chunkRepository.findAllByChunkSetIdOrderBySourceKeyAscChunkIndexAsc(chunkSetId);
        if (chunks == null || chunks.isEmpty()) return List.of();
        List<ChunkCandidate> out = new ArrayList<>();
        for (ModerationChunkEntity c : chunks) {
            if (c == null) continue;
            ChunkStatus st = c.getStatus();
            int attempts = c.getAttempts() == null ? 0 : c.getAttempts();
            boolean eligible = st == ChunkStatus.PENDING || (st == ChunkStatus.FAILED && attempts < maxAttempts);
            if (!eligible) continue;
            out.add(new ChunkCandidate(
                    c.getId(),
                    c.getSourceType(),
                    c.getFileAssetId(),
                    c.getFileName(),
                    c.getChunkIndex(),
                    c.getStartOffset(),
                    c.getEndOffset(),
                    attempts
            ));
        }
        return out;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ChunkToProcess> claimChunkById(Long chunkId) {
        if (chunkId == null) return Optional.empty();
        ModerationChunkReviewConfigDTO cfg = configService.getConfig();
        int maxAttempts = cfg == null || cfg.getMaxAttempts() == null ? 3 : cfg.getMaxAttempts();
        List<ModerationChunkEntity> list = chunkRepository.findByIdForUpdate(chunkId);
        if (list == null || list.isEmpty()) return Optional.empty();
        ModerationChunkEntity c = list.get(0);
        if (c == null) return Optional.empty();
        ChunkStatus st = c.getStatus();
        if (!(st == ChunkStatus.PENDING || st == ChunkStatus.FAILED)) return Optional.empty();
        int attempts = c.getAttempts() == null ? 0 : c.getAttempts();
        if (attempts >= maxAttempts) return Optional.empty();
        c.setAttempts(attempts + 1);
        c.setStatus(ChunkStatus.RUNNING);
        c.setLastError(null);
        c.setUpdatedAt(LocalDateTime.now());
        chunkRepository.save(c);
        return Optional.of(new ChunkToProcess(c.getId(), c.getSourceType(), c.getFileAssetId(), c.getFileName(), c.getChunkIndex(), c.getStartOffset(), c.getEndOffset()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markChunkSuccess(Long chunkId, String model, Verdict verdict, Double score, Map<String, Object> labels, Integer tokensIn, Integer tokensOut) {
        markChunkSuccess(chunkId, model, verdict, score, labels, tokensIn, tokensOut, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markChunkSuccess(Long chunkId, String model, Verdict verdict, Double score, Map<String, Object> labels, Integer tokensIn, Integer tokensOut, boolean refreshSet) {
        if (chunkId == null) return;
        ModerationChunkEntity c = chunkRepository.findById(chunkId).orElse(null);
        if (c == null) return;
        LocalDateTime now = LocalDateTime.now();
        c.setStatus(ChunkStatus.SUCCESS);
        c.setModel(model);
        c.setVerdict(verdict);
        c.setConfidence(score == null ? null : java.math.BigDecimal.valueOf(clamp01(score)));
        c.setLabels(labels);
        c.setTokensIn(tokensIn);
        c.setTokensOut(tokensOut);
        c.setDecidedAt(now);
        c.setUpdatedAt(now);
        chunkRepository.save(c);
        try {
            maybeUpdateMemory(c.getChunkSetId(), labels, score, verdict);
        } catch (Exception ignore) {
        }
        if (refreshSet) refreshSetCounters(c.getChunkSetId());
    }

    @Transactional
    public void markChunkFailed(Long chunkId, String error) {
        markChunkFailed(chunkId, error, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markChunkFailed(Long chunkId, String error, boolean refreshSet) {
        if (chunkId == null) return;
        ModerationChunkEntity c = chunkRepository.findById(chunkId).orElse(null);
        if (c == null) return;
        LocalDateTime now = LocalDateTime.now();
        c.setStatus(ChunkStatus.FAILED);
        if (error != null) {
            String t = error.trim();
            if (t.length() > 1000) t = t.substring(0, 1000);
            c.setLastError(t);
        }
        c.setUpdatedAt(now);
        chunkRepository.save(c);
        if (refreshSet) refreshSetCounters(c.getChunkSetId());
    }

    public void refreshSetCountersNow(Long chunkSetId) {
        refreshSetCounters(chunkSetId);
    }

    @Transactional
    public void refreshSetCountersDebounced(Long chunkSetId, long minIntervalMs) {
        if (chunkSetId == null) return;
        long interval = Math.max(0L, minIntervalMs);
        if (interval <= 0L) {
            refreshSetCounters(chunkSetId);
            return;
        }
        try { chunkRepository.flush(); } catch (Exception ignore) {}
        long now = System.currentTimeMillis();
        AtomicLong ref = lastSetRefreshMs.computeIfAbsent(chunkSetId, (_k) -> new AtomicLong(0L));
        long prev = ref.get();
        if ((now - prev) < interval) {
            ModerationChunkSetEntity set = chunkSetRepository.findById(chunkSetId).orElse(null);
            if (set == null) return;
            int total = safeInt(set.getTotalChunks());
            int done0 = safeInt(set.getCompletedChunks());
            int failed0 = safeInt(set.getFailedChunks());
            if (total > 0 && ((long) done0 + (long) failed0) >= (long) total) return;
            long remaining = chunkRepository.countByChunkSetIdAndStatusIn(chunkSetId, List.of(ChunkStatus.PENDING, ChunkStatus.RUNNING));
            if (total > 0 && remaining <= 0L) {
                ref.set(now);
                refreshSetCounters(chunkSetId);
            }
            return;
        }
        if (!ref.compareAndSet(prev, now)) return;
        refreshSetCounters(chunkSetId);
    }

    @Transactional(readOnly = true)
    public long countPendingOrFailed(Long chunkSetId) {
        if (chunkSetId == null) return 0L;
        ModerationChunkReviewConfigDTO cfg = configService.getConfig();
        int maxAttempts = cfg == null || cfg.getMaxAttempts() == null ? 3 : cfg.getMaxAttempts();
        return chunkRepository.countRetriableByChunkSetId(
                chunkSetId,
                ChunkStatus.PENDING,
                ChunkStatus.FAILED,
                Math.max(1, maxAttempts)
        );
    }

    @Transactional(readOnly = true)
    public Optional<String> loadChunkText(Long queueId, ChunkSourceType sourceType, Long fileAssetId, int start, int end) {
        if (queueId == null) return Optional.empty();
        ModerationChunkSetEntity set = chunkSetRepository.findByQueueId(queueId).orElse(null);
        if (set == null || set.getContentType() != ContentType.POST) return Optional.empty();
        PostsEntity p = postsRepository.findById(set.getContentId()).orElse(null);
        if (p == null) return Optional.empty();
        if (sourceType == ChunkSourceType.POST_TEXT) {
            String content = p.getContent() == null ? "" : p.getContent();
            return Optional.of(sliceSafe(content, start, end));
        }
        if (sourceType == ChunkSourceType.FILE_TEXT && fileAssetId != null) {
            FileAssetExtractionsEntity ex = fileAssetExtractionsRepository.findById(fileAssetId).orElse(null);
            if (ex == null) return Optional.empty();
            String t = ex.getExtractedText();
            if (t == null) t = "";
            return Optional.of(sliceSafe(t, start, end));
        }
        return Optional.empty();
    }

    private ModerationChunkSetEntity createChunkSet(ModerationQueueEntity q) {
        LocalDateTime now = LocalDateTime.now();
        ModerationChunkSetEntity set = new ModerationChunkSetEntity();
        set.setQueueId(q.getId());
        set.setCaseType(q.getCaseType());
        set.setContentType(q.getContentType());
        set.setContentId(q.getContentId());
        set.setStatus(ChunkSetStatus.PENDING);
        set.setTotalChunks(0);
        set.setCompletedChunks(0);
        set.setFailedChunks(0);
        set.setCreatedAt(now);
        set.setUpdatedAt(now);
        set.setVersion(0);
        return chunkSetRepository.saveAndFlush(set);
    }

    private void refreshSetCounters(Long chunkSetId) {
        if (chunkSetId == null) return;
        for (int i = 0; i < 5; i++) {
            try {
                Boolean done = requiresNewTx().execute((status) -> {
                    ModerationChunkSetEntity set = chunkSetRepository.findById(chunkSetId).orElse(null);
                    if (set == null) return Boolean.TRUE;
                    long ok = chunkRepository.countByChunkSetIdAndStatusIn(chunkSetId, List.of(ChunkStatus.SUCCESS, ChunkStatus.CANCELLED));
                    long failed = chunkRepository.countByChunkSetIdAndStatusIn(chunkSetId, List.of(ChunkStatus.FAILED));
                    LocalDateTime now = LocalDateTime.now();
                    set.setCompletedChunks((int) Math.min(Integer.MAX_VALUE, ok));
                    set.setFailedChunks((int) Math.min(Integer.MAX_VALUE, failed));
                    int total = safeInt(set.getTotalChunks());
                    if (total > 0 && (ok + failed) >= (long) total) {
                        if (set.getStatus() != ChunkSetStatus.CANCELLED) set.setStatus(ChunkSetStatus.DONE);
                    } else {
                        if (set.getStatus() != ChunkSetStatus.CANCELLED) set.setStatus(ChunkSetStatus.RUNNING);
                    }
                    set.setUpdatedAt(now);
                    chunkSetRepository.saveAndFlush(set);
                    return Boolean.TRUE;
                });
                if (Boolean.TRUE.equals(done)) return;
                return;
            } catch (OptimisticLockingFailureException e) {
                sleepQuietly(10L + (long) (Math.random() * 40));
            } catch (Exception e) {
                return;
            }
        }
    }

    private static void sleepQuietly(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void maybeUpdateMemory(Long chunkSetId, Map<String, Object> labels, Double score, Verdict verdict) {
        if (chunkSetId == null) return;
        ModerationChunkReviewConfigDTO cfg = configService.getConfig();
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnableGlobalMemory())) return;
        ModerationConfidenceFallbackConfigEntity fb = null;
        try {
            fb = fallbackConfigRepository.findFirstByOrderByUpdatedAtDescIdDesc().orElse(null);
        } catch (Exception ignore) {
            fb = null;
        }
        Map<String, Object> thresholds = fb == null ? null : fb.getThresholds();
        int memoryMaxChars = (int) clampLong(asLong(thresholds == null ? null : thresholds.get("chunk.memory.maxChars"), 8000L), 500L, 200_000L);
        int maxEvidenceItems = (int) clampLong(asLong(thresholds == null ? null : thresholds.get("chunk.memory.maxEvidenceItems"), 12L), 0L, 1_000L);
        int maxEntities = (int) clampLong(asLong(thresholds == null ? null : thresholds.get("chunk.memory.maxEntities"), 20L), 0L, 1_000L);
        int prevSummaryMaxChars = (int) clampLong(asLong(thresholds == null ? null : thresholds.get("chunk.prevSummary.maxChars"), 200L), 0L, 2_000L);

        for (int i = 0; i < 3; i++) {
            try {
                Boolean done = requiresNewTx().execute((status) -> {
                    ModerationChunkSetEntity set = chunkSetRepository.findById(chunkSetId).orElse(null);
                    if (set == null || set.getStatus() == ChunkSetStatus.CANCELLED) return Boolean.TRUE;
                    Map<String, Object> mem = set.getMemoryJson() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(set.getMemoryJson());
                    LinkedHashSet<String> risk = new LinkedHashSet<>();
                    Object r0 = mem.get("riskTags");
                    if (r0 instanceof Collection<?> col) {
                        for (Object o : col) {
                            if (o == null) continue;
                            String s = String.valueOf(o).trim();
                            if (!s.isEmpty()) risk.add(s);
                        }
                    }
                    Object rImg0 = mem.get("imageRiskTags");
                    if (rImg0 instanceof Collection<?> col) {
                        for (Object o : col) {
                            if (o == null) continue;
                            String s = String.valueOf(o).trim();
                            if (!s.isEmpty()) risk.add(s);
                        }
                    }
                    Object rt = labels == null ? null : labels.get("riskTags");
                    if (rt instanceof Collection<?> col) {
                        for (Object o : col) {
                            if (o == null) continue;
                            String s = String.valueOf(o).trim();
                            if (!s.isEmpty()) risk.add(s);
                        }
                    }
                    Object lb = labels == null ? null : labels.get("labels");
                    if (lb instanceof Collection<?> col) {
                        for (Object o : col) {
                            if (o == null) continue;
                            String s = String.valueOf(o).trim();
                            if (!s.isEmpty()) risk.add(s);
                        }
                    }
                    if (!risk.isEmpty()) mem.put("riskTags", new ArrayList<>(risk));
                    if (score != null) mem.put("maxScore", Math.max(asDouble(mem.get("maxScore")), clamp01(score)));
                    if (verdict != null) mem.put("lastVerdict", verdict.name());
                    mem.put("updatedAt", LocalDateTime.now().toString());

                    List<Map<String, Object>> entities = normalizeListOfMaps(mem.get("entities"));
                    List<String> openQuestions = normalizeStringList(mem.get("openQuestions"));

                    int chunkIndex = (int) Math.min(Integer.MAX_VALUE, asLong(labels == null ? null : labels.get("chunkIndex"), 0L));
                    Object ps = labels == null ? null : labels.get("summaryForNext");
                    if (ps != null) {
                        String t = String.valueOf(ps).trim();
                        if (t.length() > prevSummaryMaxChars) t = t.substring(0, prevSummaryMaxChars);
                        mem.put("prevSummary", t);
                        LinkedHashMap<String, Object> summaries = new LinkedHashMap<>();
                        Object sm = mem.get("summaries");
                        if (sm instanceof Map<?, ?> m) {
                            for (Map.Entry<?, ?> en : m.entrySet()) {
                                if (en == null || en.getKey() == null || en.getValue() == null) continue;
                                String k = String.valueOf(en.getKey()).trim();
                                String v = String.valueOf(en.getValue()).trim();
                                if (k.isEmpty() || v.isEmpty()) continue;
                                summaries.put(k, v);
                            }
                        }
                        summaries.put(String.valueOf(chunkIndex), t);
                        while (summaries.size() > 200) {
                            String first = summaries.keySet().iterator().next();
                            summaries.remove(first);
                        }
                        mem.put("summaries", summaries);
                    }

                    List<String> llmEvidence0 = normalizeStringList(labels == null ? null : labels.get("evidence"));
                    {
                        String k = String.valueOf(chunkIndex);
                        Object v0 = mem.get("llmEvidenceByChunk");
                        LinkedHashMap<String, Object> byChunk = new LinkedHashMap<>();
                        LinkedHashSet<String> fingerprints = new LinkedHashSet<>();
                        if (v0 instanceof Map<?, ?> m) {
                            for (Map.Entry<?, ?> en : m.entrySet()) {
                                if (en == null || en.getKey() == null || en.getValue() == null) continue;
                                String kk = String.valueOf(en.getKey()).trim();
                                if (kk.isEmpty()) continue;
                                Object vv = en.getValue();
                                if (!(vv instanceof Collection<?> col)) continue;
                                ArrayList<String> out = new ArrayList<>();
                                for (Object o : col) {
                                    if (o == null) continue;
                                    String t = sanitizeEvidenceItemForMemory(String.valueOf(o), MAX_EVIDENCE_ITEM_CHARS);
                                    if (t == null || t.isEmpty()) continue;
                                    String fp = evidenceFingerprint(t);
                                    if (fp == null || fp.isBlank()) fp = "raw|" + normalizeForEvidenceFingerprint(t);
                                    if (!fingerprints.add(fp)) continue;
                                    out.add(t);
                                    if (out.size() >= 20) break;
                                }
                                if (out.isEmpty()) continue;
                                byChunk.put(kk, out);
                            }
                        }
                        byChunk.remove(k);

                        if (!llmEvidence0.isEmpty()) {
                            ArrayList<String> llmEvidence = new ArrayList<>();
                            for (String s : llmEvidence0) {
                                if (s == null) continue;
                                String t = sanitizeEvidenceItemForMemory(s, MAX_EVIDENCE_ITEM_CHARS);
                                if (t == null || t.isEmpty()) continue;
                                String fp = evidenceFingerprint(t);
                                if (fp == null || fp.isBlank()) fp = "raw|" + normalizeForEvidenceFingerprint(t);
                                if (!fingerprints.add(fp)) continue;
                                llmEvidence.add(t);
                                if (llmEvidence.size() >= 20) break;
                            }
                            if (!llmEvidence.isEmpty()) {
                                byChunk.put(k, llmEvidence);
                            }
                        }

                        if (!byChunk.isEmpty()) {
                            while (byChunk.size() > 200) {
                                String first = byChunk.keySet().iterator().next();
                                byChunk.remove(first);
                            }
                            mem.put("llmEvidenceByChunk", byChunk);
                        } else {
                            mem.remove("llmEvidenceByChunk");
                        }
                    }

                    Object ents = labels == null ? null : labels.get("entities");
                    if (ents instanceof Collection<?> col && maxEntities > 0) {
                        LinkedHashSet<String> dedup = new LinkedHashSet<>();
                        for (Map<String, Object> e0 : entities) {
                            if (e0 == null) continue;
                            String type = e0.get("type") == null ? "" : String.valueOf(e0.get("type")).trim();
                            String value = e0.get("value") == null ? "" : String.valueOf(e0.get("value")).trim();
                            if (type.isEmpty() || value.isEmpty()) continue;
                            dedup.add(type + "|" + value);
                        }
                        for (Object o : col) {
                            if (o instanceof Map<?, ?> m) {
                                String type = m.get("type") == null ? "" : String.valueOf(m.get("type")).trim();
                                String value = m.get("value") == null ? "" : String.valueOf(m.get("value")).trim();
                                if (type.isEmpty() || value.isEmpty()) continue;
                                String k = type + "|" + value;
                                if (!dedup.add(k)) continue;
                                long ci = asLong(m.get("chunkIndex"), 0L);
                                entities.add(new LinkedHashMap<>(Map.of("type", type, "value", value, "chunkIndex", (int) Math.min(Integer.MAX_VALUE, ci))));
                                if (entities.size() >= maxEntities) break;
                            }
                        }
                        if (entities.size() > maxEntities) entities = entities.subList(0, maxEntities);
                    }

                    if (!entities.isEmpty()) mem.put("entities", entities);
                    if (!openQuestions.isEmpty()) mem.put("openQuestions", openQuestions);

                    try {
                        enforceMemoryMaxChars(mem, memoryMaxChars);
                    } catch (Exception ignore) {
                    }
                    set.setMemoryJson(mem);
                    set.setUpdatedAt(LocalDateTime.now());
                    chunkSetRepository.saveAndFlush(set);
                    return Boolean.TRUE;
                });
                if (Boolean.TRUE.equals(done)) return;
                return;
            } catch (OptimisticLockingFailureException e) {
                sleepQuietly(10L + (long) (Math.random() * 40));
            } catch (Exception e) {
                return;
            }
        }
    }

    private TransactionTemplate requiresNewTx() {
        TransactionTemplate t = cachedRequiresNewTx;
        if (t != null) return t;
        synchronized (this) {
            t = cachedRequiresNewTx;
            if (t != null) return t;
            TransactionTemplate nt = new TransactionTemplate(transactionManager);
            nt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            cachedRequiresNewTx = nt;
            return nt;
        }
    }

    private static double asDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static long asLong(Object v, long def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static long clampLong(long v, long min, long max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static List<Map<String, Object>> normalizeListOfMaps(Object v) {
        if (!(v instanceof Collection<?> col)) return new ArrayList<>();
        ArrayList<Map<String, Object>> out = new ArrayList<>();
        for (Object o : col) {
            if (o instanceof Map<?, ?> m) {
                LinkedHashMap<String, Object> mm = new LinkedHashMap<>();
                for (Map.Entry<?, ?> en : m.entrySet()) {
                    if (en == null || en.getKey() == null) continue;
                    mm.put(String.valueOf(en.getKey()), en.getValue());
                }
                out.add(mm);
            }
        }
        return out;
    }

    private static List<String> normalizeStringList(Object v) {
        if (!(v instanceof Collection<?> col)) return new ArrayList<>();
        ArrayList<String> out = new ArrayList<>();
        for (Object o : col) {
            if (o == null) continue;
            String s = String.valueOf(o).trim();
            if (s.isEmpty()) continue;
            out.add(s);
            if (out.size() >= 200) break;
        }
        return out;
    }

    private static String sanitizeEvidenceItemForMemory(String raw, int plainTextMaxChars) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;
        int limit = Math.max(50, plainTextMaxChars);
        if (t.length() <= limit) return t;
        if (isValidJsonText(t)) return t;
        return t.substring(0, limit);
    }

    private static boolean isValidJsonText(String text) {
        if (text == null || text.isBlank()) return false;
        try {
            MAPPER.readTree(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String evidenceFingerprint(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty()) return "";
        if (!(t.startsWith("{") && t.endsWith("}"))) return "raw|" + normalizeForEvidenceFingerprint(trimEvidenceNoiseTail(t));
        try {
            Map<String, Object> node = MAPPER.readValue(t, Map.class);
            if (node == null) return "raw|" + normalizeForEvidenceFingerprint(trimEvidenceNoiseTail(t));
            String text = normalizeForEvidenceFingerprint(trimEvidenceNoiseTail(asString(node.get("text"))));
            if (!text.isBlank()) return "text|" + text;
            String before = normalizeForEvidenceFingerprint(trimEvidenceNoiseTail(asString(node.get("before_context"))));
            String after = normalizeForEvidenceFingerprint(trimEvidenceNoiseTail(asString(node.get("after_context"))));
            if (!before.isBlank() || !after.isBlank()) return "ctx|" + before + "|" + after;
            String quote = normalizeForEvidenceFingerprint(trimEvidenceNoiseTail(asString(node.get("quote"))));
            if (!quote.isBlank()) return "quote|" + quote;
            return "raw|" + normalizeForEvidenceFingerprint(trimEvidenceNoiseTail(t));
        } catch (Exception e) {
            return "raw|" + normalizeForEvidenceFingerprint(trimEvidenceNoiseTail(t));
        }
    }

    private static String trimEvidenceNoiseTail(String s) {
        if (s == null) return "";
        String x = s.trim();
        if (x.isEmpty()) return "";
        return x;
    }

    private static String normalizeForEvidenceFingerprint(String s) {
        if (s == null) return "";
        String x = s.trim();
        if (x.isEmpty()) return "";
        x = x.replaceAll("\\[\\[IMAGE_\\d+\\]\\]", " ");
        x = x.replace('\u201c', '"').replace('\u201d', '"')
                .replace('\u2018', '\'').replace('\u2019', '\'');
        x = x.replaceAll("\\s+", " ").trim();
        return x.toLowerCase();
    }

    private static String asString(Object v) {
        if (v == null) return "";
        return String.valueOf(v).trim();
    }

    private static void enforceMemoryMaxChars(Map<String, Object> mem, int maxChars) throws Exception {
        if (mem == null) return;
        int limit = Math.max(500, Math.min(200_000, maxChars));
        String json = MAPPER.writeValueAsString(mem);
        int guard = 0;
        while (json.length() > limit && guard < 200) {
            guard += 1;
            Object snippetByChunk = mem.get("chunkTextSnippetByChunk");
            if (snippetByChunk instanceof Map<?, ?> m && !m.isEmpty()) {
                LinkedHashMap<String, Object> c = new LinkedHashMap<>();
                for (Map.Entry<?, ?> en : m.entrySet()) {
                    if (en == null || en.getKey() == null) continue;
                    c.put(String.valueOf(en.getKey()), en.getValue());
                }
                if (!c.isEmpty()) {
                    String last = null;
                    for (String k : c.keySet()) last = k;
                    if (last != null) c.remove(last);
                    if (c.isEmpty()) mem.remove("chunkTextSnippetByChunk");
                    else mem.put("chunkTextSnippetByChunk", c);
                    json = MAPPER.writeValueAsString(mem);
                    continue;
                }
            }
            List<?> evidence = mem.get("evidence") instanceof List<?> l ? l : null;
            if (evidence != null && !evidence.isEmpty()) {
                ArrayList<Object> c = new ArrayList<>(evidence);
                c.remove(c.size() - 1);
                if (c.isEmpty()) mem.remove("evidence");
                else mem.put("evidence", c);
                json = MAPPER.writeValueAsString(mem);
                continue;
            }
            List<?> entities = mem.get("entities") instanceof List<?> l ? l : null;
            if (entities != null && !entities.isEmpty()) {
                ArrayList<Object> c = new ArrayList<>(entities);
                c.remove(c.size() - 1);
                if (c.isEmpty()) mem.remove("entities");
                else mem.put("entities", c);
                json = MAPPER.writeValueAsString(mem);
                continue;
            }
            Object prev = mem.get("prevSummary");
            if (prev != null) {
                String s = String.valueOf(prev);
                if (s.length() > 20) {
                    s = s.substring(0, s.length() / 2);
                    mem.put("prevSummary", s);
                    json = MAPPER.writeValueAsString(mem);
                    continue;
                }
                mem.remove("prevSummary");
                json = MAPPER.writeValueAsString(mem);
                continue;
            }
            Object summary = mem.get("summary");
            if (summary != null) {
                mem.remove("summary");
                json = MAPPER.writeValueAsString(mem);
                continue;
            }
            Object summaries = mem.get("summaries");
            if (summaries instanceof Map<?, ?> m && !m.isEmpty()) {
                LinkedHashMap<String, Object> c = new LinkedHashMap<>();
                for (Map.Entry<?, ?> en : m.entrySet()) {
                    if (en == null || en.getKey() == null) continue;
                    c.put(String.valueOf(en.getKey()), en.getValue());
                }
                if (!c.isEmpty()) {
                    String last = null;
                    for (String k : c.keySet()) last = k;
                    if (last != null) c.remove(last);
                    if (c.isEmpty()) mem.remove("summaries");
                    else mem.put("summaries", c);
                    json = MAPPER.writeValueAsString(mem);
                    continue;
                }
            }
            Object llmEvidenceByChunk = mem.get("llmEvidenceByChunk");
            if (llmEvidenceByChunk instanceof Map<?, ?> m && !m.isEmpty()) {
                LinkedHashMap<String, Object> c = new LinkedHashMap<>();
                for (Map.Entry<?, ?> en : m.entrySet()) {
                    if (en == null || en.getKey() == null) continue;
                    c.put(String.valueOf(en.getKey()), en.getValue());
                }
                if (!c.isEmpty()) {
                    String last = null;
                    for (String k : c.keySet()) last = k;
                    if (last != null) c.remove(last);
                    if (c.isEmpty()) mem.remove("llmEvidenceByChunk");
                    else mem.put("llmEvidenceByChunk", c);
                    json = MAPPER.writeValueAsString(mem);
                    continue;
                }
            }
            List<?> riskTags = mem.get("riskTags") instanceof List<?> l ? l : null;
            if (riskTags != null && !riskTags.isEmpty()) {
                ArrayList<Object> c = new ArrayList<>(riskTags);
                c.remove(c.size() - 1);
                if (c.isEmpty()) mem.remove("riskTags");
                else mem.put("riskTags", c);
                json = MAPPER.writeValueAsString(mem);
                continue;
            }
            List<?> openQuestions = mem.get("openQuestions") instanceof List<?> l ? l : null;
            if (openQuestions != null && !openQuestions.isEmpty()) {
                ArrayList<Object> c = new ArrayList<>(openQuestions);
                c.remove(c.size() - 1);
                if (c.isEmpty()) mem.remove("openQuestions");
                else mem.put("openQuestions", c);
                json = MAPPER.writeValueAsString(mem);
                continue;
            }
            break;
        }
    }

    private List<PostAttachmentsEntity> safeLoadAttachments(Long postId, int max) {
        try {
            var page = postAttachmentsRepository.findByPostId(postId, PageRequest.of(0, max, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))));
            if (page == null || page.getContent() == null) return List.of();
            return page.getContent();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<PostAttachmentsEntity> safeLoadAttachments(Long postId) {
        return safeLoadAttachments(postId, 200);
    }

    private List<FileAssetExtractionsEntity> safeLoadExtractions(List<PostAttachmentsEntity> atts) {
        if (atts == null || atts.isEmpty()) return List.of();
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (PostAttachmentsEntity a : atts) {
            if (a == null || a.getFileAssetId() == null) continue;
            ids.add(a.getFileAssetId());
            if (ids.size() >= 200) break;
        }
        if (ids.isEmpty()) return List.of();
        try {
            return fileAssetExtractionsRepository.findAllById(ids);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<Span> chunkSpans(int len, Integer chunkSizeChars, Integer overlapChars, Integer maxChunksTotal) {
        int size = chunkSizeChars == null ? 4000 : Math.max(500, chunkSizeChars);
        int overlap = overlapChars == null ? 0 : Math.max(0, overlapChars);
        if (overlap >= size) overlap = Math.max(0, size / 10);
        int step = Math.max(1, size - overlap);
        int maxChunks = maxChunksTotal == null ? 300 : Math.max(1, maxChunksTotal);

        List<Span> spans = new ArrayList<>();
        int start = 0;
        int idx = 0;
        while (start < len && idx < maxChunks) {
            int end = Math.min(len, start + size);
            spans.add(new Span(start, end));
            if (end >= len) break;
            start += step;
            idx += 1;
        }
        return spans;
    }

    private static List<Span> chunkSemantic(String text, Integer chunkSizeChars, Integer overlapChars, Integer maxChunksTotal) {
        if (text == null || text.isEmpty()) return List.of();
        int len = text.length();
        int size = chunkSizeChars == null ? 4000 : Math.max(500, chunkSizeChars);
        int overlap = overlapChars == null ? 0 : Math.max(0, overlapChars);
        if (overlap >= size) overlap = Math.max(0, size / 10);
        int maxChunks = maxChunksTotal == null ? 300 : Math.max(1, maxChunksTotal);

        List<Integer> breaks = new ArrayList<>();
        breaks.add(0);
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            // Paragraphs and sentences separators
            if (c == '\n' || c == '\r' || c == '.' || c == '?' || c == '!' || c == '。' || c == '？' || c == '！' || c == ';') {
                breaks.add(i + 1);
            }
        }
        if (breaks.get(breaks.size() - 1) != len) {
            breaks.add(len);
        }

        List<Span> spans = new ArrayList<>();
        int cursor = 0;
        while (cursor < len && spans.size() < maxChunks) {
            int targetEnd = cursor + size;
            int end;
            boolean hardCut = false;

            if (targetEnd >= len) {
                end = len;
            } else {
                int idx = Collections.binarySearch(breaks, targetEnd);
                if (idx < 0) idx = -idx - 2;
                int bestBp = (idx >= 0 && idx < breaks.size()) ? breaks.get(idx) : -1;

                if (bestBp > cursor) {
                    end = bestBp;
                } else {
                    end = targetEnd;
                    hardCut = true;
                }
            }

            spans.add(new Span(cursor, end));
            if (end >= len) break;

            int targetStart = end - overlap;
            int nextCursor;
            if (hardCut) {
                nextCursor = targetStart;
            } else {
                int idx = Collections.binarySearch(breaks, targetStart);
                if (idx < 0) idx = -idx - 2;
                int bestBp = (idx >= 0 && idx < breaks.size()) ? breaks.get(idx) : -1;

                if (bestBp > cursor) {
                    nextCursor = bestBp;
                } else {
                    nextCursor = targetStart;
                }
            }

            if (nextCursor <= cursor) nextCursor = cursor + 1;
            cursor = nextCursor;
        }
        return spans;
    }

    private static String sliceSafe(String text, int start, int end) {
        if (text == null) return "";
        int s = Math.max(0, start);
        int e = Math.max(s, end);
        if (s > text.length()) return "";
        if (e > text.length()) e = text.length();
        return text.substring(s, e);
    }

    private static int safeInt(Integer v) {
        return v == null ? 0 : Math.max(0, v);
    }

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) return 0.0;
        if (v < 0) return 0.0;
        if (v > 1) return 1.0;
        return v;
    }

    private static Map<String, Object> configSnapshot(ModerationChunkReviewConfigDTO cfg,
                                                      int effectiveChunkSizeChars,
                                                      int effectiveOverlapChars,
                                                      Map<String, Object> budgetConvergenceLog) {
        if (cfg == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chunkThresholdChars", cfg.getChunkThresholdChars());
        m.put("chunkSizeChars", cfg.getChunkSizeChars());
        m.put("overlapChars", cfg.getOverlapChars());
        m.put("effectiveChunkSizeChars", effectiveChunkSizeChars);
        m.put("effectiveOverlapChars", effectiveOverlapChars);
        m.put("budgetConvergenceLog", budgetConvergenceLog == null ? Map.of() : budgetConvergenceLog);
        m.put("maxChunksTotal", cfg.getMaxChunksTotal());
        m.put("chunksPerRun", cfg.getChunksPerRun());
        m.put("maxAttempts", cfg.getMaxAttempts());
        m.put("enableTempIndexHints", cfg.getEnableTempIndexHints());
        m.put("enableContextCompress", cfg.getEnableContextCompress());
        m.put("enableGlobalMemory", cfg.getEnableGlobalMemory());
        m.put("sendImagesOnlyWhenInEvidence", cfg.getSendImagesOnlyWhenInEvidence());
        m.put("includeImagesBlockOnlyForEvidenceMatches", cfg.getIncludeImagesBlockOnlyForEvidenceMatches());
        return m;
    }

    public static class ProgressSummary {
        public Long queueId;
        public String status;
        public int total;
        public int completed;
        public int failed;
        public LocalDateTime updatedAt;
    }

    public record ChunkToProcess(Long chunkId, ChunkSourceType sourceType, Long fileAssetId, String fileName, Integer chunkIndex, Integer startOffset, Integer endOffset) {}

    private record ChunkSizingDecision(int effectiveChunkSizeChars, Map<String, Object> budgetConvergenceLog) {}

    public static class ChunkWorkResult {
        public boolean enabled;
        public boolean chunked;
        public boolean cancelled;
        public Long chunkSetId;

        public static ChunkWorkResult disabled() {
            ChunkWorkResult r = new ChunkWorkResult();
            r.enabled = false;
            return r;
        }

        public static ChunkWorkResult notChunked() {
            ChunkWorkResult r = new ChunkWorkResult();
            r.enabled = true;
            r.chunked = false;
            return r;
        }

        public static ChunkWorkResult cancelled(ModerationChunkSetEntity set) {
            ChunkWorkResult r = ready(set);
            r.cancelled = true;
            return r;
        }

        public static ChunkWorkResult ready(ModerationChunkSetEntity set) {
            ChunkWorkResult r = new ChunkWorkResult();
            r.enabled = true;
            r.chunked = true;
            r.chunkSetId = set == null ? null : set.getId();
            return r;
        }
    }

    private record Span(int start, int end) {}
}
