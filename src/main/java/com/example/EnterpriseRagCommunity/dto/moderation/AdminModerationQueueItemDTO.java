package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminModerationQueueItemDTO {
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

    private ChunkProgress chunkProgress;

    /** 统一的展示用摘要 */
    private Summary summary;

    @Data
    public static class Summary {
        /** POST: title；COMMENT: 所属帖子标题（可为空） */
        private String title;
        /** POST/COMMENT: 内容摘要 */
        private String snippet;
        private Long authorId;
        private String authorName;

        /** COMMENT 专用：所属帖子ID */
        private Long postId;
    }

    @Data
    public static class ChunkProgress {
        private String status;
        private Integer totalChunks;
        private Integer completedChunks;
        private Integer failedChunks;
        private LocalDateTime updatedAt;
    }
}
