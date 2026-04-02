package com.example.EnterpriseRagCommunity.controller.access;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.EnterpriseRagCommunity.dto.access.EmailAdminSettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.EmailInboxSettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.TotpAdminSettingsDTO;
import com.example.EnterpriseRagCommunity.service.notify.EmailEncryption;
import com.example.EnterpriseRagCommunity.service.notify.EmailTransportConfig;
import com.example.EnterpriseRagCommunity.testsupport.SecurityContextTestSupport;

class AdminSettingsControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextTestSupport.clear();
    }

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
    void normalizeTotpSettings_should_reject_maxSkew_less_than_0() {
        TotpAdminSettingsDTO dto = new TotpAdminSettingsDTO();
        dto.setIssuer("EnterpriseRagCommunity");
        dto.setAllowedAlgorithms(List.of("SHA1"));
        dto.setAllowedDigits(List.of(6));
        dto.setAllowedPeriodSeconds(List.of(30));
        dto.setMaxSkew(-1);
        dto.setDefaultAlgorithm("SHA1");
        dto.setDefaultDigits(6);
        dto.setDefaultPeriodSeconds(30);
        dto.setDefaultSkew(0);

        assertThatThrownBy(() -> invokeNormalize(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("maxSkew 仅支持 0..10");
    }

    @Test
    void normalizeTotpSettings_should_default_issuer_when_blank() throws Exception {
        TotpAdminSettingsDTO dto = new TotpAdminSettingsDTO();
        dto.setIssuer("   ");
        TotpAdminSettingsDTO normalized = invokeNormalize(dto);
        assertThat(normalized.getIssuer()).isEqualTo("EnterpriseRagCommunity");
    }

    @Test
    void normalizeTotpSettings_should_reject_issuer_too_long() {
        TotpAdminSettingsDTO dto = new TotpAdminSettingsDTO();
        dto.setIssuer("x".repeat(65));
        assertThatThrownBy(() -> invokeNormalize(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("issuer 过长");
    }

    @Test
    void normalizeTotpSettings_should_reject_defaultAlgorithm_not_in_allowedAlgorithms() {
        TotpAdminSettingsDTO dto = new TotpAdminSettingsDTO();
        dto.setIssuer("EnterpriseRagCommunity");
        dto.setAllowedAlgorithms(List.of("SHA256"));
        dto.setDefaultAlgorithm("SHA1");
        assertThatThrownBy(() -> invokeNormalize(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("defaultAlgorithm 必须在 allowedAlgorithms 中");
    }

    @Test
    void normalizeTotpSettings_should_reject_defaultDigits_not_in_allowedDigits() {
        TotpAdminSettingsDTO dto = new TotpAdminSettingsDTO();
        dto.setIssuer("EnterpriseRagCommunity");
        dto.setAllowedDigits(List.of(8));
        dto.setDefaultDigits(6);
        assertThatThrownBy(() -> invokeNormalize(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("defaultDigits 必须在 allowedDigits 中");
    }

    @Test
    void normalizeTotpSettings_should_reject_defaultPeriod_not_in_allowedPeriodSeconds() {
        TotpAdminSettingsDTO dto = new TotpAdminSettingsDTO();
        dto.setIssuer("EnterpriseRagCommunity");
        dto.setAllowedPeriodSeconds(List.of(60));
        dto.setDefaultPeriodSeconds(30);
        assertThatThrownBy(() -> invokeNormalize(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("defaultPeriodSeconds 必须在 allowedPeriodSeconds 中");
    }

    @Test
    void normalizeTotpSettings_should_reject_defaultSkew_out_of_range() {
        TotpAdminSettingsDTO dto = new TotpAdminSettingsDTO();
        dto.setIssuer("EnterpriseRagCommunity");
        dto.setMaxSkew(1);
        dto.setDefaultSkew(2);
        assertThatThrownBy(() -> invokeNormalize(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("defaultSkew 必须在 0..maxSkew 中");
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
    void normalizeEmailSettings_should_force_smtp_protocol() throws Exception {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setProtocol("imap");
        EmailAdminSettingsDTO normalized = invokeNormalizeEmail(dto);
        assertThat(normalized.getProtocol()).isEqualTo("SMTP");
    }

    @Test
    void normalizeEmailSettings_should_rewrite_imap_default_host_ports_to_smtp_defaults() throws Exception {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setEnabled(true);
        dto.setHost("imap.qiye.aliyun.com");
        dto.setPortPlain(143);
        dto.setPortEncrypted(993);
        EmailAdminSettingsDTO normalized = invokeNormalizeEmail(dto);
        assertThat(normalized.getHost()).isEqualTo("smtp.qiye.aliyun.com");
        assertThat(normalized.getPortPlain()).isEqualTo(25);
        assertThat(normalized.getPortEncrypted()).isEqualTo(465);
    }

    @Test
    void normalizeEmailSettings_should_rewrite_pop3_default_host_ports_to_smtp_defaults() throws Exception {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setEnabled(true);
        dto.setHost("pop.qiye.aliyun.com");
        dto.setPortPlain(110);
        dto.setPortEncrypted(995);
        EmailAdminSettingsDTO normalized = invokeNormalizeEmail(dto);
        assertThat(normalized.getHost()).isEqualTo("smtp.qiye.aliyun.com");
        assertThat(normalized.getPortPlain()).isEqualTo(25);
        assertThat(normalized.getPortEncrypted()).isEqualTo(465);
    }

    @Test
    void normalizeEmailSettings_should_reject_imap_like_host_when_enabled() {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setEnabled(true);
        dto.setHost("imap.example.com");
        dto.setPortPlain(143);
        dto.setPortEncrypted(993);
        assertThatThrownBy(() -> invokeNormalizeEmail(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("看起来这是收件(IMAP/POP3)配置；发送验证码需要填写 SMTP 主机与端口");
    }

    @Test
    void normalizeEmailSettings_should_reject_when_enabled_and_host_missing() {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setEnabled(true);
        dto.setHost("  ");
        assertThatThrownBy(() -> invokeNormalizeEmail(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("host 不能为空");
    }

    @Test
    void normalizeEmailSettings_should_reject_when_enabled_and_ports_invalid() {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setEnabled(true);
        dto.setHost("smtp.example.com");
        dto.setPortPlain(0);
        dto.setPortEncrypted(65536);
        assertThatThrownBy(() -> invokeNormalizeEmail(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeEmailSettings_should_reject_invalid_otp_ranges() {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setOtpTtlSeconds(10);
        assertThatThrownBy(() -> invokeNormalizeEmail(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("otpTtlSeconds 不合法（建议 60~3600 秒）");

        EmailAdminSettingsDTO dto2 = new EmailAdminSettingsDTO();
        dto2.setOtpResendWaitSeconds(1);
        assertThatThrownBy(() -> invokeNormalizeEmail(dto2))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("otpResendWaitSeconds 不合法（建议 10~3600 秒）");
    }

    @Test
    void normalizeEmailSettings_should_clamp_otpResendWaitReduction_seconds() throws Exception {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setOtpResendWaitSeconds(120);
        dto.setOtpResendWaitReductionSecondsAfterVerified(999);
        EmailAdminSettingsDTO normalized = invokeNormalizeEmail(dto);
        assertThat(normalized.getOtpResendWaitReductionSecondsAfterVerified()).isEqualTo(120);
    }

    @Test
    void normalizeEmailSettings_should_default_encryption_on_unknown_value() throws Exception {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setEncryption("bad");
        EmailAdminSettingsDTO normalized = invokeNormalizeEmail(dto);
        assertThat(normalized.getEncryption()).isEqualTo("SSL");
    }

    @Test
    void normalizeEmailSettings_should_accept_starttls_encryption() throws Exception {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setEncryption("starttls");
        EmailAdminSettingsDTO normalized = invokeNormalizeEmail(dto);
        assertThat(normalized.getEncryption()).isEqualTo("STARTTLS");
    }

    @Test
    void normalizeEmailSettings_should_reject_subjectPrefix_too_long() {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setSubjectPrefix("x".repeat(65));
        assertThatThrownBy(() -> invokeNormalizeEmail(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("subjectPrefix 过长");
    }

    @Test
    void normalizeEmailSettings_should_clamp_negative_timeouts_to_zero() throws Exception {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setConnectTimeoutMs(-1);
        dto.setTimeoutMs(-2);
        dto.setWriteTimeoutMs(-3);
        EmailAdminSettingsDTO normalized = invokeNormalizeEmail(dto);
        assertThat(normalized.getConnectTimeoutMs()).isZero();
        assertThat(normalized.getTimeoutMs()).isZero();
        assertThat(normalized.getWriteTimeoutMs()).isZero();
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
    void normalizeEmailInboxSettings_should_force_imap_protocol_and_default_folders() throws Exception {
        EmailInboxSettingsDTO dto = new EmailInboxSettingsDTO();
        dto.setProtocol("pop3");
        dto.setHost(" ");
        dto.setFolder(" ");
        dto.setSentFolder(" ");
        EmailInboxSettingsDTO normalized = invokeNormalizeInbox(dto);
        assertThat(normalized.getProtocol()).isEqualTo("IMAP");
        assertThat(normalized.getHost()).isEqualTo("imap.qiye.aliyun.com");
        assertThat(normalized.getFolder()).isEqualTo("INBOX");
        assertThat(normalized.getSentFolder()).isEqualTo("Sent");
    }

    @Test
    void normalizeEmailInboxSettings_should_reject_invalid_ports() {
        EmailInboxSettingsDTO dto = new EmailInboxSettingsDTO();
        dto.setPortPlain(0);
        dto.setPortEncrypted(70000);
        assertThatThrownBy(() -> invokeNormalizeInbox(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeEmailInboxSettings_should_reject_too_long_sentFolder() {
        EmailInboxSettingsDTO dto = new EmailInboxSettingsDTO();
        dto.setHost("imap.qiye.aliyun.com");
        dto.setSentFolder("x".repeat(129));
        assertThatThrownBy(() -> invokeNormalizeInbox(dto))
                .isInstanceOf(InvocationTargetException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("sentFolder 过长");
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

    @Test
    void toTransportConfig_should_select_ports_based_on_encryption_and_nullify_blank_sslTrust() throws Exception {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setHost("smtp.example.com");
        dto.setPortPlain(2525);
        dto.setPortEncrypted(2465);
        dto.setEncryption("SSL");
        dto.setSslTrust("  ");

        EmailTransportConfig cfg = invokeToTransport(dto);
        assertThat(cfg.encryption()).isEqualTo(EmailEncryption.SSL);
        assertThat(cfg.port()).isEqualTo(2465);
        assertThat(cfg.sslTrust()).isNull();
    }

    @Test
    void toTransportConfig_should_use_plain_port_for_starttls() throws Exception {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setHost("smtp.example.com");
        dto.setPortPlain(2525);
        dto.setPortEncrypted(2465);
        dto.setEncryption("STARTTLS");

        EmailTransportConfig cfg = invokeToTransport(dto);
        assertThat(cfg.encryption()).isEqualTo(EmailEncryption.STARTTLS);
        assertThat(cfg.port()).isEqualTo(2525);
    }

    @Test
    void toTransportConfig_should_use_plain_port_for_none() throws Exception {
        EmailAdminSettingsDTO dto = new EmailAdminSettingsDTO();
        dto.setHost("smtp.example.com");
        dto.setPortPlain(2525);
        dto.setPortEncrypted(2465);
        dto.setEncryption("NONE");

        EmailTransportConfig cfg = invokeToTransport(dto);
        assertThat(cfg.encryption()).isEqualTo(EmailEncryption.NONE);
        assertThat(cfg.port()).isEqualTo(2525);
    }

    @Test
    void safeText_should_trim_and_limit_and_replace_whitespace() throws Exception {
        assertThat(invokeSafeText(null, 10)).isNull();
        assertThat(invokeSafeText(" \n\t ", 10)).isNull();
        assertThat(invokeSafeText("a\nb\tc", 10)).isEqualTo("a b c");
        assertThat(invokeSafeText("abcdef", 3)).isEqualTo("abc");
        assertThat(invokeSafeText("abcdef", 0)).isEqualTo("");
    }

    @Test
    void currentUsernameOrNull_should_handle_missing_and_anonymous_and_blank_names() throws Exception {
        SecurityContextHolder.clearContext();
        assertThat(invokeCurrentUsernameOrNull()).isNull();

        var anon = new TestingAuthenticationToken("anonymousUser", "n/a");
        anon.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(anon);
        assertThat(invokeCurrentUsernameOrNull()).isNull();

        var unauth = new TestingAuthenticationToken("u@example.com", "n/a");
        unauth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(unauth);
        assertThat(invokeCurrentUsernameOrNull()).isNull();

        var blank = new TestingAuthenticationToken("   ", "n/a");
        blank.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(blank);
        assertThat(invokeCurrentUsernameOrNull()).isNull();
    }

    private static List<String> invokeParseStringList(String v) throws Exception {
        Method m = AdminSettingsController.class.getDeclaredMethod("parseStringList", String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Optional<List<String>> out = (java.util.Optional<List<String>>) m.invoke(null, v);
        return out.orElseGet(List::of);
    }

    private static List<Integer> invokeParseIntList(String v) throws Exception {
        Method m = AdminSettingsController.class.getDeclaredMethod("parseIntList", String.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Optional<List<Integer>> out = (java.util.Optional<List<Integer>>) m.invoke(null, v);
        return out.orElseGet(List::of);
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

    private static EmailTransportConfig invokeToTransport(EmailAdminSettingsDTO dto) throws Exception {
        Method m = AdminSettingsController.class.getDeclaredMethod("toTransportConfig", EmailAdminSettingsDTO.class);
        m.setAccessible(true);
        return (EmailTransportConfig) m.invoke(null, dto);
    }

    private static String invokeSafeText(String s, int maxLen) throws Exception {
        Method m = AdminSettingsController.class.getDeclaredMethod("safeText", String.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, s, maxLen);
    }

    private static String invokeCurrentUsernameOrNull() throws Exception {
        Method m = AdminSettingsController.class.getDeclaredMethod("currentUsernameOrNull");
        m.setAccessible(true);
        return (String) m.invoke(null);
    }

    @Test
    void parseStringList_should_return_empty_for_blank_and_split_for_values() throws Exception {
        assertThat(invokeParseStringList(null)).isEmpty();
        assertThat(invokeParseStringList(" ")).isEmpty();
        assertThat(invokeParseStringList("a, b, ,c")).containsExactly("a", "b", "c");
    }

    @Test
    void parseIntList_should_skip_invalid_numbers_and_return_empty_when_no_valid() throws Exception {
        assertThat(invokeParseIntList("1, x, 2")).containsExactly(1, 2);
        assertThat(invokeParseIntList("x, y")).isEmpty();
    }

    private static String invokeJoinInts(List<Integer> list) throws Exception {
        Method m = AdminSettingsController.class.getDeclaredMethod("joinInts", List.class);
        m.setAccessible(true);
        return (String) m.invoke(null, list);
    }
}
