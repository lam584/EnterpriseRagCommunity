package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.util.Map;

@Data
public class AiUpstreamModelsPreviewRequestDTO {
    private String providerId;
    private String baseUrl;
    private String apiKey;
    private Map<String, String> extraHeaders;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
}

