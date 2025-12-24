package com.example.EnterpriseRagCommunity.dto.content;

import lombok.Data;

import java.time.LocalDateTime;

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

    // optional display
    private String authorName;
}

