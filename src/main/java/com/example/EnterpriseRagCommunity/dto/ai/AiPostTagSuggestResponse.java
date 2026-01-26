package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AiPostTagSuggestResponse {
    private List<String> tags;
    private String model;
    private Long latencyMs;
}

