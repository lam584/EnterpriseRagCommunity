package com.example.EnterpriseRagCommunity.dto.access.response;

import java.time.LocalDateTime;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class TotpStatusResponse {
    @ApiModelProperty("是否已配置 TOTP 主密钥（app.security.totp.master-key）")
    private Boolean masterKeyConfigured;

    @ApiModelProperty("是否已启用 TOTP")
    private Boolean enabled;

    @ApiModelProperty("验证通过时间")
    private LocalDateTime verifiedAt;

    @ApiModelProperty("最近一次创建时间")
    private LocalDateTime createdAt;

    @ApiModelProperty("HMAC 算法")
    private String algorithm;

    @ApiModelProperty("验证码位数")
    private Integer digits;

    @ApiModelProperty("时间步长（秒）")
    private Integer periodSeconds;

    @ApiModelProperty("允许时间偏移窗口（步数）")
    private Integer skew;
}
