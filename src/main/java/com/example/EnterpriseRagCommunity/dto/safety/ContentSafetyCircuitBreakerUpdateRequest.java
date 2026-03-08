package com.example.EnterpriseRagCommunity.dto.safety;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ContentSafetyCircuitBreakerUpdateRequest {
    @NotNull
    @Valid
    private ContentSafetyCircuitBreakerConfigDTO config;

    @NotBlank
    private String reason;
}

