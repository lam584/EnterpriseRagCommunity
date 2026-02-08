package com.example.EnterpriseRagCommunity.dto.retrieval;

import java.util.List;

import lombok.Data;

@Data
public class HybridRerankTestRequest {
    private String queryText;
    private Integer topN;
    private Boolean debug;
    private Boolean useSavedConfig;
    private HybridRetrievalConfigDTO config;
    private List<HybridRerankTestDocumentDTO> documents;
}

