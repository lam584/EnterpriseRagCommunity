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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.EnterpriseRagCommunity.dto.access.UpdateMyProfileRequest;
import com.example.EnterpriseRagCommunity.dto.access.UsersDTO;
import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicyStatusDTO;
import com.example.EnterpriseRagCommunity.dto.access.UpdateLogin2faPreferenceRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.ChangePasswordRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.AccountSecurityService;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
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
    private final Security2faPolicyService security2faPolicyService;
    private final NotificationsService notificationsService;
    private final AccountSecurityNotificationMailer accountSecurityNotificationMailer;

    @GetMapping("/security-2fa-policy")
    public ResponseEntity<?> getMySecurity2faPolicy() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "未登录或会话已过期");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = auth.getName();
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(user.getId());
        return ResponseEntity.ok(policy);
    }

    @PutMapping("/login-2fa-preference")
    public ResponseEntity<?> updateMyLogin2faPreference(@RequestBody @Valid UpdateLogin2faPreferenceRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "未登录或会话已过期");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String email = auth.getName();
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(user.getId());
        if (!policy.isLogin2faCanEnable()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "当前策略不允许你配置登录二次验证"));
        }

        Map<String, Object> metadata0 = user.getMetadata();
        Map<String, Object> metadata = (metadata0 == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata0);

        Object prefsObj = metadata.get("preferences");
        Map<String, Object> prefs0;
        if (prefsObj instanceof Map) {
            //noinspection unchecked
            prefs0 = (Map<String, Object>) prefsObj;
        } else {
            prefs0 = null;
        }
        Map<String, Object> prefs = (prefs0 == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(prefs0);

        Object secObj = prefs.get("security");
        Map<String, Object> sec0;
        if (secObj instanceof Map) {
            //noinspection unchecked
            sec0 = (Map<String, Object>) secObj;
        } else {
            sec0 = null;
        }
        Map<String, Object> security = (sec0 == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(sec0);

        boolean enabled = req.getEnabled() != null && req.getEnabled();
        security.put("login2faEnabled", enabled);
        prefs.put("security", security);
        metadata.put("preferences", prefs);
        user.setMetadata(metadata);
        usersRepository.save(user);

        Security2faPolicyStatusDTO refreshed = security2faPolicyService.evaluateForUser(user.getId());
        return ResponseEntity.ok(refreshed);
    }

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

            Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(user.getId());

            boolean totpEnabled = accountTotpService.isEnabledByEmail(email);
            boolean canUseTotp = totpEnabled && policy.isTotpAllowed();
            boolean canUseEmail = policy.isEmailOtpAllowed();

            boolean requireTotp = policy.isTotpRequired();
            boolean requireEmail = policy.isEmailOtpRequired();

            String emailCode = req.getEmailCode() == null ? "" : req.getEmailCode().trim();
            String totpCode = req.getTotpCode() == null ? "" : req.getTotpCode().trim();

            if (requireTotp && !totpEnabled) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "管理员已强制启用 TOTP，请先在账号安全页启用后再修改密码"));
            }

            if (requireEmail && emailCode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入邮箱验证码"));
            }
            if (requireTotp && totpCode.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入动态验证码"));
            }

            if (!requireEmail && !requireTotp) {
                if (canUseEmail && canUseTotp) {
                    if (emailCode.isEmpty() && totpCode.isEmpty()) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入验证码（邮箱或动态验证码任选其一）"));
                    }
                    if (!emailCode.isEmpty()) {
                        emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.CHANGE_PASSWORD, emailCode);
                    }
                    if (!totpCode.isEmpty()) {
                        accountTotpService.requireValidEnabledCodeByEmail(email, totpCode);
                    }
                } else {
                    if (canUseEmail) {
                        if (emailCode.isEmpty()) {
                            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入邮箱验证码"));
                        }
                        emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.CHANGE_PASSWORD, emailCode);
                    }
                    if (canUseTotp) {
                        if (totpCode.isEmpty()) {
                            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入动态验证码"));
                        }
                        accountTotpService.requireValidEnabledCodeByEmail(email, totpCode);
                    }
                }
            } else {
                if (requireEmail) {
                    emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.CHANGE_PASSWORD, emailCode);
                }
                if (requireTotp) {
                    accountTotpService.requireValidEnabledCodeByEmail(email, totpCode);
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
