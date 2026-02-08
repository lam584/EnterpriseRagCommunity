package com.example.EnterpriseRagCommunity.dto.moderation;

import java.time.LocalDateTime;
import java.util.List;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;

import lombok.Data;

@Data
public class AdminModerationQueueDetailDTO {
    private Long id;

    private ModerationCaseType caseType;

    private ContentType contentType;
    private Long contentId;

    private QueueStatus status;
    private QueueStage currentStage;

    private Integer priority;
    private Long assignedToId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<String> riskTags;

    private AdminModerationQueueItemDTO.Summary summary;

    /** 详情内容：根据 contentType 选择 post/comment */
    private PostContent post;
    private CommentContent comment;

    private List<ReportInfo> reports;

    @Data
    public static class ReportInfo {
        private Long id;
        private Long reporterId;
        private String reasonCode;
        private String reasonText;
        private String status;
        private LocalDateTime createdAt;
    }

    @Data
    public static class PostContent {
        private Long id;
        private Long boardId;
        private Long authorId;
        private String title;
        private String content;
        private List<Attachment> attachments;
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

    @Data
    public static class Attachment {
        private Long id;
        private Long fileAssetId;
        private String url;
        private String fileName;
        private String mimeType;
        private Long sizeBytes;
        private Integer width;
        private Integer height;
        private LocalDateTime createdAt;
    }
}

