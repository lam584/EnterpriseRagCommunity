package com.example.EnterpriseRagCommunity.dto.retrieval;

import lombok.Data;

import java.util.List;

@Data
public class ContextClipTestRequest {
    private String queryText;
    private Long boardId;
    private Boolean debug;
    private List<String> modes;

    private Boolean useSavedConfig;
    private ContextClipConfigDTO config;
}
