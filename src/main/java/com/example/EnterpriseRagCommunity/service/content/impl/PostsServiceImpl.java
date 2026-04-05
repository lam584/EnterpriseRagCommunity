package com.example.EnterpriseRagCommunity.service.content.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.dto.content.PostsPublishDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostComposeConfigDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostsUpdateDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.CurrentUserIdResolver;
import com.example.EnterpriseRagCommunity.service.access.CurrentUsernameResolver;
import com.example.EnterpriseRagCommunity.service.access.SafeTextSupport;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.ai.AiPostSummaryTriggerService;
import com.example.EnterpriseRagCommunity.service.content.BoardAccessControlService;
import com.example.EnterpriseRagCommunity.service.content.PostLookupSupport;
import com.example.EnterpriseRagCommunity.service.content.PostComposeConfigService;
import com.example.EnterpriseRagCommunity.service.content.PostsService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagFileAssetIndexAsyncService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexVisibilitySyncService;
import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;

import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class PostsServiceImpl implements PostsService {
    private record ComposeApplyResult(boolean chunked) {
    }

    private final PostsRepository postsRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final FileAssetsRepository fileAssetsRepository;
    private final AdministratorService administratorService;
    private final AdminModerationQueueService adminModerationQueueService;
    private final ModerationRuleAutoRunner moderationRuleAutoRunner;
    private final ModerationVecAutoRunner moderationVecAutoRunner;
    private final ModerationLlmAutoRunner moderationLlmAutoRunner;
    private final AiPostSummaryTriggerService aiPostSummaryTriggerService;
    private final TagsRepository tagsRepository;
    private final RagPostIndexVisibilitySyncService ragPostIndexVisibilitySyncService;
    private final HybridRagRetrievalService hybridRagRetrievalService;
    private final VectorIndicesRepository vectorIndicesRepository;
    private final RagFileAssetIndexAsyncService ragFileAssetIndexAsyncService;
    private final BoardAccessControlService boardAccessControlService;
    private final AuditLogWriter auditLogWriter;
    private final PostComposeConfigService postComposeConfigService;
    private final ModerationQueueRepository moderationQueueRepository;

    private Long currentUserIdOrThrow() {
        return CurrentUserIdResolver.currentUserIdOrThrow(
                administratorService,
                () -> new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {},
                () -> new IllegalArgumentException("当前用户不存在")
        );
    }

    @Override
    @Transactional
    public PostsEntity publish(PostsPublishDTO dto) {
        if (dto == null) throw new IllegalArgumentException("参数不能为空");

        Long me = null;
        String actorName = currentUsernameOrNull();
        Long boardId = dto.getBoardId();
        try {
            me = currentUserIdOrThrow();

            if (boardId == null) {
                throw new IllegalArgumentException("boardId 不能为空");
            }
            if (!boardAccessControlService.canPostBoard(boardId, boardAccessControlService.currentUserRoleIds())) {
                throw new AccessDeniedException("无权在该版块发帖");
            }

            var composeCfg = postComposeConfigService.getConfig();

            PostsEntity post = new PostsEntity();
            post.setTenantId(null);
            post.setBoardId(boardId);
            post.setAuthorId(me);
            ComposeApplyResult composeResult = applyComposeFields(
                    post,
                    dto.getTitle(),
                    dto.getContent(),
                    dto.getContentFormat(),
                    dto.getTags(),
                    dto.getMetadata(),
                    composeCfg
            );

            post.setStatus(PostStatus.PENDING);
            post.setPublishedAt(null);

            post.setIsDeleted(false);

            post = postsRepository.save(post);

            List<Long> attachmentIds = dto.getAttachmentIds();
            syncPostAttachments(post.getId(), me, attachmentIds, composeCfg, composeResult.chunked());

            adminModerationQueueService.ensureEnqueuedPost(post.getId());
            moderationQueueRepository.flush(); // Ensure queue row is visible to native queries
            moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType.CONTENT, com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType.POST, post.getId())
                    .ifPresent(q -> moderationRuleAutoRunner.runForQueueId(q.getId()));
            post = postsRepository.findById(post.getId()).orElse(post);

            scheduleModerationAutoRunAfterCommit();

            try {
                aiPostSummaryTriggerService.scheduleGenerateAfterCommit(post.getId(), me);
            } catch (Exception ignore) {
            }

            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_PUBLISH",
                    "POST",
                    post.getId(),
                    AuditResult.SUCCESS,
                    "发帖",
                    null,
                    mapOfNonNull(
                            "boardId", boardId,
                            "title", safeText(post.getTitle(), 128),
                            "status", post.getStatus() == null ? null : post.getStatus().name()
                    )
            );
            return post;
        } catch (RuntimeException e) {
            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_PUBLISH",
                    "POST",
                    null,
                    AuditResult.FAIL,
                    safeText(e.getMessage(), 512),
                    null,
                    boardId == null ? Map.of() : Map.of("boardId", boardId)
            );
            throw e;
        }
    }

    private void scheduleModerationAutoRunAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        moderationRuleAutoRunner.runOnce();
                    } catch (Exception ignore) {
                    }
                    try {
                        moderationVecAutoRunner.runOnce();
                    } catch (Exception ignore) {
                    }
                    try {
                        moderationLlmAutoRunner.runOnce();
                    } catch (Exception ignore) {
                    }
                }
            });
            return;
        }
        try {
            moderationRuleAutoRunner.runOnce();
        } catch (Exception ignore) {
        }
        try {
            moderationVecAutoRunner.runOnce();
        } catch (Exception ignore) {
        }
        try {
            moderationLlmAutoRunner.runOnce();
        } catch (Exception ignore) {
        }
    }

    private static List<String> resolveTags(List<String> tags, Map<String, Object> metadata) {
        List<String> out = new ArrayList<>();
        if (tags != null) {
            for (String t : tags) {
                String s = normalizeTagSlug(t);
                if (s != null) out.add(s);
            }
            return out;
        }
        if (metadata == null) return out;
        Object raw = metadata.get("tags");
        if (!(raw instanceof List<?> list)) return out;
        for (Object v : list) {
            String s = normalizeTagSlug(v == null ? null : String.valueOf(v));
            if (s != null) out.add(s);
        }
        return out;
    }

    private static ComposeApplyResult applyComposeFields(
            PostsEntity post,
            String rawTitle,
            String content,
            ContentFormat contentFormat,
            List<String> tags,
            Map<String, Object> metadata,
            PostComposeConfigDTO composeCfg
    ) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (Boolean.TRUE.equals(composeCfg.getRequireTitle()) && title.isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (title.length() > 191) {
            throw new IllegalArgumentException("标题过长（最多 191 字符）");
        }
        post.setTitle(title);
        post.setContent(content);
        int contentLen = content == null ? 0 : content.length();
        Integer maxContentChars = composeCfg.getMaxContentChars();
        if (maxContentChars != null && maxContentChars > 0 && contentLen > maxContentChars) {
            throw new IllegalArgumentException("内容过长（最多 " + maxContentChars + " 字符）");
        }
        post.setContentLength(contentLen);
        post.setContentFormat(contentFormat == null ? ContentFormat.MARKDOWN : contentFormat);
        List<String> normalizedTags = resolveTags(tags, metadata);
        if (Boolean.TRUE.equals(composeCfg.getRequireTags()) && normalizedTags.isEmpty()) {
            throw new IllegalArgumentException("标签不能为空");
        }
        post.setMetadata(mergeMetadataWithTags(metadata, tags));

        Integer threshold = composeCfg.getChunkThresholdChars();
        boolean isChunked = threshold != null && threshold > 0 && contentLen > threshold;
        post.setIsChunkedReview(isChunked);
        post.setChunkThresholdChars(isChunked ? threshold : null);
        post.setChunkingStrategy(isChunked ? "CHARS" : null);
        return new ComposeApplyResult(isChunked);
    }

    private void scheduleFileAssetRagSyncAfterCommit(List<Long> fileAssetIds) {
        if (fileAssetIds == null || fileAssetIds.isEmpty()) return;
        LinkedHashSet<Long> uniqFileAssetIds = new LinkedHashSet<>();
        for (Long id : fileAssetIds) {
            if (id != null) uniqFileAssetIds.add(id);
        }
        if (uniqFileAssetIds.isEmpty()) return;

        Runnable syncAction = () -> {
            List<VectorIndicesEntity> indices = vectorIndicesRepository.findByStatus(VectorIndexStatus.READY);
            if (indices == null || indices.isEmpty()) return;
            for (VectorIndicesEntity vi : indices) {
                if (vi == null || vi.getId() == null) continue;
                Map<String, Object> meta = vi.getMetadata();
                String sourceType = meta == null || meta.get("sourceType") == null ? null : String.valueOf(meta.get("sourceType")).trim();
                if (!"FILE_ASSET".equalsIgnoreCase(sourceType)) continue;
                for (Long fileAssetId : uniqFileAssetIds) {
                    ragFileAssetIndexAsyncService.syncSingleFileAssetAsync(vi.getId(), fileAssetId);
                }
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncAction.run();
                }
            });
            return;
        }
        syncAction.run();
    }

    private void syncPostAttachments(Long postId, Long operatorUserId, List<Long> attachmentIds,
                                     PostComposeConfigDTO composeCfg, boolean isChunked) {
        if (attachmentIds == null || attachmentIds.isEmpty()) return;
        validateAttachmentCount(attachmentIds, composeCfg, isChunked);

        LinkedHashSet<Long> uniq = new LinkedHashSet<>(attachmentIds);
        List<Long> syncedAttachmentIds = new ArrayList<>();
        for (Long attachmentId : uniq) {
            if (attachmentId == null) continue;
            FileAssetsEntity fa = fileAssetsRepository.findById(attachmentId)
                    .orElseThrow(() -> new IllegalArgumentException("附件不存在: " + attachmentId));

            Long ownerId = fa.getOwner() == null ? null : fa.getOwner().getId();
            if (ownerId == null || !ownerId.equals(operatorUserId)) {
                throw new IllegalArgumentException("无权使用该附件: " + attachmentId);
            }
            if (fa.getStatus() != FileAssetStatus.READY) {
                throw new IllegalArgumentException("附件状态不可用: " + attachmentId);
            }

            PostAttachmentsEntity pa = new PostAttachmentsEntity();
            pa.setPostId(postId);
            pa.setFileAssetId(fa.getId());
            pa.setCreatedAt(LocalDateTime.now());
            postAttachmentsRepository.save(pa);
            syncedAttachmentIds.add(fa.getId());
        }
        scheduleFileAssetRagSyncAfterCommit(syncedAttachmentIds);
    }

    private static void validateAttachmentCount(List<Long> attachmentIds, PostComposeConfigDTO composeCfg, boolean isChunked) {
        int uniqCount = new LinkedHashSet<>(attachmentIds).size();
        Integer maxAttachments = composeCfg.getMaxAttachments();
        boolean bypass = isChunked && Boolean.TRUE.equals(composeCfg.getBypassAttachmentLimitWhenChunked());
        if (!bypass && maxAttachments != null && maxAttachments > 0 && uniqCount > maxAttachments) {
            throw new IllegalArgumentException("附件数量超限（最多 " + maxAttachments + " 个）");
        }
    }

    private static Map<String, Object> mergeMetadataWithTags(Map<String, Object> metadata, List<String> tags) {
        if (metadata == null && tags == null) return null;
        Map<String, Object> out = new HashMap<>();
        if (metadata != null) out.putAll(metadata);
        if (tags != null) {
            List<String> normalized = resolveTags(tags, null);
            out.put("tags", normalized);
        }
        return out.isEmpty() ? null : out;
    }

    private static String normalizeTagSlug(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        s = s.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (s.isEmpty()) return null;
        if (s.length() > 96) s = s.substring(0, 96);
        return s;
    }

    @Override
    public Page<PostsEntity> query(String keyword,
                                  Long postId,
                                  String searchMode,
                                  Long boardId,
                                  PostStatus status,
                                  Long authorId,
                                  LocalDate createdFrom,
                                  LocalDate createdTo,
                                  int page,
                                  int pageSize,
                                  String sortBy,
                                  String sortOrderDirection) {
        int safePage = Math.max(page, 1);
        int safePageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);

        String safeSortBy = resolveSafeSortBy(sortBy);
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortOrderDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize, Sort.by(direction, safeSortBy));

        // 1) ID search has the highest priority
        if (postId != null) {
            PostsEntity p = postsRepository.findByIdAndIsDeletedFalse(postId).orElse(null);
            List<PostsEntity> content = (p == null) ? java.util.Collections.emptyList() : java.util.Collections.singletonList(p);
            // Keep response shape consistent with Page, and mirror requested paging.
            return new PageImpl<>(content, PageRequest.of(safePage - 1, safePageSize), content.size());
        }

        // 2) Keyword search path (vector retrieval)
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            if (status != null && status != PostStatus.PUBLISHED) {
                return new PageImpl<>(List.of(), PageRequest.of(safePage - 1, safePageSize), 0);
            }
            return vectorSearchPublished(kw, boardId, authorId, createdFrom, createdTo, safePage, safePageSize);
        }

        // 3) Non-keyword filtering (spec)
        Specification<PostsEntity> spec = (root, q, cb) -> cb.equal(root.get("isDeleted"), false);

        if (boardId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("boardId"), boardId));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (authorId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("authorId"), authorId));
        }
        if (createdFrom != null || createdTo != null) {
            LocalDateTime start = createdFrom == null ? LocalDate.of(1970, 1, 1).atStartOfDay() : createdFrom.atStartOfDay();
            LocalDateTime endExclusive = createdTo == null ? LocalDate.now().plusDays(1).atStartOfDay() : createdTo.plusDays(1).atStartOfDay();
            spec = spec.and((root, q, cb) -> cb.between(root.get("createdAt"), start, endExclusive));
        }

        return postsRepository.findAll(spec, pageable);
    }

    private static String resolveSafeSortBy(String sortBy) {
        // Only allow sorting by real PostsEntity properties to avoid PropertyReferenceException (500).
        // Also keep a couple of legacy/portal-friendly aliases.
        String rawSortBy = (sortBy == null ? "" : sortBy.trim());
        String normalizedSortBy = normalizeSortByAlias(rawSortBy);

        java.util.Set<String> allowedSortBy = java.util.Set.of(
                "id",
                "tenantId",
                "boardId",
                "authorId",
                "title",
                "contentFormat",
                "status",
                "publishedAt",
                "isDeleted",
                "createdAt",
                "updatedAt"
        );
        return allowedSortBy.contains(normalizedSortBy) ? normalizedSortBy : "createdAt";
    }

    private static String normalizeSortByAlias(String rawSortBy) {
        if (rawSortBy.isEmpty()) {
            return "createdAt";
        }
        if ("hotScore".equalsIgnoreCase(rawSortBy) || "hot_score".equalsIgnoreCase(rawSortBy)) {
            return "createdAt";
        }
        if ("created_at".equalsIgnoreCase(rawSortBy)) {
            return "createdAt";
        }
        if ("updated_at".equalsIgnoreCase(rawSortBy)) {
            return "updatedAt";
        }
        if ("published_at".equalsIgnoreCase(rawSortBy)) {
            return "publishedAt";
        }
        return rawSortBy;
    }

    private Page<PostsEntity> vectorSearchPublished(String keyword, Long boardId, Long authorId, LocalDate createdFrom, LocalDate createdTo, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        int fetchLimit = Math.clamp((long) (safePage + 1) * safePageSize * 5, 50, 500);

        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setBm25K(0);
        cfg.setVecK(fetchLimit);
        cfg.setHybridK(fetchLimit);
        cfg.setMaxDocs(fetchLimit);
        cfg.setRerankEnabled(false);

        HybridRagRetrievalService.RetrieveResult rr = hybridRagRetrievalService.retrieve(keyword, boardId, cfg, false);
        List<HybridRagRetrievalService.DocHit> hits = rr == null ? List.of() : (rr.getFinalHits() == null ? List.of() : rr.getFinalHits());

        LinkedHashSet<Long> uniq = new LinkedHashSet<>();
        for (HybridRagRetrievalService.DocHit h : hits) {
            if (h == null || h.getPostId() == null) continue;
            uniq.add(h.getPostId());
        }
        List<Long> ids = new ArrayList<>(uniq);

        int offset = (safePage - 1) * safePageSize;
        if (offset >= ids.size()) {
            return new PageImpl<>(List.of(), PageRequest.of(safePage - 1, safePageSize), ids.size());
        }
        int end = Math.min(ids.size(), offset + safePageSize);
        List<Long> slice = ids.subList(offset, end);

        Map<Long, PostsEntity> byId = PostLookupSupport.loadPublishedPostsById(slice, postsRepository);

        LocalDateTime start = createdFrom == null ? null : createdFrom.atStartOfDay();
        LocalDateTime endExclusive = createdTo == null ? null : createdTo.plusDays(1).atStartOfDay();

        List<PostsEntity> content = new ArrayList<>();
        for (Long id : slice) {
            PostsEntity p = byId.get(id);
            if (p == null) continue;
            if (authorId != null && p.getAuthorId() != null && !authorId.equals(p.getAuthorId())) continue;
            if (start != null && p.getCreatedAt() != null && p.getCreatedAt().isBefore(start)) continue;
            if (endExclusive != null && p.getCreatedAt() != null && !p.getCreatedAt().isBefore(endExclusive)) continue;
            content.add(p);
        }

        boolean hasMore = ids.size() > end;
        long totalElements = hasMore ? (long) offset + content.size() + 1 : (long) offset + content.size();
        return new PageImpl<>(content, PageRequest.of(safePage - 1, safePageSize), totalElements);
    }

    @Override
    @Transactional
    public PostsEntity updateStatus(Long id, PostStatus status) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        if (status == null) throw new IllegalArgumentException("status 不能为空");

        Long me = null;
        String actorName = currentUsernameOrNull();
        try {
            me = currentUserIdOrThrow();
            PostsEntity post = postsRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + id));

            if (Boolean.TRUE.equals(post.getIsDeleted())) {
                throw new IllegalArgumentException("帖子已删除: " + id);
            }

            if (status == PostStatus.PUBLISHED && post.getPublishedAt() == null) {
                post.setPublishedAt(LocalDateTime.now());
            }
            post.setStatus(status);

            PostsEntity saved = postsRepository.save(post);
            ragPostIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());

            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_UPDATE_STATUS",
                    "POST",
                    saved.getId(),
                    AuditResult.SUCCESS,
                    "更新帖子状态",
                    null,
                    Map.of("status", status.name())
            );
            return saved;
        } catch (RuntimeException e) {
            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_UPDATE_STATUS",
                    "POST",
                    id,
                    AuditResult.FAIL,
                    safeText(e.getMessage(), 512),
                    null,
                    Map.of("status", status.name())
            );
            throw e;
        }
    }

    @Override
    public PostsEntity getById(Long id) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        return postsRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + id));
    }

    @Override
    @Transactional
    public PostsEntity update(Long id, PostsUpdateDTO dto) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        if (dto == null) throw new IllegalArgumentException("参数不能为空");

        Long me = null;
        String actorName = currentUsernameOrNull();
        Long boardId = dto.getBoardId();
        try {
            me = currentUserIdOrThrow();

            PostsEntity post = postsRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + id));

            if (Boolean.TRUE.equals(post.getIsDeleted())) {
                throw new IllegalArgumentException("帖子已删除: " + id);
            }

            boolean isAuthor = post.getAuthorId() != null && post.getAuthorId().equals(me);
            if (!isAuthor) {
                throw new IllegalArgumentException("无权编辑该帖子");
            }

            if (boardId == null) {
                throw new IllegalArgumentException("boardId 不能为空");
            }
            if (!boardAccessControlService.canPostBoard(boardId, boardAccessControlService.currentUserRoleIds())) {
                throw new AccessDeniedException("无权在该版块发帖");
            }

            var composeCfg = postComposeConfigService.getConfig();

            post.setBoardId(boardId);
            ComposeApplyResult composeResult = applyComposeFields(
                    post,
                    dto.getTitle(),
                    dto.getContent(),
                    dto.getContentFormat(),
                    dto.getTags(),
                    dto.getMetadata(),
                    composeCfg
            );

            postAttachmentsRepository.deleteByPostId(post.getId());

            List<Long> attachmentIds = dto.getAttachmentIds();
            syncPostAttachments(post.getId(), me, attachmentIds, composeCfg, composeResult.chunked());

            PostsEntity saved = postsRepository.save(post);
            if (saved.getStatus() == PostStatus.PUBLISHED && !Boolean.TRUE.equals(saved.getIsDeleted())) {
                ragPostIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());
            }

            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_UPDATE",
                    "POST",
                    saved.getId(),
                    AuditResult.SUCCESS,
                    "编辑帖子",
                    null,
                    mapOfNonNull(
                            "boardId", boardId,
                            "title", safeText(saved.getTitle(), 128)
                    )
            );
            return saved;
        } catch (RuntimeException e) {
            Map<String, Object> d = new HashMap<>();
            if (boardId != null) d.put("boardId", boardId);
            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_UPDATE",
                    "POST",
                    id,
                    AuditResult.FAIL,
                    safeText(e.getMessage(), 512),
                    null,
                    d
            );
            throw e;
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");

        Long me = null;
        String actorName = currentUsernameOrNull();
        try {
            me = currentUserIdOrThrow();
            PostsEntity post = postsRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + id));

            if (Boolean.TRUE.equals(post.getIsDeleted())) {
                return;
            }

            boolean isAuthor = post.getAuthorId() != null && post.getAuthorId().equals(me);
            if (!isAuthor) {
                throw new IllegalArgumentException("无权删除该帖子");
            }

            post.setIsDeleted(true);
            post.setStatus(PostStatus.ARCHIVED);
            PostsEntity saved = postsRepository.save(post);

            postAttachmentsRepository.deleteByPostId(saved.getId());
            moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.POST, saved.getId())
                    .ifPresent(moderationQueueRepository::delete);
            ragPostIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());

            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_DELETE",
                    "POST",
                    saved.getId(),
                    AuditResult.SUCCESS,
                    "删除帖子",
                    null,
                    mapOfNonNull("status", saved.getStatus() == null ? null : saved.getStatus().name())
            );
        } catch (RuntimeException e) {
            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_DELETE",
                    "POST",
                    id,
                    AuditResult.FAIL,
                    safeText(e.getMessage(), 512),
                    null,
                    Map.of()
            );
            throw e;
        }
    }

    private static String currentUsernameOrNull() {
        return CurrentUsernameResolver.currentUsernameOrNull();
    }

    private static Map<String, Object> mapOfNonNull(Object... kv) {
        if (kv == null || kv.length == 0) return Map.of();
        if (kv.length % 2 != 0) throw new IllegalArgumentException("kv 必须为偶数长度");
        Map<String, Object> out = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            Object k = kv[i];
            Object v = kv[i + 1];
            if (k == null) continue;
            if (v == null) continue;
            out.put(String.valueOf(k), v);
        }
        return out.isEmpty() ? Map.of() : out;
    }

    private static String safeText(String s, int maxLen) {
        return SafeTextSupport.safeText(s, maxLen);
    }
}
