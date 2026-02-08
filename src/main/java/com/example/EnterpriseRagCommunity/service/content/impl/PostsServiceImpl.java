package com.example.EnterpriseRagCommunity.service.content.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.dto.content.PostsPublishDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostsUpdateDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.TagsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import com.example.EnterpriseRagCommunity.entity.moderation.RiskLabelingEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Source;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.TagsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.RiskLabelingRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiPostRiskTagService;
import com.example.EnterpriseRagCommunity.service.ai.AiPostSummaryTriggerService;
import com.example.EnterpriseRagCommunity.service.content.BoardAccessControlService;
import com.example.EnterpriseRagCommunity.service.content.PostsService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexVisibilitySyncService;

import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
public class PostsServiceImpl implements PostsService {

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private PostAttachmentsRepository postAttachmentsRepository;

    @Autowired
    private FileAssetsRepository fileAssetsRepository;

    @Autowired
    private AdministratorService administratorService;

    @Autowired
    private AdminModerationQueueService adminModerationQueueService;

    @Autowired
    private ModerationRuleAutoRunner moderationRuleAutoRunner;

    @Autowired
    private ModerationVecAutoRunner moderationVecAutoRunner;

    @Autowired
    private ModerationLlmAutoRunner moderationLlmAutoRunner;

    @Autowired
    private AiPostRiskTagService aiPostRiskTagService;

    @Autowired
    private AiPostSummaryTriggerService aiPostSummaryTriggerService;

    @Autowired
    private TagsRepository tagsRepository;

    @Autowired
    private RiskLabelingRepository riskLabelingRepository;

    @Autowired
    private RagPostIndexVisibilitySyncService ragPostIndexVisibilitySyncService;

    @Autowired
    private HybridRagRetrievalService hybridRagRetrievalService;

    @Autowired
    private BoardAccessControlService boardAccessControlService;

