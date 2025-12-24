package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Feed/detail DTO for portal pages.
 * Mirrors fields expected by my-vite-app/src/services/postService.ts PostDTO.
 */
@Data
public class PostDetailDTO {
    private Long id;
    private Long tenantId;
    private Long boardId;
    private Long authorId;

    private String title;
    private String content;
    private ContentFormat contentFormat;
    private PostStatus status;

    private String authorName;
    private String boardName;

    // Aggregates for discover/detail
    private Long commentCount;
    private Long reactionCount;
    private Long favoriteCount;
    private Boolean likedByMe;
    private Boolean favoritedByMe;

    // optional
    private Double hotScore;

    private Map<String, Object> metadata;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
}

