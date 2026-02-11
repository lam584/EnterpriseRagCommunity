package com.example.EnterpriseRagCommunity.dto.access.request;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TotpVerifyRequest {
    @ApiModelProperty("TOTP 验证码")
    @NotBlank
    private String code;

    @ApiModelProperty("当前密码（未提前验证密码时必填）")
    private String password;

    @ApiModelProperty("邮箱验证码（当开启邮箱验证时必填）")
    private String emailCode;
}
