package com.example.EnterpriseRagCommunity.dto.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmailVerificationsUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("用户ID")
    private Long userId;
    @JsonIgnore
    private boolean hasUserId;

    @ApiModelProperty("验证码")
    @Size(max = 64)
    private String code;
    @JsonIgnore
    private boolean hasCode;

    @ApiModelProperty("用途")
    private EmailVerificationPurpose purpose;
    @JsonIgnore
    private boolean hasPurpose;

    @ApiModelProperty("过期时间")
    private LocalDateTime expiresAt;
    @JsonIgnore
    private boolean hasExpiresAt;

    @ApiModelProperty("使用时间")
    private LocalDateTime consumedAt;
    @JsonIgnore
    private boolean hasConsumedAt;

    @ApiModelProperty("创建时间（只读，不允许修改）")
    @JsonIgnore
    private LocalDateTime createdAt;

    public void setUserId(Long userId) {
        this.userId = userId;
        this.hasUserId = true;
    }

    public void setCode(String code) {
        this.code = code;
        this.hasCode = true;
    }

    public void setPurpose(EmailVerificationPurpose purpose) {
        this.purpose = purpose;
        this.hasPurpose = true;
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

