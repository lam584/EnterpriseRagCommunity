package com.example.EnterpriseRagCommunity.controller.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.EnterpriseRagCommunity.dto.access.EmailAdminSettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.EmailInboxMessageDTO;
import com.example.EnterpriseRagCommunity.dto.access.EmailInboxSettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.EmailTestSendDTO;
import com.example.EnterpriseRagCommunity.dto.access.RegistrationSettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicySettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.TotpAdminSettingsDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.access.RolesRepository;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.example.EnterpriseRagCommunity.service.notify.EmailEncryption;
import com.example.EnterpriseRagCommunity.service.notify.EmailInboxService;
import com.example.EnterpriseRagCommunity.service.notify.EmailSenderService;
import com.example.EnterpriseRagCommunity.service.notify.EmailTransportConfig;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {
    private final AppSettingsService appSettingsService;
    private final RolesRepository rolesRepository;
    private final SystemConfigurationService systemConfigurationService;
    private final EmailSenderService emailSenderService;
    private final ObjectProvider<EmailInboxService> emailInboxServiceProvider;
    private final Security2faPolicyService security2faPolicyService;
    private final AuditLogWriter auditLogWriter;
    private final AuditDiffBuilder auditDiffBuilder;

    @GetMapping("/registration")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','access'))")
    public ResponseEntity<RegistrationSettingsDTO> getRegistration() {
        long roleId = appSettingsService.getLongOrDefault(AppSettingsService.KEY_DEFAULT_REGISTER_ROLE_ID, 1L);
        boolean enabled = appSettingsService.getLongOrDefault(AppSettingsService.KEY_REGISTRATION_ENABLED, 1L) == 1L;
        RegistrationSettingsDTO dto = new RegistrationSettingsDTO();
        dto.setDefaultRegisterRoleId(roleId);
        dto.setRegistrationEnabled(enabled);
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/registration")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users','access'))")
    public ResponseEntity<RegistrationSettingsDTO> putRegistration(@RequestBody @Valid RegistrationSettingsDTO dto) {
        RegistrationSettingsDTO before = new RegistrationSettingsDTO();
        before.setDefaultRegisterRoleId(appSettingsService.getLongOrDefault(AppSettingsService.KEY_DEFAULT_REGISTER_ROLE_ID, 1L));
        before.setRegistrationEnabled(appSettingsService.getLongOrDefault(AppSettingsService.KEY_REGISTRATION_ENABLED, 1L) == 1L);

        long roleId = dto.getDefaultRegisterRoleId() != null ? dto.getDefaultRegisterRoleId() : 1L;
        if (roleId <= 0) throw new IllegalArgumentException("defaultRegisterRoleId must be positive");
        if (!rolesRepository.existsById(roleId)) throw new IllegalArgumentException("defaultRegisterRoleId 对应角色不存在: " + roleId);
        appSettingsService.upsertString(AppSettingsService.KEY_DEFAULT_REGISTER_ROLE_ID, String.valueOf(roleId));
        boolean enabled = dto.getRegistrationEnabled() == null || dto.getRegistrationEnabled();
        appSettingsService.upsertString(AppSettingsService.KEY_REGISTRATION_ENABLED, enabled ? "1" : "0");

        RegistrationSettingsDTO after = new RegistrationSettingsDTO();
        after.setDefaultRegisterRoleId(roleId);
        after.setRegistrationEnabled(enabled);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "ADMIN_SETTINGS_UPDATE",
                "REGISTRATION_SETTINGS",
                null,
                AuditResult.SUCCESS,
                "更新注册配置",
                null,
                auditDiffBuilder.build(before, after)
        );
        return ResponseEntity.ok(dto);
    }

    private static final String KEY_TOTP_ISSUER = "totp_issuer";
    private static final String KEY_TOTP_ALLOWED_ALG = "totp_allowed_algorithms";
    private static final String KEY_TOTP_ALLOWED_DIGITS = "totp_allowed_digits";
    private static final String KEY_TOTP_ALLOWED_PERIOD = "totp_allowed_period_seconds";
    private static final String KEY_TOTP_MAX_SKEW = "totp_max_skew";
    private static final String KEY_TOTP_DEFAULT_ALG = "totp_default_algorithm";
    private static final String KEY_TOTP_DEFAULT_DIGITS = "totp_default_digits";
    private static final String KEY_TOTP_DEFAULT_PERIOD = "totp_default_period_seconds";
    private static final String KEY_TOTP_DEFAULT_SKEW = "totp_default_skew";

    private static final String KEY_EMAIL_ENABLED = "email_enabled";
    private static final String KEY_EMAIL_OTP_TTL_SECONDS = "email_otp_ttl_seconds";
    private static final String KEY_EMAIL_OTP_RESEND_WAIT_SECONDS = "email_otp_resend_wait_seconds";
    private static final String KEY_EMAIL_OTP_RESEND_WAIT_REDUCTION_AFTER_VERIFIED_SECONDS = "email_otp_resend_wait_reduction_after_verified_seconds";
    private static final String KEY_EMAIL_PROTOCOL = "email_protocol";
    private static final String KEY_EMAIL_HOST = "email_host";
    private static final String KEY_EMAIL_PORT_PLAIN = "email_port_plain";
    private static final String KEY_EMAIL_PORT_ENCRYPTED = "email_port_encrypted";
    private static final String KEY_EMAIL_ENCRYPTION = "email_encryption";
    private static final String KEY_EMAIL_CONNECT_TIMEOUT_MS = "email_connect_timeout_ms";
    private static final String KEY_EMAIL_TIMEOUT_MS = "email_timeout_ms";
    private static final String KEY_EMAIL_WRITE_TIMEOUT_MS = "email_write_timeout_ms";
    private static final String KEY_EMAIL_DEBUG = "email_debug";
    private static final String KEY_EMAIL_SSL_TRUST = "email_ssl_trust";
    private static final String KEY_EMAIL_SUBJECT_PREFIX = "email_subject_prefix";
    private static final String KEY_EMAIL_USERNAME = "APP_MAIL_USERNAME";
    private static final String KEY_EMAIL_PASSWORD = "APP_MAIL_PASSWORD";
    private static final String KEY_EMAIL_FROM = "APP_MAIL_FROM_ADDRESS";
    private static final String KEY_EMAIL_FROM_NAME = "APP_MAIL_FROM_NAME";

    private static final String KEY_EMAIL_INBOX_PROTOCOL = "email_inbox_protocol";
    private static final String KEY_EMAIL_INBOX_HOST = "email_inbox_host";
    private static final String KEY_EMAIL_INBOX_PORT_PLAIN = "email_inbox_port_plain";
    private static final String KEY_EMAIL_INBOX_PORT_ENCRYPTED = "email_inbox_port_encrypted";
    private static final String KEY_EMAIL_INBOX_ENCRYPTION = "email_inbox_encryption";
    private static final String KEY_EMAIL_INBOX_CONNECT_TIMEOUT_MS = "email_inbox_connect_timeout_ms";
    private static final String KEY_EMAIL_INBOX_TIMEOUT_MS = "email_inbox_timeout_ms";
    private static final String KEY_EMAIL_INBOX_WRITE_TIMEOUT_MS = "email_inbox_write_timeout_ms";
    private static final String KEY_EMAIL_INBOX_DEBUG = "email_inbox_debug";
    private static final String KEY_EMAIL_INBOX_SSL_TRUST = "email_inbox_ssl_trust";
    private static final String KEY_EMAIL_INBOX_FOLDER = "email_inbox_folder";
    private static final String KEY_EMAIL_SENT_FOLDER = "email_sent_folder";

    private static Optional<List<String>> parseStringList(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        List<String> out = new ArrayList<>();
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            out.add(t);
        }
        if (out.isEmpty()) return Optional.empty();
        return Optional.of(out);
    }

    @PutMapping("/totp")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users_2fa','access'))")
    public ResponseEntity<TotpAdminSettingsDTO> putTotp(@RequestBody @Valid TotpAdminSettingsDTO dto) {
        TotpAdminSettingsDTO before = getTotp().getBody();
        if (before == null) before = new TotpAdminSettingsDTO();

        TotpAdminSettingsDTO normalized = normalizeTotpSettings(dto);

        appSettingsService.upsertString(KEY_TOTP_ISSUER, normalized.getIssuer());
        appSettingsService.upsertString(KEY_TOTP_ALLOWED_ALG, String.join(",", normalized.getAllowedAlgorithms()));
        appSettingsService.upsertString(KEY_TOTP_ALLOWED_DIGITS, joinInts(normalized.getAllowedDigits()));
        appSettingsService.upsertString(KEY_TOTP_ALLOWED_PERIOD, joinInts(normalized.getAllowedPeriodSeconds()));
        appSettingsService.upsertString(KEY_TOTP_MAX_SKEW, String.valueOf(normalized.getMaxSkew()));
        appSettingsService.upsertString(KEY_TOTP_DEFAULT_ALG, normalized.getDefaultAlgorithm());
        appSettingsService.upsertString(KEY_TOTP_DEFAULT_DIGITS, String.valueOf(normalized.getDefaultDigits()));
        appSettingsService.upsertString(KEY_TOTP_DEFAULT_PERIOD, String.valueOf(normalized.getDefaultPeriodSeconds()));
        appSettingsService.upsertString(KEY_TOTP_DEFAULT_SKEW, String.valueOf(normalized.getDefaultSkew()));

        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "ADMIN_SETTINGS_UPDATE",
                "TOTP_SETTINGS",
                null,
                AuditResult.SUCCESS,
                "更新 TOTP 管理配置",
                null,
                auditDiffBuilder.build(before, normalized)
        );
        return ResponseEntity.ok(normalized);
    }

    @GetMapping("/security-2fa-policy")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users_2fa','access'))")
    public ResponseEntity<Security2faPolicySettingsDTO> getSecurity2faPolicy() {
        return ResponseEntity.ok(security2faPolicyService.getAdminSettingsOrDefault());
    }

    @PutMapping("/security-2fa-policy")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users_2fa','access'))")
    public ResponseEntity<Security2faPolicySettingsDTO> putSecurity2faPolicy(@RequestBody Security2faPolicySettingsDTO dto) {
        Security2faPolicySettingsDTO before = security2faPolicyService.getAdminSettingsOrDefault();
        Security2faPolicySettingsDTO saved = security2faPolicyService.saveAdminSettings(dto);
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "ADMIN_SETTINGS_UPDATE",
                "SECURITY_2FA_POLICY",
                null,
                AuditResult.SUCCESS,
                "更新登录二次验证策略",
                null,
                auditDiffBuilder.build(before, saved)
        );
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/email")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users_2fa','access'))")
    public ResponseEntity<EmailAdminSettingsDTO> getEmail() {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setEnabled(appSettingsService.getLongOrDefault(KEY_EMAIL_ENABLED, 1L) == 1L);
        dto.setOtpTtlSeconds((int) appSettingsService.getLongOrDefault(KEY_EMAIL_OTP_TTL_SECONDS, 600L));
        dto.setOtpResendWaitSeconds((int) appSettingsService.getLongOrDefault(KEY_EMAIL_OTP_RESEND_WAIT_SECONDS, 120L));
        dto.setOtpResendWaitReductionSecondsAfterVerified((int) appSettingsService.getLongOrDefault(KEY_EMAIL_OTP_RESEND_WAIT_REDUCTION_AFTER_VERIFIED_SECONDS, 0L));
        dto.setProtocol(appSettingsService.getString(KEY_EMAIL_PROTOCOL).orElse("SMTP"));
        dto.setHost(appSettingsService.getString(KEY_EMAIL_HOST).orElse("smtp.qiye.aliyun.com"));
        dto.setPortPlain((int) appSettingsService.getLongOrDefault(KEY_EMAIL_PORT_PLAIN, 25L));
        dto.setPortEncrypted((int) appSettingsService.getLongOrDefault(KEY_EMAIL_PORT_ENCRYPTED, 465L));
        dto.setEncryption(appSettingsService.getString(KEY_EMAIL_ENCRYPTION).orElse("SSL"));
        dto.setConnectTimeoutMs((int) appSettingsService.getLongOrDefault(KEY_EMAIL_CONNECT_TIMEOUT_MS, 10_000L));
        dto.setTimeoutMs((int) appSettingsService.getLongOrDefault(KEY_EMAIL_TIMEOUT_MS, 10_000L));
        dto.setWriteTimeoutMs((int) appSettingsService.getLongOrDefault(KEY_EMAIL_WRITE_TIMEOUT_MS, 10_000L));
        dto.setDebug(appSettingsService.getLongOrDefault(KEY_EMAIL_DEBUG, 0L) == 1L);
        dto.setSslTrust(appSettingsService.getString(KEY_EMAIL_SSL_TRUST).orElse(""));
        dto.setSubjectPrefix(appSettingsService.getString(KEY_EMAIL_SUBJECT_PREFIX).orElse(""));
        dto.setUsername(systemConfigurationService.getConfig(KEY_EMAIL_USERNAME));
        dto.setPassword(systemConfigurationService.getConfig(KEY_EMAIL_PASSWORD));
        dto.setFrom(systemConfigurationService.getConfig(KEY_EMAIL_FROM));
        dto.setFromName(systemConfigurationService.getConfig(KEY_EMAIL_FROM_NAME));
        return ResponseEntity.ok(normalizeEmailSettings(dto));
    }

    @PutMapping("/email")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users_2fa','access'))")
    public ResponseEntity<EmailAdminSettingsDTO> putEmail(@RequestBody @Valid EmailAdminSettingsDTO dto) {
        EmailAdminSettingsDTO before = getEmail().getBody();
        if (before == null) before = new EmailAdminSettingsDTO();

        EmailAdminSettingsDTO normalized = normalizeEmailSettings(dto);

        appSettingsService.upsertString(KEY_EMAIL_ENABLED, normalized.getEnabled() != null && normalized.getEnabled() ? "1" : "0");
        appSettingsService.upsertString(KEY_EMAIL_OTP_TTL_SECONDS, String.valueOf(normalized.getOtpTtlSeconds()));
        appSettingsService.upsertString(KEY_EMAIL_OTP_RESEND_WAIT_SECONDS, String.valueOf(normalized.getOtpResendWaitSeconds()));
        appSettingsService.upsertString(KEY_EMAIL_OTP_RESEND_WAIT_REDUCTION_AFTER_VERIFIED_SECONDS, String.valueOf(normalized.getOtpResendWaitReductionSecondsAfterVerified()));
        appSettingsService.upsertString(KEY_EMAIL_PROTOCOL, normalized.getProtocol());
        appSettingsService.upsertString(KEY_EMAIL_HOST, normalized.getHost());
        appSettingsService.upsertString(KEY_EMAIL_PORT_PLAIN, String.valueOf(normalized.getPortPlain()));
        appSettingsService.upsertString(KEY_EMAIL_PORT_ENCRYPTED, String.valueOf(normalized.getPortEncrypted()));
        appSettingsService.upsertString(KEY_EMAIL_ENCRYPTION, normalized.getEncryption());
        appSettingsService.upsertString(KEY_EMAIL_CONNECT_TIMEOUT_MS, String.valueOf(normalized.getConnectTimeoutMs()));
        appSettingsService.upsertString(KEY_EMAIL_TIMEOUT_MS, String.valueOf(normalized.getTimeoutMs()));
        appSettingsService.upsertString(KEY_EMAIL_WRITE_TIMEOUT_MS, String.valueOf(normalized.getWriteTimeoutMs()));
        appSettingsService.upsertString(KEY_EMAIL_DEBUG, normalized.getDebug() != null && normalized.getDebug() ? "1" : "0");
        appSettingsService.upsertString(KEY_EMAIL_SSL_TRUST, normalized.getSslTrust() == null ? "" : normalized.getSslTrust());
        appSettingsService.upsertString(KEY_EMAIL_SUBJECT_PREFIX, normalized.getSubjectPrefix() == null ? "" : normalized.getSubjectPrefix());

        systemConfigurationService.saveConfig(KEY_EMAIL_USERNAME, normalized.getUsername(), false, "Updated via Admin Settings");
        systemConfigurationService.saveConfig(KEY_EMAIL_PASSWORD, normalized.getPassword(), true, "Updated via Admin Settings");
        systemConfigurationService.saveConfig(KEY_EMAIL_FROM, normalized.getFrom(), false, "Updated via Admin Settings");
        systemConfigurationService.saveConfig(KEY_EMAIL_FROM_NAME, normalized.getFromName(), false, "Updated via Admin Settings");

        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "ADMIN_SETTINGS_UPDATE",
                "EMAIL_SETTINGS",
                null,
                AuditResult.SUCCESS,
                "更新邮件服务配置",
                null,
                auditDiffBuilder.build(before, normalized)
        );
        return ResponseEntity.ok(normalized);
    }

    @PostMapping("/email/test")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users_2fa','access'))")
    public ResponseEntity<?> testEmail(@RequestBody @Valid EmailTestSendDTO dto) {
        EmailAdminSettingsDTO s = getEmail().getBody();
        if (s == null) throw new IllegalStateException("email settings missing");
        EmailAdminSettingsDTO normalized = normalizeEmailSettings(s);
        if (normalized.getEnabled() == null || !normalized.getEnabled()) {
            throw new IllegalArgumentException("邮箱服务未启用");
        }
        EmailTransportConfig cfg = toTransportConfig(normalized);
        String prefix = normalized.getSubjectPrefix() == null ? "" : normalized.getSubjectPrefix().trim();
        String subject = (prefix.isEmpty() ? "" : prefix + " ") + "邮件配置测试";
        String text = "这是一封测试邮件，用于验证邮件服务器配置是否可用。";
        emailSenderService.sendPlainText(cfg, dto.getTo(), subject, text, "ADMIN_TEST");
        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "ADMIN_EMAIL_TEST",
                "EMAIL_SETTINGS",
                null,
                AuditResult.SUCCESS,
                "发送邮件测试",
                null,
                Map.of("to", safeText(dto.getTo(), 256))
        );
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/email/inbox-config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users_2fa','access'))")
    public ResponseEntity<EmailInboxSettingsDTO> getEmailInboxConfig() {
        EmailInboxSettingsDTO dto = new EmailInboxSettingsDTO();
        dto.setProtocol(appSettingsService.getString(KEY_EMAIL_INBOX_PROTOCOL).orElse("IMAP"));
        dto.setHost(appSettingsService.getString(KEY_EMAIL_INBOX_HOST).orElse("imap.qiye.aliyun.com"));
        dto.setPortPlain((int) appSettingsService.getLongOrDefault(KEY_EMAIL_INBOX_PORT_PLAIN, 143L));
        dto.setPortEncrypted((int) appSettingsService.getLongOrDefault(KEY_EMAIL_INBOX_PORT_ENCRYPTED, 993L));
        dto.setEncryption(appSettingsService.getString(KEY_EMAIL_INBOX_ENCRYPTION).orElse("SSL"));
        dto.setConnectTimeoutMs((int) appSettingsService.getLongOrDefault(KEY_EMAIL_INBOX_CONNECT_TIMEOUT_MS, 10_000L));
        dto.setTimeoutMs((int) appSettingsService.getLongOrDefault(KEY_EMAIL_INBOX_TIMEOUT_MS, 10_000L));
        dto.setWriteTimeoutMs((int) appSettingsService.getLongOrDefault(KEY_EMAIL_INBOX_WRITE_TIMEOUT_MS, 10_000L));
        dto.setDebug(appSettingsService.getLongOrDefault(KEY_EMAIL_INBOX_DEBUG, 0L) == 1L);
        dto.setSslTrust(appSettingsService.getString(KEY_EMAIL_INBOX_SSL_TRUST).orElse(""));
        dto.setFolder(appSettingsService.getString(KEY_EMAIL_INBOX_FOLDER).orElse("INBOX"));
        dto.setSentFolder(appSettingsService.getString(KEY_EMAIL_SENT_FOLDER).orElse("Sent"));
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/email/inbox-config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users_2fa','access'))")
    public ResponseEntity<EmailInboxSettingsDTO> putEmailInboxConfig(@RequestBody @Valid EmailInboxSettingsDTO dto) {
        EmailInboxSettingsDTO before = getEmailInboxConfig().getBody();
        if (before == null) before = new EmailInboxSettingsDTO();

        EmailInboxSettingsDTO normalized = normalizeEmailInboxSettings(dto);

        appSettingsService.upsertString(KEY_EMAIL_INBOX_PROTOCOL, normalized.getProtocol());
        appSettingsService.upsertString(KEY_EMAIL_INBOX_HOST, normalized.getHost());
        appSettingsService.upsertString(KEY_EMAIL_INBOX_PORT_PLAIN, String.valueOf(normalized.getPortPlain()));
        appSettingsService.upsertString(KEY_EMAIL_INBOX_PORT_ENCRYPTED, String.valueOf(normalized.getPortEncrypted()));
        appSettingsService.upsertString(KEY_EMAIL_INBOX_ENCRYPTION, normalized.getEncryption());
        appSettingsService.upsertString(KEY_EMAIL_INBOX_CONNECT_TIMEOUT_MS, String.valueOf(normalized.getConnectTimeoutMs()));
        appSettingsService.upsertString(KEY_EMAIL_INBOX_TIMEOUT_MS, String.valueOf(normalized.getTimeoutMs()));
        appSettingsService.upsertString(KEY_EMAIL_INBOX_WRITE_TIMEOUT_MS, String.valueOf(normalized.getWriteTimeoutMs()));
        appSettingsService.upsertString(KEY_EMAIL_INBOX_DEBUG, normalized.getDebug() != null && normalized.getDebug() ? "1" : "0");
        appSettingsService.upsertString(KEY_EMAIL_INBOX_SSL_TRUST, normalized.getSslTrust() == null ? "" : normalized.getSslTrust());
        appSettingsService.upsertString(KEY_EMAIL_INBOX_FOLDER, normalized.getFolder() == null ? "INBOX" : normalized.getFolder());
        appSettingsService.upsertString(KEY_EMAIL_SENT_FOLDER, normalized.getSentFolder() == null ? "Sent" : normalized.getSentFolder());

        auditLogWriter.write(
                null,
                currentUsernameOrNull(),
                "ADMIN_SETTINGS_UPDATE",
                "EMAIL_INBOX_SETTINGS",
                null,
                AuditResult.SUCCESS,
                "更新邮箱收件箱配置",
                null,
                auditDiffBuilder.build(before, normalized)
        );
        return ResponseEntity.ok(normalized);
    }

    @GetMapping("/email/inbox")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users_2fa','access'))")
    public ResponseEntity<List<EmailInboxMessageDTO>> listEmailInbox(@RequestParam(name = "limit", required = false) Integer limit) {
        EmailInboxService emailInboxService = emailInboxServiceProvider.getIfAvailable();
        if (emailInboxService == null) {
            throw new IllegalStateException("收件箱功能不可用：缺少 Jakarta Mail 运行时依赖（jakarta.mail.*）");
        }
        EmailInboxSettingsDTO cfg = getEmailInboxConfig().getBody();
        if (cfg == null) throw new IllegalStateException("inbox settings missing");
        EmailInboxSettingsDTO normalized = normalizeEmailInboxSettings(cfg);
        List<EmailInboxMessageDTO> list = emailInboxService.listInbox(normalized, limit == null ? 20 : limit);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/email/sent")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users_2fa','access'))")
    public ResponseEntity<List<EmailInboxMessageDTO>> listEmailSent(@RequestParam(name = "limit", required = false) Integer limit) {
        EmailInboxService emailInboxService = emailInboxServiceProvider.getIfAvailable();
        if (emailInboxService == null) {
            throw new IllegalStateException("发件箱功能不可用：缺少 Jakarta Mail 运行时依赖（jakarta.mail.*）");
        }
        EmailInboxSettingsDTO cfg = getEmailInboxConfig().getBody();
        if (cfg == null) throw new IllegalStateException("inbox settings missing");
        EmailInboxSettingsDTO normalized = normalizeEmailInboxSettings(cfg);
        String folder = normalized.getSentFolder() == null || normalized.getSentFolder().isBlank() ? "Sent" : normalized.getSentFolder();

        EmailInboxSettingsDTO sentCfg = new EmailInboxSettingsDTO();
        sentCfg.setProtocol(normalized.getProtocol());
        sentCfg.setHost(normalized.getHost());
        sentCfg.setPortPlain(normalized.getPortPlain());
        sentCfg.setPortEncrypted(normalized.getPortEncrypted());
        sentCfg.setEncryption(normalized.getEncryption());
        sentCfg.setConnectTimeoutMs(normalized.getConnectTimeoutMs());
        sentCfg.setTimeoutMs(normalized.getTimeoutMs());
        sentCfg.setWriteTimeoutMs(normalized.getWriteTimeoutMs());
        sentCfg.setDebug(normalized.getDebug());
        sentCfg.setSslTrust(normalized.getSslTrust());
        sentCfg.setFolder(folder);

        List<EmailInboxMessageDTO> list = emailInboxService.listInbox(sentCfg, limit == null ? 20 : limit);
        return ResponseEntity.ok(list);
    }

    private static String currentUsernameOrNull() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return null;
            String name = auth.getName();
            return name == null || name.isBlank() ? null : name.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeText(String s, int maxLen) {
        if (s == null) return null;
        String t = s.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (t.isBlank()) return null;
        if (maxLen <= 0) return "";
        return t.length() <= maxLen ? t : t.substring(0, maxLen);
    }

    private static TotpAdminSettingsDTO normalizeTotpSettings(TotpAdminSettingsDTO input) {
        TotpAdminSettingsDTO dto = input == null ? new TotpAdminSettingsDTO() : input;

        String issuer = dto.getIssuer();
        if (issuer == null || issuer.trim().isEmpty()) issuer = "EnterpriseRagCommunity";
        issuer = issuer.trim();
        if (issuer.length() > 64) throw new IllegalArgumentException("issuer 过长");

        List<String> allowedAlgorithms = normalizeAlgorithms(dto.getAllowedAlgorithms());
        List<Integer> allowedDigits = normalizeIntSet(dto.getAllowedDigits(), List.of(6, 8));
        List<Integer> allowedPeriod = normalizeIntSet(dto.getAllowedPeriodSeconds(), List.of(30));
        int maxSkew = dto.getMaxSkew() == null ? 1 : dto.getMaxSkew();
        if (maxSkew < 0 || maxSkew > 10) throw new IllegalArgumentException("maxSkew 仅支持 0..10");

        String defaultAlg = dto.getDefaultAlgorithm();
        defaultAlg = (defaultAlg == null || defaultAlg.isBlank()) ? "SHA1" : defaultAlg.trim().toUpperCase(Locale.ROOT);
        if (!allowedAlgorithms.contains(defaultAlg)) throw new IllegalArgumentException("defaultAlgorithm 必须在 allowedAlgorithms 中");

        int defaultDigits = dto.getDefaultDigits() == null ? 6 : dto.getDefaultDigits();
        if (!allowedDigits.contains(defaultDigits)) throw new IllegalArgumentException("defaultDigits 必须在 allowedDigits 中");

        int defaultPeriod = dto.getDefaultPeriodSeconds() == null ? 30 : dto.getDefaultPeriodSeconds();
        if (!allowedPeriod.contains(defaultPeriod)) throw new IllegalArgumentException("defaultPeriodSeconds 必须在 allowedPeriodSeconds 中");

        int defaultSkew = dto.getDefaultSkew() == null ? 1 : dto.getDefaultSkew();
        if (defaultSkew < 0 || defaultSkew > maxSkew) throw new IllegalArgumentException("defaultSkew 必须在 0..maxSkew 中");

        TotpAdminSettingsDTO out = new TotpAdminSettingsDTO();
        out.setIssuer(issuer);
        out.setAllowedAlgorithms(allowedAlgorithms);
        out.setAllowedDigits(allowedDigits);
        out.setAllowedPeriodSeconds(allowedPeriod);
        out.setMaxSkew(maxSkew);
        out.setDefaultAlgorithm(defaultAlg);
        out.setDefaultDigits(defaultDigits);
        out.setDefaultPeriodSeconds(defaultPeriod);
        out.setDefaultSkew(defaultSkew);
        return out;
    }

    private static EmailAdminSettingsDTO normalizeEmailSettings(EmailAdminSettingsDTO input) {
        EmailAdminSettingsDTO dto = input == null ? new EmailAdminSettingsDTO() : input;

        boolean enabled = dto.getEnabled() != null && dto.getEnabled();
        int otpTtlSeconds = dto.getOtpTtlSeconds() == null ? 600 : dto.getOtpTtlSeconds();
        int otpResendWaitSeconds = dto.getOtpResendWaitSeconds() == null ? 120 : dto.getOtpResendWaitSeconds();
        int otpResendWaitReductionSecondsAfterVerified = dto.getOtpResendWaitReductionSecondsAfterVerified() == null ? 0 : dto.getOtpResendWaitReductionSecondsAfterVerified();
        if (otpTtlSeconds < 60 || otpTtlSeconds > 3600) throw new IllegalArgumentException("otpTtlSeconds 不合法（建议 60~3600 秒）");
        if (otpResendWaitSeconds < 10 || otpResendWaitSeconds > 3600) throw new IllegalArgumentException("otpResendWaitSeconds 不合法（建议 10~3600 秒）");
        if (otpResendWaitReductionSecondsAfterVerified < 0) otpResendWaitReductionSecondsAfterVerified = 0;
        if (otpResendWaitReductionSecondsAfterVerified > 3600) otpResendWaitReductionSecondsAfterVerified = 3600;
        if (otpResendWaitReductionSecondsAfterVerified > otpResendWaitSeconds) otpResendWaitReductionSecondsAfterVerified = otpResendWaitSeconds;
        String protocol = dto.getProtocol() == null || dto.getProtocol().isBlank() ? "SMTP" : dto.getProtocol().trim().toUpperCase(Locale.ROOT);
        if (!protocol.equals("SMTP")) protocol = "SMTP";

        String host = dto.getHost() == null ? "" : dto.getHost().trim();
        int portPlain = dto.getPortPlain() == null ? 25 : dto.getPortPlain();
        int portEncrypted = dto.getPortEncrypted() == null ? 465 : dto.getPortEncrypted();
        boolean looksLikeImapDefaults = host.equalsIgnoreCase("imap.qiye.aliyun.com") && portPlain == 143 && portEncrypted == 993;
        boolean looksLikePop3Defaults = host.equalsIgnoreCase("pop.qiye.aliyun.com") && portPlain == 110 && portEncrypted == 995;
        if (looksLikeImapDefaults || looksLikePop3Defaults) {
            host = "smtp.qiye.aliyun.com";
            portPlain = 25;
            portEncrypted = 465;
        }
        if (enabled && host.isEmpty()) throw new IllegalArgumentException("host 不能为空");
        if (enabled && (portPlain <= 0 || portPlain > 65535)) throw new IllegalArgumentException("portPlain 不合法");
        if (enabled && (portEncrypted <= 0 || portEncrypted > 65535)) throw new IllegalArgumentException("portEncrypted 不合法");
        boolean looksLikeImapConfig = host.toLowerCase(Locale.ROOT).startsWith("imap.") && (portPlain == 143 || portEncrypted == 993);
        boolean looksLikePop3Config = host.toLowerCase(Locale.ROOT).startsWith("pop.") && (portPlain == 110 || portEncrypted == 995);
        if (enabled && (looksLikeImapConfig || looksLikePop3Config)) {
            throw new IllegalArgumentException("看起来这是收件(IMAP/POP3)配置；发送验证码需要填写 SMTP 主机与端口");
        }

        String encryption = dto.getEncryption() == null || dto.getEncryption().isBlank() ? "SSL" : dto.getEncryption().trim().toUpperCase(Locale.ROOT);
        if (!encryption.equals("NONE") && !encryption.equals("SSL") && !encryption.equals("STARTTLS")) encryption = "SSL";

        int connectTimeoutMs = dto.getConnectTimeoutMs() == null ? 10_000 : dto.getConnectTimeoutMs();
        int timeoutMs = dto.getTimeoutMs() == null ? 10_000 : dto.getTimeoutMs();
        int writeTimeoutMs = dto.getWriteTimeoutMs() == null ? 10_000 : dto.getWriteTimeoutMs();
        if (connectTimeoutMs < 0) connectTimeoutMs = 0;
        if (timeoutMs < 0) timeoutMs = 0;
        if (writeTimeoutMs < 0) writeTimeoutMs = 0;

        boolean debug = dto.getDebug() != null && dto.getDebug();
        String sslTrust = dto.getSslTrust() == null ? "" : dto.getSslTrust().trim();
        String subjectPrefix = dto.getSubjectPrefix() == null ? "" : dto.getSubjectPrefix().trim();
        if (subjectPrefix.length() > 64) throw new IllegalArgumentException("subjectPrefix 过长");

        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        String password = dto.getPassword() == null ? "" : dto.getPassword();
        String from = dto.getFrom() == null ? "" : dto.getFrom().trim();
        String fromName = dto.getFromName() == null ? "" : dto.getFromName().trim();

        EmailAdminSettingsDTO out = new EmailAdminSettingsDTO();
        out.setEnabled(enabled);
        out.setOtpTtlSeconds(otpTtlSeconds);
        out.setOtpResendWaitSeconds(otpResendWaitSeconds);
        out.setOtpResendWaitReductionSecondsAfterVerified(otpResendWaitReductionSecondsAfterVerified);
        out.setProtocol(protocol);
        out.setHost(host);
        out.setPortPlain(portPlain);
        out.setPortEncrypted(portEncrypted);
        out.setEncryption(encryption);
        out.setConnectTimeoutMs(connectTimeoutMs);
        out.setTimeoutMs(timeoutMs);
        out.setWriteTimeoutMs(writeTimeoutMs);
        out.setDebug(debug);
        out.setSslTrust(sslTrust);
        out.setSubjectPrefix(subjectPrefix);
        out.setUsername(username);
        out.setPassword(password);
        out.setFrom(from);
        out.setFromName(fromName);
        return out;
    }

    private static EmailTransportConfig toTransportConfig(EmailAdminSettingsDTO s) {
        String encryption = s.getEncryption() == null ? "SSL" : s.getEncryption().trim().toUpperCase(Locale.ROOT);
        EmailEncryption enc;
        if (encryption.equals("STARTTLS")) enc = EmailEncryption.STARTTLS;
        else if (encryption.equals("NONE")) enc = EmailEncryption.NONE;
        else enc = EmailEncryption.SSL;

        int port = (enc == EmailEncryption.SSL) ? (s.getPortEncrypted() == null ? 465 : s.getPortEncrypted()) : (s.getPortPlain() == null ? 25 : s.getPortPlain());
        String sslTrust = s.getSslTrust();
        if (sslTrust != null && sslTrust.isBlank()) sslTrust = null;

        return new EmailTransportConfig(
                "smtp",
                s.getHost(),
                port,
                enc,
                s.getConnectTimeoutMs() == null ? 10_000 : s.getConnectTimeoutMs(),
                s.getTimeoutMs() == null ? 10_000 : s.getTimeoutMs(),
                s.getWriteTimeoutMs() == null ? 10_000 : s.getWriteTimeoutMs(),
                s.getDebug() != null && s.getDebug(),
                sslTrust
        );
    }

    private static EmailInboxSettingsDTO normalizeEmailInboxSettings(EmailInboxSettingsDTO input) {
        EmailInboxSettingsDTO dto = input == null ? new EmailInboxSettingsDTO() : input;

        String protocol = dto.getProtocol() == null || dto.getProtocol().isBlank() ? "IMAP" : dto.getProtocol().trim().toUpperCase(Locale.ROOT);
        if (!protocol.equals("IMAP")) protocol = "IMAP";

        String host = dto.getHost() == null ? "" : dto.getHost().trim();
        if (host.isEmpty()) host = "imap.qiye.aliyun.com";

        int portPlain = dto.getPortPlain() == null ? 143 : dto.getPortPlain();
        int portEncrypted = dto.getPortEncrypted() == null ? 993 : dto.getPortEncrypted();
        if (portPlain <= 0 || portPlain > 65535) throw new IllegalArgumentException("portPlain 不合法");
        if (portEncrypted <= 0 || portEncrypted > 65535) throw new IllegalArgumentException("portEncrypted 不合法");

        String encryption = dto.getEncryption() == null || dto.getEncryption().isBlank() ? "SSL" : dto.getEncryption().trim().toUpperCase(Locale.ROOT);
        if (!encryption.equals("NONE") && !encryption.equals("SSL") && !encryption.equals("STARTTLS")) encryption = "SSL";

        int connectTimeoutMs = dto.getConnectTimeoutMs() == null ? 10_000 : dto.getConnectTimeoutMs();
        int timeoutMs = dto.getTimeoutMs() == null ? 10_000 : dto.getTimeoutMs();
        int writeTimeoutMs = dto.getWriteTimeoutMs() == null ? 10_000 : dto.getWriteTimeoutMs();
        if (connectTimeoutMs < 0) connectTimeoutMs = 0;
        if (timeoutMs < 0) timeoutMs = 0;
        if (writeTimeoutMs < 0) writeTimeoutMs = 0;

        boolean debug = dto.getDebug() != null && dto.getDebug();
        String sslTrust = dto.getSslTrust() == null ? "" : dto.getSslTrust().trim();

        String folder = dto.getFolder();
        folder = (folder == null || folder.isBlank()) ? "INBOX" : folder.trim();
        if (folder.length() > 128) throw new IllegalArgumentException("folder 过长");

        String sentFolder = dto.getSentFolder();
        sentFolder = (sentFolder == null || sentFolder.isBlank()) ? "Sent" : sentFolder.trim();
        if (sentFolder.length() > 128) throw new IllegalArgumentException("sentFolder 过长");

        EmailInboxSettingsDTO out = new EmailInboxSettingsDTO();
        out.setProtocol(protocol);
        out.setHost(host);
        out.setPortPlain(portPlain);
        out.setPortEncrypted(portEncrypted);
        out.setEncryption(encryption);
        out.setConnectTimeoutMs(connectTimeoutMs);
        out.setTimeoutMs(timeoutMs);
        out.setWriteTimeoutMs(writeTimeoutMs);
        out.setDebug(debug);
        out.setSslTrust(sslTrust);
        out.setFolder(folder);
        out.setSentFolder(sentFolder);
        return out;
    }

    private static List<String> normalizeAlgorithms(List<String> input) {
        List<String> list = input == null ? List.of("SHA1", "SHA256", "SHA512") : input;
        List<String> out = new ArrayList<>();
        for (String a : list) {
            if (a == null) continue;
            String v = a.trim().toUpperCase(Locale.ROOT);
            if (v.isEmpty()) continue;
            if (!v.equals("SHA1") && !v.equals("SHA256") && !v.equals("SHA512")) continue;
            if (!out.contains(v)) out.add(v);
        }
        if (out.isEmpty()) out = new ArrayList<>(List.of("SHA1", "SHA256", "SHA512"));
        return out;
    }

    private static List<Integer> normalizeIntSet(List<Integer> input, List<Integer> defaultValue) {
        List<Integer> list = input == null ? defaultValue : input;
        List<Integer> out = new ArrayList<>();
        for (Integer v : list) {
            if (v == null) continue;
            if (!out.contains(v)) out.add(v);
        }
        if (out.isEmpty()) out = new ArrayList<>(defaultValue);
        return out;
    }

    private static Optional<List<Integer>> parseIntList(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        List<Integer> out = new ArrayList<>();
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            try {
                out.add(Integer.parseInt(t));
            } catch (NumberFormatException ignored) {
            }
        }
        if (out.isEmpty()) return Optional.empty();
        return Optional.of(out);
    }

    private static String joinInts(List<Integer> list) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            Integer v = list.get(i);
            if (v == null) continue;
            if (!sb.isEmpty()) sb.append(',');
            sb.append(v);
        }
        return sb.toString();
    }

    @GetMapping("/totp")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_users_2fa','access'))")
    public ResponseEntity<TotpAdminSettingsDTO> getTotp() {
        TotpAdminSettingsDTO dto = new TotpAdminSettingsDTO();
        dto.setIssuer(appSettingsService.getString(KEY_TOTP_ISSUER).orElse("EnterpriseRagCommunity"));

        dto.setAllowedAlgorithms(parseStringList(appSettingsService.getString(KEY_TOTP_ALLOWED_ALG).orElse(null)).orElse(List.of("SHA1", "SHA256", "SHA512")));
        dto.setAllowedDigits(parseIntList(appSettingsService.getString(KEY_TOTP_ALLOWED_DIGITS).orElse(null)).orElse(List.of(6, 8)));
        dto.setAllowedPeriodSeconds(parseIntList(appSettingsService.getString(KEY_TOTP_ALLOWED_PERIOD).orElse(null)).orElse(List.of(30)));
        dto.setMaxSkew((int) appSettingsService.getLongOrDefault(KEY_TOTP_MAX_SKEW, 1L));

        dto.setDefaultAlgorithm(appSettingsService.getString(KEY_TOTP_DEFAULT_ALG).orElse("SHA1"));
        dto.setDefaultDigits((int) appSettingsService.getLongOrDefault(KEY_TOTP_DEFAULT_DIGITS, 6L));
        dto.setDefaultPeriodSeconds((int) appSettingsService.getLongOrDefault(KEY_TOTP_DEFAULT_PERIOD, 30L));
        dto.setDefaultSkew((int) appSettingsService.getLongOrDefault(KEY_TOTP_DEFAULT_SKEW, 1L));
        return ResponseEntity.ok(dto);
    }
}
