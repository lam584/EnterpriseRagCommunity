package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotStatus;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotTargetType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class PostComposeAiSnapshotDTO {
    private Long id;
    private Long tenantId;
    private Long userId;
    private PostComposeAiSnapshotTargetType targetType;
    private Long draftId;
    private Long postId;

    private String beforeTitle;
    private String beforeContent;
    private Long beforeBoardId;
    private Map<String, Object> beforeMetadata;

    private String afterContent;
    private String instruction;
    private String providerId;
    private String model;
    private Double temperature;
    private Double topP;
    private PostComposeAiSnapshotStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}

