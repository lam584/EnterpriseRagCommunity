package com.example.EnterpriseRagCommunity.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import lombok.Getter;
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
import com.example.EnterpriseRagCommunity.dto.access.request.VerifyPasswordRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.ChangePasswordRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.AccountSecurityService;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.notify.AccountSecurityNotificationMailer;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationActionsEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ActionType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationAutoKickService;
import com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AccountProfileController {
    private static final String SESSION_LOGIN2FA_PREF_PWD_VERIFIED_AT = "login2faPref.passwordVerifiedAt";
    private static final long LOGIN2FA_PREF_PWD_VERIFY_TTL_MS = 10 * 60 * 1000L;

    private final UsersRepository usersRepository;
    private final AccountSecurityService accountSecurityService;
    private final AccountTotpService accountTotpService;
    private final EmailVerificationService emailVerificationService;
    @Getter
    private final EmailVerificationMailer emailVerificationMailer;
    private final Security2faPolicyService security2faPolicyService;
    private final NotificationsService notificationsService;
    private final AccountSecurityNotificationMailer accountSecurityNotificationMailer;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;
    private final ModerationQueueRepository moderationQueueRepository;
    private final ModerationActionsRepository moderationActionsRepository;
    private final ModerationAutoKickService moderationAutoKickService;
    private final ModerationRuleAutoRunner moderationRuleAutoRunner;

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    @GetMapping("/security-2fa-policy")
    public ResponseEntity<?> getMySecurity2faPolicy() {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();

        UsersEntity user = requireActiveUserByEmail(email);

        Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(user.getId());
        return ResponseEntity.ok(policy);
    }

    @PutMapping("/login-2fa-preference")
    public ResponseEntity<?> updateMyLogin2faPreference(@RequestBody @Valid UpdateLogin2faPreferenceRequest req, HttpServletRequest servletRequest) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();

        UsersEntity user = requireActiveUserByEmail(email);
        Map<String, Object> beforeAudit = summarizeLogin2faPreferenceForAudit(user);

        Security2faPolicyStatusDTO policy = security2faPolicyService.evaluateForUser(user.getId());
        if (!policy.isLogin2faCanEnable()) {
            writeAuditSafely(user.getId(), email, "ACCOUNT_LOGIN_2FA_PREFERENCE_UPDATE", AuditResult.FAIL, "配置登录二次验证失败", Map.of("reason", "policy_forbidden"));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "当前策略不允许你配置登录二次验证"));
        }
        if (!isPasswordVerified(servletRequest)) {
            writeAuditSafely(user.getId(), email, "ACCOUNT_LOGIN_2FA_PREFERENCE_UPDATE", AuditResult.FAIL, "配置登录二次验证失败", Map.of("reason", "password_not_verified"));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请先验证密码"));
        }

        String method = req.getMethod() == null ? "" : req.getMethod().trim().toLowerCase();
        if (!method.equals("totp") && !method.equals("email")) {
            writeAuditSafely(user.getId(), email, "ACCOUNT_LOGIN_2FA_PREFERENCE_UPDATE", AuditResult.FAIL, "配置登录二次验证失败", Map.of("reason", "invalid_method"));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "验证方式不合法"));
        }
        if (method.equals("totp")) {
            if (!policy.isTotpAllowed()) {
                writeAuditSafely(user.getId(), email, "ACCOUNT_LOGIN_2FA_PREFERENCE_UPDATE", AuditResult.FAIL, "配置登录二次验证失败", Map.of("reason", "totp_forbidden"));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "管理员已禁止使用动态验证码"));
            }
            boolean totpEnabled = accountTotpService.isEnabledByEmail(email);
            if (!totpEnabled) {
                writeAuditSafely(user.getId(), email, "ACCOUNT_LOGIN_2FA_PREFERENCE_UPDATE", AuditResult.FAIL, "配置登录二次验证失败", Map.of("reason", "totp_not_enabled"));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "当前账号未启用 TOTP"));
            }
            String totpCode = req.getTotpCode() == null ? "" : req.getTotpCode().trim();
            if (totpCode.isEmpty()) {
                writeAuditSafely(user.getId(), email, "ACCOUNT_LOGIN_2FA_PREFERENCE_UPDATE", AuditResult.FAIL, "配置登录二次验证失败", Map.of("reason", "totp_code_missing"));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入动态验证码"));
            }
            accountTotpService.requireValidEnabledCodeByEmail(email, totpCode);
        } else {
            if (!policy.isEmailOtpAllowed()) {
                writeAuditSafely(user.getId(), email, "ACCOUNT_LOGIN_2FA_PREFERENCE_UPDATE", AuditResult.FAIL, "配置登录二次验证失败", Map.of("reason", "email_otp_forbidden"));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "管理员已禁止使用邮箱验证码"));
            }
            String emailCode = req.getEmailCode() == null ? "" : req.getEmailCode().trim();
            if (emailCode.isEmpty()) {
                writeAuditSafely(user.getId(), email, "ACCOUNT_LOGIN_2FA_PREFERENCE_UPDATE", AuditResult.FAIL, "配置登录二次验证失败", Map.of("reason", "email_code_missing"));
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "请输入邮箱验证码"));
            }
            emailVerificationService.verifyAndConsume(user.getId(), EmailVerificationPurpose.LOGIN_2FA_PREFERENCE, emailCode);
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
        clearPasswordVerified(servletRequest);

        Map<String, Object> afterAudit = summarizeLogin2faPreferenceForAudit(user);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("method", method);
        meta.put("enabled", enabled);
        auditLogWriter.write(
                user.getId(),
                email,
                "ACCOUNT_LOGIN_2FA_PREFERENCE_UPDATE",
                "USER",
                user.getId(),
                AuditResult.SUCCESS,
                "配置登录二次验证",
                null,
                mergeDetails(auditDiffBuilder.build(beforeAudit, afterAudit), meta)
        );

        Security2faPolicyStatusDTO refreshed = security2faPolicyService.evaluateForUser(user.getId());
        return ResponseEntity.ok(refreshed);
    }

    @PostMapping("/login-2fa-preference/verify-password")
    public ResponseEntity<?> verifyLogin2faPreferencePassword(@RequestBody @Valid VerifyPasswordRequest req, HttpServletRequest servletRequest) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();

        String password = req.getPassword() == null ? "" : req.getPassword();
        try {
            accountSecurityService.verifyPasswordByEmail(email, password);
            markPasswordVerified(servletRequest);
            UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            writeAuditSafely(user.getId(), email, "ACCOUNT_LOGIN_2FA_PREFERENCE_VERIFY_PASSWORD", AuditResult.SUCCESS, "验证密码（用于修改登录二次验证）", Map.of("success", true));
            try {
                notificationsService.createNotification(user.getId(), "SECURITY", "账号安全通知", "你正在修改登录二次验证设置。");
            } catch (Exception ignore) {
            }
            return ResponseEntity.ok(Map.of("message", "密码验证通过"));
        } catch (IllegalArgumentException e) {
            try {
                UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email).orElse(null);
                if (user != null) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("success", false);
                    details.put("message", safeMsg(e.getMessage()));
                    writeAuditSafely(user.getId(), email, "ACCOUNT_LOGIN_2FA_PREFERENCE_VERIFY_PASSWORD", AuditResult.FAIL, "验证密码失败（用于修改登录二次验证）", details);
                }
            } catch (Exception ignore) {
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/profile")
    @Transactional
    public ResponseEntity<?> updateMyProfile(@RequestBody @Valid UpdateMyProfileRequest req) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();

        // IMPORTANT: authentication name is email (see AuthController.login + SecurityConfig)
        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Map<String, Object> beforeAudit = summarizeProfileForAudit(user);

        String reqUsername = req.getUsername();
        if (reqUsername != null && reqUsername.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "昵称不能为空"));
        }

        Map<String, Object> metadata0 = user.getMetadata();
        // IMPORTANT: For @Convert JSON Map fields, in-place mutations may NOT trigger dirty checking.
        // Use copy-on-write to ensure Hibernate sees the field as modified.
        Map<String, Object> metadata = (metadata0 == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata0);

        Map<String, Object> publicProfile = readProfileMap(metadata, "profile");
        Map<String, Object> pending0 = readProfileMapOrNull(metadata);
        Map<String, Object> pending = pending0 == null ? new LinkedHashMap<>(publicProfile) : new LinkedHashMap<>(pending0);
        if (!pending.containsKey("username")) pending.put("username", user.getUsername());

        if (StringUtils.hasText(reqUsername)) {
            pending.put("username", reqUsername.trim());
        }
        if (req.isAvatarUrlPresent()) pending.put("avatarUrl", emptyToNull(req.getAvatarUrl()));
        if (req.isBioPresent()) pending.put("bio", emptyToNull(req.getBio()));
        if (req.isLocationPresent()) pending.put("location", emptyToNull(req.getLocation()));
        if (req.isWebsitePresent()) pending.put("website", emptyToNull(req.getWebsite()));

        LocalDateTime now = LocalDateTime.now();
        metadata.put("profilePending", pending);
        metadata.put("profilePendingSubmittedAt", now.toString());
        user.setMetadata(metadata);

        UsersEntity saved = usersRepository.save(user);
        Long userId = saved.getId();

        ModerationQueueEntity q = moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.PROFILE, userId)
                .orElseGet(() -> {
                    ModerationQueueEntity e = new ModerationQueueEntity();
                    e.setCaseType(ModerationCaseType.CONTENT);
                    e.setContentType(ContentType.PROFILE);
                    e.setContentId(userId);
                    e.setStatus(QueueStatus.PENDING);
                    e.setCurrentStage(QueueStage.RULE);
                    e.setPriority(0);
                    e.setAssignedToId(null);
                    e.setLockedBy(null);
                    e.setLockedAt(null);
                    e.setFinishedAt(null);
                    e.setCreatedAt(now);
                    e.setUpdatedAt(now);
                    return moderationQueueRepository.save(e);
                });
        moderationQueueRepository.requeueToAuto(q.getId(), QueueStatus.PENDING, QueueStage.RULE, now);
        tryWriteProfilePendingSnapshot(q, saved, pending, now);

        Map<String, Object> metadata2 = saved.getMetadata() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(saved.getMetadata());
        Map<String, Object> pm = new LinkedHashMap<>();
        pm.put("caseType", "CONTENT");
        pm.put("queueId", q.getId());
        pm.put("status", "PENDING");
        pm.put("updatedAt", now.toString());
        metadata2.put("profileModeration", pm);
        saved.setMetadata(metadata2);
        saved = usersRepository.save(saved);

        auditLogWriter.write(
                saved.getId(),
                email,
                "ACCOUNT_PROFILE_UPDATE",
                "USER",
                saved.getId(),
                AuditResult.SUCCESS,
                "提交个人资料审核",
                null,
                auditDiffBuilder.build(beforeAudit, summarizeProfileForAudit(saved))
        );
        moderationQueueRepository.flush();
        moderationRuleAutoRunner.runForQueueId(q.getId());
        saved = usersRepository.findById(saved.getId()).orElse(saved);
        scheduleModerationAutoRunAfterCommit(q.getId());
        return ResponseEntity.ok(toSafeDTO(saved));
    }

    private void tryWriteProfilePendingSnapshot(ModerationQueueEntity q, UsersEntity u, Map<String, Object> pendingProfile, LocalDateTime submittedAt) {
        try {
            if (q == null || q.getId() == null || u == null || u.getId() == null) return;
            ModerationActionsEntity a = new ModerationActionsEntity();
            a.setQueueId(q.getId());
            a.setActorUserId(u.getId());
            a.setAction(ActionType.NOTE);
            a.setReason("PROFILE_PENDING_SNAPSHOT");
            LinkedHashMap<String, Object> snap = new LinkedHashMap<>();
            String snapshotId = "profilePending:" + q.getId() + (submittedAt == null ? "" : (":at:" + submittedAt));
            snap.put("content_snapshot_id", snapshotId);
            snap.put("target_id", u.getId());
            snap.put("pending_profile", pendingProfile == null ? Map.of() : new LinkedHashMap<>(pendingProfile));
            snap.put("pending_submitted_at", submittedAt == null ? null : submittedAt.toString());
            a.setSnapshot(snap);
            a.setCreatedAt(LocalDateTime.now());
            moderationActionsRepository.save(a);
        } catch (Exception ignore) {
        }
    }

    private void scheduleModerationAutoRunAfterCommit(Long queueId) {
        if (queueId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        moderationAutoKickService.kickQueueId(queueId);
                    } catch (Exception ignore) {
                    }
                }
            });
            return;
        }
        try {
            moderationAutoKickService.kickQueueId(queueId);
        } catch (Exception ignore) {
        }
    }

    private static Map<String, Object> readProfileMap(Map<String, Object> metadata, String key) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (metadata == null) return out;
        Object obj = metadata.get(key);
        if (!(obj instanceof Map<?, ?> m)) return out;
        for (var e : m.entrySet()) {
            Object k = e.getKey();
            if (k == null) continue;
            out.put(String.valueOf(k), e.getValue());
        }
        return out;
    }

    private static Map<String, Object> readProfileMapOrNull(Map<String, Object> metadata) {
        if (metadata == null) return null;
        Object obj = metadata.get("profilePending");
        if (!(obj instanceof Map<?, ?>)) return null;
        return readProfileMap(metadata, "profilePending");
    }

    @PostMapping("/password")
    public ResponseEntity<?> changeMyPassword(@RequestBody @Valid ChangePasswordRequest req, HttpServletRequest servletRequest) {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();

        try {
            UsersEntity user = requireActiveUserByEmail(email);

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
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("usedEmailCode", !emailCode.isEmpty());
            details.put("usedTotpCode", !totpCode.isEmpty());
            details.put("requireEmail", requireEmail);
            details.put("requireTotp", requireTotp);
            details.put("success", true);
            writeAuditSafely(user.getId(), email, "ACCOUNT_PASSWORD_CHANGE", AuditResult.SUCCESS, "修改密码", details);
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
            try {
                UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email).orElse(null);
                if (user != null) {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("success", false);
                    details.put("message", safeMsg(e.getMessage()));
                    writeAuditSafely(user.getId(), email, "ACCOUNT_PASSWORD_CHANGE", AuditResult.FAIL, "修改密码失败", details);
                }
            } catch (Exception ignore) {
            }
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
        dto.setMetadata(sanitizeMetadataForJson(user.getMetadata()));
        return dto;
    }

    private static Map<String, Object> sanitizeMetadataForJson(Map<String, Object> metadata) {
        if (metadata == null) return null;
        Object v = sanitizeJsonValue(metadata);
        if (v instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> out = (Map<String, Object>) m;
            return out;
        }
        return null;
    }

    private static Object sanitizeJsonValue(Object v) {
        switch (v) {
            case null -> {
                return null;
            }
            case Map<?, ?> m -> {
                LinkedHashMap<String, Object> out = new LinkedHashMap<>();
                for (var e : m.entrySet()) {
                    Object k = e.getKey();
                    if (k == null) continue;
                    out.put(String.valueOf(k), sanitizeJsonValue(e.getValue()));
                }
                return out;
            }
            case List<?> list -> {
                List<Object> out = new ArrayList<>(list.size());
                for (Object it : list) {
                    out.add(sanitizeJsonValue(it));
                }
                return out;
            }
            default -> {
            }
        }
        return v;
    }

    private static boolean isPasswordVerified(HttpServletRequest req) {
        try {
            HttpSession session = req.getSession(false);
            if (session == null) return false;
            Object v = session.getAttribute(AccountProfileController.SESSION_LOGIN2FA_PREF_PWD_VERIFIED_AT);
            if (!(v instanceof Long t)) return false;
            long now = System.currentTimeMillis();
            return t > 0 && now - t <= LOGIN2FA_PREF_PWD_VERIFY_TTL_MS;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static void markPasswordVerified(HttpServletRequest req) {
        try {
            req.getSession(true).setAttribute(AccountProfileController.SESSION_LOGIN2FA_PREF_PWD_VERIFIED_AT, System.currentTimeMillis());
        } catch (Exception ignore) {
        }
    }

    private static void clearPasswordVerified(HttpServletRequest req) {
        try {
            HttpSession session = req.getSession(false);
            if (session == null) return;
            session.removeAttribute(AccountProfileController.SESSION_LOGIN2FA_PREF_PWD_VERIFIED_AT);
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

    private UsersEntity requireActiveUserByEmail(String email) {
        return usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private static Map<String, Object> summarizeLogin2faPreferenceForAudit(UsersEntity user) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (user == null) return m;
        m.put("userId", user.getId());
        Object md = user.getMetadata();
        if (!(md instanceof Map<?, ?> meta)) {
            m.put("login2faEnabled", null);
            return m;
        }
        Object prefsObj = meta.get("preferences");
        if (!(prefsObj instanceof Map<?, ?> prefs)) {
            m.put("login2faEnabled", null);
            return m;
        }
        Object secObj = prefs.get("security");
        if (!(secObj instanceof Map<?, ?> sec)) {
            m.put("login2faEnabled", null);
            return m;
        }
        Object v = sec.get("login2faEnabled");
        m.put("login2faEnabled", v);
        return m;
    }

    private static Map<String, Object> summarizeProfileForAudit(UsersEntity user) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (user == null) return m;
        m.put("userId", user.getId());
        m.put("usernameLen", user.getUsername() == null ? 0 : user.getUsername().length());
        Map<?, ?> md = user.getMetadata();
        if (md == null) return m;
        Object profileObj = md.get("profile");
        if (!(profileObj instanceof Map<?, ?> profile)) return m;
        m.put("avatarUrlLen", strLen(profile.get("avatarUrl")));
        m.put("bioLen", strLen(profile.get("bio")));
        m.put("locationLen", strLen(profile.get("location")));
        m.put("websiteLen", strLen(profile.get("website")));
        return m;
    }

    private static int strLen(Object v) {
        if (v == null) return 0;
        String s = String.valueOf(v);
        return s.length();
    }

    private static Map<String, Object> mergeDetails(Map<String, Object> base, Map<String, Object> extra) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (base != null) out.putAll(base);
        if (extra != null) out.putAll(extra);
        return out;
    }

    private void writeAuditSafely(Long userId, String actorName, String action, AuditResult result, String message, Map<String, Object> details) {
        try {
            auditLogWriter.write(
                    userId,
                    actorName,
                    action,
                    "USER",
                    userId,
                    result,
                    message,
                    null,
                    details == null ? Map.of() : details
            );
        } catch (Exception ignore) {
        }
    }

    private static String safeMsg(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() <= 256) return t;
        return t.substring(0, 256);
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getMyProfileView() {
        String email = currentEmailOrNull();
        if (email == null) return unauthorized();

        UsersEntity user = usersRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UsersDTO dto = toSafeDTO(user);
        Map<String, Object> md0 = dto.getMetadata();
        Map<String, Object> md = md0 == null ? new LinkedHashMap<>() : new LinkedHashMap<>(md0);

        ModerationQueueEntity q = moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.PROFILE, user.getId()).orElse(null);
        if (q != null) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("caseType", "CONTENT");
            pm.put("queueId", q.getId());
            pm.put("status", enumName(q.getStatus()));
            pm.put("stage", enumName(q.getCurrentStage()));
            pm.put("updatedAt", q.getUpdatedAt() == null ? null : q.getUpdatedAt().toString());
            md.put("profileModeration", pm);
        }
        dto.setMetadata(md);
        return ResponseEntity.ok(dto);
    }

}
