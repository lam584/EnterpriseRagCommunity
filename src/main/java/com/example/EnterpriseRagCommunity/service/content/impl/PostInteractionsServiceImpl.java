package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostToggleResponseDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReactionsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.content.PostInteractionsService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
        Long me = currentUserIdOrThrow();

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

            // 产生通知：给帖子作者发“被点赞”通知（自己点赞自己不发）
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
        return new PostToggleResponseDTO(likedByMe, favByMe, likeCount, favCount);
    }

    @Override
    @Transactional
    public PostToggleResponseDTO toggleFavorite(Long postId) {
        if (postId == null) throw new IllegalArgumentException("postId 不能为空");
        Long me = currentUserIdOrThrow();

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
        return new PostToggleResponseDTO(likedByMe, favByMe, likeCount, favCount);
    }

    @Override
    @Transactional
    public PostToggleResponseDTO unfavorite(Long postId) {
        if (postId == null) throw new IllegalArgumentException("postId 不能为空");
        Long me = currentUserIdOrThrow();

        // deleteBy.. 本身是幂等的：无记录不会报错
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
        return new PostToggleResponseDTO(likedByMe, favByMe, likeCount, favCount);
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
