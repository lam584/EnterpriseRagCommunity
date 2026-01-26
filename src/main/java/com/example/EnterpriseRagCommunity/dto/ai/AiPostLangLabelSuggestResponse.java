package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AiPostLangLabelSuggestResponse {
    private List<String> languages;
    private String model;
    private Long latencyMs;
}

