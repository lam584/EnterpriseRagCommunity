package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuthSessionsCreateDTO {
    @ApiModelProperty("用户ID")
    @NotNull
    private Long userId;

    @ApiModelProperty("刷新令牌哈希")
    @NotNull
    @Size(max = 191)
    private String refreshTokenHash;

    @ApiModelProperty("User-Agent")
    @Size(max = 255)
    private String userAgent;

    @ApiModelProperty("IP 地址")
    @Size(max = 64)
    private String ip;

    @ApiModelProperty("过期时间")
    @NotNull
    private LocalDateTime expiresAt;

    @ApiModelProperty("撤销时间")
    private LocalDateTime revokedAt;

    @ApiModelProperty("创建时间（由数据库默认填充）")
    private LocalDateTime createdAt;
}

