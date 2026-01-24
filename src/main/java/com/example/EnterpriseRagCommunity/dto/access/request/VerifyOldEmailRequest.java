package com.example.EnterpriseRagCommunity.dto.access.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyOldEmailRequest {
    @NotBlank(message = "请选择验证方式")
    @Size(max = 16, message = "验证方式不合法")
    private String method;

    private String emailCode;

    private String totpCode;
}

