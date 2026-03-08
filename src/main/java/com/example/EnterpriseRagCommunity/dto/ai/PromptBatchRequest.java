package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class PromptBatchRequest {
    private List<String> codes;
}

