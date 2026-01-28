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

import com.example.EnterpriseRagCommunity.dto.content.CommentCreateRequest;
import com.example.EnterpriseRagCommunity.dto.content.CommentDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.content.CommentsEntity;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.content.CommentsRepository;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiLanguageDetectService;
import com.example.EnterpriseRagCommunity.service.content.CommentsService;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationLlmAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationVecAutoRunner;
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
    private ModerationRuleAutoRunner moderationRuleAutoRunner;

    @Autowired
    private ModerationVecAutoRunner moderationVecAutoRunner;

    @Autowired
    private ModerationLlmAutoRunner moderationLlmAutoRunner;

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

        Long me = currentUserIdOrThrow();

        CommentsEntity e = new CommentsEntity();
        e.setPostId(postId);
        e.setParentId(req.getParentId());
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

        // 业务规则：用户评论默认进入待审核状态；审核通过后再改为 VISIBLE
        e.setStatus(CommentStatus.PENDING);

        e.setIsDeleted(false);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());

        CommentsEntity saved = commentsRepository.save(e);

        // 新增：写入审核队列（防重复）
        adminModerationQueueService.ensureEnqueuedComment(saved.getId());

        try {
            moderationRuleAutoRunner.runOnce();
            moderationVecAutoRunner.runOnce();
            moderationLlmAutoRunner.runOnce();
        } catch (Exception ignore) {
        }

        // 回复通知：仅“有人评论了我发布的帖子（顶层评论）”才通知；多级回复暂不通知。
        if (req.getParentId() == null) {
            PostsEntity post = postsRepository.findById(postId).orElse(null);
            if (post != null && post.getAuthorId() != null && !me.equals(post.getAuthorId())) {
                String postTitle = post.getTitle() == null ? "" : post.getTitle();
                String title = "有人评论了你的帖子";
                String content = (postTitle.isBlank() ? "" : ("帖子《" + postTitle + "》")) + "收到了新的评论：" + req.getContent();
                // 避免 content 太长（表字段是 TEXT，但前端展示也需要可读性）
                if (content.length() > 500) {
                    content = content.substring(0, 500) + "...";
                }
                notificationsService.createNotification(post.getAuthorId(), "REPLY_POST", title, content);
            }
        }

        return toDTO(saved);
    }

    @Override
    public long countByPostId(Long postId) {
        if (postId == null) return 0;
        return commentsRepository.countByPostIdAndStatusAndIsDeletedFalse(postId, CommentStatus.VISIBLE);
    }
}
