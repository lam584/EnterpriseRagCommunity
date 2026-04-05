package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.TotpAdminSettingsDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class TotpPolicySettingsSupport {

    private TotpPolicySettingsSupport() {
    }

    public static TotpAdminSettingsDTO buildSettings(
            AppSettingsService appSettingsService,
            Function<String, Optional<List<String>>> parseStringList,
            Function<String, Optional<List<Integer>>> parseIntList,
            String issuerKey,
            String allowedAlgKey,
            String allowedDigitsKey,
            String allowedPeriodKey,
            String maxSkewKey,
            String defaultAlgKey,
            String defaultDigitsKey,
            String defaultPeriodKey,
            String defaultSkewKey
    ) {
        TotpAdminSettingsDTO dto = new TotpAdminSettingsDTO();
        dto.setIssuer(appSettingsService.getString(issuerKey).orElse("EnterpriseRagCommunity"));
        dto.setAllowedAlgorithms(parseStringList.apply(appSettingsService.getString(allowedAlgKey).orElse(null)).orElse(List.of("SHA1", "SHA256", "SHA512")));
        dto.setAllowedDigits(parseIntList.apply(appSettingsService.getString(allowedDigitsKey).orElse(null)).orElse(List.of(6, 8)));
        dto.setAllowedPeriodSeconds(parseIntList.apply(appSettingsService.getString(allowedPeriodKey).orElse(null)).orElse(List.of(30)));
        int maxSkew = (int) appSettingsService.getLongOrDefault(maxSkewKey, 1L);
        if (maxSkew < 0) maxSkew = 0;
        if (maxSkew > 10) maxSkew = 10;
        dto.setMaxSkew(maxSkew);
        dto.setDefaultAlgorithm(appSettingsService.getString(defaultAlgKey).orElse("SHA1"));
        dto.setDefaultDigits((int) appSettingsService.getLongOrDefault(defaultDigitsKey, 6L));
        dto.setDefaultPeriodSeconds((int) appSettingsService.getLongOrDefault(defaultPeriodKey, 30L));
        int defaultSkew = (int) appSettingsService.getLongOrDefault(defaultSkewKey, 1L);
        if (defaultSkew < 0) defaultSkew = 0;
        if (defaultSkew > maxSkew) defaultSkew = maxSkew;
        dto.setDefaultSkew(defaultSkew);
        return dto;
    }
}
