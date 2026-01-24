package com.example.EnterpriseRagCommunity.dto.retrieval;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import lombok.Data;

@Data
public class ContextClipConfigDTO {
    private Boolean enabled;

    private ContextWindowPolicy policy;

    private Integer maxItems;
    private Integer maxContextTokens;
    private Integer reserveAnswerTokens;
    private Integer perItemMaxTokens;
    private Integer maxPromptChars;

    private Double minScore;
    private Integer maxSamePostItems;
    private Boolean requireTitle;

    private Boolean dedupByPostId;
    private Boolean dedupByTitle;
    private Boolean dedupByContentHash;

    private String sectionTitle;
    private String itemHeaderTemplate;
    private String separator;

    private Boolean showPostId;
    private Boolean showChunkIndex;
    private Boolean showScore;
    private Boolean showTitle;

    private String extraInstruction;

    private Boolean logEnabled;
    private Double logSampleRate;
    private Integer logMaxDays;
}

