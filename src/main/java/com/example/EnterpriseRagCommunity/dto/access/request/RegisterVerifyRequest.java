package com.example.EnterpriseRagCommunity.dto.access.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterVerifyRequest {
    @NotBlank
    @Email
    @Size(max = 191)
    private String email;

    @NotBlank
    @Size(min = 4, max = 64)
    private String code;
}
