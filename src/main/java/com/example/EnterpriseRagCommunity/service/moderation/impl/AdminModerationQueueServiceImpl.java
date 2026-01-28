package com.example.EnterpriseRagCommunity.service.moderation.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBackfillRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBackfillResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueItemDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationQueueQueryDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Source;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.RiskLabelingService;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexVisibilitySyncService;

import jakarta.transaction.Transactional;

@Service
public class AdminModerationQueueServiceImpl implements AdminModerationQueueService {

    @Autowired
    private ModerationQueueRepository moderationQueueRepository;

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private CommentsRepository commentsRepository;

    @Autowired
    private com.example.EnterpriseRagCommunity.service.AdministratorService administratorService;

    @Autowired
    private ReportsRepository reportsRepository;

    @Autowired
    private NotificationsService notificationsService;

    @Autowired
    private ModerationPipelineRunRepository moderationPipelineRunRepository;

    @Autowired
    private ModerationRuleAutoRunner moderationRuleAutoRunner;

    @Autowired
    private ModerationVecAutoRunner moderationVecAutoRunner;

    @Autowired
    private RiskLabelingService riskLabelingService;

    @Autowired
    private RagPostIndexVisibilitySyncService ragPostIndexVisibilitySyncService;

    @Autowired
    private AuditLogWriter auditLogWriter;

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
        int pageSize = query.getPageSize() == null ? 20 : Math.min(Math.max(query.getPageSize(), 1), 500);

        String orderBy = query.getOrderBy();
        String sortDir = query.getSort();
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

        String field = "createdAt";
        if (orderBy != null) {
            String ob = orderBy.trim();
            if (ob.equals("createdAt") || ob.equals("updatedAt") || ob.equals("id") || ob.equals("priority")) {
                field = ob;
            }
        }

