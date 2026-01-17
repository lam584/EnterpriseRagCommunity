package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSamplesEntity;
import lombok.Data;

@Data
public class ModerationSampleUpdateRequest {

    private ModerationSamplesEntity.Category category;

    private ModerationSamplesEntity.ContentType refContentType;
    private Long refContentId;

    /** If provided, will re-normalize and re-hash. */
    private String rawText;

    private Integer riskLevel;
    /** JSON string (e.g. ["ad","spam"]). */
    private String labels;

    private ModerationSamplesEntity.Source source;
    private Boolean enabled;
}
