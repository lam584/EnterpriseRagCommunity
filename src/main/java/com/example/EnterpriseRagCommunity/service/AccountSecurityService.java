package com.example.EnterpriseRagCommunity.service;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AccountSecurityService {

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void changePasswordByEmail(String email, String currentPassword, String newPassword) {
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + email));

        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("请输入旧密码");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("请输入新密码");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度至少 6 位");
        }
        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("新密码不能与旧密码相同");
        }

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("旧密码不正确");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        usersRepository.save(user);
    }
}