        Sort sort = Sort.by(new Sort.Order(direction, field));
        if (!field.equals("id")) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        }
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

        Map<Long, List<String>> postRiskTagsById = riskLabelingService.getRiskTagSlugsByTargets(ContentType.POST, postIds);
        Map<Long, List<String>> commentRiskTagsById = riskLabelingService.getRiskTagSlugsByTargets(ContentType.COMMENT, commentIds);

        return entityPage.map(e -> {
            List<String> riskTags = e.getContentType() == ContentType.POST
                    ? postRiskTagsById.getOrDefault(e.getContentId(), List.of())
                    : commentRiskTagsById.getOrDefault(e.getContentId(), List.of());
            return toItemDTO(e, postsById.get(e.getContentId()), commentsById.get(e.getContentId()), commentPostsById, riskTags);
        });
    }

    @Override
    public AdminModerationQueueDetailDTO getDetail(Long id) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        ModerationQueueEntity q = moderationQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核任务不存在: " + id));

        AdminModerationQueueDetailDTO dto = baseDetail(q);
        dto.setRiskTags(riskLabelingService.getRiskTagSlugs(q.getContentType(), q.getContentId()));

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

        try {
            ReportTargetType targetType = q.getContentType() == ContentType.POST ? ReportTargetType.POST : ReportTargetType.COMMENT;
            Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
            Page<ReportsEntity> page = reportsRepository.findByTargetTypeAndTargetId(targetType, q.getContentId(), pageable);
            List<AdminModerationQueueDetailDTO.ReportInfo> infos = new ArrayList<>();
            if (page != null && page.getContent() != null) {
                for (ReportsEntity r : page.getContent()) {
                    if (r == null) continue;
                    AdminModerationQueueDetailDTO.ReportInfo info = new AdminModerationQueueDetailDTO.ReportInfo();
                    info.setId(r.getId());
                    info.setReporterId(r.getReporterId());
                    info.setReasonCode(r.getReasonCode());
                    info.setReasonText(r.getReasonText());
                    info.setStatus(r.getStatus() == null ? null : r.getStatus().name());
                    info.setCreatedAt(r.getCreatedAt());
                    infos.add(info);
                }
            }
            dto.setReports(infos);
        } catch (Exception ignore) {
            dto.setReports(List.of());
        }

        return dto;
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO approve(Long id, String reason) {
        UsersEntity actor = currentUserOrThrow();
        return approveInternal(id, reason, actor, true, newTraceId());
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO autoApprove(Long id, String reason, String traceId) {
        return approveInternal(id, reason, null, false, traceId);
    }

    private AdminModerationQueueDetailDTO approveInternal(Long id, String reason, UsersEntity actor, boolean manual, String traceId) {
        ModerationQueueEntity q = moderationQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核任务不存在: " + id));

        if (q.getStatus() == QueueStatus.APPROVED) {
            return getDetail(id);
        }
        if (q.getStatus() == QueueStatus.REJECTED) {
            throw new IllegalStateException("该任务已驳回，不能直接通过；如需再次处理请先进入人工审核");
        }
        if (q.getCaseType() == ModerationCaseType.REPORT) {
            Long actorId = actor == null ? null : actor.getId();
            Map<Long, Integer> reportCountByReporterId = new HashMap<>();
            try {
                ReportTargetType t = q.getContentType() == ContentType.POST ? ReportTargetType.POST : ReportTargetType.COMMENT;
                List<ReportsEntity> pending = reportsRepository.findAllByTargetTypeAndTargetIdAndStatus(t, q.getContentId(), ReportStatus.PENDING);
                if (pending != null) {
                    for (ReportsEntity r : pending) {
                        if (r == null || r.getReporterId() == null) continue;
                        reportCountByReporterId.merge(r.getReporterId(), 1, Integer::sum);
                    }
                }
                reportsRepository.resolveAllPendingByTarget(t, q.getContentId(), ReportStatus.REJECTED, actorId, LocalDateTime.now(), reason == null ? "REPORT_DISMISSED" : reason);
            } catch (Exception ignore) {
            }
            try {
                if (q.getContentType() == ContentType.POST) {
                    PostsEntity post = postsRepository.findById(q.getContentId()).orElse(null);
                    if (post != null && !Boolean.TRUE.equals(post.getIsDeleted()) && post.getStatus() == PostStatus.REJECTED) {
                        post.setStatus(PostStatus.PUBLISHED);
                        if (post.getPublishedAt() == null) post.setPublishedAt(LocalDateTime.now());
                        PostsEntity saved = postsRepository.save(post);
                        ragPostIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());
                    }
                } else if (q.getContentType() == ContentType.COMMENT) {
                    CommentsEntity c = commentsRepository.findById(q.getContentId()).orElse(null);
                    if (c != null && !Boolean.TRUE.equals(c.getIsDeleted()) && c.getStatus() == CommentStatus.REJECTED) {
                        c.setStatus(CommentStatus.VISIBLE);
                        c.setUpdatedAt(LocalDateTime.now());
                        commentsRepository.save(c);
                    }
                }
            } catch (Exception ignore) {
            }
            try {
                if (!reportCountByReporterId.isEmpty()) {
                    String targetLabel = q.getContentType() == ContentType.POST ? "帖子" : "评论";
                    String resolution = reason == null ? "内容正常" : reason;
                    for (Map.Entry<Long, Integer> e : reportCountByReporterId.entrySet()) {
                        Long reporterId = e.getKey();
                        Integer cnt = e.getValue();
                        String content = "你的举报已处理：举报未核实（" + resolution + "）。目标：" + targetLabel + " #" + q.getContentId()
                                + (cnt != null && cnt > 1 ? "（本次合并 " + cnt + " 条举报）" : "");
                        notificationsService.createNotification(reporterId, "REPORT", "举报结果通知", content);
                    }
                }
            } catch (Exception ignore) {
            }
        } else {
            if (q.getContentType() == ContentType.POST) {
                PostsEntity post = postsRepository.findById(q.getContentId())
                        .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + q.getContentId()));
                if (Boolean.TRUE.equals(post.getIsDeleted())) throw new IllegalArgumentException("帖子已删除: " + post.getId());
                post.setStatus(PostStatus.PUBLISHED);
                if (post.getPublishedAt() == null) post.setPublishedAt(LocalDateTime.now());
                PostsEntity saved = postsRepository.save(post);
                ragPostIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());
            } else if (q.getContentType() == ContentType.COMMENT) {
                CommentsEntity c = commentsRepository.findById(q.getContentId())
                        .orElseThrow(() -> new IllegalArgumentException("评论不存在: " + q.getContentId()));
                if (Boolean.TRUE.equals(c.getIsDeleted())) throw new IllegalArgumentException("评论已删除: " + c.getId());
                c.setStatus(CommentStatus.VISIBLE);
                c.setUpdatedAt(LocalDateTime.now());
                commentsRepository.save(c);
            }
        }

        q.setStatus(QueueStatus.APPROVED);
        q.setFinishedAt(LocalDateTime.now());
        q.setUpdatedAt(LocalDateTime.now());
        moderationQueueRepository.save(q);

        AdminModerationQueueDetailDTO detail = getDetail(id);
        String action = manual ? "MODERATION_MANUAL_APPROVE" : "MODERATION_AUTO_APPROVE";
        String message = q.getCaseType() == ModerationCaseType.REPORT
                ? (manual ? "人工处理：驳回举报" : "自动处理：驳回举报")
                : (manual ? "人工处理：通过内容" : "自动处理：通过内容");
        Map<String, Object> details = new HashMap<>();
        details.put("queueId", q.getId());
        details.put("caseType", q.getCaseType() == null ? null : q.getCaseType().name());
        details.put("contentType", q.getContentType() == null ? null : q.getContentType().name());
        details.put("contentId", q.getContentId());
        details.put("reason", reason);
        details.put("status", detail.getStatus() == null ? null : detail.getStatus().name());
        details.put("stage", detail.getCurrentStage() == null ? null : detail.getCurrentStage().name());
        details.put("assignedToId", detail.getAssignedToId());
        if (manual) {
            auditLogWriter.write(actor.getId(), actorName(actor), action, "MODERATION_QUEUE", q.getId(), AuditResult.SUCCESS, message, traceId, details);
        } else {
            auditLogWriter.writeSystem(action, "MODERATION_QUEUE", q.getId(), AuditResult.SUCCESS, message, ensureAutoTraceId(traceId), details);
        }
        return detail;
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO overrideApprove(Long id, String reason) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        ModerationQueueEntity q = moderationQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核任务不存在: " + id));

        if (q.getStatus() == QueueStatus.APPROVED) {
            return getDetail(id);
        }
        if (q.getStatus() == QueueStatus.REJECTED) {
            toHuman(id, reason);
        }
        return approve(id, reason);
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO reject(Long id, String reason) {
        UsersEntity actor = currentUserOrThrow();
        return rejectInternal(id, reason, actor, true, newTraceId());
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO autoReject(Long id, String reason, String traceId) {
        return rejectInternal(id, reason, null, false, traceId);
    }

    private AdminModerationQueueDetailDTO rejectInternal(Long id, String reason, UsersEntity actor, boolean manual, String traceId) {
        ModerationQueueEntity q = moderationQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核任务不存在: " + id));

        if (q.getStatus() == QueueStatus.REJECTED) {
            return getDetail(id);
        }
        if (q.getStatus() == QueueStatus.APPROVED) {
            throw new IllegalStateException("该任务已通过，不能直接驳回；如需再次处理请先进入人工审核");
        }

        if (q.getCaseType() == ModerationCaseType.REPORT) {
            Map<Long, Integer> reportCountByReporterId = new HashMap<>();
            if (q.getContentType() == ContentType.POST) {
                PostsEntity post = postsRepository.findById(q.getContentId())
                        .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + q.getContentId()));
                if (Boolean.TRUE.equals(post.getIsDeleted())) throw new IllegalArgumentException("帖子已删除: " + post.getId());
                post.setStatus(PostStatus.REJECTED);
                PostsEntity saved = postsRepository.save(post);
                ragPostIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());
            } else if (q.getContentType() == ContentType.COMMENT) {
                CommentsEntity c = commentsRepository.findById(q.getContentId())
                        .orElseThrow(() -> new IllegalArgumentException("评论不存在: " + q.getContentId()));
                if (Boolean.TRUE.equals(c.getIsDeleted())) throw new IllegalArgumentException("评论已删除: " + c.getId());
                c.setStatus(CommentStatus.REJECTED);
                c.setUpdatedAt(LocalDateTime.now());
                commentsRepository.save(c);
            }

            Long actorId = actor == null ? null : actor.getId();
            try {
                ReportTargetType t = q.getContentType() == ContentType.POST ? ReportTargetType.POST : ReportTargetType.COMMENT;
                List<ReportsEntity> pending = reportsRepository.findAllByTargetTypeAndTargetIdAndStatus(t, q.getContentId(), ReportStatus.PENDING);
                if (pending != null) {
                    for (ReportsEntity r : pending) {
                        if (r == null || r.getReporterId() == null) continue;
                        reportCountByReporterId.merge(r.getReporterId(), 1, Integer::sum);
                    }
                }
                reportsRepository.resolveAllPendingByTarget(t, q.getContentId(), ReportStatus.RESOLVED, actorId, LocalDateTime.now(), reason == null ? "REPORT_CONFIRMED" : reason);
            } catch (Exception ignore) {
            }
            try {
                if (!reportCountByReporterId.isEmpty()) {
                    String targetLabel = q.getContentType() == ContentType.POST ? "帖子" : "评论";
                    String resolution = reason == null ? "已核实并已处理" : reason;
                    for (Map.Entry<Long, Integer> e : reportCountByReporterId.entrySet()) {
                        Long reporterId = e.getKey();
                        Integer cnt = e.getValue();
                        String content = "你的举报已处理：举报已核实（" + resolution + "）。目标：" + targetLabel + " #" + q.getContentId()
                                + (cnt != null && cnt > 1 ? "（本次合并 " + cnt + " 条举报）" : "");
                        notificationsService.createNotification(reporterId, "REPORT", "举报结果通知", content);
                    }
                }
            } catch (Exception ignore) {
            }
        } else {
            if (q.getContentType() == ContentType.POST) {
                PostsEntity post = postsRepository.findById(q.getContentId())
                        .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + q.getContentId()));
                if (Boolean.TRUE.equals(post.getIsDeleted())) throw new IllegalArgumentException("帖子已删除: " + post.getId());
                post.setStatus(PostStatus.REJECTED);
                PostsEntity saved = postsRepository.save(post);
                ragPostIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());
            } else if (q.getContentType() == ContentType.COMMENT) {
                CommentsEntity c = commentsRepository.findById(q.getContentId())
                        .orElseThrow(() -> new IllegalArgumentException("评论不存在: " + q.getContentId()));
                if (Boolean.TRUE.equals(c.getIsDeleted())) throw new IllegalArgumentException("评论已删除: " + c.getId());
                c.setStatus(CommentStatus.REJECTED);
                c.setUpdatedAt(LocalDateTime.now());
                commentsRepository.save(c);
            }
        }

        q.setStatus(QueueStatus.REJECTED);
        q.setFinishedAt(LocalDateTime.now());
        q.setUpdatedAt(LocalDateTime.now());
        moderationQueueRepository.save(q);

        AdminModerationQueueDetailDTO detail = getDetail(id);
        String action = manual ? "MODERATION_MANUAL_REJECT" : "MODERATION_AUTO_REJECT";
        String message = q.getCaseType() == ModerationCaseType.REPORT
                ? (manual ? "人工处理：核实举报" : "自动处理：核实举报")
                : (manual ? "人工处理：驳回内容" : "自动处理：驳回内容");
        Map<String, Object> details = new HashMap<>();
        details.put("queueId", q.getId());
        details.put("caseType", q.getCaseType() == null ? null : q.getCaseType().name());
        details.put("contentType", q.getContentType() == null ? null : q.getContentType().name());
        details.put("contentId", q.getContentId());
        details.put("reason", reason);
        details.put("status", detail.getStatus() == null ? null : detail.getStatus().name());
        details.put("stage", detail.getCurrentStage() == null ? null : detail.getCurrentStage().name());
        details.put("assignedToId", detail.getAssignedToId());
        if (manual) {
            auditLogWriter.write(actor.getId(), actorName(actor), action, "MODERATION_QUEUE", q.getId(), AuditResult.SUCCESS, message, traceId, details);
        } else {
            auditLogWriter.writeSystem(action, "MODERATION_QUEUE", q.getId(), AuditResult.SUCCESS, message, ensureAutoTraceId(traceId), details);
        }
        return detail;
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO overrideReject(Long id, String reason) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        ModerationQueueEntity q = moderationQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核任务不存在: " + id));

        if (q.getStatus() == QueueStatus.REJECTED) {
            return getDetail(id);
        }
        if (q.getStatus() == QueueStatus.APPROVED) {
            toHuman(id, reason);
        }
        return reject(id, reason);
    }

    @Override
    @Transactional
    public void ensureEnqueuedPost(Long postId) {
        if (postId == null) return;
        moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.POST, postId)
                .orElseGet(() -> {
                    ModerationQueueEntity e = new ModerationQueueEntity();
                    e.setCaseType(ModerationCaseType.CONTENT);
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
        moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.COMMENT, commentId)
                .orElseGet(() -> {
                    ModerationQueueEntity e = new ModerationQueueEntity();
                    e.setCaseType(ModerationCaseType.CONTENT);
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
        UsersEntity actor = currentUserOrThrow();
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

                    boolean exists = moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.POST, p.getId()).isPresent();
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

                boolean exists = moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.COMMENT, c.getId()).isPresent();
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

        Map<String, Object> details = new HashMap<>();
        details.put("dryRun", req != null && Boolean.TRUE.equals(req.getDryRun()));
        details.put("limit", req != null ? req.getLimit() : null);
        details.put("contentTypes", req != null ? req.getContentTypes() : null);
        details.put("createdFrom", req != null ? req.getCreatedFrom() : null);
        details.put("createdTo", req != null ? req.getCreatedTo() : null);
        details.put("enqueued", resp.getEnqueued());
        details.put("alreadyQueued", resp.getAlreadyQueued());
        details.put("skipped", resp.getSkipped());
        details.put("scannedPosts", resp.getScannedPosts());
        details.put("scannedComments", resp.getScannedComments());
        auditLogWriter.write(actor.getId(), actorName(actor), "MODERATION_MANUAL_BACKFILL", "SYSTEM", null, AuditResult.SUCCESS, "人工操作：补齐历史待审入队", newTraceId(), details);
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

    private AdminModerationQueueItemDTO toItemDTO(ModerationQueueEntity q, PostsEntity post, CommentsEntity comment, Map<Long, PostsEntity> commentPostsById, List<String> riskTags) {
        AdminModerationQueueItemDTO dto = new AdminModerationQueueItemDTO();
        dto.setId(q.getId());
        dto.setCaseType(q.getCaseType());
        dto.setContentType(q.getContentType());
        dto.setContentId(q.getContentId());
        dto.setStatus(q.getStatus());
        dto.setCurrentStage(q.getCurrentStage());
        dto.setPriority(q.getPriority());
        dto.setAssignedToId(q.getAssignedToId());
        dto.setCreatedAt(q.getCreatedAt());
        dto.setUpdatedAt(q.getUpdatedAt());
        dto.setRiskTags(riskTags == null ? List.of() : riskTags);

        if (q.getContentType() == ContentType.POST) {
            dto.setSummary(buildPostSummary(post));
        } else if (q.getContentType() == ContentType.COMMENT) {
            PostsEntity p = null;
            if (comment != null && comment.getPostId() != null) p = commentPostsById.get(comment.getPostId());
            dto.setSummary(buildCommentSummary(comment, p));
        }

        if (q.getCaseType() == ModerationCaseType.REPORT) {
            try {
                ReportTargetType t = q.getContentType() == ContentType.POST ? ReportTargetType.POST : ReportTargetType.COMMENT;
                Pageable pageable = PageRequest.of(0, 1, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
                Page<ReportsEntity> page = reportsRepository.findByTargetTypeAndTargetId(t, q.getContentId(), pageable);
                ReportsEntity latest = (page == null || page.getContent() == null || page.getContent().isEmpty()) ? null : page.getContent().get(0);
                if (latest != null) {
                    AdminModerationQueueItemDTO.Summary s = dto.getSummary();
                    if (s == null) s = new AdminModerationQueueItemDTO.Summary();
                    s.setSnippet(buildReportReasonSnippet(latest));
                    dto.setSummary(s);
                }
            } catch (Exception ignore) {
            }
        }
        return dto;
    }

    private AdminModerationQueueDetailDTO baseDetail(ModerationQueueEntity q) {
        AdminModerationQueueDetailDTO dto = new AdminModerationQueueDetailDTO();
        dto.setId(q.getId());
        dto.setCaseType(q.getCaseType());
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

    private static String buildReportReasonSnippet(ReportsEntity r) {
        if (r == null) return null;
        String code = r.getReasonCode();
        String text = r.getReasonText();
        String s = (code == null ? "" : code.trim());
        if (text != null && !text.trim().isEmpty()) {
            s = s.isEmpty() ? text.trim() : (s + " - " + text.trim());
        }
        if (s.isEmpty()) return "举报原因：—";
        if (s.length() > 140) s = s.substring(0, 140) + "...";
        return "举报原因：" + s;
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

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO claim(Long id) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        UsersEntity actor = currentUserOrThrow();
        Long userId = actor.getId();
        int updated = moderationQueueRepository.claimHuman(id, userId, LocalDateTime.now());
        if (updated <= 0) {
            throw new IllegalStateException("认领失败：任务不存在、不是待人工审核或已被认领");
        }
        AdminModerationQueueDetailDTO detail = getDetail(id);
        Map<String, Object> details = new HashMap<>();
        details.put("queueId", detail.getId());
        details.put("caseType", detail.getCaseType() == null ? null : detail.getCaseType().name());
        details.put("contentType", detail.getContentType() == null ? null : detail.getContentType().name());
        details.put("contentId", detail.getContentId());
        details.put("status", detail.getStatus() == null ? null : detail.getStatus().name());
        details.put("stage", detail.getCurrentStage() == null ? null : detail.getCurrentStage().name());
        details.put("assignedToId", detail.getAssignedToId());
        auditLogWriter.write(actor.getId(), actorName(actor), "MODERATION_MANUAL_CLAIM", "MODERATION_QUEUE", detail.getId(), AuditResult.SUCCESS, "人工操作：认领审核任务", newTraceId(), details);
        return detail;
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO release(Long id) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        UsersEntity actor = currentUserOrThrow();
        Long userId = actor.getId();
        int updated = moderationQueueRepository.releaseHuman(id, userId, LocalDateTime.now());
        if (updated <= 0) {
            throw new IllegalStateException("释放失败：只能释放自己认领的任务");
        }
        AdminModerationQueueDetailDTO detail = getDetail(id);
        Map<String, Object> details = new HashMap<>();
        details.put("queueId", detail.getId());
        details.put("caseType", detail.getCaseType() == null ? null : detail.getCaseType().name());
        details.put("contentType", detail.getContentType() == null ? null : detail.getContentType().name());
        details.put("contentId", detail.getContentId());
        details.put("status", detail.getStatus() == null ? null : detail.getStatus().name());
        details.put("stage", detail.getCurrentStage() == null ? null : detail.getCurrentStage().name());
        details.put("assignedToId", detail.getAssignedToId());
        auditLogWriter.write(actor.getId(), actorName(actor), "MODERATION_MANUAL_RELEASE", "MODERATION_QUEUE", detail.getId(), AuditResult.SUCCESS, "人工操作：释放审核任务", newTraceId(), details);
        return detail;
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO requeueToAuto(Long id, String reason) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        UsersEntity actor = currentUserOrThrow();
        // 允许任何有 action 权限的管理员执行；reason 目前仅保留参数位
        int updated = moderationQueueRepository.requeueToAuto(id, QueueStatus.PENDING, QueueStage.RULE, LocalDateTime.now());
        if (updated <= 0) {
            throw new IllegalStateException("重新入队失败：任务不存在或状态不允许");
        }

        // 清空该任务之前的审核/流水线信息：删除 pipeline run/step trace，让后续自动审核从 RULE 全新开始。
        // best-effort：即便清理失败，也不影响 requeue 主流程。
        try {
            moderationPipelineRunRepository.deleteAllByQueueId(id);
        } catch (Exception ignore) {
        }

        // 关键：点击后要求立刻推进 RULE/VEC/LLM。
        // 注意：各 runner 内部自带条件判断/锁/开关；这里 best-effort 串联触发一次。
        try {
            moderationRuleAutoRunner.runOnce();
        } catch (Exception ignore) {
        }
        try {
            moderationVecAutoRunner.runOnce();
        } catch (Exception ignore) {
        }

        AdminModerationQueueDetailDTO detail = getDetail(id);
        Map<String, Object> details = new HashMap<>();
        details.put("queueId", detail.getId());
        details.put("caseType", detail.getCaseType() == null ? null : detail.getCaseType().name());
        details.put("contentType", detail.getContentType() == null ? null : detail.getContentType().name());
        details.put("contentId", detail.getContentId());
        details.put("reason", reason);
        details.put("status", detail.getStatus() == null ? null : detail.getStatus().name());
        details.put("stage", detail.getCurrentStage() == null ? null : detail.getCurrentStage().name());
        details.put("assignedToId", detail.getAssignedToId());
        auditLogWriter.write(actor.getId(), actorName(actor), "MODERATION_MANUAL_REQUEUE", "MODERATION_QUEUE", detail.getId(), AuditResult.SUCCESS, "人工操作：重新进入自动审核", newTraceId(), details);
        return detail;
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO toHuman(Long id, String reason) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        UsersEntity actor = currentUserOrThrow();
        int updated = moderationQueueRepository.toHuman(id, QueueStatus.HUMAN, QueueStage.HUMAN, LocalDateTime.now());
        if (updated <= 0) {
            throw new IllegalStateException("进入人工审核失败：仅允许已终态任务进入人工审核");
        }
        AdminModerationQueueDetailDTO detail = getDetail(id);
        Map<String, Object> details = new HashMap<>();
        details.put("queueId", detail.getId());
        details.put("caseType", detail.getCaseType() == null ? null : detail.getCaseType().name());
        details.put("contentType", detail.getContentType() == null ? null : detail.getContentType().name());
        details.put("contentId", detail.getContentId());
        details.put("reason", reason);
        details.put("status", detail.getStatus() == null ? null : detail.getStatus().name());
        details.put("stage", detail.getCurrentStage() == null ? null : detail.getCurrentStage().name());
        details.put("assignedToId", detail.getAssignedToId());
        auditLogWriter.write(actor.getId(), actorName(actor), "MODERATION_MANUAL_TO_HUMAN", "MODERATION_QUEUE", detail.getId(), AuditResult.SUCCESS, "人工操作：进入人工审核", newTraceId(), details);
        return detail;
    }

    @Override
    public List<String> getRiskTags(Long queueId) {
        if (queueId == null) throw new IllegalArgumentException("queueId 不能为空");
        ModerationQueueEntity q = moderationQueueRepository.findById(queueId)
                .orElseThrow(() -> new IllegalArgumentException("审核任务不存在: " + queueId));
        return riskLabelingService.getRiskTagSlugs(q.getContentType(), q.getContentId());
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO setRiskTags(Long queueId, List<String> riskTags) {
        if (queueId == null) throw new IllegalArgumentException("queueId 不能为空");
        UsersEntity actor = currentUserOrThrow();
        ModerationQueueEntity q = moderationQueueRepository.findById(queueId)
                .orElseThrow(() -> new IllegalArgumentException("审核任务不存在: " + queueId));

        if (q.getStatus() != QueueStatus.HUMAN) {
            throw new IllegalStateException("仅允许在人工审核状态下设置风险标签");
        }

        riskLabelingService.replaceRiskTags(q.getContentType(), q.getContentId(), Source.HUMAN, riskTags, null, true);
        AdminModerationQueueDetailDTO detail = getDetail(queueId);
        Map<String, Object> details = new HashMap<>();
        details.put("queueId", detail.getId());
        details.put("caseType", detail.getCaseType() == null ? null : detail.getCaseType().name());
        details.put("contentType", detail.getContentType() == null ? null : detail.getContentType().name());
        details.put("contentId", detail.getContentId());
        details.put("riskTags", riskTags);
        details.put("status", detail.getStatus() == null ? null : detail.getStatus().name());
        details.put("stage", detail.getCurrentStage() == null ? null : detail.getCurrentStage().name());
        details.put("assignedToId", detail.getAssignedToId());
        auditLogWriter.write(actor.getId(), actorName(actor), "MODERATION_MANUAL_SET_RISK_TAGS", "MODERATION_QUEUE", detail.getId(), AuditResult.SUCCESS, "人工操作：更新风险标签", newTraceId(), details);
        return detail;
    }

    private Long currentUserIdOrThrow() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {
            };
        }
        String email = auth.getName();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"))
                .getId();
    }

    private UsersEntity currentUserOrThrow() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {
            };
        }
        String email = auth.getName();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"));
    }

    private String actorName(UsersEntity user) {
        if (user == null) return null;
        String u = user.getUsername();
        if (u != null && !u.isBlank()) return u;
        return user.getEmail();
    }

    private String ensureAutoTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) return traceId;
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String newTraceId() {
        return "human-" + UUID.randomUUID();
    }
}
