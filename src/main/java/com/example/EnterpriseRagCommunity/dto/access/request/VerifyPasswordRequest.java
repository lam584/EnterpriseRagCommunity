package com.example.EnterpriseRagCommunity.dto.access.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyPasswordRequest {
    @NotBlank(message = "请输入密码")
    @Size(max = 191, message = "密码过长")
    private String password;
}

