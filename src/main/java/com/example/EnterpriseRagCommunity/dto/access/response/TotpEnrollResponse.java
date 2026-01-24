package com.example.EnterpriseRagCommunity.dto.access.response;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class TotpEnrollResponse {
    @ApiModelProperty("otpauth URI（用于生成二维码）")
    private String otpauthUri;

    @ApiModelProperty("Base32 密钥（仅本次返回，建议妥善保存）")
    private String secretBase32;

    @ApiModelProperty("HMAC 算法")
    private String algorithm;

    @ApiModelProperty("验证码位数")
    private Integer digits;

    @ApiModelProperty("时间步长（秒）")
    private Integer periodSeconds;

    @ApiModelProperty("允许时间偏移窗口（步数）")
    private Integer skew;
}

