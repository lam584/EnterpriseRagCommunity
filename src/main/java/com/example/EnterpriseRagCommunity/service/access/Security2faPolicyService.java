package com.example.EnterpriseRagCommunity.service.access;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicySettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicyStatusDTO;
import com.example.EnterpriseRagCommunity.entity.access.UserRoleLinksEntity;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class Security2faPolicyService {
    public static final String KEY_TOTP_POLICY = "security_totp_policy";
    public static final String KEY_TOTP_ROLE_IDS = "security_totp_role_ids";
    public static final String KEY_EMAIL_OTP_POLICY = "security_email_otp_policy";
    public static final String KEY_EMAIL_OTP_ROLE_IDS = "security_email_otp_role_ids";

    public enum EnablePolicy {
        FORBID_ALL,
        ALLOW_ALL,
        ALLOW_ROLES,
        REQUIRE_ALL,
        REQUIRE_ROLES;

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

    private final AppSettingsService appSettingsService;
    private final UserRoleLinksRepository userRoleLinksRepository;
    private final EmailVerificationMailer emailVerificationMailer;

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

        dto.setTotpPolicy(totpPolicy.name());
        dto.setTotpRoleIds(parseRoleIds(appSettingsService.getString(KEY_TOTP_ROLE_IDS)));

        dto.setEmailOtpPolicy(emailPolicy.name());
        dto.setEmailOtpRoleIds(parseRoleIds(appSettingsService.getString(KEY_EMAIL_OTP_ROLE_IDS)));
        return normalizeAdminSettings(dto);
    }

    @Transactional
    public Security2faPolicySettingsDTO saveAdminSettings(Security2faPolicySettingsDTO raw) {
        Security2faPolicySettingsDTO normalized = normalizeAdminSettings(raw);
        appSettingsService.upsertString(KEY_TOTP_POLICY, normalized.getTotpPolicy());
        appSettingsService.upsertString(KEY_TOTP_ROLE_IDS, joinRoleIds(normalized.getTotpRoleIds()));
        appSettingsService.upsertString(KEY_EMAIL_OTP_POLICY, normalized.getEmailOtpPolicy());
        appSettingsService.upsertString(KEY_EMAIL_OTP_ROLE_IDS, joinRoleIds(normalized.getEmailOtpRoleIds()));
        return normalized;
    }

    @Transactional(readOnly = true)
    public Security2faPolicyStatusDTO evaluateForUser(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        Security2faPolicySettingsDTO settings = getAdminSettingsOrDefault();

        Set<Long> userRoleIds = loadUserRoleIds(userId);
        EnablePolicy totpPolicy = EnablePolicy.parseOrDefault(settings.getTotpPolicy(), EnablePolicy.ALLOW_ALL);
        EnablePolicy emailPolicy = EnablePolicy.parseOrDefault(settings.getEmailOtpPolicy(), EnablePolicy.ALLOW_ALL);

        boolean totpMatched = matches(totpPolicy, userRoleIds, settings.getTotpRoleIds());
        boolean emailMatched = matches(emailPolicy, userRoleIds, settings.getEmailOtpRoleIds());

        boolean totpAllowed = allowedOf(totpPolicy, totpMatched);
        boolean totpRequired = requiredOf(totpPolicy, totpMatched);

        boolean emailSvcEnabled = emailVerificationMailer.isEnabled();
        boolean emailAllowed = emailSvcEnabled && allowedOf(emailPolicy, emailMatched);
        boolean emailRequired = emailSvcEnabled && requiredOf(emailPolicy, emailMatched);

        Security2faPolicyStatusDTO status = new Security2faPolicyStatusDTO();
        status.setTotpAllowed(totpAllowed);
        status.setTotpRequired(totpRequired);
        status.setTotpCanDisable(!totpRequired);
        status.setEmailOtpAllowed(emailAllowed);
        status.setEmailOtpRequired(emailRequired);
        status.setEmailServiceEnabled(emailSvcEnabled);
        return status;
    }

    public Security2faPolicySettingsDTO normalizeAdminSettings(Security2faPolicySettingsDTO raw) {
        Security2faPolicySettingsDTO dto = raw == null ? new Security2faPolicySettingsDTO() : raw;

        EnablePolicy totpPolicy = EnablePolicy.parseOrDefault(dto.getTotpPolicy(), EnablePolicy.ALLOW_ALL);
        EnablePolicy emailPolicy = EnablePolicy.parseOrDefault(dto.getEmailOtpPolicy(), EnablePolicy.ALLOW_ALL);

        List<Long> totpRoleIds = normalizeRoleIdList(dto.getTotpRoleIds());
        List<Long> emailRoleIds = normalizeRoleIdList(dto.getEmailOtpRoleIds());

        if ((totpPolicy == EnablePolicy.ALLOW_ROLES || totpPolicy == EnablePolicy.REQUIRE_ROLES) && totpRoleIds.isEmpty()) {
            throw new IllegalArgumentException("TOTP 策略为 *_ROLES 时必须选择 roleId");
        }
        if ((emailPolicy == EnablePolicy.ALLOW_ROLES || emailPolicy == EnablePolicy.REQUIRE_ROLES) && emailRoleIds.isEmpty()) {
            throw new IllegalArgumentException("邮箱验证码策略为 *_ROLES 时必须选择 roleId");
        }

        if (totpPolicy != EnablePolicy.ALLOW_ROLES && totpPolicy != EnablePolicy.REQUIRE_ROLES) totpRoleIds = List.of();
        if (emailPolicy != EnablePolicy.ALLOW_ROLES && emailPolicy != EnablePolicy.REQUIRE_ROLES) emailRoleIds = List.of();

        Security2faPolicySettingsDTO normalized = new Security2faPolicySettingsDTO();
        normalized.setTotpPolicy(totpPolicy.name());
        normalized.setTotpRoleIds(totpRoleIds);
        normalized.setEmailOtpPolicy(emailPolicy.name());
        normalized.setEmailOtpRoleIds(emailRoleIds);
        return normalized;
    }

    private Set<Long> loadUserRoleIds(Long userId) {
        List<UserRoleLinksEntity> links = userRoleLinksRepository.findByUserId(userId);
        Set<Long> set = new LinkedHashSet<>();
        for (UserRoleLinksEntity link : links) {
            if (link.getRoleId() != null) set.add(link.getRoleId());
        }
        return set;
    }

    private static boolean matches(EnablePolicy policy, Set<Long> userRoleIds, List<Long> configuredRoleIds) {
        if (policy == EnablePolicy.ALLOW_ROLES || policy == EnablePolicy.REQUIRE_ROLES) {
            if (userRoleIds == null || userRoleIds.isEmpty()) return false;
            if (configuredRoleIds == null || configuredRoleIds.isEmpty()) return false;
            for (Long rid : configuredRoleIds) {
                if (rid != null && userRoleIds.contains(rid)) return true;
            }
            return false;
        }
        return true;
    }

    private static boolean allowedOf(EnablePolicy policy, boolean matched) {
        return switch (policy) {
            case FORBID_ALL -> false;
            case ALLOW_ALL, REQUIRE_ALL -> true;
            case ALLOW_ROLES, REQUIRE_ROLES -> matched;
        };
    }

    private static boolean requiredOf(EnablePolicy policy, boolean matched) {
        return switch (policy) {
            case REQUIRE_ALL -> true;
            case REQUIRE_ROLES -> matched;
            default -> false;
        };
    }

    private static List<Long> parseRoleIds(Optional<String> raw) {
        if (raw == null) return List.of();
        String v = raw.orElse("");
        if (v.isBlank()) return List.of();
        String[] parts = v.split(",");
        List<Long> out = new ArrayList<>();
        for (String p : parts) {
            String s = p == null ? "" : p.trim();
            if (s.isEmpty()) continue;
            try {
                long n = Long.parseLong(s);
                if (n > 0) out.add(n);
            } catch (NumberFormatException ignored) {
            }
        }
        return normalizeRoleIdList(out);
    }

    private static List<Long> normalizeRoleIdList(List<Long> list) {
        if (list == null || list.isEmpty()) return List.of();
        return list.stream()
                .filter(Objects::nonNull)
                .map(Long::longValue)
                .filter(v -> v > 0)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    private static String joinRoleIds(List<Long> list) {
        if (list == null || list.isEmpty()) return "";
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}

