package com.example.EnterpriseRagCommunity.controller;

import java.util.HashMap;
import java.util.Map;

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
import com.example.EnterpriseRagCommunity.dto.access.request.TotpVerifyRequest;
import com.example.EnterpriseRagCommunity.dto.access.response.TotpEnrollResponse;
import com.example.EnterpriseRagCommunity.dto.access.response.TotpStatusResponse;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.TotpPolicyService;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account/totp")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AccountTotpController {
    private final AccountTotpService accountTotpService;
    private final TotpPolicyService totpPolicyService;
    private final UsersRepository usersRepository;
    private final EmailVerificationService emailVerificationService;
    private final EmailVerificationMailer emailVerificationMailer;

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
    public ResponseEntity<?> enroll(@RequestBody(required = false) TotpEnrollRequest req) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();
        TotpEnrollResponse resp = accountTotpService.enrollByEmail(email, req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody @Valid TotpVerifyRequest req) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();
        if (emailVerificationMailer.isEnabled()) {
            String emailCode = req.getEmailCode() == null ? "" : req.getEmailCode().trim();
            if (emailCode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入邮箱验证码"));
            }
            UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
            emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.TOTP_ENABLE, emailCode);
        }
        TotpStatusResponse resp = accountTotpService.verifyByEmail(email, req.getPassword(), req.getCode());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/disable")
    public ResponseEntity<?> disable(@RequestBody @Valid TotpDisableRequest req) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();
        TotpStatusResponse resp = accountTotpService.disableByEmail(email, req.getCode());
        return ResponseEntity.ok(resp);
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
