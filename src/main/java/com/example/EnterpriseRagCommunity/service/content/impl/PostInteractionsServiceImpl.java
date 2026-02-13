package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostToggleResponseDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReactionsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.content.PostInteractionsService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class PostInteractionsServiceImpl implements PostInteractionsService {

    @Autowired
    private ReactionsRepository reactionsRepository;

    @Autowired
    private AdministratorService administratorService;

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private NotificationsService notificationsService;

    @Autowired
    private AuditLogWriter auditLogWriter;

    private Long currentUserIdOrThrow() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
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
    public PostToggleResponseDTO toggleLike(Long postId) {
        if (postId == null) throw new IllegalArgumentException("postId 不能为空");
        Long me = null;
        String actorName = currentUsernameOrNull();
        try {
            me = currentUserIdOrThrow();

            boolean existed = reactionsRepository.existsByUserIdAndTargetTypeAndTargetIdAndType(
                    me, ReactionTargetType.POST, postId, ReactionType.LIKE
            );
            if (existed) {
                reactionsRepository.deleteByUserIdAndTargetTypeAndTargetIdAndType(
                        me, ReactionTargetType.POST, postId, ReactionType.LIKE
                );
            } else {
                ReactionsEntity e = new ReactionsEntity();
                e.setUserId(me);
                e.setTargetType(ReactionTargetType.POST);
                e.setTargetId(postId);
                e.setType(ReactionType.LIKE);
                e.setCreatedAt(LocalDateTime.now());
                reactionsRepository.save(e);

                PostsEntity post = postsRepository.findById(postId).orElse(null);
                if (post != null && post.getAuthorId() != null && !me.equals(post.getAuthorId())) {
                    String title = "有人点赞了你的帖子";
                    String content = "你的帖子《" + post.getTitle() + "》收到一个赞。";
                    notificationsService.createNotification(post.getAuthorId(), "LIKE_POST", title, content);
                }
            }

            long likeCount = reactionsRepository.countByTargetTypeAndTargetIdAndType(
                    ReactionTargetType.POST, postId, ReactionType.LIKE
            );
            long favCount = reactionsRepository.countByTargetTypeAndTargetIdAndType(
                    ReactionTargetType.POST, postId, ReactionType.FAVORITE
            );
            boolean likedByMe = !existed;
            boolean favByMe = reactionsRepository.existsByUserIdAndTargetTypeAndTargetIdAndType(
                    me, ReactionTargetType.POST, postId, ReactionType.FAVORITE
            );

            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_LIKE_TOGGLE",
                    "POST",
                    postId,
                    AuditResult.SUCCESS,
                    likedByMe ? "点赞帖子" : "取消点赞帖子",
                    null,
                    Map.of(
                            "liked", likedByMe,
                            "likeCount", likeCount,
                            "favoriteCount", favCount
                    )
            );
            return new PostToggleResponseDTO(likedByMe, favByMe, likeCount, favCount);
        } catch (RuntimeException e) {
            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_LIKE_TOGGLE",
                    "POST",
                    postId,
                    AuditResult.FAIL,
                    safeText(e.getMessage(), 512),
                    null,
                    Map.of()
            );
            throw e;
        }
    }

    @Override
    @Transactional
    public PostToggleResponseDTO toggleFavorite(Long postId) {
        if (postId == null) throw new IllegalArgumentException("postId 不能为空");
        Long me = null;
        String actorName = currentUsernameOrNull();
        try {
            me = currentUserIdOrThrow();

            boolean existed = reactionsRepository.existsByUserIdAndTargetTypeAndTargetIdAndType(
                    me, ReactionTargetType.POST, postId, ReactionType.FAVORITE
            );
            if (existed) {
                reactionsRepository.deleteByUserIdAndTargetTypeAndTargetIdAndType(
                        me, ReactionTargetType.POST, postId, ReactionType.FAVORITE
                );
            } else {
                ReactionsEntity e = new ReactionsEntity();
                e.setUserId(me);
                e.setTargetType(ReactionTargetType.POST);
                e.setTargetId(postId);
                e.setType(ReactionType.FAVORITE);
                e.setCreatedAt(LocalDateTime.now());
                reactionsRepository.save(e);
            }

            long likeCount = reactionsRepository.countByTargetTypeAndTargetIdAndType(
                    ReactionTargetType.POST, postId, ReactionType.LIKE
            );
            long favCount = reactionsRepository.countByTargetTypeAndTargetIdAndType(
                    ReactionTargetType.POST, postId, ReactionType.FAVORITE
            );
            boolean likedByMe = reactionsRepository.existsByUserIdAndTargetTypeAndTargetIdAndType(
                    me, ReactionTargetType.POST, postId, ReactionType.LIKE
            );
            boolean favByMe = !existed;

            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_FAVORITE_TOGGLE",
                    "POST",
                    postId,
                    AuditResult.SUCCESS,
                    favByMe ? "收藏帖子" : "取消收藏帖子",
                    null,
                    Map.of(
                            "favorited", favByMe,
                            "likeCount", likeCount,
                            "favoriteCount", favCount
                    )
            );
            return new PostToggleResponseDTO(likedByMe, favByMe, likeCount, favCount);
        } catch (RuntimeException e) {
            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_FAVORITE_TOGGLE",
                    "POST",
                    postId,
                    AuditResult.FAIL,
                    safeText(e.getMessage(), 512),
                    null,
                    Map.of()
            );
            throw e;
        }
    }

    @Override
    @Transactional
    public PostToggleResponseDTO unfavorite(Long postId) {
        if (postId == null) throw new IllegalArgumentException("postId 不能为空");
        Long me = null;
        String actorName = currentUsernameOrNull();
        try {
            me = currentUserIdOrThrow();

            reactionsRepository.deleteByUserIdAndTargetTypeAndTargetIdAndType(
                    me, ReactionTargetType.POST, postId, ReactionType.FAVORITE
            );

            long likeCount = reactionsRepository.countByTargetTypeAndTargetIdAndType(
                    ReactionTargetType.POST, postId, ReactionType.LIKE
            );
            long favCount = reactionsRepository.countByTargetTypeAndTargetIdAndType(
                    ReactionTargetType.POST, postId, ReactionType.FAVORITE
            );
            boolean likedByMe = reactionsRepository.existsByUserIdAndTargetTypeAndTargetIdAndType(
                    me, ReactionTargetType.POST, postId, ReactionType.LIKE
            );
            boolean favByMe = false;

            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_UNFAVORITE",
                    "POST",
                    postId,
                    AuditResult.SUCCESS,
                    "取消收藏帖子",
                    null,
                    Map.of(
                            "favorited", false,
                            "likeCount", likeCount,
                            "favoriteCount", favCount
                    )
            );
            return new PostToggleResponseDTO(likedByMe, favByMe, likeCount, favCount);
        } catch (RuntimeException e) {
            auditLogWriter.write(
                    me,
                    actorName,
                    "POST_UNFAVORITE",
                    "POST",
                    postId,
                    AuditResult.FAIL,
                    safeText(e.getMessage(), 512),
                    null,
                    Map.of()
            );
            throw e;
        }
    }

    private static String currentUsernameOrNull() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
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

    @Override
    public long countLikes(Long postId) {
        if (postId == null) return 0;
        return reactionsRepository.countByTargetTypeAndTargetIdAndType(
                ReactionTargetType.POST, postId, ReactionType.LIKE
        );
    }

    @Override
    public long countFavorites(Long postId) {
        if (postId == null) return 0;
        return reactionsRepository.countByTargetTypeAndTargetIdAndType(
                ReactionTargetType.POST, postId, ReactionType.FAVORITE
        );
    }

    @Override
    public boolean likedByMe(Long postId) {
        if (postId == null) return false;
        Long me = currentUserIdOrThrow();
        return reactionsRepository.existsByUserIdAndTargetTypeAndTargetIdAndType(
                me, ReactionTargetType.POST, postId, ReactionType.LIKE
        );
    }

    @Override
    public boolean favoritedByMe(Long postId) {
        if (postId == null) return false;
        Long me = currentUserIdOrThrow();
        return reactionsRepository.existsByUserIdAndTargetTypeAndTargetIdAndType(
                me, ReactionTargetType.POST, postId, ReactionType.FAVORITE
        );
    }
}
