package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.access.request.EmailVerificationSendRequest;
import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicyStatusDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/account/email-verification")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AccountEmailVerificationController {
    private final UsersRepository usersRepository;
    private final EmailVerificationService emailVerificationService;
    private final EmailVerificationMailer emailVerificationMailer;
    private final NotificationsService notificationsService;
    private final Security2faPolicyService security2faPolicyService;

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestBody @Valid EmailVerificationSendRequest req) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();

        EmailVerificationPurpose purpose = parsePurpose(req.getPurpose());
        if (purpose != EmailVerificationPurpose.CHANGE_PASSWORD
                && purpose != EmailVerificationPurpose.LOGIN_2FA_PREFERENCE
                && purpose != EmailVerificationPurpose.TOTP_ENABLE
                && purpose != EmailVerificationPurpose.TOTP_DISABLE) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "不支持的用途"));
        }
        if (!emailVerificationMailer.isEnabled()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "邮箱服务未启用"));
        }

        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(user.getId());
        if (!policy.isEmailOtpAllowed()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "管理员已禁止使用邮箱验证码"));
        }
        String code = emailVerificationService.issueCode(user.getId(), purpose);
        emailVerificationMailer.sendVerificationCode(email, code, purpose);
        try {
            if (purpose == EmailVerificationPurpose.CHANGE_PASSWORD) {
                notificationsService.createNotification(user.getId(), "SECURITY", "账号安全通知", "你正在进入修改密码流程，验证码已发送。");
            } else if (purpose == EmailVerificationPurpose.LOGIN_2FA_PREFERENCE) {
                notificationsService.createNotification(user.getId(), "SECURITY", "账号安全通知", "你正在修改登录二次验证设置，验证码已发送。");
            } else if (purpose == EmailVerificationPurpose.TOTP_ENABLE) {
                notificationsService.createNotification(user.getId(), "SECURITY", "账号安全通知", "你正在进入修改 TOTP 流程，验证码已发送。");
            } else if (purpose == EmailVerificationPurpose.TOTP_DISABLE) {
                notificationsService.createNotification(user.getId(), "SECURITY", "账号安全通知", "你正在进入停用 TOTP 流程，验证码已发送。");
            }
        } catch (Exception ignore) {
        }
        return ResponseEntity.ok(Map.of(
                "message", "验证码已发送",
                "resendWaitSeconds", emailVerificationService.getDefaultResendWaitSeconds(),
                "codeTtlSeconds", emailVerificationService.getDefaultTtlSeconds()
        ));
    }

    private static EmailVerificationPurpose parsePurpose(String raw) {
        String v = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (v.isEmpty()) throw new IllegalArgumentException("用途不能为空");
        try {
            return EmailVerificationPurpose.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("用途不合法");
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
