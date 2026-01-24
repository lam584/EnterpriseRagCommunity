package com.example.EnterpriseRagCommunity.dto.access.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetResetRequest {
    @NotBlank
    @Email
    @Size(max = 191)
    private String email;

    private String totpCode;

    private String emailCode;

    @NotBlank
    @Size(min = 6, max = 191)
    private String newPassword;
}
