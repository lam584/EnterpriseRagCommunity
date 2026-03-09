package com.example.EnterpriseRagCommunity.service.access;

import com.example.EnterpriseRagCommunity.dto.access.TotpAdminSettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.request.TotpEnrollRequest;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TotpPolicyServiceBranchTest {

    @Test
    void getSettingsOrDefault_should_apply_defaults_and_bounds() {
        AppSettingsService app = mock(AppSettingsService.class);
        when(app.getString("totp_issuer")).thenReturn(Optional.empty());
        when(app.getString("totp_allowed_algorithms")).thenReturn(Optional.of("sha1, sha256, ,sha512"));
        when(app.getString("totp_allowed_digits")).thenReturn(Optional.of("6, x, 8"));
        when(app.getString("totp_allowed_period_seconds")).thenReturn(Optional.of("30, 60"));
        when(app.getLongOrDefault("totp_max_skew", 1L)).thenReturn(99L);
        when(app.getString("totp_default_algorithm")).thenReturn(Optional.of("sha256"));
        when(app.getLongOrDefault("totp_default_digits", 6L)).thenReturn(8L);
        when(app.getLongOrDefault("totp_default_period_seconds", 30L)).thenReturn(60L);
        when(app.getLongOrDefault("totp_default_skew", 1L)).thenReturn(-2L);

        TotpPolicyService s = new TotpPolicyService(app);
        TotpAdminSettingsDTO dto = s.getSettingsOrDefault();

        assertEquals("EnterpriseRagCommunity", dto.getIssuer());
        assertEquals(java.util.List.of("SHA1", "SHA256", "SHA512"), dto.getAllowedAlgorithms());
        assertEquals(java.util.List.of(6, 8), dto.getAllowedDigits());
        assertEquals(java.util.List.of(30, 60), dto.getAllowedPeriodSeconds());
        assertEquals(10, dto.getMaxSkew());
        assertEquals("sha256", dto.getDefaultAlgorithm());
        assertEquals(8, dto.getDefaultDigits());
        assertEquals(60, dto.getDefaultPeriodSeconds());
        assertEquals(0, dto.getDefaultSkew());
    }

    @Test
    void resolveForEnroll_should_validate_algorithm_digits_period_and_skew() {
        AppSettingsService app = mock(AppSettingsService.class);
        when(app.getString("totp_issuer")).thenReturn(Optional.of("  issuer  "));
        when(app.getString("totp_allowed_algorithms")).thenReturn(Optional.of("sha1,sha256"));
        when(app.getString("totp_allowed_digits")).thenReturn(Optional.of("6,8"));
        when(app.getString("totp_allowed_period_seconds")).thenReturn(Optional.of("30"));
        when(app.getLongOrDefault("totp_max_skew", 1L)).thenReturn(2L);
        when(app.getString("totp_default_algorithm")).thenReturn(Optional.of("SHA1"));
        when(app.getLongOrDefault("totp_default_digits", 6L)).thenReturn(6L);
        when(app.getLongOrDefault("totp_default_period_seconds", 30L)).thenReturn(30L);
        when(app.getLongOrDefault("totp_default_skew", 1L)).thenReturn(1L);

        TotpPolicyService s = new TotpPolicyService(app);
        TotpEnrollRequest ok = new TotpEnrollRequest();
        ok.setAlgorithm("sha256");
        ok.setDigits(8);
        ok.setPeriodSeconds(30);
        ok.setSkew(2);
        TotpPolicyService.ResolvedTotpConfig cfg = s.resolveForEnroll(ok);
        assertEquals("issuer", cfg.issuer());
        assertEquals("SHA256", cfg.algorithm());
        assertEquals(8, cfg.digits());

        TotpEnrollRequest badAlg = new TotpEnrollRequest();
        badAlg.setAlgorithm("md5");
        assertThrows(IllegalArgumentException.class, () -> s.resolveForEnroll(badAlg));

        TotpEnrollRequest badDigits = new TotpEnrollRequest();
        badDigits.setDigits(7);
        assertThrows(IllegalArgumentException.class, () -> s.resolveForEnroll(badDigits));

        TotpEnrollRequest badPeriod = new TotpEnrollRequest();
        badPeriod.setPeriodSeconds(45);
        assertThrows(IllegalArgumentException.class, () -> s.resolveForEnroll(badPeriod));

        TotpEnrollRequest badSkew = new TotpEnrollRequest();
        badSkew.setSkew(3);
        assertThrows(IllegalArgumentException.class, () -> s.resolveForEnroll(badSkew));
    }
}
