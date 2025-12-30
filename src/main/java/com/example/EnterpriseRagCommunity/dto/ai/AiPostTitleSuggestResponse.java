package com.example.EnterpriseRagCommunity.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AiPostTitleSuggestResponse {
    private List<String> titles;
    private String model;
    private Long latencyMs;
}

