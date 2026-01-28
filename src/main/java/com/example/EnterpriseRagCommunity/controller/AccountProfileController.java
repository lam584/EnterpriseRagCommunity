package com.example.EnterpriseRagCommunity.controller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.EnterpriseRagCommunity.dto.access.UpdateMyProfileRequest;
import com.example.EnterpriseRagCommunity.dto.access.UsersDTO;
import com.example.EnterpriseRagCommunity.dto.access.request.ChangePasswordRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.AccountSecurityService;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.notify.AccountSecurityNotificationMailer;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AccountProfileController {

    private final UsersRepository usersRepository;
    private final AccountSecurityService accountSecurityService;
    private final AccountTotpService accountTotpService;
    private final EmailVerificationService emailVerificationService;
    private final EmailVerificationMailer emailVerificationMailer;
    private final NotificationsService notificationsService;
    private final AccountSecurityNotificationMailer accountSecurityNotificationMailer;

    @PutMapping("/profile")
    public ResponseEntity<?> updateMyProfile(@RequestBody @Valid UpdateMyProfileRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "未登录或会话已过期");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        // IMPORTANT: authentication name is email (see AuthController.login + SecurityConfig)
        String email = auth.getName();
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (StringUtils.hasText(req.getUsername())) {
            user.setUsername(req.getUsername().trim());
        }

        Map<String, Object> metadata0 = user.getMetadata();
        // IMPORTANT: For @Convert JSON Map fields, in-place mutations may NOT trigger dirty checking.
        // Use copy-on-write to ensure Hibernate sees the field as modified.
        Map<String, Object> metadata = (metadata0 == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata0);

        Object profileObj = metadata.get("profile");
        Map<String, Object> profile0;
        if (profileObj instanceof Map) {
            //noinspection unchecked
            profile0 = (Map<String, Object>) profileObj;
        } else {
            profile0 = null;
        }
        Map<String, Object> profile = (profile0 == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(profile0);

        // Only patch known keys. Don't overwrite whole metadata.
        // Semantics:
        // - missing field in JSON => leave as-is
        // - explicit null => clear (set to null)
        // - string => set (empty string treated as null)
        if (req.isAvatarUrlPresent()) profile.put("avatarUrl", emptyToNull(req.getAvatarUrl()));
        if (req.isBioPresent()) profile.put("bio", emptyToNull(req.getBio()));
        if (req.isLocationPresent()) profile.put("location", emptyToNull(req.getLocation()));
        if (req.isWebsitePresent()) profile.put("website", emptyToNull(req.getWebsite()));

        metadata.put("profile", profile);
        user.setMetadata(metadata);

        UsersEntity saved = usersRepository.save(user);
        return ResponseEntity.ok(toSafeDTO(saved));
    }

    @PostMapping("/password")
    public ResponseEntity<?> changeMyPassword(@RequestBody @Valid ChangePasswordRequest req, HttpServletRequest servletRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "未登录或会话已过期");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = auth.getName();

        try {
            UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            boolean emailSvcEnabled = emailVerificationMailer.isEnabled();
            boolean totpEnabled = accountTotpService.isEnabledByEmail(email);

            if (emailSvcEnabled && totpEnabled) {
                // 1. If both are enabled, allow user to choose one
                String emailCode = req.getEmailCode() == null ? "" : req.getEmailCode().trim();
                String totpCode = req.getTotpCode() == null ? "" : req.getTotpCode().trim();

                if (emailCode.isEmpty() && totpCode.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入验证码（邮箱或动态验证码任选其一）"));
                }

                if (!emailCode.isEmpty()) {
                    emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.CHANGE_PASSWORD, emailCode);
                } else {
                    accountTotpService.requireValidEnabledCodeByEmail(email, totpCode);
                }
            } else {
                // 2. Otherwise enforce whichever is enabled
                if (emailSvcEnabled) {
                    String emailCode = req.getEmailCode() == null ? "" : req.getEmailCode().trim();
                    if (emailCode.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入邮箱验证码"));
                    }
                    emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.CHANGE_PASSWORD, emailCode);
                }
                if (totpEnabled) {
                    String code = req.getTotpCode() == null ? "" : req.getTotpCode().trim();
                    if (code.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入动态验证码"));
                    }
                    accountTotpService.requireValidEnabledCodeByEmail(email, code);
                }
            }
            accountSecurityService.changePasswordByEmail(email, req.getCurrentPassword(), req.getNewPassword());
            try {
                notificationsService.createNotification(user.getId(), "SECURITY", "账号安全通知", "你的账号密码已修改成功。");
            } catch (Exception ignore) {
            }
            try {
                accountSecurityNotificationMailer.sendPasswordChanged(user.getEmail());
            } catch (Exception ignore) {
            }

            HttpSession session = servletRequest.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();

            return ResponseEntity.ok(Map.of("message", "密码修改成功"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    private static String emptyToNull(String v) {
        String t = v == null ? null : v.trim();
        return (t == null || t.isEmpty()) ? null : t;
    }

    private static UsersDTO toSafeDTO(UsersEntity user) {
        UsersDTO dto = new UsersDTO();
        dto.setId(user.getId());
        if (user.getTenantId() != null) {
            dto.setTenantId(user.getTenantId().getId());
        }
        dto.setEmail(user.getEmail());
        dto.setUsername(user.getUsername());
        dto.setStatus(user.getStatus());
        dto.setIsDeleted(user.getIsDeleted());
        dto.setMetadata(user.getMetadata());
        return dto;
    }
}
