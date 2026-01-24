package com.example.EnterpriseRagCommunity.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QaMessageUpdateRequest {
    @NotBlank
    private String content;
}
