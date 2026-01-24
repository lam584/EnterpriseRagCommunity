package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class TotpSecretsQueryDTO extends PageRequestDTO {
    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @ApiModelProperty("TOTP密钥加密值")
    private byte[] secretEncrypted;

    @ApiModelProperty("HMAC 算法（SHA1/SHA256/SHA512）")
    private String algorithm;

    @ApiModelProperty("验证码位数（6/8）")
    private Integer digits;

    @ApiModelProperty("时间步长（秒）")
    private Integer periodSeconds;

    @ApiModelProperty("允许时间偏移窗口（步数）")
    private Integer skew;

    @ApiModelProperty("验证时间")
    private LocalDateTime verifiedAt;

    @ApiModelProperty("创建时间")
    private LocalDateTime createdAt;

    // 范围查询字段
    @ApiModelProperty("验证时间-起")
    private LocalDateTime verifiedFrom;

    @ApiModelProperty("验证时间-止")
    private LocalDateTime verifiedTo;

    @ApiModelProperty("创建时间-起")
    private LocalDateTime createdAfter;

    @ApiModelProperty("创建时间-止")
    private LocalDateTime createdBefore;
}
