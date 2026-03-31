package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuthSessionsUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("用户ID")
    private Long userId;
    @JsonIgnore
    private boolean hasUserId;

    @ApiModelProperty("刷新令牌哈希")
    private @Size(max = 191) String refreshTokenHash;
    @JsonIgnore
    private boolean hasRefreshTokenHash;

    @ApiModelProperty("User-Agent")
    private @Size(max = 255) String userAgent;
    @JsonIgnore
    private boolean hasUserAgent;

    @ApiModelProperty("IP 地址")
    private @Size(max = 64) String ip;
    @JsonIgnore
    private boolean hasIp;

    @ApiModelProperty("过期时间")
    private LocalDateTime expiresAt;
    @JsonIgnore
    private boolean hasExpiresAt;

    @ApiModelProperty("撤销时间")
    private LocalDateTime revokedAt;
    @JsonIgnore
    private boolean hasRevokedAt;

    @ApiModelProperty("创建时间（不可修改，保留仅供序列化）")
    private LocalDateTime createdAt;

    public void setUserId(Long userId) {
        this.userId = userId;
        this.hasUserId = true;
    }

    public void setRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
        this.hasRefreshTokenHash = true;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        this.hasUserAgent = true;
    }

    public void setIp(String ip) {
        this.ip = ip;
        this.hasIp = true;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
        this.hasExpiresAt = true;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
        this.hasRevokedAt = true;
    }
}

