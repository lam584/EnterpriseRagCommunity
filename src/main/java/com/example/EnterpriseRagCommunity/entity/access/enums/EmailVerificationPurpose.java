package com.example.EnterpriseRagCommunity.entity.access.enums;

public enum EmailVerificationPurpose {
    VERIFY_EMAIL,
    PASSWORD_RESET,
    REGISTER,
    LOGIN_2FA,
    LOGIN_2FA_PREFERENCE,
    CHANGE_PASSWORD,
    CHANGE_EMAIL,
    CHANGE_EMAIL_OLD,
    TOTP_ENABLE,
    TOTP_DISABLE;

    public String getDisplayNameZh() {
        return switch (this) {
            case VERIFY_EMAIL -> "验证邮箱";
            case PASSWORD_RESET -> "重置密码";
            case REGISTER -> "注册";
            case LOGIN_2FA -> "登录二次验证";
            case LOGIN_2FA_PREFERENCE -> "修改登录二次验证设置";
            case CHANGE_PASSWORD -> "修改密码";
            case CHANGE_EMAIL -> "更换邮箱";
            case CHANGE_EMAIL_OLD -> "验证旧邮箱";
            case TOTP_ENABLE -> "启用二次验证";
            case TOTP_DISABLE -> "停用二次验证";
        };
    }
}
