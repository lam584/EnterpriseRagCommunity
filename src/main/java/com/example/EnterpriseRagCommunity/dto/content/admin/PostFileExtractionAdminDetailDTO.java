package com.example.EnterpriseRagCommunity.dto.content.admin;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class PostFileExtractionAdminDetailDTO extends PostFileExtractionAdminListItemDTO {
    private String extractedText;
    private String extractedMetadataJson;
    private Map<String, Object> extractedMetadata;
    private List<Map<String, Object>> extractedImages;
    private String llmInputPreview;
}
