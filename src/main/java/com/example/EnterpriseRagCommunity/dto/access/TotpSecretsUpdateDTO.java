package com.example.EnterpriseRagCommunity.dto.access;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TotpSecretsUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("用户ID")
    private Long userId;
    @JsonIgnore
    private boolean hasUserId;

    @ApiModelProperty("TOTP密钥加密值 (最长512字节)")
    private @Size(max = 512) byte[] secretEncrypted;
    @JsonIgnore
    private boolean hasSecretEncrypted;

    @ApiModelProperty("HMAC 算法（SHA1/SHA256/SHA512）")
    private String algorithm;
    @JsonIgnore
    private boolean hasAlgorithm;

    @ApiModelProperty("验证码位数（6/8）")
    private Integer digits;
    @JsonIgnore
    private boolean hasDigits;

    @ApiModelProperty("时间步长（秒）")
    private Integer periodSeconds;
    @JsonIgnore
    private boolean hasPeriodSeconds;

    @ApiModelProperty("允许时间偏移窗口（步数）")
    private Integer skew;
    @JsonIgnore
    private boolean hasSkew;

    @ApiModelProperty("是否启用二次验证")
    private Boolean enabled;
    @JsonIgnore
    private boolean hasEnabled;

    @ApiModelProperty("验证通过时间")
    private LocalDateTime verifiedAt;
    @JsonIgnore
    private boolean hasVerifiedAt;

    @ApiModelProperty("创建时间（只读，不允许修改）")
    @JsonIgnore
    private LocalDateTime createdAt;

    public void setUserId(Long userId) {
        this.userId = userId;
        this.hasUserId = true;
    }

    public void setSecretEncrypted(byte[] secretEncrypted) {
        this.secretEncrypted = secretEncrypted;
        this.hasSecretEncrypted = true;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
        this.hasAlgorithm = true;
    }

    public void setDigits(Integer digits) {
        this.digits = digits;
        this.hasDigits = true;
    }

    public void setPeriodSeconds(Integer periodSeconds) {
        this.periodSeconds = periodSeconds;
        this.hasPeriodSeconds = true;
    }

    public void setSkew(Integer skew) {
        this.skew = skew;
        this.hasSkew = true;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
        this.hasEnabled = true;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
        this.hasVerifiedAt = true;
    }
}
