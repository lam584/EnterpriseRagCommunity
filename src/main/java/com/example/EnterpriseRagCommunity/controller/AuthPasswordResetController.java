package com.example.EnterpriseRagCommunity.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.EnterpriseRagCommunity.dto.access.request.PasswordResetResetRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.PasswordResetSendCodeRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.PasswordResetStatusRequest;
import com.example.EnterpriseRagCommunity.dto.access.response.PasswordResetStatusResponse;
import com.example.EnterpriseRagCommunity.service.AuthPasswordResetService;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AuthPasswordResetController {
    private final AuthPasswordResetService authPasswordResetService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/status")
    public ResponseEntity<?> status(@RequestBody @Valid PasswordResetStatusRequest req) {
        PasswordResetStatusResponse resp = authPasswordResetService.status(req.getEmail());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestBody @Valid PasswordResetResetRequest req) {
        try {
            String totpCode = req.getTotpCode() == null ? "" : req.getTotpCode().trim();
            String emailCode = req.getEmailCode() == null ? "" : req.getEmailCode().trim();
            if (!totpCode.isEmpty()) {
                authPasswordResetService.resetPasswordByEmailTotp(req.getEmail(), totpCode, req.getNewPassword());
            } else {
                authPasswordResetService.resetPasswordByEmailCode(req.getEmail(), emailCode, req.getNewPassword());
            }
            return ResponseEntity.ok(Map.of("message", "密码已重置，请使用新密码登录"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/send-code")
    public ResponseEntity<?> sendCode(@RequestBody @Valid PasswordResetSendCodeRequest req) {
        try {
            authPasswordResetService.sendPasswordResetEmailCode(req.getEmail());
            return ResponseEntity.ok(Map.of(
                    "message", "验证码已发送",
                    "resendWaitSeconds", emailVerificationService.getDefaultResendWaitSeconds(),
                    "codeTtlSeconds", emailVerificationService.getDefaultTtlSeconds()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }
}
