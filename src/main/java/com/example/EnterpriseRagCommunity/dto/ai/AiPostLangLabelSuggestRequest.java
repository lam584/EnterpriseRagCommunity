package com.example.EnterpriseRagCommunity.dto.ai;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiPostLangLabelSuggestRequest {
    @Size(max = 160, message = "title过长")
    private String title;

    @Size(max = 200000, message = "content过长")
    private String content;
}

