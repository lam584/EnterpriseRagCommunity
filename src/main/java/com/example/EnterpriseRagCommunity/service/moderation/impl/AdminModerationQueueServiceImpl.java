package com.example.EnterpriseRagCommunity.service.moderation.impl;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBackfillRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBackfillResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueItemDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationQueueQueryDTO;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AdminModerationQueueServiceImpl implements AdminModerationQueueService {

    @Autowired
    private ModerationQueueRepository moderationQueueRepository;

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private CommentsRepository commentsRepository;

    @Override
    public Page<AdminModerationQueueItemDTO> list(ModerationQueueQueryDTO query) {
        if (query == null) query = new ModerationQueueQueryDTO();

        // 提前提取 query 字段，避免在 lambda 中捕获非 final/非实际上 final 的局部变量
        final Long qId = query.getId();
        final ContentType qContentType = query.getContentType();
        final Long qContentId = query.getContentId();
        final QueueStatus qStatus = query.getStatus();
        final QueueStage qCurrentStage = query.getCurrentStage();
        final Long qAssignedToId = query.getAssignedToId();
        final Integer qMinPriority = query.getMinPriority();
        final Integer qMaxPriority = query.getMaxPriority();
        final LocalDateTime qCreatedFrom = query.getCreatedFrom();
        final LocalDateTime qCreatedTo = query.getCreatedTo();
        final LocalDateTime qUpdatedFrom = query.getUpdatedFrom();
        final LocalDateTime qUpdatedTo = query.getUpdatedTo();

        int page = query.getPageNum() == null ? 1 : Math.max(query.getPageNum(), 1);
        int pageSize = query.getPageSize() == null ? 20 : Math.min(Math.max(query.getPageSize(), 1), 200);

        Sort sort = Sort.by(Sort.Order.desc("priority"), Sort.Order.asc("createdAt"));
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);

        Specification<ModerationQueueEntity> spec = (root, q, cb) -> cb.conjunction();

        if (qId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("id"), qId));
        }
        if (qContentType != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("contentType"), qContentType));
        }
        if (qContentId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("contentId"), qContentId));
        }
        if (qStatus != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), qStatus));
        }
        if (qCurrentStage != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("currentStage"), qCurrentStage));
        }
        if (qAssignedToId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("assignedToId"), qAssignedToId));
        }
        if (qMinPriority != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("priority"), qMinPriority));
        }
        if (qMaxPriority != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("priority"), qMaxPriority));
        }
        if (qCreatedFrom != null || qCreatedTo != null) {
            LocalDateTime start = qCreatedFrom == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : qCreatedFrom;
            LocalDateTime end = qCreatedTo == null ? LocalDateTime.now().plusYears(100) : qCreatedTo;
            spec = spec.and((root, q, cb) -> cb.between(root.get("createdAt"), start, end));
        }
        if (qUpdatedFrom != null || qUpdatedTo != null) {
            LocalDateTime start = qUpdatedFrom == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : qUpdatedFrom;
            LocalDateTime end = qUpdatedTo == null ? LocalDateTime.now().plusYears(100) : qUpdatedTo;
            spec = spec.and((root, q, cb) -> cb.between(root.get("updatedAt"), start, end));
        }

        Page<ModerationQueueEntity> entityPage = moderationQueueRepository.findAll(spec, pageable);

        // Batch load posts/comments for summaries
        Set<Long> postIds = new HashSet<>();
        Set<Long> commentIds = new HashSet<>();
        for (ModerationQueueEntity e : entityPage.getContent()) {
            if (e == null) continue;
            if (e.getContentType() == ContentType.POST) postIds.add(e.getContentId());
            if (e.getContentType() == ContentType.COMMENT) commentIds.add(e.getContentId());
        }

        Map<Long, PostsEntity> postsById = loadPostsByIds(postIds);
        Map<Long, CommentsEntity> commentsById = loadCommentsByIds(commentIds);

        // For comments, also load their post titles
        Set<Long> commentPostIds = new HashSet<>();
        for (CommentsEntity c : commentsById.values()) {
            if (c != null && c.getPostId() != null) commentPostIds.add(c.getPostId());
        }
        Map<Long, PostsEntity> commentPostsById = loadPostsByIds(commentPostIds);

        return entityPage.map(e -> toItemDTO(e, postsById.get(e.getContentId()), commentsById.get(e.getContentId()), commentPostsById));
    }

    @Override
    public AdminModerationQueueDetailDTO getDetail(Long id) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        ModerationQueueEntity q = moderationQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核任务不存在: " + id));

        AdminModerationQueueDetailDTO dto = baseDetail(q);

        if (q.getContentType() == ContentType.POST) {
            PostsEntity p = postsRepository.findById(q.getContentId()).orElse(null);
            dto.setSummary(buildPostSummary(p));
            dto.setPost(toPostContent(p));
        } else if (q.getContentType() == ContentType.COMMENT) {
            CommentsEntity c = commentsRepository.findById(q.getContentId()).orElse(null);
            PostsEntity post = (c == null || c.getPostId() == null) ? null : postsRepository.findById(c.getPostId()).orElse(null);
            dto.setSummary(buildCommentSummary(c, post));
            dto.setComment(toCommentContent(c));
        }

        return dto;
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO approve(Long id, String reason) {
        ModerationQueueEntity q = moderationQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核任务不存在: " + id));

        if (q.getStatus() == QueueStatus.APPROVED || q.getStatus() == QueueStatus.REJECTED) {
            // 幂等：直接返回最新详情
            return getDetail(id);
        }
        if (q.getContentType() == ContentType.POST) {
            PostsEntity post = postsRepository.findById(q.getContentId())
                    .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + q.getContentId()));
            if (Boolean.TRUE.equals(post.getIsDeleted())) throw new IllegalArgumentException("帖子已删除: " + post.getId());
            post.setStatus(PostStatus.PUBLISHED);
            if (post.getPublishedAt() == null) post.setPublishedAt(LocalDateTime.now());
            postsRepository.save(post);
        } else if (q.getContentType() == ContentType.COMMENT) {
            CommentsEntity c = commentsRepository.findById(q.getContentId())
                    .orElseThrow(() -> new IllegalArgumentException("评论不存在: " + q.getContentId()));
            if (Boolean.TRUE.equals(c.getIsDeleted())) throw new IllegalArgumentException("评论已删除: " + c.getId());
            c.setStatus(CommentStatus.VISIBLE);
            c.setUpdatedAt(LocalDateTime.now());
            commentsRepository.save(c);
        }

        q.setStatus(QueueStatus.APPROVED);
        q.setUpdatedAt(LocalDateTime.now());
        moderationQueueRepository.save(q);

        return getDetail(id);
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO reject(Long id, String reason) {
        ModerationQueueEntity q = moderationQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核任务不存在: " + id));

        if (q.getStatus() == QueueStatus.APPROVED || q.getStatus() == QueueStatus.REJECTED) {
            return getDetail(id);
        }

        if (q.getContentType() == ContentType.POST) {
            PostsEntity post = postsRepository.findById(q.getContentId())
                    .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + q.getContentId()));
            if (Boolean.TRUE.equals(post.getIsDeleted())) throw new IllegalArgumentException("帖子已删除: " + post.getId());
            post.setStatus(PostStatus.REJECTED);
            postsRepository.save(post);
        } else if (q.getContentType() == ContentType.COMMENT) {
            CommentsEntity c = commentsRepository.findById(q.getContentId())
                    .orElseThrow(() -> new IllegalArgumentException("评论不存在: " + q.getContentId()));
            if (Boolean.TRUE.equals(c.getIsDeleted())) throw new IllegalArgumentException("评论已删除: " + c.getId());
            c.setStatus(CommentStatus.REJECTED);
            c.setUpdatedAt(LocalDateTime.now());
            commentsRepository.save(c);
        }

        q.setStatus(QueueStatus.REJECTED);
        q.setUpdatedAt(LocalDateTime.now());
        moderationQueueRepository.save(q);

        return getDetail(id);
    }

    @Override
    @Transactional
    public void ensureEnqueuedPost(Long postId) {
        if (postId == null) return;
        moderationQueueRepository.findByContentTypeAndContentId(ContentType.POST, postId)
                .orElseGet(() -> {
                    ModerationQueueEntity e = new ModerationQueueEntity();
                    e.setContentType(ContentType.POST);
                    e.setContentId(postId);
                    e.setStatus(QueueStatus.PENDING);
                    // start pipeline at RULE stage
                    e.setCurrentStage(QueueStage.RULE);
                    e.setPriority(0);
                    e.setAssignedToId(null);
                    e.setCreatedAt(LocalDateTime.now());
                    e.setUpdatedAt(LocalDateTime.now());
                    return moderationQueueRepository.save(e);
                });
    }

    @Override
    @Transactional
    public void ensureEnqueuedComment(Long commentId) {
        if (commentId == null) return;
        moderationQueueRepository.findByContentTypeAndContentId(ContentType.COMMENT, commentId)
                .orElseGet(() -> {
                    ModerationQueueEntity e = new ModerationQueueEntity();
                    e.setContentType(ContentType.COMMENT);
                    e.setContentId(commentId);
                    e.setStatus(QueueStatus.PENDING);
                    // start pipeline at RULE stage
                    e.setCurrentStage(QueueStage.RULE);
                    e.setPriority(0);
                    e.setAssignedToId(null);
                    e.setCreatedAt(LocalDateTime.now());
                    e.setUpdatedAt(LocalDateTime.now());
                    return moderationQueueRepository.save(e);
                });
    }

    @Override
    @Transactional
    public AdminModerationQueueBackfillResponse backfill(AdminModerationQueueBackfillRequest req) {
        AdminModerationQueueBackfillResponse resp = new AdminModerationQueueBackfillResponse();

        // defaults
        boolean dryRun = req != null && Boolean.TRUE.equals(req.getDryRun());
        int limit = req != null && req.getLimit() != null ? req.getLimit() : 500;
        limit = Math.min(Math.max(limit, 1), 5000);

        Set<ContentType> types = new HashSet<>();
        if (req == null || req.getContentTypes() == null || req.getContentTypes().isEmpty()) {
            types.add(ContentType.POST);
            types.add(ContentType.COMMENT);
        } else {
            types.addAll(req.getContentTypes());
        }

        LocalDateTime createdFrom = req == null ? null : req.getCreatedFrom();
        LocalDateTime createdTo = req == null ? null : req.getCreatedTo();
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new IllegalArgumentException("createdFrom 不能晚于 createdTo");
        }

        int remaining = limit;

        // ---- posts ----
        if (types.contains(ContentType.POST) && remaining > 0) {
            List<Long> ids = postsRepository.findIdsByStatusAndIsDeletedFalse(PostStatus.PENDING);
            resp.setScannedPosts(ids == null ? 0 : ids.size());
            if (ids != null) {
                for (Long id : ids) {
                    if (id == null) continue;
                    if (remaining <= 0) break;

                    PostsEntity p = postsRepository.findById(id).orElse(null);
                    if (p == null) {
                        resp.setSkipped(resp.getSkipped() + 1);
                        continue;
                    }
                    if (Boolean.TRUE.equals(p.getIsDeleted())) {
                        resp.setSkipped(resp.getSkipped() + 1);
                        continue;
                    }
                    if (p.getStatus() != PostStatus.PENDING) {
                        resp.setSkipped(resp.getSkipped() + 1);
                        continue;
                    }
                    if (!withinWindow(p.getCreatedAt(), createdFrom, createdTo)) {
                        resp.setSkipped(resp.getSkipped() + 1);
                        continue;
                    }

                    boolean exists = moderationQueueRepository.findByContentTypeAndContentId(ContentType.POST, p.getId()).isPresent();
                    if (exists) {
                        resp.setAlreadyQueued(resp.getAlreadyQueued() + 1);
                        continue;
                    }

                    if (!dryRun) {
                        ensureEnqueuedPost(p.getId());
                    }
                    resp.setEnqueued(resp.getEnqueued() + 1);
                    remaining--;
                }
            }
        }

        // ---- comments ----
        if (types.contains(ContentType.COMMENT) && remaining > 0) {
            Specification<CommentsEntity> spec = (root, q, cb) -> cb.conjunction();
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), CommentStatus.PENDING));
            spec = spec.and((root, q, cb) -> cb.isFalse(root.get("isDeleted")));
            if (createdFrom != null) {
                spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }

            // 按创建时间升序扫描，更符合“先来先审”
            Pageable pageable = PageRequest.of(0, Math.min(remaining, 1000), Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
            Page<CommentsEntity> page = commentsRepository.findAll(spec, pageable);

            resp.setScannedComments((int) page.getTotalElements());

            for (CommentsEntity c : page.getContent()) {
                if (c == null || c.getId() == null) {
                    resp.setSkipped(resp.getSkipped() + 1);
                    continue;
                }
                if (remaining <= 0) break;

                boolean exists = moderationQueueRepository.findByContentTypeAndContentId(ContentType.COMMENT, c.getId()).isPresent();
                if (exists) {
                    resp.setAlreadyQueued(resp.getAlreadyQueued() + 1);
                    continue;
                }

                if (!dryRun) {
                    ensureEnqueuedComment(c.getId());
                }
                resp.setEnqueued(resp.getEnqueued() + 1);
                remaining--;
            }
        }

        return resp;
    }

    private boolean withinWindow(LocalDateTime t, LocalDateTime from, LocalDateTime to) {
        if (t == null) return true;
        if (from != null && t.isBefore(from)) return false;
        if (to != null && t.isAfter(to)) return false;
        return true;
    }

    private Map<Long, PostsEntity> loadPostsByIds(Set<Long> ids) {
        Map<Long, PostsEntity> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) return map;
        postsRepository.findAllById(ids).forEach(p -> map.put(p.getId(), p));
        return map;
    }

    private Map<Long, CommentsEntity> loadCommentsByIds(Set<Long> ids) {
        Map<Long, CommentsEntity> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) return map;
        commentsRepository.findAllById(ids).forEach(c -> map.put(c.getId(), c));
        return map;
    }

    private AdminModerationQueueItemDTO toItemDTO(ModerationQueueEntity q, PostsEntity post, CommentsEntity comment, Map<Long, PostsEntity> commentPostsById) {
        AdminModerationQueueItemDTO dto = new AdminModerationQueueItemDTO();
        dto.setId(q.getId());
        dto.setContentType(q.getContentType());
        dto.setContentId(q.getContentId());
        dto.setStatus(q.getStatus());
        dto.setCurrentStage(q.getCurrentStage());
        dto.setPriority(q.getPriority());
        dto.setAssignedToId(q.getAssignedToId());
        dto.setCreatedAt(q.getCreatedAt());
        dto.setUpdatedAt(q.getUpdatedAt());

        if (q.getContentType() == ContentType.POST) {
            dto.setSummary(buildPostSummary(post));
        } else if (q.getContentType() == ContentType.COMMENT) {
            PostsEntity p = null;
            if (comment != null && comment.getPostId() != null) p = commentPostsById.get(comment.getPostId());
            dto.setSummary(buildCommentSummary(comment, p));
        }
        return dto;
    }

    private AdminModerationQueueDetailDTO baseDetail(ModerationQueueEntity q) {
        AdminModerationQueueDetailDTO dto = new AdminModerationQueueDetailDTO();
        dto.setId(q.getId());
        dto.setContentType(q.getContentType());
        dto.setContentId(q.getContentId());
        dto.setStatus(q.getStatus());
        dto.setCurrentStage(q.getCurrentStage());
        dto.setPriority(q.getPriority());
        dto.setAssignedToId(q.getAssignedToId());
        dto.setCreatedAt(q.getCreatedAt());
        dto.setUpdatedAt(q.getUpdatedAt());
        return dto;
    }

    private AdminModerationQueueItemDTO.Summary buildPostSummary(PostsEntity p) {
        if (p == null) return null;
        AdminModerationQueueItemDTO.Summary s = new AdminModerationQueueItemDTO.Summary();
        s.setTitle(p.getTitle());
        s.setSnippet(excerpt(p.getContent(), 120));
        s.setAuthorId(p.getAuthorId());
        s.setAuthorName(null);
        return s;
    }

    private AdminModerationQueueItemDTO.Summary buildCommentSummary(CommentsEntity c, PostsEntity post) {
        if (c == null) return null;
        AdminModerationQueueItemDTO.Summary s = new AdminModerationQueueItemDTO.Summary();
        s.setTitle(post == null ? null : post.getTitle());
        s.setSnippet(excerpt(c.getContent(), 120));
        s.setAuthorId(c.getAuthorId());
        s.setAuthorName(null);
        s.setPostId(c.getPostId());
        return s;
    }

    private static String excerpt(String text, int maxLen) {
        if (text == null) return null;
        String t = text.trim().replaceAll("\\s+", " ");
        if (t.length() <= maxLen) return t;
        return t.substring(0, maxLen) + "...";
    }

    private AdminModerationQueueDetailDTO.PostContent toPostContent(PostsEntity p) {
        if (p == null) return null;
        AdminModerationQueueDetailDTO.PostContent pc = new AdminModerationQueueDetailDTO.PostContent();
        pc.setId(p.getId());
        pc.setBoardId(p.getBoardId());
        pc.setAuthorId(p.getAuthorId());
        pc.setTitle(p.getTitle());
        pc.setContent(p.getContent());
        pc.setStatus(p.getStatus() == null ? null : p.getStatus().name());
        pc.setCreatedAt(p.getCreatedAt());
        return pc;
    }

    private AdminModerationQueueDetailDTO.CommentContent toCommentContent(CommentsEntity c) {
        if (c == null) return null;
        AdminModerationQueueDetailDTO.CommentContent cc = new AdminModerationQueueDetailDTO.CommentContent();
        cc.setId(c.getId());
        cc.setPostId(c.getPostId());
        cc.setParentId(c.getParentId());
        cc.setAuthorId(c.getAuthorId());
        cc.setContent(c.getContent());
        cc.setStatus(c.getStatus() == null ? null : c.getStatus().name());
        cc.setCreatedAt(c.getCreatedAt());
        return cc;
    }
}
