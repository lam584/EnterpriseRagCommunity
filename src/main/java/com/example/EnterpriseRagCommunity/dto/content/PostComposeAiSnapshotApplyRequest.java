package com.example.EnterpriseRagCommunity.dto.content;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostComposeAiSnapshotApplyRequest {
    @NotBlank
    private String afterContent;
}

