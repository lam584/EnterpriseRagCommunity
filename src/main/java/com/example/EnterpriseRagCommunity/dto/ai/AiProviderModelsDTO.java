package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiProviderModelsDTO {
    private String providerId;
    private List<AiProviderModelDTO> models;
}

