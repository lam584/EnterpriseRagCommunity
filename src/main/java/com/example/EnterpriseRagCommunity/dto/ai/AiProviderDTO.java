package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.util.Map;

@Data
public class AiProviderDTO {
    private String id;
    private String name;
    private String type;
    private String baseUrl;
    private String apiKey;
    private String defaultChatModel;
    private String defaultEmbeddingModel;
    private String defaultRerankModel;
    private String rerankEndpointPath;
    private Boolean supportsVision;
    private Map<String, String> extraHeaders;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Boolean enabled;
}
