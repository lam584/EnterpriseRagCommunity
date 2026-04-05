package com.example.EnterpriseRagCommunity.service.moderation.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBackfillRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBackfillResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBatchRequeueResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueItemDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueRiskTagItemDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationQueueQueryDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostAttachmentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReportsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReportTargetType;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationActionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationPipelineRunEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ActionType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Source;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostAttachmentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReportsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationPipelineRunRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.FileAssetExtractionsRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.UsersService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationAutoKickService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationPipelineRunSupport;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationRiskTagSupport;
import com.example.EnterpriseRagCommunity.service.moderation.RiskLabelingService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;

import com.example.EnterpriseRagCommunity.service.retrieval.RagCommentIndexVisibilitySyncService;
import com.example.EnterpriseRagCommunity.service.retrieval.RagPostIndexVisibilitySyncService;

import jakarta.transaction.Transactional;

@Service
@RequiredArgsConstructor
public class AdminModerationQueueServiceImpl implements AdminModerationQueueService {
    private final ModerationQueueRepository moderationQueueRepository;
    private final PostsRepository postsRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final FileAssetExtractionsRepository fileAssetExtractionsRepository;
    private final CommentsRepository commentsRepository;
    private final UsersRepository usersRepository;
    private final com.example.EnterpriseRagCommunity.service.AdministratorService administratorService;
    private final ReportsRepository reportsRepository;
    private final NotificationsService notificationsService;
    private final ModerationPipelineRunRepository moderationPipelineRunRepository;
    private final ModerationActionsRepository moderationActionsRepository;

    private ModerationAutoKickService moderationAutoKickService;
    private final RiskLabelingService riskLabelingService;
    private final ModerationChunkReviewService moderationChunkReviewService;
    private final RagPostIndexVisibilitySyncService ragPostIndexVisibilitySyncService;
    private final RagCommentIndexVisibilitySyncService ragCommentIndexVisibilitySyncService;
    private final AuditLogWriter auditLogWriter;
    private final UsersService usersService;
    private AdminModerationQueueService self;

    @Autowired
    void setModerationAutoKickService(@Lazy ModerationAutoKickService moderationAutoKickService) {
        this.moderationAutoKickService = moderationAutoKickService;
    }

    @Autowired
    void setSelf(@Lazy AdminModerationQueueService self) {
        this.self = self;
    }

