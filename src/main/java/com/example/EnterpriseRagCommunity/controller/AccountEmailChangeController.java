package com.example.EnterpriseRagCommunity.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.EnterpriseRagCommunity.dto.access.request.ChangeEmailConfirmRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.EmailChangeSendCodeRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.VerifyOldEmailRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.VerifyPasswordRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.AccountSecurityService;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.notify.AccountEmailChangeNotificationMailer;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account/email-change")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AccountEmailChangeController {
    private static final String SESSION_PASSWORD_VERIFIED_AT = "emailChange.passwordVerifiedAt";
    private static final String SESSION_OLD_VERIFIED_AT = "emailChange.oldVerifiedAt";
    private static final Duration FLOW_TTL = Duration.ofMinutes(10);

    private final UsersRepository usersRepository;
    private final EmailVerificationService emailVerificationService;
    private final EmailVerificationMailer emailVerificationMailer;
    private final AccountTotpService accountTotpService;
    private final AccountSecurityService accountSecurityService;
    private final AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer;

    @PostMapping("/verify-password")
    public ResponseEntity<?> verifyPassword(@RequestBody @Valid VerifyPasswordRequest req, HttpServletRequest servletRequest) {
        String currentEmail = currentEmailOrNull();
        if (currentEmail == null) return unauthorized();

        String password = req.getPassword() == null ? "" : req.getPassword();
        try {
            accountSecurityService.verifyPasswordByEmail(currentEmail, password);
            HttpSession session = servletRequest.getSession(true);
            session.setAttribute(SESSION_PASSWORD_VERIFIED_AT, LocalDateTime.now());
            session.removeAttribute(SESSION_OLD_VERIFIED_AT);
            return ResponseEntity.ok(Map.of("message", "密码验证通过"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/old/send-code")
    public ResponseEntity<?> sendOldEmailCode(HttpServletRequest servletRequest) {
        String currentEmail = currentEmailOrNull();
        if (currentEmail == null) return unauthorized();

        try {
            requirePasswordVerified(servletRequest);
            if (!emailVerificationMailer.isEnabled()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "邮箱服务未启用"));
            }

            UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(currentEmail)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            String code = emailVerificationService.issueCode(user.getId(), EmailVerificationPurpose.CHANGE_EMAIL_OLD, currentEmail);
            emailVerificationMailer.sendVerificationCode(currentEmail, code, EmailVerificationPurpose.CHANGE_EMAIL_OLD);
            return ResponseEntity.ok(Map.of("message", "验证码已发送"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/old/verify")
    public ResponseEntity<?> verifyOld(@RequestBody @Valid VerifyOldEmailRequest req, HttpServletRequest servletRequest) {
        String currentEmail = currentEmailOrNull();
        if (currentEmail == null) return unauthorized();

        String method = req.getMethod() == null ? "" : req.getMethod().trim().toLowerCase();
        if (!method.equals("email") && !method.equals("totp")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "验证方式不合法"));
        }

        try {
            requirePasswordVerified(servletRequest);

            UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(currentEmail)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            if (method.equals("email")) {
                if (!emailVerificationMailer.isEnabled()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "邮箱服务未启用，无法使用邮箱验证码验证旧邮箱"));
                }
                String emailCode = trimToEmpty(req.getEmailCode());
                if (emailCode.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入旧邮箱验证码"));
                }
                emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.CHANGE_EMAIL_OLD, currentEmail, emailCode);
            } else {
                boolean totpEnabled = accountTotpService.isEnabledByEmail(currentEmail);
                if (!totpEnabled) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "当前账号未启用二次验证"));
                }
                String totpCode = trimToEmpty(req.getTotpCode());
                if (totpCode.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入动态验证码"));
                }
                accountTotpService.requireValidEnabledCodeByEmail(currentEmail, totpCode);
            }

            HttpSession session = servletRequest.getSession(true);
            session.setAttribute(SESSION_OLD_VERIFIED_AT, LocalDateTime.now());
            return ResponseEntity.ok(Map.of("message", "验证通过"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/send-code")
    public ResponseEntity<?> sendCode(@RequestBody @Valid EmailChangeSendCodeRequest req, HttpServletRequest servletRequest) {
        String currentEmail = currentEmailOrNull();
        if (currentEmail == null) return unauthorized();

        String newEmail = normalizeEmail(req.getNewEmail());
        if (newEmail.equals(currentEmail)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "新邮箱不能与当前邮箱相同"));
        }

        try {
            requirePasswordVerified(servletRequest);
            requireOldVerified(servletRequest);

            UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(currentEmail)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            if (isEmailInUseByOthers(newEmail, user.getId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "该邮箱已绑定其他账号"));
            }
            if (!emailVerificationMailer.isEnabled()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "邮箱服务未启用"));
            }

            String code = emailVerificationService.issueCode(user.getId(), EmailVerificationPurpose.CHANGE_EMAIL, newEmail);
            emailVerificationMailer.sendVerificationCode(newEmail, code, EmailVerificationPurpose.CHANGE_EMAIL);
            return ResponseEntity.ok(Map.of("message", "验证码已发送"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> change(@RequestBody @Valid ChangeEmailConfirmRequest req, HttpServletRequest servletRequest) {
        String currentEmail = currentEmailOrNull();
        if (currentEmail == null) return unauthorized();

        String newEmail = normalizeEmail(req.getNewEmail());
        if (newEmail.equals(currentEmail)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "新邮箱不能与当前邮箱相同"));
        }

        try {
            requirePasswordVerified(servletRequest);
            requireOldVerified(servletRequest);

            UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(currentEmail)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            if (isEmailInUseByOthers(newEmail, user.getId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "该邮箱已绑定其他账号"));
            }

            if (!emailVerificationMailer.isEnabled()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "邮箱服务未启用，无法验证新邮箱"));
            }

            String newEmailCode = trimToEmpty(req.getNewEmailCode());
            if (newEmailCode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入新邮箱验证码"));
            }
            emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.CHANGE_EMAIL, newEmail, newEmailCode);

            String oldEmail = currentEmail;
            user.setEmail(newEmail);
            user.setSessionInvalidatedAt(LocalDateTime.now());
            usersRepository.save(user);

            try {
                accountEmailChangeNotificationMailer.sendChangeEmailSuccessNotifications(oldEmail, newEmail);
            } catch (Exception ignored) {
            }

            HttpSession session = servletRequest.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();

            return ResponseEntity.ok(Map.of("message", "邮箱更换成功，请重新登录"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    private boolean isEmailInUseByOthers(String email, Long currentUserId) {
        Optional<UsersEntity> found = usersRepository.findByEmailAndIsDeletedFalse(email);
        if (found.isEmpty()) return false;
        Long id = found.get().getId();
        return id != null && !id.equals(currentUserId);
    }

    private static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String trimToEmpty(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String currentEmailOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String name = auth.getName();
        return name == null ? null : name.trim();
    }

    private static ResponseEntity<Map<String, String>> unauthorized() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "未登录或会话已过期");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    private static LocalDateTime readFlowTime(HttpSession session, String key) {
        if (session == null) return null;
        Object v = session.getAttribute(key);
        if (v instanceof LocalDateTime t) return t;
        return null;
    }

    private void requirePasswordVerified(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        LocalDateTime at = readFlowTime(session, SESSION_PASSWORD_VERIFIED_AT);
        if (at == null || at.isBefore(LocalDateTime.now().minus(FLOW_TTL))) {
            throw new IllegalArgumentException("请先验证密码");
        }
    }

    private void requireOldVerified(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        LocalDateTime at = readFlowTime(session, SESSION_OLD_VERIFIED_AT);
        if (at == null || at.isBefore(LocalDateTime.now().minus(FLOW_TTL))) {
            throw new IllegalArgumentException("请先验证旧邮箱或动态验证码");
        }
    }
}
