package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.TotpAdminSettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.request.TotpEnrollRequest;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TotpPolicyService {
    private static final String KEY_TOTP_ISSUER = "totp_issuer";
    private static final String KEY_TOTP_ALLOWED_ALG = "totp_allowed_algorithms";
    private static final String KEY_TOTP_ALLOWED_DIGITS = "totp_allowed_digits";
    private static final String KEY_TOTP_ALLOWED_PERIOD = "totp_allowed_period_seconds";
    private static final String KEY_TOTP_MAX_SKEW = "totp_max_skew";
    private static final String KEY_TOTP_DEFAULT_ALG = "totp_default_algorithm";
    private static final String KEY_TOTP_DEFAULT_DIGITS = "totp_default_digits";
    private static final String KEY_TOTP_DEFAULT_PERIOD = "totp_default_period_seconds";
    private static final String KEY_TOTP_DEFAULT_SKEW = "totp_default_skew";

    private final AppSettingsService appSettingsService;

    private static Optional<List<String>> parseStringList(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        List<String> out = new ArrayList<>();
        for (String s : raw.split(",")) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            out.add(t.trim().toUpperCase(Locale.ROOT));
        }
        if (out.isEmpty()) return Optional.empty();
        return Optional.of(out);
    }

    @Transactional(readOnly = true)
    public ResolvedTotpConfig resolveForEnroll(TotpEnrollRequest req) {
        TotpAdminSettingsDTO s = getSettingsOrDefault();

        String algorithm = req == null || req.getAlgorithm() == null ? s.getDefaultAlgorithm() : req.getAlgorithm();
        algorithm = algorithm == null ? "SHA1" : algorithm.trim().toUpperCase(Locale.ROOT);
        if (!s.getAllowedAlgorithms().contains(algorithm)) throw new IllegalArgumentException("不支持的算法：" + algorithm);

        int digits = req == null || req.getDigits() == null ? s.getDefaultDigits() : req.getDigits();
        if (!s.getAllowedDigits().contains(digits)) throw new IllegalArgumentException("不支持的验证码位数：" + digits);

        int periodSeconds = req == null || req.getPeriodSeconds() == null ? s.getDefaultPeriodSeconds() : req.getPeriodSeconds();
        if (!s.getAllowedPeriodSeconds().contains(periodSeconds)) throw new IllegalArgumentException("不支持的时间步长：" + periodSeconds);

        int maxSkew = s.getMaxSkew() == null ? 1 : s.getMaxSkew();
        int skew = req == null || req.getSkew() == null ? s.getDefaultSkew() : req.getSkew();
        if (skew < 0 || skew > maxSkew) throw new IllegalArgumentException("skew 必须在 0.." + maxSkew + " 内");

        String issuer = s.getIssuer();
        if (issuer == null || issuer.isBlank()) issuer = "EnterpriseRagCommunity";
        issuer = issuer.trim();

        return new ResolvedTotpConfig(issuer, algorithm, digits, periodSeconds, skew);
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

    @Transactional(readOnly = true)
    public TotpAdminSettingsDTO getSettingsOrDefault() {
        return TotpPolicySettingsSupport.buildSettings(
                appSettingsService,
                TotpPolicyService::parseStringList,
                TotpPolicyService::parseIntList,
                KEY_TOTP_ISSUER,
                KEY_TOTP_ALLOWED_ALG,
                KEY_TOTP_ALLOWED_DIGITS,
                KEY_TOTP_ALLOWED_PERIOD,
                KEY_TOTP_MAX_SKEW,
                KEY_TOTP_DEFAULT_ALG,
                KEY_TOTP_DEFAULT_DIGITS,
                KEY_TOTP_DEFAULT_PERIOD,
                KEY_TOTP_DEFAULT_SKEW
        );
    }

    public record ResolvedTotpConfig(String issuer, String algorithm, int digits, int periodSeconds, int skew) {
    }
}
