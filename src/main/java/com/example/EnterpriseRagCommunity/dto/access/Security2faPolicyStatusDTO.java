package com.example.EnterpriseRagCommunity.dto.access;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class Security2faPolicyStatusDTO {
    @ApiModelProperty("TOTP 是否允许启用/使用")
    private boolean totpAllowed;

    @ApiModelProperty("TOTP 是否强制启用/使用")
    private boolean totpRequired;

    @ApiModelProperty("当前用户是否允许关闭 TOTP（强制启用时为 false）")
    private boolean totpCanDisable;

    @ApiModelProperty("邮箱验证码是否允许使用（受策略与邮箱服务可用性影响）")
    private boolean emailOtpAllowed;

    @ApiModelProperty("邮箱验证码是否强制使用（受策略与邮箱服务可用性影响）")
    private boolean emailOtpRequired;

    @ApiModelProperty("邮箱服务是否开启（SMTP 可用开关）")
    private boolean emailServiceEnabled;
}

