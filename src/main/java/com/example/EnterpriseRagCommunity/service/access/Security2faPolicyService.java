package com.example.EnterpriseRagCommunity.service.access;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicySettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicyStatusDTO;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class Security2faPolicyService {
    public static final String KEY_TOTP_POLICY = "security_totp_policy";
    public static final String KEY_TOTP_ROLE_IDS = "security_totp_role_ids";
    public static final String KEY_TOTP_USER_IDS = "security_totp_user_ids";
    public static final String KEY_EMAIL_OTP_POLICY = "security_email_otp_policy";
    public static final String KEY_EMAIL_OTP_ROLE_IDS = "security_email_otp_role_ids";
    public static final String KEY_EMAIL_OTP_USER_IDS = "security_email_otp_user_ids";
    public static final String KEY_LOGIN2FA_MODE = "security_login2fa_mode";
    public static final String KEY_LOGIN2FA_SCOPE_POLICY = "security_login2fa_scope_policy";
    public static final String KEY_LOGIN2FA_ROLE_IDS = "security_login2fa_role_ids";
    public static final String KEY_LOGIN2FA_USER_IDS = "security_login2fa_user_ids";

    public enum EnablePolicy {
        FORBID_ALL,
        ALLOW_ALL,
        ALLOW_ROLES,
        ALLOW_USERS,
        REQUIRE_ALL,
        REQUIRE_ROLES,
        REQUIRE_USERS;

        public static EnablePolicy parseOrDefault(String raw, EnablePolicy fallback) {
            if (raw == null) return fallback;
            String v = raw.trim().toUpperCase(Locale.ROOT);
            if (v.isEmpty()) return fallback;
            try {
                return EnablePolicy.valueOf(v);
            } catch (Exception ignored) {
                return fallback;
            }
        }
    }

    public enum Login2faMode {
        DISABLED,
        EMAIL_ONLY,
        TOTP_ONLY,
        EMAIL_OR_TOTP;

        public static Login2faMode parseOrDefault(String raw, Login2faMode fallback) {
            if (raw == null) return fallback;
            String v = raw.trim().toUpperCase(Locale.ROOT);
            if (v.isEmpty()) return fallback;
            try {
                return Login2faMode.valueOf(v);
            } catch (Exception ignored) {
                return fallback;
            }
        }
    }

    private final AppSettingsService appSettingsService;
    private final UserRoleLinksRepository userRoleLinksRepository;
    private final UsersRepository usersRepository;
    private final EmailVerificationMailer emailVerificationMailer;

    private static List<Long> parseIdList(String raw) {
        String v = raw == null ? "" : raw;
        if (v.isBlank()) return List.of();
        String[] parts = v.split(",");
        List<Long> out = new ArrayList<>();
        for (String p : parts) {
            String s = p.trim();
            if (s.isEmpty()) continue;
            try {
                long n = Long.parseLong(s);
                if (n > 0) out.add(n);
            } catch (NumberFormatException ignored) {
            }
        }
        return normalizeIdList(out);
    }

    @Transactional
    public Security2faPolicySettingsDTO saveAdminSettings(Security2faPolicySettingsDTO raw) {
        Security2faPolicySettingsDTO normalized = normalizeAdminSettings(raw);
        appSettingsService.upsertString(KEY_TOTP_POLICY, normalized.getTotpPolicy());
        appSettingsService.upsertString(KEY_TOTP_ROLE_IDS, joinIdList(normalized.getTotpRoleIds()));
        appSettingsService.upsertString(KEY_TOTP_USER_IDS, joinIdList(normalized.getTotpUserIds()));
        appSettingsService.upsertString(KEY_EMAIL_OTP_POLICY, normalized.getEmailOtpPolicy());
        appSettingsService.upsertString(KEY_EMAIL_OTP_ROLE_IDS, joinIdList(normalized.getEmailOtpRoleIds()));
        appSettingsService.upsertString(KEY_EMAIL_OTP_USER_IDS, joinIdList(normalized.getEmailOtpUserIds()));

        appSettingsService.upsertString(KEY_LOGIN2FA_MODE, normalized.getLogin2faMode());
        appSettingsService.upsertString(KEY_LOGIN2FA_SCOPE_POLICY, normalized.getLogin2faScopePolicy());
        appSettingsService.upsertString(KEY_LOGIN2FA_ROLE_IDS, joinIdList(normalized.getLogin2faRoleIds()));
        appSettingsService.upsertString(KEY_LOGIN2FA_USER_IDS, joinIdList(normalized.getLogin2faUserIds()));
        return normalized;
    }

    @Transactional(readOnly = true)
    public Security2faPolicyStatusDTO evaluateForUser(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        Security2faPolicySettingsDTO settings = getAdminSettingsOrDefault();

        Set<Long> userRoleIds = loadUserRoleIds(userId);
        EnablePolicy totpPolicy = EnablePolicy.parseOrDefault(settings.getTotpPolicy(), EnablePolicy.ALLOW_ALL);
        EnablePolicy emailPolicy = EnablePolicy.parseOrDefault(settings.getEmailOtpPolicy(), EnablePolicy.ALLOW_ALL);

        boolean totpMatched = matches(totpPolicy, userId, userRoleIds, settings.getTotpRoleIds(), settings.getTotpUserIds());
        boolean emailMatched = matches(emailPolicy, userId, userRoleIds, settings.getEmailOtpRoleIds(), settings.getEmailOtpUserIds());

        boolean totpAllowed = allowedOf(totpPolicy, totpMatched);
        boolean totpRequired = requiredOf(totpPolicy, totpMatched);

        boolean emailSvcEnabled = emailVerificationMailer.isEnabled();
        boolean emailAllowed = emailSvcEnabled && allowedOf(emailPolicy, emailMatched);
        boolean emailRequired = emailSvcEnabled && requiredOf(emailPolicy, emailMatched);

        Login2faMode login2faMode = Login2faMode.parseOrDefault(settings.getLogin2faMode(), Login2faMode.DISABLED);
        EnablePolicy login2faScopePolicy = EnablePolicy.parseOrDefault(settings.getLogin2faScopePolicy(), EnablePolicy.ALLOW_ALL);
        boolean login2faMatched = matches(login2faScopePolicy, userId, userRoleIds, settings.getLogin2faRoleIds(), settings.getLogin2faUserIds());
        boolean login2faAllowed = login2faMode != Login2faMode.DISABLED && allowedOf(login2faScopePolicy, login2faMatched);
        boolean login2faRequired = login2faMode != Login2faMode.DISABLED && requiredOf(login2faScopePolicy, login2faMatched);
        boolean login2faCanEnable = login2faMode != Login2faMode.DISABLED
                && !login2faRequired
                && (login2faScopePolicy == EnablePolicy.ALLOW_ALL || login2faScopePolicy == EnablePolicy.ALLOW_ROLES || login2faScopePolicy == EnablePolicy.ALLOW_USERS)
                && login2faAllowed;
        boolean login2faEnabled = login2faRequired || (login2faCanEnable && isLogin2faEnabledByUserPreference(userId));

        Security2faPolicyStatusDTO status = new Security2faPolicyStatusDTO();
        status.setTotpAllowed(totpAllowed);
        status.setTotpRequired(totpRequired);
        status.setTotpCanDisable(!totpRequired);
        status.setEmailOtpAllowed(emailAllowed);
        status.setEmailOtpRequired(emailRequired);
        status.setEmailServiceEnabled(emailSvcEnabled);
        status.setLogin2faAllowed(login2faAllowed);
        status.setLogin2faRequired(login2faRequired);
        status.setLogin2faCanEnable(login2faCanEnable);
        status.setLogin2faEnabled(login2faEnabled);
        return status;
    }

    @Transactional(readOnly = true)
    public Login2faMode evaluateLogin2faModeForUser(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        Security2faPolicySettingsDTO settings = getAdminSettingsOrDefault();
        Login2faMode mode = Login2faMode.parseOrDefault(settings.getLogin2faMode(), Login2faMode.DISABLED);
        if (mode == Login2faMode.DISABLED) return Login2faMode.DISABLED;

        EnablePolicy scopePolicy = EnablePolicy.parseOrDefault(settings.getLogin2faScopePolicy(), EnablePolicy.ALLOW_ALL);
        Set<Long> userRoleIds = loadUserRoleIds(userId);
        boolean matched = matches(scopePolicy, userId, userRoleIds, settings.getLogin2faRoleIds(), settings.getLogin2faUserIds());
        boolean required = requiredOf(scopePolicy, matched);
        if (required) return mode;
        boolean allowed = allowedOf(scopePolicy, matched);
        if (!allowed) return Login2faMode.DISABLED;
        return isLogin2faEnabledByUserPreference(userId) ? mode : Login2faMode.DISABLED;
    }

    public Security2faPolicySettingsDTO normalizeAdminSettings(Security2faPolicySettingsDTO raw) {
        Security2faPolicySettingsDTO dto = raw == null ? new Security2faPolicySettingsDTO() : raw;

        EnablePolicy totpPolicy = EnablePolicy.parseOrDefault(dto.getTotpPolicy(), EnablePolicy.ALLOW_ALL);
        EnablePolicy emailPolicy = EnablePolicy.parseOrDefault(dto.getEmailOtpPolicy(), EnablePolicy.ALLOW_ALL);

        List<Long> totpRoleIds = normalizeIdList(dto.getTotpRoleIds());
        List<Long> totpUserIds = normalizeIdList(dto.getTotpUserIds());
        List<Long> emailRoleIds = normalizeIdList(dto.getEmailOtpRoleIds());
        List<Long> emailUserIds = normalizeIdList(dto.getEmailOtpUserIds());

        if ((totpPolicy == EnablePolicy.ALLOW_ROLES || totpPolicy == EnablePolicy.REQUIRE_ROLES) && totpRoleIds.isEmpty()) {
            throw new IllegalArgumentException("TOTP 策略为 *_ROLES 时必须选择 roleId");
        }
        if ((totpPolicy == EnablePolicy.ALLOW_USERS || totpPolicy == EnablePolicy.REQUIRE_USERS) && totpUserIds.isEmpty()) {
            throw new IllegalArgumentException("TOTP 策略为 *_USERS 时必须选择 userId");
        }
        if ((emailPolicy == EnablePolicy.ALLOW_ROLES || emailPolicy == EnablePolicy.REQUIRE_ROLES) && emailRoleIds.isEmpty()) {
            throw new IllegalArgumentException("邮箱验证码策略为 *_ROLES 时必须选择 roleId");
        }
        if ((emailPolicy == EnablePolicy.ALLOW_USERS || emailPolicy == EnablePolicy.REQUIRE_USERS) && emailUserIds.isEmpty()) {
            throw new IllegalArgumentException("邮箱验证码策略为 *_USERS 时必须选择 userId");
        }

        if (totpPolicy != EnablePolicy.ALLOW_ROLES && totpPolicy != EnablePolicy.REQUIRE_ROLES) totpRoleIds = List.of();
        if (totpPolicy != EnablePolicy.ALLOW_USERS && totpPolicy != EnablePolicy.REQUIRE_USERS) totpUserIds = List.of();
        if (emailPolicy != EnablePolicy.ALLOW_ROLES && emailPolicy != EnablePolicy.REQUIRE_ROLES) emailRoleIds = List.of();
        if (emailPolicy != EnablePolicy.ALLOW_USERS && emailPolicy != EnablePolicy.REQUIRE_USERS) emailUserIds = List.of();

        Login2faMode login2faMode = Login2faMode.parseOrDefault(dto.getLogin2faMode(), Login2faMode.DISABLED);
        EnablePolicy login2faScopePolicy = EnablePolicy.parseOrDefault(dto.getLogin2faScopePolicy(), EnablePolicy.ALLOW_ALL);
        List<Long> login2faRoleIds = normalizeIdList(dto.getLogin2faRoleIds());
        List<Long> login2faUserIds = normalizeIdList(dto.getLogin2faUserIds());
        if ((login2faScopePolicy == EnablePolicy.ALLOW_ROLES || login2faScopePolicy == EnablePolicy.REQUIRE_ROLES) && login2faRoleIds.isEmpty()) {
            throw new IllegalArgumentException("登录二次验证策略为 *_ROLES 时必须选择 roleId");
        }
        if ((login2faScopePolicy == EnablePolicy.ALLOW_USERS || login2faScopePolicy == EnablePolicy.REQUIRE_USERS) && login2faUserIds.isEmpty()) {
            throw new IllegalArgumentException("登录二次验证策略为 *_USERS 时必须选择 userId");
        }
        if (login2faScopePolicy != EnablePolicy.ALLOW_ROLES && login2faScopePolicy != EnablePolicy.REQUIRE_ROLES) {
            login2faRoleIds = List.of();
        }
        if (login2faScopePolicy != EnablePolicy.ALLOW_USERS && login2faScopePolicy != EnablePolicy.REQUIRE_USERS) {
            login2faUserIds = List.of();
        }

        Security2faPolicySettingsDTO normalized = new Security2faPolicySettingsDTO();
        normalized.setTotpPolicy(totpPolicy.name());
        normalized.setTotpRoleIds(totpRoleIds);
        normalized.setTotpUserIds(totpUserIds);
        normalized.setEmailOtpPolicy(emailPolicy.name());
        normalized.setEmailOtpRoleIds(emailRoleIds);
        normalized.setEmailOtpUserIds(emailUserIds);
        normalized.setLogin2faMode(login2faMode.name());
        normalized.setLogin2faScopePolicy(login2faScopePolicy.name());
        normalized.setLogin2faRoleIds(login2faRoleIds);
        normalized.setLogin2faUserIds(login2faUserIds);
        return normalized;
    }

    @Transactional(readOnly = true)
    public boolean isLogin2faEnabledByUserPreference(Long userId) {
        if (userId == null || userId <= 0) return false;
        UsersEntity user = usersRepository.findById(userId).orElse(null);
        if (user == null) return false;
        return readLogin2faEnabledFromMetadata(user.getMetadata());
    }

    @SuppressWarnings("unchecked")
    public boolean readLogin2faEnabledFromMetadata(java.util.Map<String, Object> metadata) {
        if (metadata == null) return false;
        Object prefsObj = metadata.get("preferences");
        if (!(prefsObj instanceof java.util.Map)) return false;
        java.util.Map<String, Object> prefs = (java.util.Map<String, Object>) prefsObj;
        Object secObj = prefs.get("security");
        if (!(secObj instanceof java.util.Map)) return false;
        java.util.Map<String, Object> security = (java.util.Map<String, Object>) secObj;
        Object v = security.get("login2faEnabled");
        return v instanceof Boolean && (Boolean) v;
    }

    private Set<Long> loadUserRoleIds(Long userId) {
        List<UserRoleLinksEntity> links = userRoleLinksRepository.findByUserId(userId);
        Set<Long> set = new LinkedHashSet<>();
        for (UserRoleLinksEntity link : links) {
            if (link.getRoleId() != null) set.add(link.getRoleId());
        }
        return set;
    }

    private static boolean matches(EnablePolicy policy, Long userId, Set<Long> userRoleIds, List<Long> configuredRoleIds, List<Long> configuredUserIds) {
        if (policy == EnablePolicy.ALLOW_ROLES || policy == EnablePolicy.REQUIRE_ROLES) {
            if (userRoleIds == null || userRoleIds.isEmpty()) return false;
            if (configuredRoleIds == null || configuredRoleIds.isEmpty()) return false;
            for (Long rid : configuredRoleIds) {
                if (rid != null && userRoleIds.contains(rid)) return true;
            }
            return false;
        }
        if (policy == EnablePolicy.ALLOW_USERS || policy == EnablePolicy.REQUIRE_USERS) {
            if (userId == null || userId <= 0) return false;
            if (configuredUserIds == null || configuredUserIds.isEmpty()) return false;
            for (Long uid : configuredUserIds) {
                if (uid != null && uid.equals(userId)) return true;
            }
            return false;
        }
        return true;
    }

    private static boolean allowedOf(EnablePolicy policy, boolean matched) {
        return switch (policy) {
            case FORBID_ALL -> false;
            case ALLOW_ALL, REQUIRE_ALL -> true;
            case ALLOW_ROLES, REQUIRE_ROLES, ALLOW_USERS, REQUIRE_USERS -> matched;
        };
    }

    private static boolean requiredOf(EnablePolicy policy, boolean matched) {
        return switch (policy) {
            case REQUIRE_ALL -> true;
            case REQUIRE_ROLES, REQUIRE_USERS -> matched;
            default -> false;
        };
    }

    @Transactional(readOnly = true)
    public Security2faPolicySettingsDTO getAdminSettingsOrDefault() {
        Security2faPolicySettingsDTO dto = new Security2faPolicySettingsDTO();
        EnablePolicy totpPolicy = EnablePolicy.parseOrDefault(
                appSettingsService.getString(KEY_TOTP_POLICY).orElse(null),
                EnablePolicy.ALLOW_ALL
        );
        EnablePolicy emailPolicy = EnablePolicy.parseOrDefault(
                appSettingsService.getString(KEY_EMAIL_OTP_POLICY).orElse(null),
                EnablePolicy.ALLOW_ALL
        );
        Login2faMode login2faMode = Login2faMode.parseOrDefault(
                appSettingsService.getString(KEY_LOGIN2FA_MODE).orElse(null),
                Login2faMode.EMAIL_OR_TOTP
        );
        EnablePolicy login2faScopePolicy = EnablePolicy.parseOrDefault(
                appSettingsService.getString(KEY_LOGIN2FA_SCOPE_POLICY).orElse(null),
                EnablePolicy.ALLOW_ALL
        );

        dto.setTotpPolicy(totpPolicy.name());
        dto.setTotpRoleIds(parseIdList(appSettingsService.getString(KEY_TOTP_ROLE_IDS).orElse(null)));
        dto.setTotpUserIds(parseIdList(appSettingsService.getString(KEY_TOTP_USER_IDS).orElse(null)));

        dto.setEmailOtpPolicy(emailPolicy.name());
        dto.setEmailOtpRoleIds(parseIdList(appSettingsService.getString(KEY_EMAIL_OTP_ROLE_IDS).orElse(null)));
        dto.setEmailOtpUserIds(parseIdList(appSettingsService.getString(KEY_EMAIL_OTP_USER_IDS).orElse(null)));

        dto.setLogin2faMode(login2faMode.name());
        dto.setLogin2faScopePolicy(login2faScopePolicy.name());
        dto.setLogin2faRoleIds(parseIdList(appSettingsService.getString(KEY_LOGIN2FA_ROLE_IDS).orElse(null)));
        dto.setLogin2faUserIds(parseIdList(appSettingsService.getString(KEY_LOGIN2FA_USER_IDS).orElse(null)));
        return normalizeAdminSettings(dto);
    }

    private static List<Long> normalizeIdList(List<Long> list) {
        if (list == null || list.isEmpty()) return List.of();
        return list.stream()
                .filter(Objects::nonNull)
                .filter(v -> v > 0)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    private static String joinIdList(List<Long> list) {
        if (list == null || list.isEmpty()) return "";
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
