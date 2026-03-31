package com.example.EnterpriseRagCommunity.service.content.admin.impl;

import com.example.EnterpriseRagCommunity.dto.content.admin.CommentAdminDTO;
import com.example.EnterpriseRagCommunity.dto.content.admin.CommentSetDeletedRequest;
import com.example.EnterpriseRagCommunity.dto.content.admin.CommentUpdateStatusRequest;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.content.admin.AdminCommentsService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentIndexVisibilitySyncService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class AdminCommentsServiceImpl implements AdminCommentsService {

    private static final Logger log = LoggerFactory.getLogger(AdminCommentsServiceImpl.class);

    @Autowired
    private CommentsRepository commentsRepository;

    @Autowired
    private AdministratorService administratorService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private RagCommentIndexVisibilitySyncService ragCommentIndexVisibilitySyncService;

    @Autowired
    private AuditLogWriter auditLogWriter;

    @Autowired
    private AuditDiffBuilder auditDiffBuilder;

    private static String buildPostExcerpt(String content, int maxLen) {
        if (content == null) return null;
        String s = content
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll("\\s+", " ")
                .trim();
        if (s.isBlank()) return "";
        if (maxLen <= 0) return s;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "…";
    }

    private static CommentAdminDTO toAdminDTO(CommentsEntity e,
                                            String authorName,
                                            String postTitle,
                                            String postExcerpt) {
        CommentAdminDTO dto = new CommentAdminDTO();
        dto.setId(e.getId());
        dto.setPostId(e.getPostId());
        dto.setParentId(e.getParentId());
        dto.setAuthorId(e.getAuthorId());
        dto.setAuthorName(authorName);
        dto.setContent(e.getContent());
        dto.setStatus(e.getStatus() == null ? null : e.getStatus().name());
        dto.setIsDeleted(Boolean.TRUE.equals(e.getIsDeleted()));
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setPostTitle(postTitle);
        dto.setPostExcerpt(postExcerpt);
        return dto;
    }

    private String safeAuthorName(Long authorId) {
        if (authorId == null) return null;
        try {
            return administratorService.findById(authorId)
                    .map(u -> {
                        if (u.getUsername() != null && !u.getUsername().isBlank()) return u.getUsername();
                        return u.getEmail();
                    })
                    .orElse(null);
        } catch (Exception ignored) {
            // 管理列表不要因为作者信息缺失而失败
            return null;
        }
    }

    private CommentStatus parseStatusOrNull(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return CommentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Ignore invalid comment status filter: {}", status);
            return null;
        }
    }

    private Map<Long, PostsEntity> loadPostsByIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return Collections.emptyMap();
        List<Long> ids = postIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Collections.emptyMap();
        // Use findAllById (may include deleted posts too) to keep admin display consistent.
        List<PostsEntity> posts = postsRepository.findAllById(ids);
        Map<Long, PostsEntity> m = new HashMap<>();
        for (PostsEntity p : posts) {
            if (p != null && p.getId() != null) m.put(p.getId(), p);
        }
        return m;
    }

    @Override
    public Page<CommentAdminDTO> list(int page,
                                     int pageSize,
                                     Long postId,
                                     Long authorId,
                                     String authorName,
                                     LocalDateTime createdFrom,
                                     LocalDateTime createdTo,
                                     String status,
                                     Boolean isDeleted,
                                     String keyword) {

        int safePage = Math.max(page, 1);
        int safePageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        CommentStatus statusEnum = parseStatusOrNull(status);

        // authorName -> candidate authorIds (username/email like)
        List<Long> authorIdsByName = null;
        if (authorName != null && !authorName.isBlank()) {
            String like = "%" + authorName.trim() + "%";
            authorIdsByName = usersRepository.findAll((root, q, cb) -> {
                        var p1 = cb.like(cb.lower(root.get("username")), like.toLowerCase());
                        var p2 = cb.like(cb.lower(root.get("email")), like.toLowerCase());
                        // soft-delete aware
                        var p3 = cb.isFalse(root.get("isDeleted"));
                        return cb.and(p3, cb.or(p1, p2));
                    })
                    .stream()
                    .map(u -> u.getId())
                    .toList();
            // no match -> return empty quickly
            if (authorIdsByName.isEmpty()) {
                return Page.empty(pageable);
            }
        }

        final List<Long> authorIdsByNameFinal = authorIdsByName;

        Specification<CommentsEntity> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (postId != null) {
                predicates.add(cb.equal(root.get("postId"), postId));
            }
            if (authorId != null) {
                predicates.add(cb.equal(root.get("authorId"), authorId));
            }
            if (authorIdsByNameFinal != null) {
                predicates.add(root.get("authorId").in(authorIdsByNameFinal));
            }
            if (statusEnum != null) {
                predicates.add(cb.equal(root.get("status"), statusEnum));
            }
            if (isDeleted != null) {
                if (Boolean.FALSE.equals(isDeleted)) {
                    // 兼容历史数据 is_deleted = NULL
                    predicates.add(cb.or(cb.isNull(root.get("isDeleted")), cb.isFalse(root.get("isDeleted"))));
                } else {
                    predicates.add(cb.isTrue(root.get("isDeleted")));
                }
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }
            if (keyword != null && !keyword.isBlank()) {
                String trimmed = keyword.trim();
                String like = "%" + trimmed + "%";
                List<jakarta.persistence.criteria.Predicate> orPredicates = new ArrayList<>();
                orPredicates.add(cb.like(root.get("content"), like));
                // 增强：纯数字 keyword
                if (trimmed.matches("\\d+")) {
                    try {
                        Long n = Long.parseLong(trimmed);
                        orPredicates.add(cb.equal(root.get("id"), n));
                        orPredicates.add(cb.equal(root.get("authorId"), n));
                        orPredicates.add(cb.equal(root.get("postId"), n));
                    } catch (NumberFormatException ignored) {
                        // ignore
                    }
                }
                predicates.add(cb.or(orPredicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
            }

            if (!predicates.isEmpty()) {
                if (query != null) {
                    query.where(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                }
            }
            return query.getRestriction();
        };

        Page<CommentsEntity> entityPage = commentsRepository.findAll(spec, pageable);

        // Batch load related posts for current page
        Set<Long> postIds = new HashSet<>();
        for (CommentsEntity e : entityPage.getContent()) {
            if (e != null && e.getPostId() != null) postIds.add(e.getPostId());
        }
        Map<Long, PostsEntity> postsById = loadPostsByIds(postIds);

        return entityPage.map(e -> {
            PostsEntity p = e.getPostId() == null ? null : postsById.get(e.getPostId());
            String title = p == null ? null : p.getTitle();
            String excerpt = p == null ? null : buildPostExcerpt(p.getContent(), 80);
            return toAdminDTO(e, safeAuthorName(e.getAuthorId()), title, excerpt);
        });
    }

    @Override
    @Transactional
    public CommentAdminDTO updateStatus(Long id, CommentUpdateStatusRequest req) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        if (req == null) throw new IllegalArgumentException("参数不能为空");

        CommentStatus status = parseStatusOrNull(req.getStatus());
        if (status == null) throw new IllegalArgumentException("status 不能为空");

        CommentsEntity e = commentsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("评论不存在: " + id));
        Map<String, Object> before = summarizeForAudit(e);

        e.setStatus(status);
        e.setUpdatedAt(LocalDateTime.now());

        CommentsEntity saved = commentsRepository.save(e);
        ragCommentIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());
        auditLogWriter.write(
                currentUserIdOrNull(),
                currentActorNameOrNull(),
                "COMMENT_STATUS_UPDATE",
                "COMMENT",
                saved.getId(),
                AuditResult.SUCCESS,
                "更新评论状态",
                null,
                auditDiffBuilder.build(before, summarizeForAudit(saved))
        );
        // try fill post info for single item as well
        PostsEntity p = saved.getPostId() == null ? null : postsRepository.findById(saved.getPostId()).orElse(null);
        return toAdminDTO(saved,
                safeAuthorName(saved.getAuthorId()),
                p == null ? null : p.getTitle(),
                p == null ? null : buildPostExcerpt(p.getContent(), 80));
    }

    @Override
    @Transactional
    public CommentAdminDTO setDeleted(Long id, CommentSetDeletedRequest req) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        if (req == null) throw new IllegalArgumentException("参数不能为空");
        if (req.getIsDeleted() == null) throw new IllegalArgumentException("isDeleted 不能为空");

        CommentsEntity e = commentsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("评论不存在: " + id));
        Map<String, Object> before = summarizeForAudit(e);

        e.setIsDeleted(req.getIsDeleted());
        e.setUpdatedAt(LocalDateTime.now());

        CommentsEntity saved = commentsRepository.save(e);
        ragCommentIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());
        auditLogWriter.write(
                currentUserIdOrNull(),
                currentActorNameOrNull(),
                "COMMENT_DELETE_TOGGLE",
                "COMMENT",
                saved.getId(),
                AuditResult.SUCCESS,
                "更新评论删除标记",
                null,
                auditDiffBuilder.build(before, summarizeForAudit(saved))
        );
        PostsEntity p = saved.getPostId() == null ? null : postsRepository.findById(saved.getPostId()).orElse(null);
        return toAdminDTO(saved,
                safeAuthorName(saved.getAuthorId()),
                p == null ? null : p.getTitle(),
                p == null ? null : buildPostExcerpt(p.getContent(), 80));
    }

    private static Map<String, Object> summarizeForAudit(CommentsEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (e == null) return m;
        m.put("id", e.getId());
        m.put("postId", e.getPostId());
        m.put("parentId", e.getParentId());
        m.put("authorId", e.getAuthorId());
        m.put("status", e.getStatus() == null ? null : e.getStatus().name());
        m.put("isDeleted", Boolean.TRUE.equals(e.getIsDeleted()));
        return m;
    }

    private Long currentUserIdOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        String email = auth.getName();
        return administratorService.findByUsername(email).map(x -> x.getId()).orElse(null);
    }

    private String currentActorNameOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
        String name = auth.getName();
        return name == null || name.isBlank() ? null : name.trim();
    }
}
