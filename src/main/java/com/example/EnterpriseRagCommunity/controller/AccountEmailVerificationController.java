package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.access.request.EmailVerificationSendRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
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

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestBody @Valid EmailVerificationSendRequest req) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();

        EmailVerificationPurpose purpose = parsePurpose(req.getPurpose());
        if (purpose != EmailVerificationPurpose.CHANGE_PASSWORD && purpose != EmailVerificationPurpose.TOTP_ENABLE) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "不支持的 purpose"));
        }
        if (!emailVerificationMailer.isEnabled()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "邮箱服务未启用"));
        }

        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
        String code = emailVerificationService.issueCode(user.getId(), purpose);
        emailVerificationMailer.sendVerificationCode(email, code, purpose);
        return ResponseEntity.ok(Map.of("message", "验证码已发送"));
    }

    private static EmailVerificationPurpose parsePurpose(String raw) {
        String v = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (v.isEmpty()) throw new IllegalArgumentException("purpose is required");
        return EmailVerificationPurpose.valueOf(v);
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
