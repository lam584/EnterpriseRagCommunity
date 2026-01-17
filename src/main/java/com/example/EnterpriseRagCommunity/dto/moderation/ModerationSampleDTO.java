package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.ModerationSamplesEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModerationSampleDTO {

    private Long id;
    private ModerationSamplesEntity.Category category;
    private ModerationSamplesEntity.ContentType refContentType;
    private Long refContentId;

    private String rawText;
    private String normalizedText;
    private String textHash;

    private Integer riskLevel;
    /** JSON string in MySQL. */
    private String labels;

    private ModerationSamplesEntity.Source source;
    private Boolean enabled;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Whether this sample was synced to ES during this request.
     * null means unknown/not attempted (for backward compatibility).
     */
    private Boolean esSynced;

    /**
     * Optional message for ES sync result, for UI hint.
     */
    private String esSyncMessage;

    public static ModerationSampleDTO fromEntity(ModerationSamplesEntity e) {
        if (e == null) return null;
        ModerationSampleDTO dto = new ModerationSampleDTO();
        dto.setId(e.getId());
        dto.setCategory(e.getCategory());
        dto.setRefContentType(e.getRefContentType());
        dto.setRefContentId(e.getRefContentId());
        dto.setRawText(e.getRawText());
        dto.setNormalizedText(e.getNormalizedText());
        dto.setTextHash(e.getTextHash());
        dto.setRiskLevel(e.getRiskLevel());
        dto.setLabels(e.getLabels());
        dto.setSource(e.getSource());
        dto.setEnabled(e.getEnabled());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    public static ModerationSampleDTO fromEntityWithEsSync(ModerationSamplesEntity e, Boolean esSynced, String esSyncMessage) {
        ModerationSampleDTO dto = fromEntity(e);
        if (dto == null) return null;
        dto.setEsSynced(esSynced);
        dto.setEsSyncMessage(esSyncMessage);
        return dto;
    }
}
