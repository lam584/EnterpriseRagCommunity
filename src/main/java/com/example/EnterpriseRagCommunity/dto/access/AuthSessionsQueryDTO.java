package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
@EqualsAndHashCode(callSuper = true)
@Data
public class AuthSessionsQueryDTO extends PageRequestDTO {
    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("刷新令牌哈希（精确匹配或模糊）")
    private String refreshTokenHash;

    @ApiModelProperty("User-Agent（模糊匹配）")
    private String userAgent;

    @ApiModelProperty("IP 地址（模糊匹配）")
    private String ip;

    @ApiModelProperty("过期时间-起")
    private LocalDateTime expiresFrom;

    @ApiModelProperty("过期时间-止")
    private LocalDateTime expiresTo;

    @ApiModelProperty("撤销时间-起")
    private LocalDateTime revokedFrom;

    @ApiModelProperty("撤销时间-止")
    private LocalDateTime revokedTo;

    @ApiModelProperty("创建时间-起")
    private LocalDateTime createdAfter;

    @ApiModelProperty("创建时间-止")
    private LocalDateTime createdBefore;
}
