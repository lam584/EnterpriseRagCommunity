package com.example.EnterpriseRagCommunity.dto.access;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TotpSecretsCreateDTO {
    // id 由数据库生成，不在 CreateDTO 中出现

    @ApiModelProperty("用户ID")
    @NotNull
    private Long userId;

    @ApiModelProperty("TOTP密钥加密值 (最长512字节)")
    @NotNull
    @Size(max = 512)
    private byte[] secretEncrypted;

    @ApiModelProperty("是否启用二次验证，默认 false")
    private Boolean enabled = Boolean.FALSE;

    @ApiModelProperty("验证通过时间")
    private LocalDateTime verifiedAt;

    @ApiModelProperty("创建时间（数据库默认值填充）")
    @JsonIgnore
    private LocalDateTime createdAt;
}

