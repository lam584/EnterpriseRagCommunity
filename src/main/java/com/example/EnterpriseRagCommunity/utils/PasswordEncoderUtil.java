package com.example.EnterpriseRagCommunity.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordEncoderUtil {
    public static void main(String[] args) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String rawPassword = (args != null && args.length > 0) ? args[0] : null;
        if (rawPassword == null || rawPassword.isBlank()) {
            System.out.println("Usage: PasswordEncoderUtil <rawPassword>");
            return;
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);
        System.out.println(encodedPassword);
    }
}
