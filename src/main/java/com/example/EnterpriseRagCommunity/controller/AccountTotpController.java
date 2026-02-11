package com.example.EnterpriseRagCommunity.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.EnterpriseRagCommunity.dto.access.TotpAdminSettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.request.TotpDisableRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.TotpEnrollRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.TotpPasswordVerifyRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.TotpVerifyRequest;
import com.example.EnterpriseRagCommunity.dto.access.response.TotpEnrollResponse;
import com.example.EnterpriseRagCommunity.dto.access.response.TotpStatusResponse;
import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicyStatusDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.TotpPolicyService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.notify.AccountSecurityNotificationMailer;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

@RestController
@RequestMapping("/api/account/totp")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AccountTotpController {
    private static final String SESSION_ENABLE_PWD_VERIFIED_AT = "totp_enable_pwd_verified_at";
    private static final String SESSION_DISABLE_PWD_VERIFIED_AT = "totp_disable_pwd_verified_at";
    private static final long PWD_VERIFY_TTL_MS = 5 * 60 * 1000L;

    private final AccountTotpService accountTotpService;
    private final TotpPolicyService totpPolicyService;
    private final UsersRepository usersRepository;
    private final EmailVerificationService emailVerificationService;
    private final EmailVerificationMailer emailVerificationMailer;
    private final Security2faPolicyService security2faPolicyService;
    private final NotificationsService notificationsService;
    private final AccountSecurityNotificationMailer accountSecurityNotificationMailer;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/policy")
    public ResponseEntity<?> policy() {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();
        TotpAdminSettingsDTO dto = totpPolicyService.getSettingsOrDefault();
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();
        TotpStatusResponse resp = accountTotpService.getStatusByEmail(email);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/enroll")
    public ResponseEntity<?> enroll(@RequestBody(required = false) TotpEnrollRequest req, jakarta.servlet.http.HttpServletRequest servletRequest) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(user.getId());
        if (!policy.isTotpAllowed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "管理员已禁止启用 TOTP"));
        }
        if (!policy.isEmailOtpAllowed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "管理员已禁止使用邮箱验证码，无法启用 TOTP"));
        }
        if (!isPasswordVerified(servletRequest, SESSION_ENABLE_PWD_VERIFIED_AT)) {
            String pwd = (req == null || req.getPassword() == null) ? "" : req.getPassword().trim();
            if (pwd.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请先验证密码"));
            }
            if (!passwordEncoder.matches(pwd, user.getPasswordHash())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "密码不正确"));
            }
            markPasswordVerified(servletRequest, SESSION_ENABLE_PWD_VERIFIED_AT);
        }
        String emailCode = (req == null || req.getEmailCode() == null) ? "" : req.getEmailCode().trim();
        if (emailCode.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入邮箱验证码"));
        }
        emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.TOTP_ENABLE, emailCode);
        TotpEnrollResponse resp = accountTotpService.enrollByEmail(email, req);
        try {
            notificationsService.createNotification(user.getId(), "SECURITY", "账号安全通知", "你正在进入修改 TOTP 流程。");
        } catch (Exception ignore) {
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody @Valid TotpVerifyRequest req, jakarta.servlet.http.HttpServletRequest servletRequest) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(user.getId());
        if (!policy.isTotpAllowed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "管理员已禁止启用 TOTP"));
        }
        String pwd = req.getPassword() == null ? "" : req.getPassword().trim();
        TotpStatusResponse resp;
        if (!pwd.isEmpty()) {
            resp = accountTotpService.verifyByEmail(email, pwd, req.getCode());
            markPasswordVerified(servletRequest, SESSION_ENABLE_PWD_VERIFIED_AT);
        } else {
            if (!isPasswordVerified(servletRequest, SESSION_ENABLE_PWD_VERIFIED_AT)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请先验证密码"));
            }
            resp = accountTotpService.verifyByEmailWithoutPassword(email, req.getCode());
        }
        try {
            if (resp != null && Boolean.TRUE.equals(resp.getEnabled())) {
                clearPasswordVerified(servletRequest, SESSION_ENABLE_PWD_VERIFIED_AT);
                notificationsService.createNotification(user.getId(), "SECURITY", "账号安全通知", "你的账号已启用 TOTP。");
                try {
                    accountSecurityNotificationMailer.sendTotpEnabled(user.getEmail());
                } catch (Exception ignore) {
                }
            }
        } catch (Exception ignore) {
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/disable")
    public ResponseEntity<?> disable(@RequestBody @Valid TotpDisableRequest req, jakarta.servlet.http.HttpServletRequest servletRequest) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(user.getId());
        if (policy.isTotpRequired()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "管理员已强制启用 TOTP，无法关闭"));
        }
        if (!isPasswordVerified(servletRequest, SESSION_DISABLE_PWD_VERIFIED_AT)) {
            String pwd = req.getPassword() == null ? "" : req.getPassword().trim();
            if (pwd.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请先验证密码"));
            }
            if (!passwordEncoder.matches(pwd, user.getPasswordHash())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "密码不正确"));
            }
            markPasswordVerified(servletRequest, SESSION_DISABLE_PWD_VERIFIED_AT);
        }
        String method = req.getMethod() == null ? "" : req.getMethod().trim().toLowerCase(Locale.ROOT);
        if (method.isEmpty()) method = "totp";

        TotpStatusResponse resp;
        if ("email".equals(method)) {
            if (!policy.isEmailOtpAllowed()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "管理员已禁止使用邮箱验证码"));
            }
            String emailCode = req.getEmailCode() == null ? "" : req.getEmailCode().trim();
            if (emailCode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入邮箱验证码"));
            }
            emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.TOTP_DISABLE, emailCode);
            resp = accountTotpService.disableByEmailWithoutTotp(email);
        } else {
            String code = req.getCode() == null ? "" : req.getCode().trim();
            if (code.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入验证码"));
            }
            resp = accountTotpService.disableByEmail(email, code);
        }
        try {
            if (resp != null && Boolean.FALSE.equals(resp.getEnabled())) {
                clearPasswordVerified(servletRequest, SESSION_DISABLE_PWD_VERIFIED_AT);
                notificationsService.createNotification(user.getId(), "SECURITY", "账号安全通知", "你的账号已关闭 TOTP。");
                try {
                    accountSecurityNotificationMailer.sendTotpDisabled(user.getEmail());
                } catch (Exception ignore) {
                }
            }
        } catch (Exception ignore) {
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/verify-password")
    public ResponseEntity<?> verifyPassword(@RequestBody @Valid TotpPasswordVerifyRequest req, jakarta.servlet.http.HttpServletRequest servletRequest) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
        String pwd = req.getPassword() == null ? "" : req.getPassword().trim();
        if (pwd.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入密码"));
        if (!passwordEncoder.matches(pwd, user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "密码不正确"));
        }
        String action = req.getAction() == null ? "" : req.getAction().trim().toUpperCase(Locale.ROOT);
        if ("ENABLE".equals(action)) {
            markPasswordVerified(servletRequest, SESSION_ENABLE_PWD_VERIFIED_AT);
        } else if ("DISABLE".equals(action)) {
            markPasswordVerified(servletRequest, SESSION_DISABLE_PWD_VERIFIED_AT);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "用途不支持"));
        }
        return ResponseEntity.ok(Map.of("message", "密码验证通过"));
    }

    private static boolean isPasswordVerified(jakarta.servlet.http.HttpServletRequest req, String key) {
        try {
            var session = req.getSession(false);
            if (session == null) return false;
            Object v = session.getAttribute(key);
            if (!(v instanceof Long t)) return false;
            long now = System.currentTimeMillis();
            return t > 0 && now - t <= PWD_VERIFY_TTL_MS;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static void markPasswordVerified(jakarta.servlet.http.HttpServletRequest req, String key) {
        try {
            req.getSession(true).setAttribute(key, System.currentTimeMillis());
        } catch (Exception ignore) {
        }
    }

    private static void clearPasswordVerified(jakarta.servlet.http.HttpServletRequest req, String key) {
        try {
            var session = req.getSession(false);
            if (session == null) return;
            session.removeAttribute(key);
        } catch (Exception ignore) {
        }
    }

    private static String currentEmailOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }

    private static ResponseEntity<Map<String, String>> unauthorized() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "未登录或会话已过期");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
}
