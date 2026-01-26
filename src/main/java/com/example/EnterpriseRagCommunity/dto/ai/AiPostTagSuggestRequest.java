package com.example.EnterpriseRagCommunity.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AiPostTagSuggestRequest {

    private String title;

    @NotBlank
    private String content;

    private Integer count;

    private String model;

    private Double temperature;

    private String boardName;

    private List<String> tags;
}

