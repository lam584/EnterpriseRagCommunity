package com.example.EnterpriseRagCommunity.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.access.response.PasswordResetStatusResponse;
import com.example.EnterpriseRagCommunity.entity.access.TotpSecretsEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.TotpSecretsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.TotpCryptoService;
import com.example.EnterpriseRagCommunity.service.access.TotpService;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthPasswordResetService {
    private final UsersRepository usersRepository;
    private final TotpSecretsRepository totpSecretsRepository;
    private final TotpCryptoService totpCryptoService;
    private final TotpService totpService;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final EmailVerificationMailer emailVerificationMailer;

    @Transactional(readOnly = true)
    public PasswordResetStatusResponse status(String email) {
        PasswordResetStatusResponse resp = new PasswordResetStatusResponse();
        resp.setEmailEnabled(emailVerificationMailer.isEnabled());
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email).orElse(null);
        if (user == null) {
            resp.setAllowed(false);
            resp.setTotpEnabled(false);
            resp.setMessage("该账号暂不支持找回密码");
            return resp;
        }

        TotpSecretsEntity enabled = totpSecretsRepository.findTopByUserIdAndEnabledTrueOrderByCreatedAtDesc(user.getId()).orElse(null);
        if (enabled == null) {
            resp.setTotpEnabled(false);
            if (emailVerificationMailer.isEnabled()) {
                resp.setAllowed(true);
                resp.setMessage(null);
                return resp;
            }
            resp.setAllowed(false);
            resp.setMessage("该账号未启用 TOTP，暂不支持找回密码");
            return resp;
        }

        resp.setAllowed(true);
        resp.setTotpEnabled(true);
        resp.setMessage(null);
        return resp;
    }

    @Transactional
    public void sendPasswordResetEmailCode(String email) {
        if (!emailVerificationMailer.isEnabled()) {
            throw new IllegalArgumentException("邮箱服务未启用");
        }
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        String code = emailVerificationService.issueCode(user.getId(), EmailVerificationPurpose.PASSWORD_RESET);
        emailVerificationMailer.sendVerificationCode(user.getEmail(), code, EmailVerificationPurpose.PASSWORD_RESET);
    }

    @Transactional
    public void resetPasswordByEmailTotp(String email, String totpCode, String newPassword) {
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("该账号暂不支持找回密码"));

        TotpSecretsEntity enabled = totpSecretsRepository.findTopByUserIdAndEnabledTrueOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("该账号未启用 TOTP，暂不支持找回密码"));

        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("请输入新密码");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度至少 6 位");
        }

        String normalized = totpCode == null ? "" : totpCode.trim();
        int digits = requireValidTotpDigits(enabled.getDigits());
        if (normalized.length() != digits) {
            throw new IllegalArgumentException("验证码格式不正确，应为 " + digits + " 位数字");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("验证码格式不正确，应为 " + digits + " 位数字");
            }
        }

        if (!totpCryptoService.isConfigured()) {
            throw new IllegalStateException("TOTP 主密钥未配置，请联系管理员");
        }

        byte[] secret = totpCryptoService.decrypt(enabled.getSecretEncrypted());
        boolean ok = totpService.verifyCode(
                secret,
                normalized,
                enabled.getAlgorithm(),
                digits,
                enabled.getPeriodSeconds() == null ? 30 : enabled.getPeriodSeconds(),
                enabled.getSkew() == null ? 0 : enabled.getSkew()
        );
        if (!ok) {
            throw new IllegalArgumentException("验证码不正确");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        usersRepository.save(user);
    }

    @Transactional
    public void resetPasswordByEmailCode(String email, String emailCode, String newPassword) {
        if (!emailVerificationMailer.isEnabled()) {
            throw new IllegalArgumentException("邮箱服务未启用");
        }
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("请输入新密码");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度至少 6 位");
        }
        String code = emailCode == null ? "" : emailCode.trim();
        if (code.isEmpty()) throw new IllegalArgumentException("请输入邮箱验证码");
        emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.PASSWORD_RESET, code);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        usersRepository.save(user);
    }

    private static int requireValidTotpDigits(Integer configuredDigits) {
        int digits = configuredDigits == null ? 0 : configuredDigits;
        if (digits != 6 && digits != 8) {
            throw new IllegalStateException("TOTP 配置不正确");
        }
        return digits;
    }
}
