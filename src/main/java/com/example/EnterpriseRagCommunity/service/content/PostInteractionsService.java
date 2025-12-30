package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.PostToggleResponseDTO;

public interface PostInteractionsService {
    PostToggleResponseDTO toggleLike(Long postId);

    PostToggleResponseDTO toggleFavorite(Long postId);

    /**
     * 取消收藏（幂等）：如果不存在收藏记录也会返回成功态。
     */
    PostToggleResponseDTO unfavorite(Long postId);

    long countLikes(Long postId);

    long countFavorites(Long postId);

    boolean likedByMe(Long postId);

    boolean favoritedByMe(Long postId);
}
