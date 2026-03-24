package com.example.EnterpriseRagCommunity.service.content.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.EnterpriseRagCommunity.dto.content.CommentCreateRequest;
import com.example.EnterpriseRagCommunity.dto.content.CommentDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.ai.AiLanguageDetectService;
import com.example.EnterpriseRagCommunity.service.content.CommentsService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationAutoKickService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;

import jakarta.transaction.Transactional;

@Service
public class CommentsServiceImpl implements CommentsService {

    @Autowired
    private CommentsRepository commentsRepository;

    @Autowired
    private AdministratorService administratorService;

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private NotificationsService notificationsService;

    @Autowired
    private AdminModerationQueueService adminModerationQueueService;

    @Autowired
    private ModerationQueueRepository moderationQueueRepository;

    @Autowired
    private ModerationAutoKickService moderationAutoKickService;

    @Autowired
    private com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner moderationRuleAutoRunner;

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

    private Long currentUserIdOrNull() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
            String email = auth.getName();
            return administratorService.findByUsername(email).map(UsersEntity::getId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private ReactionsRepository reactionsRepository;

    @Autowired
    private AiLanguageDetectService aiLanguageDetectService;

    @Autowired
    private AuditLogWriter auditLogWriter;

    private static String extractProfileString(UsersEntity u, String key) {
        if (u == null || u.getMetadata() == null) return null;
        Object profileObj = u.getMetadata().get("profile");
        if (!(profileObj instanceof Map<?, ?> m)) return null;
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static CommentDTO toDTO(CommentsEntity e) {
        CommentDTO dto = new CommentDTO();
        dto.setId(e.getId());
        dto.setPostId(e.getPostId());
        dto.setParentId(e.getParentId());
        dto.setAuthorId(e.getAuthorId());
        dto.setContent(e.getContent());
        dto.setStatus(e.getStatus() == null ? null : e.getStatus().name());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        dto.setMetadata(e.getMetadata());
        return dto;
    }

    @Override
    public Page<CommentDTO> listByPostId(Long postId, int page, int pageSize, boolean includeMinePending) {
        if (postId == null) throw new IllegalArgumentException("postId 不能为空");
        int safePage = Math.max(page, 1);
        int safePageSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CommentsEntity> entityPage;
        Long mineUserId = includeMinePending ? currentUserIdOrNull() : null;
        if (includeMinePending && mineUserId != null) {
            entityPage = commentsRepository.findVisibleOrMinePending(postId, mineUserId, pageable);
        } else {
            entityPage = commentsRepository.findByPostIdAndStatusAndIsDeletedFalse(postId, CommentStatus.VISIBLE, pageable);
        }
        List<CommentsEntity> entities = entityPage.getContent();
        List<CommentDTO> dtos = entities.stream().map(CommentsServiceImpl::toDTO).toList();

        Set<Long> authorIds = entities.stream()
                .map(CommentsEntity::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UsersEntity> usersById = usersRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(UsersEntity::getId, x -> x));

        List<Long> commentIds = entities.stream()
                .map(CommentsEntity::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, Long> likeCountByCommentId = new HashMap<>();
        if (!commentIds.isEmpty()) {
            for (Object[] row : reactionsRepository.countByTargetIdsGrouped(ReactionTargetType.COMMENT, ReactionType.LIKE, commentIds)) {
                if (row == null || row.length < 2) continue;
                Long targetId = row[0] == null ? null : ((Number) row[0]).longValue();
                Long cnt = row[1] == null ? 0L : ((Number) row[1]).longValue();
                if (targetId != null) likeCountByCommentId.put(targetId, cnt);
            }
        }

        Long viewerId = currentUserIdOrNull();
        Set<Long> likedIds = viewerId == null || commentIds.isEmpty()
                ? Set.of()
                : Set.copyOf(reactionsRepository.findTargetIdsLikedByUser(viewerId, ReactionTargetType.COMMENT, ReactionType.LIKE, commentIds));

        for (CommentDTO dto : dtos) {
            UsersEntity u = dto.getAuthorId() == null ? null : usersById.get(dto.getAuthorId());
            if (dto.getAuthorName() == null && u != null) dto.setAuthorName(u.getUsername());
            if (u != null) {
                dto.setAuthorAvatarUrl(extractProfileString(u, "avatarUrl"));
                dto.setAuthorLocation(extractProfileString(u, "location"));
            }
            long likeCount = dto.getId() == null ? 0L : likeCountByCommentId.getOrDefault(dto.getId(), 0L);
            dto.setLikeCount(likeCount);
            dto.setLikedByMe(dto.getId() != null && likedIds.contains(dto.getId()));
        }

        return new PageImpl<>(dtos, pageable, entityPage.getTotalElements());
    }

    @Override
    @Transactional
    public CommentDTO createForPost(Long postId, CommentCreateRequest req) {
        if (postId == null) throw new IllegalArgumentException("postId 不能为空");
        if (req == null) throw new IllegalArgumentException("参数不能为空");
        Long me = null;
        String actorName = currentUsernameOrNull();
        Long parentId = req.getParentId();
        try {
            me = currentUserIdOrThrow();

            CommentsEntity e = new CommentsEntity();
            e.setPostId(postId);
            e.setParentId(parentId);
            e.setAuthorId(me);
            e.setContent(req.getContent());

            try {
                List<String> langs = aiLanguageDetectService.detectLanguages(req.getContent());
                if (langs != null && !langs.isEmpty()) {
                    Map<String, Object> meta = e.getMetadata() == null ? new HashMap<>() : new HashMap<>(e.getMetadata());
                    meta.put("languages", langs);
                    e.setMetadata(meta);
                }
            } catch (Exception ignore) {
            }

            e.setStatus(CommentStatus.PENDING);

            e.setIsDeleted(false);
            e.setCreatedAt(LocalDateTime.now());
            e.setUpdatedAt(LocalDateTime.now());

            CommentsEntity saved = commentsRepository.save(e);

            adminModerationQueueService.ensureEnqueuedComment(saved.getId());
            moderationQueueRepository.flush(); // Ensure queue row is visible
            Long queueId = moderationQueueRepository
                    .findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.COMMENT, saved.getId())
                    .map(q -> q.getId())
                    .orElse(null);
            if (queueId != null) {
                moderationRuleAutoRunner.runForQueueId(queueId);
                saved = commentsRepository.findById(saved.getId()).orElse(saved);
            }
            scheduleModerationAutoRunAfterCommit(queueId);

            if (parentId == null) {
                PostsEntity post = postsRepository.findById(postId).orElse(null);
                if (post != null && post.getAuthorId() != null && !me.equals(post.getAuthorId())) {
                    String postTitle = post.getTitle() == null ? "" : post.getTitle();
                    String title = "有人评论了你的帖子";
                    String content = (postTitle.isBlank() ? "" : ("帖子《" + postTitle + "》")) + "收到了新的评论：" + req.getContent();
                    if (content.length() > 500) {
                        content = content.substring(0, 500) + "...";
                    }
                    notificationsService.createNotification(post.getAuthorId(), "REPLY_POST", title, content);
                }
            }

            Map<String, Object> successDetails = new HashMap<>();
            successDetails.put("postId", postId);
            successDetails.put("parentId", parentId);
            successDetails.put("status", saved.getStatus() == null ? null : saved.getStatus().name());
            auditLogWriter.write(
                    me,
                    actorName,
                    "COMMENT_CREATE",
                    "COMMENT",
                    saved.getId(),
                    AuditResult.SUCCESS,
                    "发表评论",
                    null,
                    successDetails
            );

            return toDTO(saved);
        } catch (RuntimeException ex) {
            Map<String, Object> failDetails = new HashMap<>();
            failDetails.put("postId", postId);
            failDetails.put("parentId", parentId);
            auditLogWriter.write(
                    me,
                    actorName,
                    "COMMENT_CREATE",
                    "COMMENT",
                    null,
                    AuditResult.FAIL,
                    safeText(ex.getMessage(), 512),
                    null,
                    failDetails
            );
            throw ex;
        }
    }

    @Override
    public long countByPostId(Long postId) {
        if (postId == null) return 0;
        return commentsRepository.countByPostIdAndStatusAndIsDeletedFalse(postId, CommentStatus.VISIBLE);
    }

    private void scheduleModerationAutoRunAfterCommit(Long queueId) {
        if (queueId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        moderationAutoKickService.kickQueueId(queueId);
                    } catch (Exception ignore) {
                    }
                }
            });
            return;
        }
        try {
            moderationAutoKickService.kickQueueId(queueId);
        } catch (Exception ignore) {
        }
    }

    private static String currentUsernameOrNull() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
            String name = auth.getName();
            return name == null || name.isBlank() ? null : name.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeText(String s, int maxLen) {
        if (s == null) return null;
        String t = s.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (t.isBlank()) return null;
        if (maxLen <= 0) return "";
        return t.length() <= maxLen ? t : t.substring(0, maxLen);
    }
}
