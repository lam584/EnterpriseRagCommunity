package com.example.EnterpriseRagCommunity.dto.access;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class Security2faPolicySettingsDTO {
    @ApiModelProperty("TOTP 启用策略：FORBID_ALL | ALLOW_ALL | ALLOW_ROLES | ALLOW_USERS | REQUIRE_ALL | REQUIRE_ROLES | REQUIRE_USERS")
    private String totpPolicy;

    @ApiModelProperty("TOTP 策略命中的 roleId 列表（仅 *_ROLES 时有效）")
    private List<Long> totpRoleIds;

    @ApiModelProperty("TOTP 策略命中的 userId 列表（仅 *_USERS 时有效）")
    private List<Long> totpUserIds;

    @ApiModelProperty("邮箱验证码策略：FORBID_ALL | ALLOW_ALL | ALLOW_ROLES | ALLOW_USERS | REQUIRE_ALL | REQUIRE_ROLES | REQUIRE_USERS")
    private String emailOtpPolicy;

    @ApiModelProperty("邮箱验证码策略命中的 roleId 列表（仅 *_ROLES 时有效）")
    private List<Long> emailOtpRoleIds;

    @ApiModelProperty("邮箱验证码策略命中的 userId 列表（仅 *_USERS 时有效）")
    private List<Long> emailOtpUserIds;

    @ApiModelProperty("登录二次验证方式：DISABLED | EMAIL_ONLY | TOTP_ONLY | EMAIL_OR_TOTP")
    private String login2faMode;

    @ApiModelProperty("登录二次验证作用范围：FORBID_ALL | ALLOW_ALL | ALLOW_ROLES | ALLOW_USERS | REQUIRE_ALL | REQUIRE_ROLES | REQUIRE_USERS")
    private String login2faScopePolicy;

    @ApiModelProperty("登录二次验证策略命中的 roleId 列表（仅 *_ROLES 时有效）")
    private List<Long> login2faRoleIds;

    @ApiModelProperty("登录二次验证策略命中的 userId 列表（仅 *_USERS 时有效）")
    private List<Long> login2faUserIds;
}
