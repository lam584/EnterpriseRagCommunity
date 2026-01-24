package com.example.EnterpriseRagCommunity.dto.access.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "请输入旧密码")
    private String currentPassword;

    @NotBlank(message = "请输入新密码")
    @Size(min = 6, message = "新密码长度至少 6 位")
    private String newPassword;

    private String totpCode;

    private String emailCode;
}

