package com.example.EnterpriseRagCommunity.service.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicySettingsDTO;

class Security2faPolicyServiceTest {

    @Test
    void normalizeAdminSettings_should_default_policies_and_clear_role_ids_when_not_roles_mode() {
        Security2faPolicyService svc = new Security2faPolicyService(null, null, null);

        Security2faPolicySettingsDTO in = new Security2faPolicySettingsDTO();
        in.setTotpPolicy(null);
        in.setTotpRoleIds(List.of(1L, 2L));
        in.setEmailOtpPolicy("ALLOW_ALL");
        in.setEmailOtpRoleIds(List.of(3L));

        Security2faPolicySettingsDTO out = svc.normalizeAdminSettings(in);
        assertThat(out.getTotpPolicy()).isEqualTo("ALLOW_ALL");
        assertThat(out.getTotpRoleIds()).isEmpty();
        assertThat(out.getEmailOtpPolicy()).isEqualTo("ALLOW_ALL");
        assertThat(out.getEmailOtpRoleIds()).isEmpty();
    }

    @Test
    void normalizeAdminSettings_should_require_role_ids_when_roles_mode() {
        Security2faPolicyService svc = new Security2faPolicyService(null, null, null);

        Security2faPolicySettingsDTO in = new Security2faPolicySettingsDTO();
        in.setTotpPolicy("ALLOW_ROLES");
        in.setTotpRoleIds(List.of());
        in.setEmailOtpPolicy("ALLOW_ALL");

        assertThatThrownBy(() -> svc.normalizeAdminSettings(in))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("TOTP 策略为 *_ROLES 时必须选择 roleId");
    }

    @Test
    void normalizeAdminSettings_should_sort_and_dedup_role_ids() {
        Security2faPolicyService svc = new Security2faPolicyService(null, null, null);

        Security2faPolicySettingsDTO in = new Security2faPolicySettingsDTO();
        in.setTotpPolicy("REQUIRE_ROLES");
        in.setTotpRoleIds(java.util.Arrays.asList(2L, 1L, 2L, 0L, -1L, null));
        in.setEmailOtpPolicy("FORBID_ALL");
        in.setEmailOtpRoleIds(List.of(9L));

        Security2faPolicySettingsDTO out = svc.normalizeAdminSettings(in);
        assertThat(out.getTotpRoleIds()).containsExactly(1L, 2L);
        assertThat(out.getEmailOtpRoleIds()).isEmpty();
    }
}
