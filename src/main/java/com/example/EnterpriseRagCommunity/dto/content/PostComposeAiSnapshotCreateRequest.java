package com.example.EnterpriseRagCommunity.dto.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class PostComposeAiSnapshotCreateRequest {
    @NotNull
    private PostComposeAiSnapshotTargetType targetType;

    private Long draftId;
    private Long postId;

    @Size(max = 191)
    private String beforeTitle;

    @NotNull
    private String beforeContent;

    @NotNull
    private Long beforeBoardId;

    private Map<String, Object> beforeMetadata;

    @Size(max = 8000)
    private String instruction;

    @Size(max = 128)
    private String providerId;

    @Size(max = 128)
    private String model;

    private Double temperature;

    private Double topP;
}
