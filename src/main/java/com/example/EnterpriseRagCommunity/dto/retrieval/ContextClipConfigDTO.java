package com.example.EnterpriseRagCommunity.dto.retrieval;

import com.example.EnterpriseRagCommunity.entity.semantic.enums.ContextWindowPolicy;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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
    @Min(100)
    @Max(1_000_000)
    private Integer contextTokenBudget;

    private Double minScore;
    private Integer maxSamePostItems;
    private Boolean requireTitle;
    @DecimalMin("0.0")
    @DecimalMax("10.0")
    private Double alpha;
    @DecimalMin("0.0")
    @DecimalMax("10.0")
    private Double beta;
    @DecimalMin("0.0")
    @DecimalMax("10.0")
    private Double gamma;
    @Pattern(
            regexp = "^(NONE|REL_ONLY|REL_IMP|REL_IMP_RED)$",
            flags = Pattern.Flag.CASE_INSENSITIVE
    )
    private String ablationMode;
    private Boolean crossSourceDedup;

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
