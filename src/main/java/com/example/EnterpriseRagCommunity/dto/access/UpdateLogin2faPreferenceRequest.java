package com.example.EnterpriseRagCommunity.dto.access;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateLogin2faPreferenceRequest {
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;

    @NotBlank(message = "method 不能为空")
    private String method;

    @Size(max = 32, message = "totpCode 过长")
    private String totpCode;

    @Size(max = 32, message = "emailCode 过长")
    private String emailCode;
}
