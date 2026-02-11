package com.example.EnterpriseRagCommunity.dto.access.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class TotpEnrollRequest {
    @ApiModelProperty("当前密码（未提前验证密码时必填）")
    private String password;

    @ApiModelProperty("邮箱验证码（启用前校验，必填）")
    private String emailCode;

    @ApiModelProperty("HMAC 算法（SHA1/SHA256/SHA512），默认 SHA1")
    private String algorithm;

    @ApiModelProperty("验证码位数（6/8），默认 6")
    private Integer digits;

    @ApiModelProperty("时间步长（秒），默认 30")
    private Integer periodSeconds;

    @ApiModelProperty("允许时间偏移窗口（步数），默认 1")
    private Integer skew;
}
