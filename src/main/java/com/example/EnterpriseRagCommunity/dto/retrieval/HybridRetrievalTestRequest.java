package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class HybridRetrievalTestRequest {
    private String queryText;
    private Long boardId;
    private Boolean debug;
    private Boolean useSavedConfig;
    private HybridRetrievalConfigDTO config;
}
