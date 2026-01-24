package com.example.EnterpriseRagCommunity.controller.access;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.example.EnterpriseRagCommunity.dto.access.EmailAdminSettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.EmailInboxSettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.TotpAdminSettingsDTO;

class AdminSettingsControllerTest {

    @Test
    void normalizeTotpSettings_should_default_to_30s_and_maxSkew_1() throws Exception {
        TotpAdminSettingsDTO normalized = invokeNormalize(null);
        assertThat(normalized.getAllowedPeriodSeconds()).containsExactly(30);
        assertThat(normalized.getMaxSkew()).isEqualTo(1);
    }

    @Test
    void normalizeTotpSettings_should_allow_maxSkew_up_to_10() throws Exception {
        TotpAdminSettingsDTO dto = new TotpAdminSettingsDTO();
        dto.setIssuer("EnterpriseRagCommunity");
        dto.setAllowedAlgorithms(List.of("SHA1"));
        dto.setAllowedDigits(List.of(6));
        dto.setAllowedPeriodSeconds(List.of(30));
        dto.setMaxSkew(6);
        dto.setDefaultAlgorithm("SHA1");
        dto.setDefaultDigits(6);
        dto.setDefaultPeriodSeconds(30);
        dto.setDefaultSkew(1);

        TotpAdminSettingsDTO normalized = invokeNormalize(dto);
        assertThat(normalized.getMaxSkew()).isEqualTo(6);
    }

    @Test
    void normalizeTotpSettings_should_reject_maxSkew_greater_than_10() {
        TotpAdminSettingsDTO dto = new TotpAdminSettingsDTO();
        dto.setIssuer("EnterpriseRagCommunity");
        dto.setAllowedAlgorithms(List.of("SHA1"));
        dto.setAllowedDigits(List.of(6));
        dto.setAllowedPeriodSeconds(List.of(30));
        dto.setMaxSkew(11);
        dto.setDefaultAlgorithm("SHA1");
        dto.setDefaultDigits(6);
        dto.setDefaultPeriodSeconds(30);
        dto.setDefaultSkew(1);

        assertThatThrownBy(() -> invokeNormalize(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("maxSkew 仅支持 0..10");
    }

    @Test
    void normalizeEmailSettings_should_default_to_smtp_ports() throws Exception {
        EmailAdminSettingsDTO normalized = invokeNormalizeEmail(null);
        assertThat(normalized.getEnabled()).isFalse();
        assertThat(normalized.getProtocol()).isEqualTo("SMTP");
        assertThat(normalized.getPortPlain()).isEqualTo(25);
        assertThat(normalized.getPortEncrypted()).isEqualTo(465);
        assertThat(normalized.getEncryption()).isEqualTo("SSL");
    }

    @Test
    void normalizeEmailInboxSettings_should_default_to_imap_host_ports_and_inbox() throws Exception {
        EmailInboxSettingsDTO normalized = invokeNormalizeInbox(null);
        assertThat(normalized.getProtocol()).isEqualTo("IMAP");
        assertThat(normalized.getHost()).isEqualTo("imap.qiye.aliyun.com");
        assertThat(normalized.getPortPlain()).isEqualTo(143);
        assertThat(normalized.getPortEncrypted()).isEqualTo(993);
        assertThat(normalized.getEncryption()).isEqualTo("SSL");
        assertThat(normalized.getFolder()).isEqualTo("INBOX");
    }

    @Test
    void normalizeEmailInboxSettings_should_reject_too_long_folder() throws Exception {
        EmailInboxSettingsDTO dto = new EmailInboxSettingsDTO();
        dto.setHost("imap.qiye.aliyun.com");
        dto.setFolder("x".repeat(129));
        assertThatThrownBy(() -> invokeNormalizeInbox(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("folder 过长");
    }

    private static TotpAdminSettingsDTO invokeNormalize(TotpAdminSettingsDTO dto) throws Exception {
        Method m = AdminSettingsController.class.getDeclaredMethod("normalizeTotpSettings", TotpAdminSettingsDTO.class);
        m.setAccessible(true);
        return (TotpAdminSettingsDTO) m.invoke(null, dto);
    }

    private static EmailAdminSettingsDTO invokeNormalizeEmail(EmailAdminSettingsDTO dto) throws Exception {
        Method m = AdminSettingsController.class.getDeclaredMethod("normalizeEmailSettings", EmailAdminSettingsDTO.class);
        m.setAccessible(true);
        return (EmailAdminSettingsDTO) m.invoke(null, dto);
    }

    private static EmailInboxSettingsDTO invokeNormalizeInbox(EmailInboxSettingsDTO dto) throws Exception {
        Method m = AdminSettingsController.class.getDeclaredMethod("normalizeEmailInboxSettings", EmailInboxSettingsDTO.class);
        m.setAccessible(true);
        return (EmailInboxSettingsDTO) m.invoke(null, dto);
    }
}
