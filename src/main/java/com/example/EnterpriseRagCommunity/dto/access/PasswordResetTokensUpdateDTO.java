package com.example.EnterpriseRagCommunity.dto.access;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PasswordResetTokensUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("用户ID")
    private Long userId;
    @JsonIgnore
    private boolean hasUserId;

    @ApiModelProperty("重置令牌哈希")
    @Size(max = 191)
    private String tokenHash;
    @JsonIgnore
    private boolean hasTokenHash;

    @ApiModelProperty("过期时间")
    private LocalDateTime expiresAt;
    @JsonIgnore
    private boolean hasExpiresAt;

    @ApiModelProperty("使用时间")
    private LocalDateTime consumedAt;
    @JsonIgnore
    private boolean hasConsumedAt;

    @ApiModelProperty("创建时间（不可修改）")
    @JsonIgnore
    private LocalDateTime createdAt;

    public void setUserId(Long userId) {
        this.userId = userId;
        this.hasUserId = true;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
        this.hasTokenHash = true;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
        this.hasExpiresAt = true;
    }

    public void setConsumedAt(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
        this.hasConsumedAt = true;
    }
}

