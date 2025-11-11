package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class AuthSessionsUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("用户ID")
    private Optional<Long> userId = Optional.empty();

    @ApiModelProperty("刷新令牌哈希")
    private Optional<@Size(max = 191) String> refreshTokenHash = Optional.empty();

    @ApiModelProperty("User-Agent")
    private Optional<@Size(max = 255) String> userAgent = Optional.empty();

    @ApiModelProperty("IP 地址")
    private Optional<@Size(max = 64) String> ip = Optional.empty();

    @ApiModelProperty("过期时间")
    private Optional<LocalDateTime> expiresAt = Optional.empty();

    @ApiModelProperty("撤销时间")
    private Optional<LocalDateTime> revokedAt = Optional.empty();

    @ApiModelProperty("创建时间（不可修改，保留仅供序列化）")
    private Optional<LocalDateTime> createdAt = Optional.empty();
}

