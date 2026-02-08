package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiUpstreamModelsDTO {
    private String providerId;
    private List<String> models;
}

