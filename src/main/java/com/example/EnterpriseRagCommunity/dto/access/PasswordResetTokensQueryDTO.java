package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class PasswordResetTokensQueryDTO extends PageRequestDTO {
    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("令牌哈希")
    private String tokenHash;

    @ApiModelProperty("过期时间（等值）")
    private LocalDateTime expiresAt;

    @ApiModelProperty("过期时间-起")
    private LocalDateTime expiresFrom;

    @ApiModelProperty("过期时间-止")
    private LocalDateTime expiresTo;

    @ApiModelProperty("使用时间（等值）")
    private LocalDateTime consumedAt;

    @ApiModelProperty("使用时间-起")
    private LocalDateTime consumedFrom;

    @ApiModelProperty("使用时间-止")
    private LocalDateTime consumedTo;

    @ApiModelProperty("创建时间（等值）")
    private LocalDateTime createdAt;

    @ApiModelProperty("创建时间-起")
    private LocalDateTime createdAfter;

    @ApiModelProperty("创建时间-止")
    private LocalDateTime createdBefore;
}
