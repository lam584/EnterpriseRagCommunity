package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AiPostRiskTagSuggestResponse {
    private List<String> riskTags;
    private String model;
    private Long latencyMs;
}

