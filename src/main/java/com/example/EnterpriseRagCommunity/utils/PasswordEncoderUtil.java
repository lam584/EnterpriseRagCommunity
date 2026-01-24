package com.example.EnterpriseRagCommunity.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordEncoderUtil {
    public static void main(String[] args) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String rawPassword = "Qq076835378.";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        System.out.println("Raw Password: " + rawPassword);
        System.out.println("BCrypt Encoded Password: " + encodedPassword);
        // 示例输出: $2a$10$N0x/4gq.N6zJ4.A3d5t8qOfPzU4Xw0.s8C7yW3k.B0u9Q7d1Xq2m
        // 复制输出的密文（以 $2a$ 或 $2b$ 开头的那段字符串）
    }
}