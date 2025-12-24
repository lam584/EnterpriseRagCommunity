package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.dto.content.PostToggleResponseDTO;
import com.example.EnterpriseRagCommunity.entity.content.FavoritesEntity;
import com.example.EnterpriseRagCommunity.entity.content.ReactionsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionTargetType;
import com.example.EnterpriseRagCommunity.entity.content.enums.ReactionType;
import com.example.EnterpriseRagCommunity.repository.content.FavoritesRepository;
import com.example.EnterpriseRagCommunity.repository.content.ReactionsRepository;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.content.PostInteractionsService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PostInteractionsServiceImpl implements PostInteractionsService {

    @Autowired
    private ReactionsRepository reactionsRepository;

    @Autowired
    private FavoritesRepository favoritesRepository;

    @Autowired
    private AdministratorService administratorService;

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
        }

        long likeCount = reactionsRepository.countByTargetTypeAndTargetIdAndType(
                ReactionTargetType.POST, postId, ReactionType.LIKE
        );
        long favCount = favoritesRepository.countByPostId(postId);
        boolean likedByMe = !existed;
        boolean favByMe = favoritesRepository.existsByUserIdAndPostId(me, postId);
        return new PostToggleResponseDTO(likedByMe, favByMe, likeCount, favCount);
    }

    @Override
    @Transactional
    public PostToggleResponseDTO toggleFavorite(Long postId) {
        if (postId == null) throw new IllegalArgumentException("postId 不能为空");
        Long me = currentUserIdOrThrow();

        boolean existed = favoritesRepository.existsByUserIdAndPostId(me, postId);
        if (existed) {
            favoritesRepository.deleteByUserIdAndPostId(me, postId);
        } else {
            FavoritesEntity e = new FavoritesEntity();
            e.setPostId(postId);
            e.setUserId(me);
            e.setCreatedAt(LocalDateTime.now());
            favoritesRepository.save(e);
        }

        long likeCount = reactionsRepository.countByTargetTypeAndTargetIdAndType(
                ReactionTargetType.POST, postId, ReactionType.LIKE
        );
        long favCount = favoritesRepository.countByPostId(postId);
        boolean likedByMe = reactionsRepository.existsByUserIdAndTargetTypeAndTargetIdAndType(
                me, ReactionTargetType.POST, postId, ReactionType.LIKE
        );
        boolean favByMe = !existed;
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
        return favoritesRepository.countByPostId(postId);
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
        return favoritesRepository.existsByUserIdAndPostId(me, postId);
    }
}
