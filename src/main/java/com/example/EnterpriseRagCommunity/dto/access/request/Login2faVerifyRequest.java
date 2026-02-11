package com.example.EnterpriseRagCommunity.dto.access.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class Login2faVerifyRequest {
    @NotBlank
    @Size(max = 16)
    private String method;

    @NotBlank
    @Size(max = 32)
    private String code;
}

