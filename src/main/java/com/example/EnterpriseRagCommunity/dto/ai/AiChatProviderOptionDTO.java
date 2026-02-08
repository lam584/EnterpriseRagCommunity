package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiChatProviderOptionDTO {
    private String id;
    private String name;
    private String defaultChatModel;
    private List<AiChatModelOptionDTO> chatModels;
}

