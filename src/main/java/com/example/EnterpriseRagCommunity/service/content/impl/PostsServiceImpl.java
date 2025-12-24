package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostsPublishDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity;
import com.example.EnterpriseRagCommunity.entity.monitor.enums.FileAssetStatus;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.content.PostsService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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

        PostsEntity post = new PostsEntity();
        post.setTenantId(null);
        post.setBoardId(dto.getBoardId());
        post.setAuthorId(me);
        post.setTitle(dto.getTitle() == null ? "" : dto.getTitle().trim());
        post.setContent(dto.getContent());
        post.setContentFormat(dto.getContentFormat() == null ? ContentFormat.MARKDOWN : dto.getContentFormat());
        post.setStatus(PostStatus.PUBLISHED);
        post.setPublishedAt(LocalDateTime.now());
        post.setIsDeleted(false);
        post.setMetadata(dto.getMetadata());

        post = postsRepository.save(post);

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
                    int slash = fileName.lastIndexOf('/');
                    fileName = slash >= 0 ? fileName.substring(slash + 1) : fileName;
                }
                if (fileName == null || fileName.isBlank()) {
                    fileName = "file";
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

        return post;
    }

    private static boolean isLikelyFullTextUnfriendlyKeyword(String kw) {
        if (kw == null) return true;
        String s = kw.trim();
        if (s.isEmpty()) return true;
        // MySQL FULLTEXT often ignores very short tokens (e.g. < 3) or numeric-only tokens.
        if (s.length() < 3) return true;
        boolean allDigits = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c)) {
                allDigits = false;
                break;
            }
        }
        return allDigits;
    }

    private static String escapeForLike(String raw) {
        if (raw == null) return null;
        // ESCAPE '\\' in SQL; here we escape backslash first, then % and _.
        return raw.replace("\\\\", "\\\\\\\\")
                .replace("%", "\\\\%")
                .replace("_", "\\\\_");
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

        String safeSortBy = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortOrderDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize, Sort.by(direction, safeSortBy));

        // 1) ID search has the highest priority
        if (postId != null) {
            PostsEntity p = postsRepository.findByIdAndIsDeletedFalse(postId).orElse(null);
            List<PostsEntity> content = (p == null) ? java.util.Collections.emptyList() : java.util.Collections.singletonList(p);
            // Keep response shape consistent with Page, and mirror requested paging.
            return new PageImpl<>(content, PageRequest.of(safePage - 1, safePageSize), content.size());
        }

        String mode = (searchMode == null || searchMode.isBlank()) ? "AUTO" : searchMode.trim().toUpperCase(Locale.ROOT);

        // 2) Keyword search path (FULLTEXT / LIKE / AUTO)
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            Pageable unsortedPageable = PageRequest.of(safePage - 1, safePageSize);

            if ("LIKE".equals(mode) || ("AUTO".equals(mode) && isLikelyFullTextUnfriendlyKeyword(kw))) {
                String escaped = escapeForLike(kw);
                return postsRepository.searchLikeOrderByCreatedAtDesc(escaped, unsortedPageable);
            }

            // default FULLTEXT
            String q = kw;
            if (!q.endsWith("*")) {
                q = q + "*";
            }
            return postsRepository.searchFullTextOrderByCreatedAtDesc(q, unsortedPageable);
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

        return postsRepository.save(post);
    }

    @Override
    public PostsEntity getById(Long id) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        return postsRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + id));
    }
}
