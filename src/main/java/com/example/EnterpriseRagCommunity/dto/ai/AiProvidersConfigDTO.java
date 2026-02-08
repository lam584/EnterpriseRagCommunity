package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiProvidersConfigDTO {
    private String activeProviderId;
    private List<AiProviderDTO> providers;
}

