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
    private List<AdminModerationQueueRiskTagItemDTO> riskTagItems;

    private AdminModerationQueueItemDTO.Summary summary;

    private AdminModerationQueueItemDTO.ChunkProgress chunkProgress;

    /** 详情内容：根据 contentType 选择 post/comment */
    private PostContent post;
    private CommentContent comment;
    private ProfileContent profile;

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

        private String extractStatus;
        private Integer extractedTextChars;
        private String extractedTextSnippet;
        private String extractedMetadataJsonSnippet;
        private String extractionErrorMessage;
        private LocalDateTime extractionUpdatedAt;
    }

    @Data
    public static class ProfileContent {
        private Long id;
        private String publicUsername;
        private String publicAvatarUrl;
        private String publicBio;
        private String publicLocation;
        private String publicWebsite;

        private String pendingUsername;
        private String pendingAvatarUrl;
        private String pendingBio;
        private String pendingLocation;
        private String pendingWebsite;
        private String pendingSubmittedAt;
    }
}

