package com.example.EnterpriseRagCommunity.dto.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmailVerificationsCreateDTO {
    // id 由数据库生成，CreateDTO 不应出现

    @ApiModelProperty("用户ID")
    @NotNull
    private Long userId;

    @ApiModelProperty("验证码")
    @NotNull
    @Size(max = 64)
    private String code;

    @ApiModelProperty("用途")
    @NotNull
    private EmailVerificationPurpose purpose;

    @ApiModelProperty("过期时间")
    @NotNull
    private LocalDateTime expiresAt;

    @ApiModelProperty("使用时间（通常为空，由系统在消费时填充）")
    private LocalDateTime consumedAt;

    @ApiModelProperty("创建时间（由数据库默认值填充）")
    @JsonIgnore
    private LocalDateTime createdAt;
}

