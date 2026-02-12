package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicyStatusDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.security.stepup.AdminStepUpInterceptor;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/step-up")
@RequiredArgsConstructor
public class AdminStepUpController {
    private static final int DEFAULT_TTL_SECONDS = 600;

    private final UsersRepository usersRepository;
    private final EmailVerificationService emailVerificationService;
    private final AccountTotpService accountTotpService;
    private final Security2faPolicyService security2faPolicyService;

    @GetMapping("/status")
    public ResponseEntity<?> status(HttpSession session) {
        UsersEntity user = currentUserOrThrow();
        Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(user.getId());
        long now = Instant.now().toEpochMilli();
        long okUntil = readOkUntil(session);

        List<String> methods = new ArrayList<>();
        if (policy.isEmailOtpAllowed()) methods.add("email");
        if (accountTotpService.isEnabledByEmail(user.getEmail())) methods.add("totp");

        return ResponseEntity.ok(Map.of(
                "ok", okUntil > now,
                "okUntilEpochMs", okUntil,
                "ttlSeconds", DEFAULT_TTL_SECONDS,
                "methods", methods,
                "emailOtpAllowed", policy.isEmailOtpAllowed()
        ));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody @Valid VerifyRequest req, HttpSession session) {
        UsersEntity user = currentUserOrThrow();

        String method = normalizeMethod(req.getMethod());
        if ("email".equals(method)) {
            Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(user.getId());
            if (!policy.isEmailOtpAllowed()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "管理员已禁止使用邮箱验证码"));
            }
            emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.ADMIN_STEP_UP, req.getCode());
        } else if ("totp".equals(method)) {
            accountTotpService.requireValidEnabledCodeByEmail(user.getEmail(), req.getCode());
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "不支持的验证方式"));
        }

        long okUntil = Instant.now().plusSeconds(DEFAULT_TTL_SECONDS).toEpochMilli();
        session.setAttribute(AdminStepUpInterceptor.SESSION_KEY_OK_UNTIL_EPOCH_MS, okUntil);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "okUntilEpochMs", okUntil,
                "ttlSeconds", DEFAULT_TTL_SECONDS
        ));
    }

    @PostMapping("/clear")
    public ResponseEntity<?> clear(HttpSession session) {
        session.removeAttribute(AdminStepUpInterceptor.SESSION_KEY_OK_UNTIL_EPOCH_MS);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private static long readOkUntil(HttpSession session) {
        if (session == null) return 0L;
        Object raw = session.getAttribute(AdminStepUpInterceptor.SESSION_KEY_OK_UNTIL_EPOCH_MS);
        if (raw instanceof Number n) return n.longValue();
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (Exception ignore) {
                return 0L;
            }
        }
        return 0L;
    }

    private static String normalizeMethod(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return v.isEmpty() ? "" : v;
    }

    private UsersEntity currentUserOrThrow() {
        String email = currentEmailOrNull();
        if (email == null) throw new IllegalStateException("未登录或会话已过期");
        return usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private static String currentEmailOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }

    @Data
    public static class VerifyRequest {
        @NotBlank
        private String method;
        @NotBlank
        private String code;
    }
}

