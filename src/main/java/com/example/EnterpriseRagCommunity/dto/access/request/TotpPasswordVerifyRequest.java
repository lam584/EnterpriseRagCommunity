package com.example.EnterpriseRagCommunity.dto.access.request;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TotpPasswordVerifyRequest {
    @ApiModelProperty("当前密码")
    @NotBlank
    @Size(max = 191)
    private String password;

    @ApiModelProperty("用途：ENABLE 或 DISABLE")
    @NotBlank
    @Size(max = 32)
    private String action;
}

