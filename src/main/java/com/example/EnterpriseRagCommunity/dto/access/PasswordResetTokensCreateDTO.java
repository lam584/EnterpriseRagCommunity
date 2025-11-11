package com.example.EnterpriseRagCommunity.dto.access;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PasswordResetTokensCreateDTO {
    @ApiModelProperty("用户ID")
    @NotNull
    private Long userId;

    @ApiModelProperty("重置令牌哈希")
    @NotBlank
    @Size(max = 191)
    private String tokenHash;

    @ApiModelProperty("过期时间")
    @NotNull
    private LocalDateTime expiresAt;

    @ApiModelProperty("使用时间（可空）")
    private LocalDateTime consumedAt;

    @ApiModelProperty("创建时间（由DB默认填充）")
    @JsonIgnore
    private LocalDateTime createdAt;
}