    @Override
    public Page<AdminModerationQueueItemDTO> list(ModerationQueueQueryDTO query) {
        if (query == null) query = new ModerationQueueQueryDTO();

        // 提前提取 query 字段，避免在 lambda 中捕获非 final/非实际上 final 的局部变量
        final Long qId = query.getId();
        final Long qBoardId = query.getBoardId();
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
        if (qBoardId != null) {
            spec = spec.and((root, q, cb) -> {
                if (q == null) return cb.disjunction();
                Subquery<Long> sq = q.subquery(Long.class);
                var p = sq.from(PostsEntity.class);
                sq.select(p.get("id"))
                        .where(
                                cb.equal(p.get("boardId"), qBoardId),
                                cb.isFalse(p.get("isDeleted"))
                        );
                return cb.and(
                        cb.equal(root.get("contentType"), ContentType.POST),
                        root.get("contentId").in(sq)
                );
            });
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
        Set<Long> profileUserIds = new HashSet<>();
        Set<Long> queueIds = new HashSet<>();
        for (ModerationQueueEntity e : entityPage.getContent()) {
            if (e == null) continue;
            if (e.getId() != null) queueIds.add(e.getId());
            if (e.getContentType() == ContentType.POST) postIds.add(e.getContentId());
            if (e.getContentType() == ContentType.COMMENT) commentIds.add(e.getContentId());
            if (e.getContentType() == ContentType.PROFILE) profileUserIds.add(e.getContentId());
        }

        Map<Long, PostsEntity> postsById = loadPostsByIds(postIds);
        Map<Long, CommentsEntity> commentsById = loadCommentsByIds(commentIds);
        Map<Long, UsersEntity> usersById = loadUsersByIds(profileUserIds);

        // For comments, also load their post titles
        Set<Long> commentPostIds = new HashSet<>();
        for (CommentsEntity c : commentsById.values()) {
            if (c != null && c.getPostId() != null) commentPostIds.add(c.getPostId());
        }
        Map<Long, PostsEntity> commentPostsById = loadPostsByIds(commentPostIds);

        Map<Long, List<RiskLabelingService.RiskTagItem>> postRiskTagItemsById = riskLabelingService.getRiskTagItemsByTargets(ContentType.POST, postIds);
        Map<Long, List<RiskLabelingService.RiskTagItem>> commentRiskTagItemsById = riskLabelingService.getRiskTagItemsByTargets(ContentType.COMMENT, commentIds);
        Map<Long, List<RiskLabelingService.RiskTagItem>> profileRiskTagItemsById = riskLabelingService.getRiskTagItemsByTargets(ContentType.PROFILE, profileUserIds);
        Map<Long, ModerationChunkReviewService.ProgressSummary> chunkProgressByQueueId = moderationChunkReviewService.loadProgressSummaries(queueIds);

        return entityPage.map(e -> {
            List<RiskLabelingService.RiskTagItem> riskTagItems = switch (e.getContentType()) {
                case POST -> postRiskTagItemsById.getOrDefault(e.getContentId(), List.of());
                case COMMENT -> commentRiskTagItemsById.getOrDefault(e.getContentId(), List.of());
                case PROFILE -> profileRiskTagItemsById.getOrDefault(e.getContentId(), List.of());
            };
            List<String> riskTags = ModerationRiskTagSupport.collectRiskTags(riskTagItems);
            List<AdminModerationQueueRiskTagItemDTO> riskTagItemDTOs = ModerationRiskTagSupport.toRiskTagItemDtos(riskTagItems);
            AdminModerationQueueItemDTO dto = toItemDTO(
                    e,
                    postsById.get(e.getContentId()),
                    commentsById.get(e.getContentId()),
                    usersById.get(e.getContentId()),
                    commentPostsById,
                    riskTags
            );
            dto.setRiskTagItems(riskTagItemDTOs);
            ModerationChunkReviewService.ProgressSummary ps = chunkProgressByQueueId.get(e.getId());
            dto.setChunkProgress(toChunkProgress(ps));
            return dto;
        });
    }

    private static AdminModerationQueueDetailDTO.Attachment toAttachment(PostAttachmentsEntity a, com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity ex) {
        AdminModerationQueueDetailDTO.Attachment dto = new AdminModerationQueueDetailDTO.Attachment();
        dto.setId(a.getId());
        dto.setFileAssetId(a.getFileAssetId());
        com.example.EnterpriseRagCommunity.entity.monitor.FileAssetsEntity fa = a.getFileAsset();
        dto.setUrl(fa != null ? fa.getUrl() : null);
        dto.setFileName(fa != null ? fa.getOriginalName() : null);
        dto.setMimeType(fa != null ? fa.getMimeType() : null);
        dto.setSizeBytes(fa != null ? fa.getSizeBytes() : null);
        dto.setWidth(a.getWidth());
        dto.setHeight(a.getHeight());
        dto.setCreatedAt(a.getCreatedAt());
        if (ex != null) {
            dto.setExtractStatus(enumName(ex.getExtractStatus()));
            String text = ex.getExtractedText();
            dto.setExtractedTextChars(text == null ? 0 : text.length());
            dto.setExtractedTextSnippet(snippet(text, 2000));
            dto.setExtractedMetadataJsonSnippet(snippet(ex.getExtractedMetadataJson(), 1000));
            dto.setExtractionErrorMessage(snippet(ex.getErrorMessage(), 500));
            dto.setExtractionUpdatedAt(ex.getUpdatedAt());
        }
        return dto;
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static AdminModerationQueueItemDTO.ChunkProgress toChunkProgress(ModerationChunkReviewService.ProgressSummary ps) {
        if (ps == null || ps.total <= 0) {
            return null;
        }
        AdminModerationQueueItemDTO.ChunkProgress cp = new AdminModerationQueueItemDTO.ChunkProgress();
        cp.setStatus(ps.status);
        cp.setTotalChunks(ps.total);
        cp.setCompletedChunks(ps.completed);
        cp.setFailedChunks(ps.failed);
        cp.setUpdatedAt(ps.updatedAt);
        return cp;
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO approve(Long id, String reason) {
        UsersEntity actor = currentUserOrThrow();
        String checkedReason = requireReason(reason);
        return approveInternal(id, checkedReason, actor, true, newTraceId());
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO autoApprove(Long id, String reason, String traceId) {
        return approveInternal(id, reason, null, false, traceId);
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO overrideApprove(Long id, String reason) {
        return overrideDecision(id, reason, QueueStatus.APPROVED, this::approve);
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO reject(Long id, String reason) {
        UsersEntity actor = currentUserOrThrow();
        String checkedReason = requireReason(reason);
        return rejectInternal(id, checkedReason, actor, true, newTraceId());
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO autoReject(Long id, String reason, String traceId) {
        return rejectInternal(id, reason, null, false, traceId);
    }

    @Override
    public AdminModerationQueueDetailDTO getDetail(Long id) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        ModerationQueueEntity q = moderationQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核任务不存在: " + id));

        AdminModerationQueueDetailDTO dto = baseDetail(q);
        List<RiskLabelingService.RiskTagItem> riskTagItems = riskLabelingService.getRiskTagItems(q.getContentType(), q.getContentId());
        List<String> riskTags = ModerationRiskTagSupport.collectRiskTags(riskTagItems);
        List<AdminModerationQueueRiskTagItemDTO> riskTagItemDTOs = ModerationRiskTagSupport.toRiskTagItemDtos(riskTagItems);
        dto.setRiskTags(riskTags);
        dto.setRiskTagItems(riskTagItemDTOs);
        try {
            ModerationChunkReviewService.ProgressSummary ps = moderationChunkReviewService.loadProgressSummaries(List.of(q.getId())).get(q.getId());
            dto.setChunkProgress(toChunkProgress(ps));
        } catch (Exception ignore) {
        }

        if (q.getContentType() == ContentType.POST) {
            PostsEntity p = postsRepository.findById(q.getContentId()).orElse(null);
            dto.setSummary(buildPostSummary(p));
            AdminModerationQueueDetailDTO.PostContent pc = toPostContent(p);
            if (pc != null && p.getId() != null) {
                try {
                    Pageable pageable = PageRequest.of(0, 200, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
                    Page<PostAttachmentsEntity> page = postAttachmentsRepository.findByPostId(p.getId(), pageable);
                    List<AdminModerationQueueDetailDTO.Attachment> atts = new ArrayList<>();
                    if (page != null) {
                        page.getContent();
                        Map<Long, com.example.EnterpriseRagCommunity.entity.monitor.FileAssetExtractionsEntity> exById = new HashMap<>();
                        try {
                            Set<Long> faIds = new HashSet<>();
                            for (PostAttachmentsEntity a : page.getContent()) {
                                if (a == null || a.getFileAssetId() == null) continue;
                                faIds.add(a.getFileAssetId());
                                if (faIds.size() >= 200) break;
                            }
                            if (!faIds.isEmpty()) {
                                for (var ex : fileAssetExtractionsRepository.findAllById(faIds)) {
                                    if (ex != null && ex.getFileAssetId() != null)
                                        exById.put(ex.getFileAssetId(), ex);
                                }
                            }
                        } catch (Exception ignore) {
                        }
                        for (PostAttachmentsEntity a : page.getContent()) {
                            if (a == null) continue;
                            atts.add(toAttachment(a, exById.get(a.getFileAssetId())));
                        }
                    }
                    pc.setAttachments(atts.isEmpty() ? List.of() : atts);
                } catch (Exception ignore) {
                    pc.setAttachments(List.of());
                }
            }
            dto.setPost(pc);
        } else if (q.getContentType() == ContentType.COMMENT) {
            CommentsEntity c = commentsRepository.findById(q.getContentId()).orElse(null);
            PostsEntity post = (c == null || c.getPostId() == null) ? null : postsRepository.findById(c.getPostId()).orElse(null);
            dto.setSummary(buildCommentSummary(c, post));
            dto.setComment(toCommentContent(c));
        } else if (q.getContentType() == ContentType.PROFILE) {
            UsersEntity u = usersRepository.findById(q.getContentId()).orElse(null);
            dto.setSummary(buildProfileSummary(u));
            dto.setProfile(toProfileContent(u, q.getId()));
        }

        try {
            ReportTargetType targetType = toReportTargetType(q.getContentType());
            Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
            Page<ReportsEntity> page = reportsRepository.findByTargetTypeAndTargetId(targetType, q.getContentId(), pageable);
            List<AdminModerationQueueDetailDTO.ReportInfo> infos = new ArrayList<>();
            if (page != null) {
                page.getContent();
                for (ReportsEntity r : page.getContent()) {
                    if (r == null) continue;
                    AdminModerationQueueDetailDTO.ReportInfo info = new AdminModerationQueueDetailDTO.ReportInfo();
                    info.setId(r.getId());
                    info.setReporterId(r.getReporterId());
                    info.setReasonCode(r.getReasonCode());
                    info.setReasonText(r.getReasonText());
                    info.setStatus(enumName(r.getStatus()));
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
    public AdminModerationQueueDetailDTO overrideReject(Long id, String reason) {
        return overrideDecision(id, reason, QueueStatus.REJECTED, this::reject);
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
            Map<Long, Integer> reportCountByReporterId = new HashMap<>(countPendingReportsByReporter(q));
            try {
                ReportTargetType t = toReportTargetType(q.getContentType());
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
                        CommentsEntity saved = commentsRepository.save(c);
                        ragCommentIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());
                    }
                }
            } catch (Exception ignore) {
            }
            try {
                if (!reportCountByReporterId.isEmpty()) {
                    String targetLabel = labelFor(q.getContentType());
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
                updateCommentStatusAndScheduleSync(q, CommentStatus.VISIBLE);
            } else if (q.getContentType() == ContentType.PROFILE) {
                ProfileUserState state = loadProfileUserStateOrThrow(q);
                UsersEntity u = state.user();
                Map<String, Object> md = state.metadata();
                Map<String, Object> pending = readMap(md, "profilePending");
                String pendingUsername = strOrNull(pending.get("username"));
                if (pendingUsername != null) u.setUsername(pendingUsername);

                Map<String, Object> publicProfile0 = readMap(md, "profile");
                Map<String, Object> publicProfile = new HashMap<>(publicProfile0);
                if (pending.containsKey("avatarUrl")) publicProfile.put("avatarUrl", pending.get("avatarUrl"));
                if (pending.containsKey("bio")) publicProfile.put("bio", pending.get("bio"));
                if (pending.containsKey("location")) publicProfile.put("location", pending.get("location"));
                if (pending.containsKey("website")) publicProfile.put("website", pending.get("website"));
                md.put("profile", publicProfile);
                md.remove("profilePending");
                md.remove("profilePendingSubmittedAt");
                md.remove("profileModeration");
                u.setMetadata(md);
                usersRepository.save(u);
            }
        }

        try {
            notifyModerationResultToAuthor(q, true, reason);
        } catch (Exception ignore) {
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
        Map<String, Object> details = buildQueueAuditDetails(detail);
        details.put("reason", reason);
        writeQueueAudit(actor, manual, traceId, action, q.getId(), message, details);
        return detail;
    }

    @Override
    @Transactional
    public void ensureEnqueuedPost(Long postId) {
        ensureEnqueuedContent(ContentType.POST, postId);
    }

    @Override
    @Transactional
    public void ensureEnqueuedComment(Long commentId) {
        ensureEnqueuedContent(ContentType.COMMENT, commentId);
    }

    @Override
    @Transactional
    public void ensureEnqueuedProfile(Long userId) {
        ensureEnqueuedContent(ContentType.PROFILE, userId);
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
        if (types.contains(ContentType.POST)) {
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
            Specification<CommentsEntity> spec = buildPendingCommentSpec(createdFrom, createdTo);

            // 按创建时间升序扫描，更符合"先来先审"
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
        return to == null || !t.isAfter(to);
    }

    private void ensureEnqueuedContent(ContentType contentType, Long contentId) {
        if (contentType == null || contentId == null) return;
        moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, contentType, contentId)
                .orElseGet(() -> moderationQueueRepository.save(newPendingQueue(contentType, contentId)));
    }

    private static ModerationQueueEntity newPendingQueue(ContentType contentType, Long contentId) {
        LocalDateTime now = LocalDateTime.now();
        ModerationQueueEntity entity = new ModerationQueueEntity();
        entity.setCaseType(ModerationCaseType.CONTENT);
        entity.setContentType(contentType);
        entity.setContentId(contentId);
        entity.setStatus(QueueStatus.PENDING);
        entity.setCurrentStage(QueueStage.RULE);
        entity.setPriority(0);
        entity.setAssignedToId(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
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

    private Map<Long, UsersEntity> loadUsersByIds(Set<Long> ids) {
        Map<Long, UsersEntity> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) return map;
        usersRepository.findAllById(ids).forEach(u -> map.put(u.getId(), u));
        return map;
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
            Map<Long, Integer> reportCountByReporterId = new HashMap<>(countPendingReportsByReporter(q));
            if (q.getContentType() == ContentType.POST) {
                rejectPostAndScheduleSync(q);
            } else if (q.getContentType() == ContentType.COMMENT) {
                updateCommentStatusAndScheduleSync(q, CommentStatus.REJECTED);
            } else if (q.getContentType() == ContentType.PROFILE) {
                ProfileUserState state = loadProfileUserStateOrThrow(q);
                UsersEntity u = state.user();
                Map<String, Object> md = state.metadata();
                Map<String, Object> publicProfile0 = readMap(md, "profile");
                Map<String, Object> publicProfile = new HashMap<>(publicProfile0);
                publicProfile.put("avatarUrl", null);
                publicProfile.put("bio", null);
                publicProfile.put("location", null);
                publicProfile.put("website", null);
                md.put("profile", publicProfile);
                md.remove("profilePending");
                md.remove("profilePendingSubmittedAt");
                u.setMetadata(md);
                usersRepository.save(u);
            }

            Long actorId = actor == null ? null : actor.getId();
            try {
                ReportTargetType t = toReportTargetType(q.getContentType());
                reportsRepository.resolveAllPendingByTarget(t, q.getContentId(), ReportStatus.RESOLVED, actorId, LocalDateTime.now(), reason == null ? "REPORT_CONFIRMED" : reason);
            } catch (Exception ignore) {
            }
            try {
                if (!reportCountByReporterId.isEmpty()) {
                    String targetLabel = labelFor(q.getContentType());
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
                rejectPostAndScheduleSync(q);
            } else if (q.getContentType() == ContentType.COMMENT) {
                CommentsEntity c = commentsRepository.findById(q.getContentId())
                        .orElseThrow(() -> new IllegalArgumentException("评论不存在: " + q.getContentId()));
                if (Boolean.TRUE.equals(c.getIsDeleted()))
                    throw new IllegalArgumentException("评论已删除: " + c.getId());
                c.setStatus(CommentStatus.REJECTED);
                c.setUpdatedAt(LocalDateTime.now());
                CommentsEntity saved = commentsRepository.save(c);
                ragCommentIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());
            } else if (q.getContentType() == ContentType.PROFILE) {
                ProfileUserState state = loadProfileUserStateOrThrow(q);
                UsersEntity u = state.user();
                Map<String, Object> md = state.metadata();
                md.remove("profilePending");
                md.remove("profilePendingSubmittedAt");
                Map<String, Object> pm = new HashMap<>();
                pm.put("caseType", "CONTENT");
                pm.put("status", "REJECTED");
                pm.put("updatedAt", LocalDateTime.now().toString());
                pm.put("reason", reason);
                md.put("profileModeration", pm);
                u.setMetadata(md);
                usersRepository.save(u);
            }
        }

        try {
            notifyModerationResultToAuthor(q, false, reason);
        } catch (Exception ignore) {
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
        Map<String, Object> details = buildQueueAuditDetails(detail);
        details.put("reason", reason);
        writeQueueAudit(actor, manual, traceId, action, q.getId(), message, details);
        return detail;
    }

    private void rejectPostAndScheduleSync(ModerationQueueEntity q) {
        PostsEntity post = postsRepository.findById(q.getContentId())
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在: " + q.getContentId()));
        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new IllegalArgumentException("帖子已删除: " + post.getId());
        }
        post.setStatus(PostStatus.REJECTED);
        PostsEntity saved = postsRepository.save(post);
        ragPostIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());
    }

    private CommentsEntity loadRejectableComment(ModerationQueueEntity queue) {
        CommentsEntity comment = commentsRepository.findById(queue.getContentId())
                .orElseThrow(() -> new IllegalArgumentException("评论不存在: " + queue.getContentId()));
        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new IllegalArgumentException("评论已删除: " + comment.getId());
        }
        return comment;
    }

    private void updateCommentStatusAndScheduleSync(ModerationQueueEntity queue, CommentStatus status) {
        CommentsEntity comment = loadRejectableComment(queue);
        comment.setStatus(status);
        comment.setUpdatedAt(LocalDateTime.now());
        CommentsEntity saved = commentsRepository.save(comment);
        ragCommentIndexVisibilitySyncService.scheduleSyncAfterCommit(saved.getId());
    }

    private AdminModerationQueueDetailDTO baseDetail(ModerationQueueEntity q) {
        AdminModerationQueueDetailDTO dto = new AdminModerationQueueDetailDTO();
        applyBaseQueueFields(dto, q);
        return dto;
    }

    private static void applyBaseQueueFields(AdminModerationQueueDetailDTO dto, ModerationQueueEntity queue) {
        dto.setId(queue.getId());
        dto.setCaseType(queue.getCaseType());
        dto.setContentType(queue.getContentType());
        dto.setContentId(queue.getContentId());
        dto.setStatus(queue.getStatus());
        dto.setCurrentStage(queue.getCurrentStage());
        dto.setPriority(queue.getPriority());
        dto.setAssignedToId(queue.getAssignedToId());
        dto.setCreatedAt(queue.getCreatedAt());
        dto.setUpdatedAt(queue.getUpdatedAt());
    }

    private static void applyBaseQueueFields(AdminModerationQueueItemDTO dto, ModerationQueueEntity queue) {
        dto.setId(queue.getId());
        dto.setCaseType(queue.getCaseType());
        dto.setContentType(queue.getContentType());
        dto.setContentId(queue.getContentId());
        dto.setStatus(queue.getStatus());
        dto.setCurrentStage(queue.getCurrentStage());
        dto.setPriority(queue.getPriority());
        dto.setAssignedToId(queue.getAssignedToId());
        dto.setCreatedAt(queue.getCreatedAt());
        dto.setUpdatedAt(queue.getUpdatedAt());
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

    private AdminModerationQueueItemDTO.Summary buildProfileSummary(UsersEntity u) {
        if (u == null) return null;
        AdminModerationQueueItemDTO.Summary s = new AdminModerationQueueItemDTO.Summary();
        s.setTitle(u.getUsername());
        s.setSnippet(excerpt(readUserProfileString(u, "bio"), 120));
        s.setAuthorId(u.getId());
        s.setAuthorName(null);
        return s;
    }

    private static ReportTargetType toReportTargetType(ContentType ct) {
        if (ct == null) return ReportTargetType.POST;
        return switch (ct) {
            case POST -> ReportTargetType.POST;
            case COMMENT -> ReportTargetType.COMMENT;
            case PROFILE -> ReportTargetType.PROFILE;
        };
    }

    private static String labelFor(ContentType ct) {
        if (ct == null) return "内容";
        return switch (ct) {
            case POST -> "帖子";
            case COMMENT -> "评论";
            case PROFILE -> "资料";
        };
    }

    private void notifyModerationResultToAuthor(ModerationQueueEntity q, boolean approved, String reason) {
        if (q == null || q.getContentType() == null || q.getContentId() == null) return;
        Long userId = resolveAuthorUserId(q);
        if (userId == null) return;

        String targetLabel = labelFor(q.getContentType());
        boolean reported = q.getCaseType() == ModerationCaseType.REPORT;
        String title = reported
                ? (approved ? (targetLabel + "复审通过") : (targetLabel + "复审未通过"))
                : (approved ? (targetLabel + "审核通过") : (targetLabel + "审核未通过"));

        String r = reason == null ? "" : reason.trim();
        if (r.length() > 200) r = r.substring(0, 200);

        String content;
        if (reported) {
            content = approved
                    ? ("你的" + targetLabel + "已完成复审：未发现违规。目标：" + targetLabel + " #" + q.getContentId()
                    + suffixReason(r, "说明"))
                    : ("你的" + targetLabel + "已完成复审：已确认违规并已处理。目标：" + targetLabel + " #" + q.getContentId()
                    + suffixReason(r, "原因"));
        } else {
            content = approved
                    ? ("你的" + targetLabel + "已通过审核。目标：" + targetLabel + " #" + q.getContentId()
                    + suffixReason(r, "说明"))
                    : ("你的" + targetLabel + "未通过审核。目标：" + targetLabel + " #" + q.getContentId()
                    + suffixReason(r, "原因"));
        }

        notificationsService.createNotification(userId, "MODERATION", "审核通知", content);
    }

    private static String readUserProfileString(UsersEntity u, String key) {
        if (u == null || key == null) return null;
        Map<String, Object> md = u.getMetadata();
        if (md == null) return null;
        Object p0 = md.get("profile");
        if (!(p0 instanceof Map<?, ?> p)) return null;
        Object v = p.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static String suffixReason(String reason, String label) {
        if (reason == null || reason.isEmpty()) return "";
        return "；" + label + "：" + reason;
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

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO banUser(Long id, String reason) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        UsersEntity actor = currentUserOrThrow();
        String checkedReason = requireReason(reason);

        ModerationQueueEntity q = moderationQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核任务不存在: " + id));

        Long targetUserId = resolveAuthorUserId(q);

        if (targetUserId == null) {
            throw new IllegalStateException("无法识别内容作者，不能封禁");
        }

        usersService.banUser(targetUserId, actor.getId(), actorName(actor), checkedReason, "MODERATION_QUEUE", id);

        Map<String, Object> details = buildQueueAuditDetails(q);
        details.put("bannedUserId", targetUserId);
        details.put("reason", checkedReason);
        auditLogWriter.write(actor.getId(), actorName(actor), "MODERATION_MANUAL_BAN_USER", "MODERATION_QUEUE", q.getId(), AuditResult.SUCCESS, "人工操作：封禁用户", newTraceId(), details);

        return getDetail(id);
    }

    private AdminModerationQueueItemDTO toItemDTO(ModerationQueueEntity q, PostsEntity post, CommentsEntity comment, UsersEntity user, Map<Long, PostsEntity> commentPostsById, List<String> riskTags) {
        AdminModerationQueueItemDTO dto = new AdminModerationQueueItemDTO();
        applyBaseQueueFields(dto, q);
        dto.setRiskTags(riskTags == null ? List.of() : riskTags);

        if (q.getContentType() == ContentType.POST) {
            dto.setSummary(buildPostSummary(post));
        } else if (q.getContentType() == ContentType.COMMENT) {
            PostsEntity p = null;
            if (comment != null && comment.getPostId() != null) p = commentPostsById.get(comment.getPostId());
            dto.setSummary(buildCommentSummary(comment, p));
        } else if (q.getContentType() == ContentType.PROFILE) {
            dto.setSummary(buildProfileSummary(user));
        }

        if (q.getCaseType() == ModerationCaseType.REPORT) {
            try {
                ReportTargetType t = toReportTargetType(q.getContentType());
                Pageable pageable = PageRequest.of(0, 1, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
                Page<ReportsEntity> page = reportsRepository.findByTargetTypeAndTargetId(t, q.getContentId(), pageable);
                ReportsEntity latest = page.getContent().isEmpty() ? null : page.getContent().get(0);
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

    private AdminModerationQueueDetailDTO.ProfileContent toProfileContent(UsersEntity u, Long queueId) {
        if (u == null) return null;
        AdminModerationQueueDetailDTO.ProfileContent pc = new AdminModerationQueueDetailDTO.ProfileContent();
        pc.setId(u.getId());
        pc.setPublicUsername(u.getUsername());
        pc.setPublicAvatarUrl(readUserProfileString(u, "avatarUrl"));
        pc.setPublicBio(readUserProfileString(u, "bio"));
        pc.setPublicLocation(readUserProfileString(u, "location"));
        pc.setPublicWebsite(readUserProfileString(u, "website"));

        Map<String, Object> md = u.getMetadata();
        Map<String, Object> pending = readMap(md, "profilePending");
        applyPendingProfile(pc, pending, strOrNull(md == null ? null : md.get("profilePendingSubmittedAt")));

        boolean missingPending =
                pc.getPendingUsername() == null
                        && pc.getPendingAvatarUrl() == null
                        && pc.getPendingBio() == null
                        && pc.getPendingLocation() == null
                        && pc.getPendingWebsite() == null
                        && pc.getPendingSubmittedAt() == null;
        if (missingPending && queueId != null) {
            Map<String, Object> snap = readLatestProfilePendingSnapshot(queueId);
            Map<String, Object> snapPending = readMap(snap, "pending_profile");
            applyPendingProfile(pc, snapPending, strOrNull(snap.get("pending_submitted_at")));
        }
        return pc;
    }

    private static void applyPendingProfile(AdminModerationQueueDetailDTO.ProfileContent pc, Map<String, Object> pending, String submittedAt) {
        if (pc == null) return;
        Map<String, Object> source = pending == null ? Map.of() : pending;
        pc.setPendingUsername(strOrNull(source.get("username")));
        pc.setPendingAvatarUrl(strOrNull(source.get("avatarUrl")));
        pc.setPendingBio(strOrNull(source.get("bio")));
        pc.setPendingLocation(strOrNull(source.get("location")));
        pc.setPendingWebsite(strOrNull(source.get("website")));
        pc.setPendingSubmittedAt(submittedAt);
    }

    private Map<String, Object> readLatestProfilePendingSnapshot(Long queueId) {
        if (queueId == null) return Map.of();
        try {
            List<ModerationActionsEntity> actions = moderationActionsRepository.findAllByQueueId(queueId);
            if (actions == null || actions.isEmpty()) return Map.of();
            ModerationActionsEntity best = null;
            for (ModerationActionsEntity a : actions) {
                if (a == null) continue;
                if (a.getAction() != ActionType.NOTE) continue;
                if (!"PROFILE_PENDING_SNAPSHOT".equals(a.getReason())) continue;
                if (best == null) {
                    best = a;
                    continue;
                }
                LocalDateTime ac = a.getCreatedAt();
                LocalDateTime bc = best.getCreatedAt();
                if (ac != null && bc != null) {
                    if (ac.isAfter(bc)) best = a;
                } else if (ac != null) {
                    best = a;
                } else if (bc == null) {
                    Long aid = a.getId();
                    Long bid = best.getId();
                    if (aid != null && bid != null && aid > bid) best = a;
                }
            }
            Map<String, Object> snap = best == null ? null : best.getSnapshot();
            return snap == null ? Map.of() : snap;
        } catch (Exception ignore) {
            return Map.of();
        }
    }

    private static Map<String, Object> readMap(Map<String, Object> md, String key) {
        if (md == null || key == null) return Map.of();
        Object o = md.get(key);
        if (!(o instanceof Map<?, ?> m)) return Map.of();
        Map<String, Object> out = new HashMap<>();
        for (var e : m.entrySet()) {
            Object k = e.getKey();
            if (k == null) continue;
            out.put(String.valueOf(k), e.getValue());
        }
        return out;
    }

    private static String strOrNull(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static AdminModerationQueueDetailDTO.Attachment toAttachment(PostAttachmentsEntity a) {
        return toAttachment(a, null);
    }

    private AdminModerationQueueDetailDTO.PostContent toPostContent(PostsEntity p) {
        if (p == null) return null;
        AdminModerationQueueDetailDTO.PostContent pc = new AdminModerationQueueDetailDTO.PostContent();
        pc.setId(p.getId());
        pc.setBoardId(p.getBoardId());
        pc.setAuthorId(p.getAuthorId());
        pc.setTitle(p.getTitle());
        pc.setContent(p.getContent());
        pc.setStatus(enumName(p.getStatus()));
        pc.setCreatedAt(p.getCreatedAt());
        return pc;
    }

    private static String snippet(String text, int max) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty()) return null;
        if (t.length() <= max) return t;
        return t.substring(0, max) + "...";
    }

    private AdminModerationQueueDetailDTO.CommentContent toCommentContent(CommentsEntity c) {
        if (c == null) return null;
        AdminModerationQueueDetailDTO.CommentContent cc = new AdminModerationQueueDetailDTO.CommentContent();
        cc.setId(c.getId());
        cc.setPostId(c.getPostId());
        cc.setParentId(c.getParentId());
        cc.setAuthorId(c.getAuthorId());
        cc.setContent(c.getContent());
        cc.setStatus(enumName(c.getStatus()));
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
        writeManualQueueAudit(actor, "MODERATION_MANUAL_CLAIM", detail.getId(), "人工操作：认领审核任务", buildQueueAuditDetails(detail));
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
        writeManualQueueAudit(actor, "MODERATION_MANUAL_RELEASE", detail.getId(), "人工操作：释放审核任务", buildQueueAuditDetails(detail));
        return detail;
    }

    @Override
    @Transactional
    public AdminModerationQueueBatchRequeueResponse batchRequeueToAuto(List<Long> ids, String reason, String reviewStage) {
        UsersEntity actor = currentUserOrThrow();
        String checkedReason = requireReason(reason);
        String normalizedReviewStage = normalizeReviewStageInput(reviewStage);
        LinkedHashSet<Long> uniq = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id == null || id <= 0) continue;
                uniq.add(id);
                if (uniq.size() >= 500) break;
            }
        }
        if (uniq.isEmpty()) throw new IllegalArgumentException("ids 不能为空");

        LocalDateTime now = LocalDateTime.now();
        List<Long> successIds = new ArrayList<>();
        List<AdminModerationQueueBatchRequeueResponse.FailedItem> failedItems = new ArrayList<>();

        for (Long id : uniq) {
            try {
                int updated = moderationQueueRepository.requeueToAutoWithReviewStage(id, QueueStatus.PENDING, QueueStage.RULE, normalizedReviewStage, now);
                if (updated <= 0) throw new IllegalStateException("任务不存在或状态不允许");
                try {
                    sealRunningPipelineRun(id);
                } catch (Exception ignore) {
                }
                successIds.add(id);
            } catch (Exception e) {
                AdminModerationQueueBatchRequeueResponse.FailedItem fi = new AdminModerationQueueBatchRequeueResponse.FailedItem();
                fi.setId(id);
                fi.setError(e.getMessage() == null ? "失败" : e.getMessage());
                failedItems.add(fi);
            }
        }

        if (!successIds.isEmpty()) {
            for (Long queueId : successIds) {
                try {
                    moderationAutoKickService.kickQueueId(queueId);
                } catch (Exception ignore) {
                }
            }
        }


        AdminModerationQueueBatchRequeueResponse resp = new AdminModerationQueueBatchRequeueResponse();
        resp.setTotal(uniq.size());
        resp.setSuccess(successIds.size());
        resp.setFailed(failedItems.size());
        resp.setSuccessIds(successIds);
        resp.setFailedItems(failedItems);

        Map<String, Object> details = new HashMap<>();
        details.put("total", resp.getTotal());
        details.put("success", resp.getSuccess());
        details.put("failed", resp.getFailed());
        details.put("reason", checkedReason);
        details.put("reviewStage", normalizedReviewStage);
        details.put("successIds", successIds);
        if (!failedItems.isEmpty()) details.put("failedItems", failedItems);
        auditLogWriter.write(actor.getId(), actorName(actor), "MODERATION_MANUAL_BATCH_REQUEUE", "MODERATION_QUEUE", null, AuditResult.SUCCESS, "人工操作：批量进入再次审核", newTraceId(), details);

        return resp;
    }

    private void sealRunningPipelineRun(Long queueId) {
        ModerationPipelineRunSupport.sealRunningPipelineRun(queueId, moderationPipelineRunRepository);
    }

    private static String normalizeReviewStageInput(String reviewStage) {
        if (reviewStage == null) return null;
        String s = reviewStage.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return null;
        if ("default".equals(s) || "reported".equals(s) || "appeal".equals(s)) return s;
        throw new IllegalArgumentException("reviewStage 仅支持 default/reported/appeal");
    }

    @Override
    @Transactional
    public AdminModerationQueueDetailDTO requeueToAuto(Long id, String reason, String reviewStage) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        UsersEntity actor = currentUserOrThrow();
        String checkedReason = requireReason(reason);
        String normalizedReviewStage = normalizeReviewStageInput(reviewStage);
        // 允许任何有 action 权限的管理员执行；reason 目前仅保留参数位
        int updated = moderationQueueRepository.requeueToAutoWithReviewStage(id, QueueStatus.PENDING, QueueStage.RULE, normalizedReviewStage, LocalDateTime.now());
        if (updated <= 0) {
            throw new IllegalStateException("重新入队失败：任务不存在或状态不允许");
        }

        try {
            sealRunningPipelineRun(id);
        } catch (Exception ignore) {
        }

        // 关键：点击后要求立刻推进 RULE/VEC/LLM。
        // 注意：auto kick 内部按当前 stage best-effort 触发一次。
        try {
            moderationAutoKickService.kickQueueId(id);
        } catch (Exception ignore) {
        }


        AdminModerationQueueDetailDTO detail = getDetail(id);
        Map<String, Object> details = buildQueueAuditDetails(detail);
        details.put("reason", checkedReason);
        details.put("reviewStage", normalizedReviewStage);
        writeManualQueueAudit(actor, "MODERATION_MANUAL_REQUEUE", detail.getId(), "人工操作：重新进入自动审核", details);
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
    public AdminModerationQueueDetailDTO toHuman(Long id, String reason) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        UsersEntity actor = currentUserOrThrow();
        String checkedReason = requireReason(reason);
        int updated = moderationQueueRepository.toHuman(id, QueueStatus.HUMAN, QueueStage.HUMAN, LocalDateTime.now());
        if (updated <= 0) {
            throw new IllegalStateException("进入人工审核失败：仅允许已终态任务进入人工审核");
        }
        AdminModerationQueueDetailDTO detail = getDetail(id);
        Map<String, Object> details = buildQueueAuditDetails(detail);
        details.put("reason", checkedReason);
        auditLogWriter.write(actor.getId(), actorName(actor), "MODERATION_MANUAL_TO_HUMAN", "MODERATION_QUEUE", detail.getId(), AuditResult.SUCCESS, "人工操作：进入人工审核", newTraceId(), details);
        return detail;
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
        Map<String, Object> details = buildQueueAuditDetails(detail);
        details.put("riskTags", riskTags);
        auditLogWriter.write(actor.getId(), actorName(actor), "MODERATION_MANUAL_SET_RISK_TAGS", "MODERATION_QUEUE", detail.getId(), AuditResult.SUCCESS, "人工操作：更新风险标签", newTraceId(), details);
        return detail;
    }

    private Specification<CommentsEntity> buildPendingCommentSpec(LocalDateTime createdFrom, LocalDateTime createdTo) {
        Specification<CommentsEntity> spec = (root, q, cb) -> cb.conjunction();
        spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), CommentStatus.PENDING));
        spec = spec.and((root, q, cb) -> cb.isFalse(root.get("isDeleted")));
        if (createdFrom != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
        }
        if (createdTo != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
        }
        return spec;
    }

    private Map<String, Object> buildQueueAuditDetails(AdminModerationQueueDetailDTO detail) {
        Map<String, Object> details = new HashMap<>();
        if (detail == null) return details;
        putQueueAuditDetails(
                details,
                detail.getId(),
                detail.getCaseType(),
                detail.getContentType(),
                detail.getContentId(),
                detail.getStatus(),
                detail.getCurrentStage(),
                detail.getAssignedToId()
        );
        return details;
    }

    private Map<String, Object> buildQueueAuditDetails(ModerationQueueEntity queue) {
        Map<String, Object> details = new HashMap<>();
        if (queue == null) return details;
        putQueueAuditDetails(
                details,
                queue.getId(),
                queue.getCaseType(),
                queue.getContentType(),
                queue.getContentId(),
                queue.getStatus(),
                queue.getCurrentStage(),
                queue.getAssignedToId()
        );
        return details;
    }

    private static void putQueueAuditDetails(
            Map<String, Object> details,
            Long queueId,
            ModerationCaseType caseType,
            ContentType contentType,
            Long contentId,
            QueueStatus status,
            QueueStage stage,
            Long assignedToId
    ) {
        if (details == null) return;
        details.put("queueId", queueId);
        details.put("caseType", enumName(caseType));
        details.put("contentType", enumName(contentType));
        details.put("contentId", contentId);
        details.put("status", enumName(status));
        details.put("stage", enumName(stage));
        details.put("assignedToId", assignedToId);
    }

    private Map<Long, Integer> countPendingReportsByReporter(ModerationQueueEntity queue) {
        Map<Long, Integer> reportCountByReporterId = new HashMap<>();
        if (queue == null || queue.getContentType() == null || queue.getContentId() == null) return reportCountByReporterId;
        try {
            ReportTargetType targetType = toReportTargetType(queue.getContentType());
            List<ReportsEntity> pending = reportsRepository.findAllByTargetTypeAndTargetIdAndStatus(targetType, queue.getContentId(), ReportStatus.PENDING);
            if (pending == null) return reportCountByReporterId;
            for (ReportsEntity report : pending) {
                if (report == null || report.getReporterId() == null) continue;
                reportCountByReporterId.merge(report.getReporterId(), 1, Integer::sum);
            }
        } catch (Exception ignore) {
        }
        return reportCountByReporterId;
    }

    private ProfileUserState loadProfileUserStateOrThrow(ModerationQueueEntity queue) {
        UsersEntity user = usersRepository.findById(queue.getContentId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + queue.getContentId()));
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new IllegalArgumentException("用户已删除: " + user.getId());
        }
        Map<String, Object> metadata = user.getMetadata() == null ? new HashMap<>() : new HashMap<>(user.getMetadata());
        return new ProfileUserState(user, metadata);
    }

    private void writeManualQueueAudit(UsersEntity actor, String action, Long queueId, String message, Map<String, Object> details) {
        if (actor == null) return;
        auditLogWriter.write(actor.getId(), actorName(actor), action, "MODERATION_QUEUE", queueId, AuditResult.SUCCESS, message, newTraceId(), details);
    }

    private void writeQueueAudit(UsersEntity actor, boolean manual, String traceId, String action, Long queueId, String message, Map<String, Object> details) {
        if (manual) {
            writeManualQueueAudit(actor, action, queueId, message, details);
            return;
        }
        auditLogWriter.writeSystem(action, "MODERATION_QUEUE", queueId, AuditResult.SUCCESS, message, ensureAutoTraceId(traceId), details);
    }

    private AdminModerationQueueDetailDTO overrideDecision(
            Long id,
            String reason,
            QueueStatus targetStatus,
            QueueAction action
    ) {
        if (id == null) throw new IllegalArgumentException("id 不能为空");
        ModerationQueueEntity q = moderationQueueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审核任务不存在: " + id));
        if (q.getStatus() == targetStatus) {
            return getDetail(id);
        }
        if (isOppositeTerminalStatus(q.getStatus(), targetStatus)) {
            transactionalSelf().toHuman(id, reason);
        }
        return action.apply(id, reason);
    }

    private AdminModerationQueueService transactionalSelf() {
        return self != null ? self : this;
    }

    private static boolean isOppositeTerminalStatus(QueueStatus currentStatus, QueueStatus targetStatus) {
        return (currentStatus == QueueStatus.REJECTED && targetStatus == QueueStatus.APPROVED)
                || (currentStatus == QueueStatus.APPROVED && targetStatus == QueueStatus.REJECTED);
    }

    private Long resolveAuthorUserId(ModerationQueueEntity q) {
        if (q == null || q.getContentType() == null || q.getContentId() == null) return null;
        return switch (q.getContentType()) {
            case POST -> {
                PostsEntity post = postsRepository.findById(q.getContentId()).orElse(null);
                yield post == null ? null : post.getAuthorId();
            }
            case COMMENT -> {
                CommentsEntity comment = commentsRepository.findById(q.getContentId()).orElse(null);
                yield comment == null ? null : comment.getAuthorId();
            }
            case PROFILE -> q.getContentId();
        };
    }

    @FunctionalInterface
    private interface QueueAction {
        AdminModerationQueueDetailDTO apply(Long id, String reason);
    }

    private record ProfileUserState(UsersEntity user, Map<String, Object> metadata) {
    }

    private Long currentUserIdOrThrow() {
        String email = requireAuthenticatedEmail();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"))
                .getId();
    }

    private UsersEntity currentUserOrThrow() {
        String email = requireAuthenticatedEmail();
        return administratorService.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"));
    }

    private String requireAuthenticatedEmail() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new org.springframework.security.core.AuthenticationException("未登录或会话已过期") {
            };
        }
        return auth.getName();
    }

    private String actorName(UsersEntity user) {
        if (user == null) return null;
        String u = user.getUsername();
        if (u != null && !u.isBlank()) return u;
        return user.getEmail();
    }

    private String requireReason(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) throw new IllegalArgumentException("reason 不能为空");
        return r;
    }

    private String ensureAutoTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) return traceId;
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String newTraceId() {
        return "human-" + UUID.randomUUID();
    }
}
