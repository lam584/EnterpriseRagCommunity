package com.example.EnterpriseRagCommunity.dto.access.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    @Email
    @Size(max = 191)
    private String email; // 对齐: SQL users.email → DTO.email

    @NotBlank
    @Size(max = 191)
    private String password;
}