    private Long currentUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {};
        }
        String email = auth.getName();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"))
                .getId();
    }

    @Override
    @Transactional
    public PostsEntity publish(PostsPublishDTO dto) {
        if (dto == null) throw new IllegalArgumentException("参数不能为空");

        Long me = currentUserIdOrThrow();

        if (dto.getBoardId() == null) {
            throw new IllegalArgumentException("boardId 不能为空");
        }
        if (!boardAccessControlService.canPostBoard(dto.getBoardId(), boardAccessControlService.currentUserRoleIds())) {
            throw new AccessDeniedException("无权在该版块发帖");
        }

        PostsEntity post = new PostsEntity();
        post.setTenantId(null);
        post.setBoardId(dto.getBoardId());
        post.setAuthorId(me);
        post.setTitle(dto.getTitle() == null ? "" : dto.getTitle().trim());
        post.setContent(dto.getContent());
        post.setContentFormat(dto.getContentFormat() == null ? ContentFormat.MARKDOWN : dto.getContentFormat());

        // 业务规则：用户发帖默认进入待审核状态；审核通过（切到 PUBLISHED）时再补齐 publishedAt
        post.setStatus(PostStatus.PENDING);
        post.setPublishedAt(null);

        post.setIsDeleted(false);
        post.setMetadata(dto.getMetadata());

        post = postsRepository.save(post);

        try {
            List<String> suggested = aiPostRiskTagService.suggestRiskTags(post.getTitle(), post.getContent());
            if (suggested != null && !suggested.isEmpty()) {
                for (String raw : suggested) {
                    String slug = normalizeRiskTagSlug(raw);
                    if (slug == null || slug.isBlank()) continue;

                    TagsEntity tag = tagsRepository.findByTenantIdAndTypeAndSlug(1L, TagType.RISK, slug).orElse(null);
                    if (tag == null) {
                        TagsEntity created = new TagsEntity();
                        created.setTenantId(1L);
                        created.setType(TagType.RISK);
                        created.setName(normalizeRiskTagName(raw, slug));
                        created.setSlug(slug);
                        created.setDescription(null);
                        created.setIsSystem(false);
                        created.setIsActive(true);
                        created.setCreatedAt(LocalDateTime.now());
                        tag = tagsRepository.save(created);
                    }

                    if (riskLabelingRepository.findByTargetTypeAndTargetIdAndTagIdAndSource(ContentType.POST, post.getId(), tag.getId(), Source.LLM).isEmpty()) {
                        RiskLabelingEntity rl = new RiskLabelingEntity();
                        rl.setTargetType(ContentType.POST);
                        rl.setTargetId(post.getId());
                        rl.setTagId(tag.getId());
                        rl.setSource(Source.LLM);
                        rl.setConfidence(null);
                        rl.setCreatedAt(LocalDateTime.now());
                        riskLabelingRepository.save(rl);
                    }
                }
            }
        } catch (Exception ignore) {
        }

        // 新增：写入审核队列（防重复）
        adminModerationQueueService.ensureEnqueuedPost(post.getId());

        try {
            moderationRuleAutoRunner.runOnce();
            moderationVecAutoRunner.runOnce();
            moderationLlmAutoRunner.runOnce();
        } catch (Exception ignore) {
        }

        List<Long> attachmentIds = dto.getAttachmentIds();
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            // de-dup while preserving order
            LinkedHashSet<Long> uniq = new LinkedHashSet<>(attachmentIds);
            for (Long id : uniq) {
                if (id == null) continue;
                FileAssetsEntity fa = fileAssetsRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("附件不存在: " + id));

                // ownership check
                Long ownerId = fa.getOwner() == null ? null : fa.getOwner().getId();
                if (ownerId == null || !ownerId.equals(me)) {
                    throw new IllegalArgumentException("无权使用该附件: " + id);
                }
                if (fa.getStatus() != FileAssetStatus.READY) {
                    throw new IllegalArgumentException("附件状态不可用: " + id);
                }

                String fileName = fa.getUrl();
                if (fileName != null) {
                    int q = fileName.indexOf('?');
                    if (q >= 0) fileName = fileName.substring(0, q);
                    int h = fileName.indexOf('#');
                    if (h >= 0) fileName = fileName.substring(0, h);
                    int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
                    fileName = slash >= 0 ? fileName.substring(slash + 1) : fileName;
                }
                fileName = (fileName == null ? "" : fileName);
                fileName = fileName.replaceAll("[\\r\\n\\t]+", " ").trim();
                fileName = fileName.replaceAll("[\\\\/]+", "_");
                if (fileName.isBlank()) {
                    fileName = "file";
                }
                if (fileName.length() > 512) {
                    fileName = fileName.substring(fileName.length() - 512);
                }

                PostAttachmentsEntity pa = new PostAttachmentsEntity();
                pa.setPostId(post.getId());
                pa.setFileAssetId(fa.getId());
                pa.setUrl(fa.getUrl());
                pa.setFileName(fileName);
                pa.setMimeType(fa.getMimeType());
                pa.setSizeBytes(fa.getSizeBytes());
                pa.setCreatedAt(LocalDateTime.now());
                postAttachmentsRepository.save(pa);
            }
        }

        try {
            aiPostSummaryTriggerService.scheduleGenerateAfterCommit(post.getId(), me);
        } catch (Exception ignore) {
        }

        return post;
    }

    private static String normalizeRiskTagName(String raw, String slug) {
        String n = raw == null ? "" : raw.trim();
        if (n.isBlank()) n = slug;
        n = n.replaceAll("[\\r\\n\\t]+", " ");
        if (n.length() > 64) n = n.substring(0, 64);
        return n.trim();
    }

    private static String normalizeRiskTagSlug(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isBlank()) return null;
        s = s.replace('_', '-');
        s = s.replaceAll("[^a-z0-9\\-\\s]+", "");
        s = s.replaceAll("\\s+", "-");
        s = s.replaceAll("-{2,}", "-");
        s = s.replaceAll("^-+|-+$", "");
        if (s.length() > 96) s = s.substring(0, 96);
        s = s.replaceAll("^-+|-+$", "");
        return s.isBlank() ? null : s;
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

        // Only allow sorting by real PostsEntity properties to avoid PropertyReferenceException (500).
        // Also keep a couple of legacy/portal-friendly aliases.
        String rawSortBy = (sortBy == null ? "" : sortBy.trim());
        String normalizedSortBy;
        if (rawSortBy.isEmpty()) {
            normalizedSortBy = "createdAt";
        } else if ("hotScore".equalsIgnoreCase(rawSortBy) || "hot_score".equalsIgnoreCase(rawSortBy)) {
            // hotScore is not a PostsEntity field. Map to a safe default for now.
            normalizedSortBy = "createdAt";
        } else if ("created_at".equalsIgnoreCase(rawSortBy)) {
            normalizedSortBy = "createdAt";
        } else if ("updated_at".equalsIgnoreCase(rawSortBy)) {
            normalizedSortBy = "updatedAt";
        } else if ("published_at".equalsIgnoreCase(rawSortBy)) {
            normalizedSortBy = "publishedAt";
        } else {
            normalizedSortBy = rawSortBy;
        }

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

        String safeSortBy = allowedSortBy.contains(normalizedSortBy) ? normalizedSortBy : "createdAt";
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

    private Page<PostsEntity> vectorSearchPublished(String keyword, Long boardId, Long authorId, LocalDate createdFrom, LocalDate createdTo, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        int fetchLimit = Math.min(500, Math.max(50, (safePage + 1) * safePageSize * 5));

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

        Map<Long, PostsEntity> byId = new HashMap<>();
        for (PostsEntity p : postsRepository.findByIdInAndIsDeletedFalseAndStatus(slice, PostStatus.PUBLISHED)) {
            if (p == null || p.getId() == null) continue;
            byId.put(p.getId(), p);
        }

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

        PostsEntity post = postsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + id));

        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new IllegalArgumentException("帖子已删除: " + id);
        }

        // 业务规则：从非发布状态切到 PUBLISHED 时，补齐 publishedAt
        if (status == PostStatus.PUBLISHED && post.getPublishedAt() == null) {
            post.setPublishedAt(LocalDateTime.now());
        }
        post.setStatus(status);

        PostsEntity saved = postsRepository.save(post);
        ragPostIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());
        return saved;
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

        Long me = currentUserIdOrThrow();

        PostsEntity post = postsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + id));

        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new IllegalArgumentException("帖子已删除: " + id);
        }

        boolean isAuthor = post.getAuthorId() != null && post.getAuthorId().equals(me);
        if (!isAuthor) {
            throw new IllegalArgumentException("无权编辑该帖子");
        }

        if (dto.getBoardId() == null) {
            throw new IllegalArgumentException("boardId 不能为空");
        }
        if (!boardAccessControlService.canPostBoard(dto.getBoardId(), boardAccessControlService.currentUserRoleIds())) {
            throw new AccessDeniedException("无权在该版块发帖");
        }

        post.setBoardId(dto.getBoardId());
        post.setTitle(dto.getTitle() == null ? "" : dto.getTitle().trim());
        post.setContent(dto.getContent());
        post.setContentFormat(dto.getContentFormat() == null ? ContentFormat.MARKDOWN : dto.getContentFormat());
        post.setMetadata(dto.getMetadata());

        // sync attachments: keep it simple - replace all
        postAttachmentsRepository.deleteByPostId(post.getId());

        List<Long> attachmentIds = dto.getAttachmentIds();
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            LinkedHashSet<Long> uniq = new LinkedHashSet<>(attachmentIds);
            for (Long faId : uniq) {
                if (faId == null) continue;
                FileAssetsEntity fa = fileAssetsRepository.findById(faId)
                        .orElseThrow(() -> new IllegalArgumentException("附件不存在: " + faId));

                Long ownerId = fa.getOwner() == null ? null : fa.getOwner().getId();
                if (ownerId == null || !ownerId.equals(me)) {
                    throw new IllegalArgumentException("无权使用该附件: " + faId);
                }
                if (fa.getStatus() != FileAssetStatus.READY) {
                    throw new IllegalArgumentException("附件状态不可用: " + faId);
                }

                String fileName = fa.getUrl();
                if (fileName != null) {
                    int q = fileName.indexOf('?');
                    if (q >= 0) fileName = fileName.substring(0, q);
                    int h = fileName.indexOf('#');
                    if (h >= 0) fileName = fileName.substring(0, h);
                    int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
                    fileName = slash >= 0 ? fileName.substring(slash + 1) : fileName;
                }
                fileName = (fileName == null ? "" : fileName);
                fileName = fileName.replaceAll("[\\r\\n\\t]+", " ").trim();
                fileName = fileName.replaceAll("[\\\\/]+", "_");
                if (fileName.isBlank()) {
                    fileName = "file";
                }
                if (fileName.length() > 512) {
                    fileName = fileName.substring(fileName.length() - 512);
                }

                PostAttachmentsEntity pa = new PostAttachmentsEntity();
                pa.setPostId(post.getId());
                pa.setFileAssetId(fa.getId());
                pa.setUrl(fa.getUrl());
                pa.setFileName(fileName);
                pa.setMimeType(fa.getMimeType());
                pa.setSizeBytes(fa.getSizeBytes());
                pa.setCreatedAt(LocalDateTime.now());
                postAttachmentsRepository.save(pa);
            }
        }

        PostsEntity saved = postsRepository.save(post);
        if (saved.getStatus() == PostStatus.PUBLISHED && !Boolean.TRUE.equals(saved.getIsDeleted())) {
            ragPostIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());
        }
        return saved;
    }
}
