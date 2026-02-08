package com.example.EnterpriseRagCommunity.dto.access;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Optional;

@Data
public class TotpSecretsUpdateDTO {
    @ApiModelProperty("主键ID")
    @NotNull
    private Long id;

    @ApiModelProperty("用户ID")
    private Optional<Long> userId = Optional.empty();

    @ApiModelProperty("TOTP密钥加密值 (最长512字节)")
    private Optional<@Size(max = 512) byte[]> secretEncrypted = Optional.empty();

    @ApiModelProperty("HMAC 算法（SHA1/SHA256/SHA512）")
    private Optional<String> algorithm = Optional.empty();

    @ApiModelProperty("验证码位数（6/8）")
    private Optional<Integer> digits = Optional.empty();

    @ApiModelProperty("时间步长（秒）")
    private Optional<Integer> periodSeconds = Optional.empty();

    @ApiModelProperty("允许时间偏移窗口（步数）")
    private Optional<Integer> skew = Optional.empty();

    @ApiModelProperty("是否启用二次验证")
    private Optional<Boolean> enabled = Optional.empty();

    @ApiModelProperty("验证通过时间")
    private Optional<LocalDateTime> verifiedAt = Optional.empty();

    @ApiModelProperty("创建时间（只读，不允许修改）")
    @JsonIgnore
    private Optional<LocalDateTime> createdAt = Optional.empty();
}
