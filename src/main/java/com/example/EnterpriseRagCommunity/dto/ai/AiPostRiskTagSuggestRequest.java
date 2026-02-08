package com.example.EnterpriseRagCommunity.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiPostRiskTagSuggestRequest {
    private String title;

    @NotBlank
    private String content;

    private Integer count;
}

