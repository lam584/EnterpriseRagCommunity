package com.example.EnterpriseRagCommunity.dto.access.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailVerificationSendRequest {
    @NotBlank
    @Size(max = 32)
    private String purpose;
}
