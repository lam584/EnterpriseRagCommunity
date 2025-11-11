package com.example.EnterpriseRagCommunity.dto.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class EmailVerificationsUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("用户ID")
    private Optional<Long> userId = Optional.empty();

    @ApiModelProperty("验证码")
    @Size(max = 64)
    private Optional<String> code = Optional.empty();

    @ApiModelProperty("用途")
    private Optional<EmailVerificationPurpose> purpose = Optional.empty();

    @ApiModelProperty("过期时间")
    private Optional<LocalDateTime> expiresAt = Optional.empty();

    @ApiModelProperty("使用时间")
    private Optional<LocalDateTime> consumedAt = Optional.empty();

    @ApiModelProperty("创建时间（只读，不允许修改）")
    @JsonIgnore
    private Optional<LocalDateTime> createdAt = Optional.empty();
}

