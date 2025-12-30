package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminModerationQueueDetailDTO {
    private Long id;

    private ContentType contentType;
    private Long contentId;

    private QueueStatus status;
    private QueueStage currentStage;

    private Integer priority;
    private Long assignedToId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private AdminModerationQueueItemDTO.Summary summary;

    /** 详情内容：根据 contentType 选择 post/comment */
    private PostContent post;
    private CommentContent comment;

    @Data
    public static class PostContent {
        private Long id;
        private Long boardId;
        private Long authorId;
        private String title;
        private String content;
        private String status;
        private LocalDateTime createdAt;
    }

    @Data
    public static class CommentContent {
        private Long id;
        private Long postId;
        private Long parentId;
        private Long authorId;
        private String content;
        private String status;
        private LocalDateTime createdAt;
    }
}

