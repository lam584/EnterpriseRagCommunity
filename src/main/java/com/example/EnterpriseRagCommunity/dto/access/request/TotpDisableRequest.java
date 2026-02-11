package com.example.EnterpriseRagCommunity.dto.access.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class TotpDisableRequest {
    @ApiModelProperty("当前密码（未提前验证密码时必填）")
    private String password;

    @ApiModelProperty("校验方式：totp 或 email（默认 totp）")
    private String method;

    @ApiModelProperty("当前 TOTP 验证码（method=totp 时必填）")
    private String code;

    @ApiModelProperty("邮箱验证码（method=email 时必填）")
    private String emailCode;
}
