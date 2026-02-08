package com.example.EnterpriseRagCommunity.dto.access;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class Security2faPolicySettingsDTO {
    @ApiModelProperty("TOTP 启用策略：FORBID_ALL | ALLOW_ALL | ALLOW_ROLES | REQUIRE_ALL | REQUIRE_ROLES")
    private String totpPolicy;

    @ApiModelProperty("TOTP 策略命中的 roleId 列表（仅 *_ROLES 时有效）")
    private List<Long> totpRoleIds;

    @ApiModelProperty("邮箱验证码策略：FORBID_ALL | ALLOW_ALL | ALLOW_ROLES | REQUIRE_ALL | REQUIRE_ROLES")
    private String emailOtpPolicy;

    @ApiModelProperty("邮箱验证码策略命中的 roleId 列表（仅 *_ROLES 时有效）")
    private List<Long> emailOtpRoleIds;
}

