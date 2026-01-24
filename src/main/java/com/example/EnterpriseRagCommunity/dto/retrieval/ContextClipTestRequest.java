package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

@Data
public class ContextClipTestRequest {
    private String queryText;
    private Long boardId;
    private Boolean debug;

    private Boolean useSavedConfig;
    private ContextClipConfigDTO config;
}

