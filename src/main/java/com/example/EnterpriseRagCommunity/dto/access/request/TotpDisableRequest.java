package com.example.EnterpriseRagCommunity.dto.access.request;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TotpDisableRequest {
    @ApiModelProperty("当前 TOTP 验证码")
    @NotBlank
    private String code;
}

