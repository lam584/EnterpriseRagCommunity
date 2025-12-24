package com.example.EnterpriseRagCommunity.service.content;

import com.example.EnterpriseRagCommunity.dto.content.PostToggleResponseDTO;

public interface PostInteractionsService {
    PostToggleResponseDTO toggleLike(Long postId);

    PostToggleResponseDTO toggleFavorite(Long postId);

    long countLikes(Long postId);

    long countFavorites(Long postId);

    boolean likedByMe(Long postId);

    boolean favoritedByMe(Long postId);
}

