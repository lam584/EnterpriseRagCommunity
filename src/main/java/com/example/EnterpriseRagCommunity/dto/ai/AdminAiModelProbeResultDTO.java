package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

@Data
public class AdminAiModelProbeResultDTO {
    private String providerId;
    private String modelName;
    private String kind;
    private Boolean ok;
    private Long latencyMs;
    private String errorMessage;
    private String usedProviderId;
    private String usedModel;
}

