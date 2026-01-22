package com.example.EnterpriseRagCommunity.dto.moderation;

import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import lombok.Data;

@Data
public class SimilarityCheckRequest {

    /** For admin manual check: the text to check. */
    private String text;

    /** Optional: tie to a moderation_queue content for auditing. */
    private ContentType contentType;
    private Long contentId;

    /** Optional override. */
    private Integer topK;
    private Double threshold;

    private Integer numCandidates;
    private String embeddingModel;
    private Integer embeddingDims;
    private Integer maxInputChars;
}
