package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class PromptBatchResponse {
    private List<PromptContentDTO> prompts;
    private List<String> missingCodes;
}

