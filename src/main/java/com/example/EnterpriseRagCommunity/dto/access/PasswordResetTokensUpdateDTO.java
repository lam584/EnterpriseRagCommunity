package com.example.EnterpriseRagCommunity.dto.access;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class PasswordResetTokensUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("用户ID")
    private Optional<Long> userId = Optional.empty();

    @ApiModelProperty("重置令牌哈希")
    @Size(max = 191)
    private Optional<String> tokenHash = Optional.empty();

    @ApiModelProperty("过期时间")
    private Optional<LocalDateTime> expiresAt = Optional.empty();

    @ApiModelProperty("使用时间")
    private Optional<LocalDateTime> consumedAt = Optional.empty();

    @ApiModelProperty("创建时间（不可修改）")
    @JsonIgnore
    private Optional<LocalDateTime> createdAt = Optional.empty();
}

