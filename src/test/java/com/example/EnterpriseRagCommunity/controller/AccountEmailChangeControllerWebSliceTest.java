package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.AccountSecurityService;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.notify.AccountEmailChangeNotificationMailer;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountEmailChangeController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountEmailChangeControllerWebSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsersRepository usersRepository;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private EmailVerificationMailer emailVerificationMailer;

    @MockitoBean
    private AccountTotpService accountTotpService;

    @MockitoBean
    private AccountSecurityService accountSecurityService;

    @MockitoBean
    private AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer;

    @MockitoBean
    private NotificationsService notificationsService;

    @MockitoBean
    private AuditLogWriter auditLogWriter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static UsersEntity user(long id, String email) {
        UsersEntity u = new UsersEntity();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    private static MockHttpSession verifiedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailChange.passwordVerifiedAt", LocalDateTime.now());
        session.setAttribute("emailChange.oldVerifiedAt", LocalDateTime.now());
        return session;
    }

    @Test
    void verifyPassword_shouldReturn401_whenNoAuthInContext() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/api/account/email-change/verify-password")
                        .contentType(APPLICATION_JSON)
                        .content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void verifyPassword_shouldReturn401_whenAnonymousUserPrincipal() throws Exception {
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("anonymousUser");
        when(auth.getName()).thenReturn("whatever");
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/account/email-change/verify-password")
                        .contentType(APPLICATION_JSON)
                        .content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void verifyPassword_shouldReturn401_whenAuthNameNull() throws Exception {
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("u");
        when(auth.getName()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/account/email-change/verify-password")
                        .contentType(APPLICATION_JSON)
                        .content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_ok@example.invalid")
    void verifyPassword_shouldIgnoreAuditAndNotificationFailures() throws Exception {
        UsersEntity u = user(1L, "zz_test_ec_slice_ok@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_ok@example.invalid")).thenReturn(Optional.of(u));
        doThrow(new RuntimeException("audit down"))
                .when(auditLogWriter)
                .write(anyLong(), any(), any(), any(), anyLong(), any(), any(), any(), any());
        doThrow(new RuntimeException("notify down"))
                .when(notificationsService)
                .createNotification(anyLong(), any(), any(), any());

        mockMvc.perform(post("/api/account/email-change/verify-password")
                        .contentType(APPLICATION_JSON)
                        .content("{\"password\":\"pass1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("密码验证通过"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_fail@example.invalid")
    void verifyPassword_shouldAuditTruncatedMessage_onFailure() throws Exception {
        UsersEntity u = user(2L, "zz_test_ec_slice_fail@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_fail@example.invalid")).thenReturn(Optional.of(u));

        String longMsg = "x".repeat(400);
        doThrow(new IllegalArgumentException(longMsg))
                .when(accountSecurityService)
                .verifyPasswordByEmail(eq("zz_test_ec_slice_fail@example.invalid"), any());

        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);

        mockMvc.perform(post("/api/account/email-change/verify-password")
                        .contentType(APPLICATION_JSON)
                        .content("{\"password\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(longMsg.substring(0, 256)));

        verify(auditLogWriter).write(
                eq(2L),
                eq("zz_test_ec_slice_fail@example.invalid"),
                eq("ACCOUNT_EMAIL_CHANGE_VERIFY_PASSWORD"),
                eq("USER"),
                eq(2L),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.FAIL),
                eq("验证密码失败（用于修改邮箱）"),
                eq(null),
                detailsCaptor.capture()
        );

        Object msg = detailsCaptor.getValue().get("message");
        assertThat(msg).isInstanceOf(String.class);
        assertThat(((String) msg).length()).isEqualTo(256);
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_old_send_disabled@example.invalid")
    void oldSendCode_shouldReturn400_whenMailerDisabled() throws Exception {
        UsersEntity u = user(3L, "zz_test_ec_slice_old_send_disabled@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_old_send_disabled@example.invalid")).thenReturn(Optional.of(u));
        when(emailVerificationMailer.isEnabled()).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailChange.passwordVerifiedAt", LocalDateTime.now());

        mockMvc.perform(post("/api/account/email-change/old/send-code").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("邮箱服务未启用"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_old_send_ok@example.invalid")
    void oldSendCode_shouldReturn200_whenCodeIssued() throws Exception {
        UsersEntity u = user(31L, "zz_test_ec_slice_old_send_ok@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_old_send_ok@example.invalid")).thenReturn(Optional.of(u));
        when(emailVerificationMailer.isEnabled()).thenReturn(true);
        when(emailVerificationService.issueCode(31L, EmailVerificationPurpose.CHANGE_EMAIL_OLD, "zz_test_ec_slice_old_send_ok@example.invalid"))
                .thenReturn("111111");
        when(emailVerificationService.getDefaultResendWaitSeconds()).thenReturn(60);
        when(emailVerificationService.getDefaultTtlSeconds()).thenReturn(600);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailChange.passwordVerifiedAt", LocalDateTime.now());

        mockMvc.perform(post("/api/account/email-change/old/send-code").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("验证码已发送"))
                .andExpect(jsonPath("$.resendWaitSeconds").value(60))
                .andExpect(jsonPath("$.codeTtlSeconds").value(600));

        verify(emailVerificationMailer).sendVerificationCode(
                "zz_test_ec_slice_old_send_ok@example.invalid",
                "111111",
                EmailVerificationPurpose.CHANGE_EMAIL_OLD
        );
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_old_send_boom@example.invalid")
    void oldSendCode_shouldReturn400_whenSendThrows() throws Exception {
        UsersEntity u = user(32L, "zz_test_ec_slice_old_send_boom@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_old_send_boom@example.invalid")).thenReturn(Optional.of(u));
        when(emailVerificationMailer.isEnabled()).thenReturn(true);
        when(emailVerificationService.issueCode(32L, EmailVerificationPurpose.CHANGE_EMAIL_OLD, "zz_test_ec_slice_old_send_boom@example.invalid"))
                .thenReturn("222222");
        doThrow(new IllegalStateException("boom"))
                .when(emailVerificationMailer)
                .sendVerificationCode("zz_test_ec_slice_old_send_boom@example.invalid", "222222", EmailVerificationPurpose.CHANGE_EMAIL_OLD);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailChange.passwordVerifiedAt", LocalDateTime.now());

        mockMvc.perform(post("/api/account/email-change/old/send-code").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("boom"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_old_verify_totp_empty@example.invalid")
    void oldVerify_shouldReturn400_whenTotpCodeEmpty() throws Exception {
        UsersEntity u = user(4L, "zz_test_ec_slice_old_verify_totp_empty@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_old_verify_totp_empty@example.invalid")).thenReturn(Optional.of(u));
        when(accountTotpService.isEnabledByEmail("zz_test_ec_slice_old_verify_totp_empty@example.invalid")).thenReturn(true);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailChange.passwordVerifiedAt", LocalDateTime.now());

        mockMvc.perform(post("/api/account/email-change/old/verify")
                        .session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"method\":\"totp\",\"totpCode\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入动态验证码"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_old_verify_invalid_method@example.invalid")
    void oldVerify_shouldReturn400_whenMethodInvalid() throws Exception {
        UsersEntity u = user(41L, "zz_test_ec_slice_old_verify_invalid_method@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_old_verify_invalid_method@example.invalid")).thenReturn(Optional.of(u));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailChange.passwordVerifiedAt", LocalDateTime.now());

        mockMvc.perform(post("/api/account/email-change/old/verify")
                        .session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"method\":\"sms\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("验证方式不合法"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_old_verify_email_mailer_off@example.invalid")
    void oldVerify_shouldReturn400_whenEmailMailerDisabled() throws Exception {
        UsersEntity u = user(42L, "zz_test_ec_slice_old_verify_email_mailer_off@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_old_verify_email_mailer_off@example.invalid")).thenReturn(Optional.of(u));
        when(emailVerificationMailer.isEnabled()).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailChange.passwordVerifiedAt", LocalDateTime.now());

        mockMvc.perform(post("/api/account/email-change/old/verify")
                        .session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"method\":\"email\",\"emailCode\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("邮箱服务未启用，无法使用邮箱验证码验证旧邮箱"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_old_verify_email_code_empty@example.invalid")
    void oldVerify_shouldReturn400_whenEmailCodeEmpty() throws Exception {
        UsersEntity u = user(43L, "zz_test_ec_slice_old_verify_email_code_empty@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_old_verify_email_code_empty@example.invalid")).thenReturn(Optional.of(u));
        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailChange.passwordVerifiedAt", LocalDateTime.now());

        mockMvc.perform(post("/api/account/email-change/old/verify")
                        .session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"method\":\"email\",\"emailCode\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入旧邮箱验证码"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_old_verify_totp_disabled@example.invalid")
    void oldVerify_shouldReturn400_whenTotpDisabled() throws Exception {
        UsersEntity u = user(44L, "zz_test_ec_slice_old_verify_totp_disabled@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_old_verify_totp_disabled@example.invalid")).thenReturn(Optional.of(u));
        when(accountTotpService.isEnabledByEmail("zz_test_ec_slice_old_verify_totp_disabled@example.invalid")).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailChange.passwordVerifiedAt", LocalDateTime.now());

        mockMvc.perform(post("/api/account/email-change/old/verify")
                        .session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"method\":\"totp\",\"totpCode\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前账号未启用二次验证"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_old_verify_email_boom@example.invalid")
    void oldVerify_shouldReturn400_whenEmailVerifyThrows() throws Exception {
        UsersEntity u = user(45L, "zz_test_ec_slice_old_verify_email_boom@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_old_verify_email_boom@example.invalid")).thenReturn(Optional.of(u));
        when(emailVerificationMailer.isEnabled()).thenReturn(true);
        doThrow(new IllegalArgumentException("bad code"))
                .when(emailVerificationService)
                .verifyAndConsume(45L, EmailVerificationPurpose.CHANGE_EMAIL_OLD, "zz_test_ec_slice_old_verify_email_boom@example.invalid", "111111");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailChange.passwordVerifiedAt", LocalDateTime.now());

        mockMvc.perform(post("/api/account/email-change/old/verify")
                        .session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"method\":\"email\",\"emailCode\":\"111111\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad code"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_old_verify_totp_boom@example.invalid")
    void oldVerify_shouldReturn400_whenTotpVerifyThrows() throws Exception {
        UsersEntity u = user(46L, "zz_test_ec_slice_old_verify_totp_boom@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_old_verify_totp_boom@example.invalid")).thenReturn(Optional.of(u));
        when(accountTotpService.isEnabledByEmail("zz_test_ec_slice_old_verify_totp_boom@example.invalid")).thenReturn(true);
        doThrow(new IllegalArgumentException("bad totp"))
                .when(accountTotpService)
                .requireValidEnabledCodeByEmail("zz_test_ec_slice_old_verify_totp_boom@example.invalid", "999999");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("emailChange.passwordVerifiedAt", LocalDateTime.now());

        mockMvc.perform(post("/api/account/email-change/old/verify")
                        .session(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"method\":\"totp\",\"totpCode\":\"999999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad totp"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_same@example.invalid")
    void sendCode_shouldReturn400_whenNewEmailSameAsCurrent() throws Exception {
        mockMvc.perform(post("/api/account/email-change/send-code")
                        .session(verifiedSession())
                        .contentType(APPLICATION_JSON)
                        .content("{\"newEmail\":\"zz_test_ec_slice_same@example.invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("新邮箱不能与当前邮箱相同"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_id_null@example.invalid")
    void sendCode_shouldNotTreatEmailAsInUse_whenFoundIdNull() throws Exception {
        UsersEntity current = user(5L, "zz_test_ec_slice_id_null@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_id_null@example.invalid")).thenReturn(Optional.of(current));

        UsersEntity found = new UsersEntity();
        found.setId(null);
        found.setEmail("zz_test_ec_slice_new_idnull@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_new_idnull@example.invalid")).thenReturn(Optional.of(found));

        when(emailVerificationMailer.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/account/email-change/send-code")
                        .session(verifiedSession())
                        .contentType(APPLICATION_JSON)
                        .content("{\"newEmail\":\"zz_test_ec_slice_new_idnull@example.invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("邮箱服务未启用"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_id_same@example.invalid")
    void sendCode_shouldNotTreatEmailAsInUse_whenFoundIdSameAsCurrent() throws Exception {
        UsersEntity current = user(9L, "zz_test_ec_slice_id_same@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_id_same@example.invalid")).thenReturn(Optional.of(current));

        UsersEntity found = user(9L, "zz_test_ec_slice_new_idsame@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_new_idsame@example.invalid")).thenReturn(Optional.of(found));

        when(emailVerificationMailer.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/account/email-change/send-code")
                        .session(verifiedSession())
                        .contentType(APPLICATION_JSON)
                        .content("{\"newEmail\":\"zz_test_ec_slice_new_idsame@example.invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("邮箱服务未启用"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_send_ok@example.invalid")
    void sendCode_shouldReturn200_whenCodeIssued() throws Exception {
        UsersEntity current = user(10L, "zz_test_ec_slice_send_ok@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_send_ok@example.invalid")).thenReturn(Optional.of(current));
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_send_ok_new@example.invalid")).thenReturn(Optional.empty());
        when(emailVerificationMailer.isEnabled()).thenReturn(true);
        when(emailVerificationService.issueCode(10L, EmailVerificationPurpose.CHANGE_EMAIL, "zz_test_ec_slice_send_ok_new@example.invalid"))
                .thenReturn("333333");
        when(emailVerificationService.getDefaultResendWaitSeconds()).thenReturn(60);
        when(emailVerificationService.getDefaultTtlSeconds()).thenReturn(600);

        mockMvc.perform(post("/api/account/email-change/send-code")
                        .session(verifiedSession())
                        .contentType(APPLICATION_JSON)
                        .content("{\"newEmail\":\"zz_test_ec_slice_send_ok_new@example.invalid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("验证码已发送"))
                .andExpect(jsonPath("$.resendWaitSeconds").value(60))
                .andExpect(jsonPath("$.codeTtlSeconds").value(600));

        verify(emailVerificationMailer).sendVerificationCode(
                "zz_test_ec_slice_send_ok_new@example.invalid",
                "333333",
                EmailVerificationPurpose.CHANGE_EMAIL
        );
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_send_issue_boom@example.invalid")
    void sendCode_shouldReturn400_whenIssueThrows() throws Exception {
        UsersEntity current = user(11L, "zz_test_ec_slice_send_issue_boom@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_send_issue_boom@example.invalid")).thenReturn(Optional.of(current));
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_send_issue_boom_new@example.invalid")).thenReturn(Optional.empty());
        when(emailVerificationMailer.isEnabled()).thenReturn(true);
        doThrow(new IllegalStateException("issue boom"))
                .when(emailVerificationService)
                .issueCode(11L, EmailVerificationPurpose.CHANGE_EMAIL, "zz_test_ec_slice_send_issue_boom_new@example.invalid");

        mockMvc.perform(post("/api/account/email-change/send-code")
                        .session(verifiedSession())
                        .contentType(APPLICATION_JSON)
                        .content("{\"newEmail\":\"zz_test_ec_slice_send_issue_boom_new@example.invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("issue boom"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_change_mailer_off@example.invalid")
    void change_shouldReturn400_whenMailerDisabled() throws Exception {
        UsersEntity current = user(6L, "zz_test_ec_slice_change_mailer_off@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_change_mailer_off@example.invalid")).thenReturn(Optional.of(current));
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_change_new@example.invalid")).thenReturn(Optional.empty());
        when(emailVerificationMailer.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/account/email-change")
                        .session(verifiedSession())
                        .contentType(APPLICATION_JSON)
                        .content("{\"newEmail\":\"zz_test_ec_slice_change_new@example.invalid\",\"newEmailCode\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("邮箱服务未启用，无法验证新邮箱"));
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_change_fail@example.invalid")
    void change_shouldAuditMaskedEmail_onVerifyFailure() throws Exception {
        UsersEntity current = user(7L, "zz_test_ec_slice_change_fail@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_change_fail@example.invalid")).thenReturn(Optional.of(current));
        when(usersRepository.findByEmailAndIsDeletedFalse("a@b.com")).thenReturn(Optional.empty());
        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        doThrow(new IllegalArgumentException("验证码错误"))
                .when(emailVerificationService)
                .verifyAndConsume(7L, EmailVerificationPurpose.CHANGE_EMAIL, "a@b.com", "bad");

        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);

        mockMvc.perform(post("/api/account/email-change")
                        .session(verifiedSession())
                        .contentType(APPLICATION_JSON)
                        .content("{\"newEmail\":\"a@b.com\",\"newEmailCode\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("验证码错误"));

        verify(auditLogWriter).write(
                eq(7L),
                eq("zz_test_ec_slice_change_fail@example.invalid"),
                eq("ACCOUNT_EMAIL_CHANGE_CONFIRM"),
                eq("USER"),
                eq(7L),
                eq(com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.FAIL),
                eq("更换邮箱失败"),
                eq(null),
                detailsCaptor.capture()
        );

        assertThat(detailsCaptor.getValue().get("newEmailMasked")).isEqualTo("***@b.com");
    }

    @Test
    @WithMockUser(username = "zz_test_ec_slice_change_ok_ignore_notify@example.invalid")
    void change_shouldIgnoreNotificationFailures() throws Exception {
        UsersEntity current = user(8L, "zz_test_ec_slice_change_ok_ignore_notify@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_change_ok_ignore_notify@example.invalid")).thenReturn(Optional.of(current));
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_slice_change_ok_new@example.invalid")).thenReturn(Optional.empty());
        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        doThrow(new RuntimeException("mail down"))
                .when(accountEmailChangeNotificationMailer)
                .sendChangeEmailSuccessNotifications(any(), any());
        doThrow(new RuntimeException("notify down"))
                .when(notificationsService)
                .createNotification(anyLong(), any(), any(), any());

        mockMvc.perform(post("/api/account/email-change")
                        .session(verifiedSession())
                        .contentType(APPLICATION_JSON)
                        .content("{\"newEmail\":\"zz_test_ec_slice_change_ok_new@example.invalid\",\"newEmailCode\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("邮箱更换成功，请重新登录"));

        verify(usersRepository).save(any());
    }
}
