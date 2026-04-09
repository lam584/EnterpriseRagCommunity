package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiChatOptionsDTO {
    private String activeProviderId;
    private List<AiChatProviderOptionDTO> providers;
    private Boolean assistantManualModelSelectionEnabled;
    private Boolean postComposeManualModelSelectionEnabled;
}

