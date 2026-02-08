package com.example.EnterpriseRagCommunity.dto.retrieval;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class HybridRerankTestResponse {
    private String queryText;
    private Integer topN;

    private Boolean ok;
    private Integer latencyMs;
    private String errorMessage;

    private String usedProviderId;
    private String usedModel;
    private Integer totalTokens;

    private List<HybridRerankTestHitDTO> results;
    private Map<String, Object> debugInfo;
}

