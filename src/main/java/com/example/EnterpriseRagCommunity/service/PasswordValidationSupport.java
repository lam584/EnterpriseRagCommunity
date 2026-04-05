package com.example.EnterpriseRagCommunity.service;

public final class PasswordValidationSupport {

    private PasswordValidationSupport() {
    }

    public static void requireNewPassword(String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("请输入新密码");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度至少 6 位");
        }
    }
}
