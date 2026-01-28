package com.example.EnterpriseRagCommunity.dto.content;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class CommentDTO {
    private Long id;
    private Long postId;
    private Long parentId;
    private Long authorId;

    private String content;
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Map<String, Object> metadata;

    // optional display
    private String authorName;
    private String authorAvatarUrl;
    private String authorLocation;
    private Long likeCount;
    private Boolean likedByMe;
}

