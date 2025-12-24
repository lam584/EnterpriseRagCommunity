package com.example.EnterpriseRagCommunity.dto.content.admin;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentAdminDTO {
    private Long id;
    private Long postId;
    private Long parentId;
    private Long authorId;

    private String content;
    private String status;

    private Boolean isDeleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // optional display
    private String authorName;

    // optional display: post info (filled by admin list endpoint)
    private String postTitle;
    /**
     * A short excerpt for admin list display. Prefer plain text + truncated length.
     */
    private String postExcerpt;
}
