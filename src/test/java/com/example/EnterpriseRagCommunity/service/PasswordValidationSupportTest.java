package com.example.EnterpriseRagCommunity.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordValidationSupportTest {

    @Test
    void requireNewPassword_shouldValidateBlankAndShortPasswords() {
        IllegalArgumentException blank = assertThrows(
                IllegalArgumentException.class,
                () -> PasswordValidationSupport.requireNewPassword("  ")
        );
        assertEquals("请输入新密码", blank.getMessage());

        IllegalArgumentException shortPwd = assertThrows(
                IllegalArgumentException.class,
                () -> PasswordValidationSupport.requireNewPassword("12345")
        );
        assertEquals("新密码长度至少 6 位", shortPwd.getMessage());

        assertDoesNotThrow(() -> PasswordValidationSupport.requireNewPassword("123456"));
    }
}
