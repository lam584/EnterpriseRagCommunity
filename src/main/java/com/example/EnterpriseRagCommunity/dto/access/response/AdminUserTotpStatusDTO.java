package com.example.EnterpriseRagCommunity.dto.access.response;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserTotpStatusDTO {
    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("邮箱")
    private String email;

    @ApiModelProperty("用户名")
    private String username;

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

