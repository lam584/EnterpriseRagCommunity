package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.access.request.ChangeEmailConfirmRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.EmailChangeSendCodeRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.VerifyOldEmailRequest;
import com.example.EnterpriseRagCommunity.dto.access.request.VerifyPasswordRequest;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.AccountSecurityService;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.notify.AccountEmailChangeNotificationMailer;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountEmailChangeControllerUnitTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void setAuthenticatedEmail(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "pw", java.util.List.of())
        );
    }

    private static UsersEntity user(long id, String email) {
        UsersEntity u = new UsersEntity();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    @Test
    void verifyPassword_shouldTreatNullPasswordAsEmptyString() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer = mock(AccountEmailChangeNotificationMailer.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        AccountEmailChangeController controller = new AccountEmailChangeController(
                usersRepository,
                emailVerificationService,
                emailVerificationMailer,
                accountTotpService,
                accountSecurityService,
                accountEmailChangeNotificationMailer,
                notificationsService,
                auditLogWriter
        );

        setAuthenticatedEmail("zz_test_ec_unit_null_pw@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_unit_null_pw@example.invalid"))
                .thenReturn(Optional.of(user(1L, "zz_test_ec_unit_null_pw@example.invalid")));

        VerifyPasswordRequest req = new VerifyPasswordRequest();
        req.setPassword(null);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        Object body = controller.verifyPassword(req, servletRequest).getBody();

        verify(accountSecurityService).verifyPasswordByEmail("zz_test_ec_unit_null_pw@example.invalid", "");
        assertThat(body).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) body).get("message")).isEqualTo("密码验证通过");
    }

    @Test
    void verifyOld_shouldTreatNullMethodAsInvalid() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer = mock(AccountEmailChangeNotificationMailer.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        AccountEmailChangeController controller = new AccountEmailChangeController(
                usersRepository,
                emailVerificationService,
                emailVerificationMailer,
                accountTotpService,
                accountSecurityService,
                accountEmailChangeNotificationMailer,
                notificationsService,
                auditLogWriter
        );

        setAuthenticatedEmail("zz_test_ec_unit_null_method@example.invalid");

        VerifyOldEmailRequest req = new VerifyOldEmailRequest();
        req.setMethod(null);

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(servletRequest.getSession(true)).thenReturn(session);
        when(servletRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("emailChange.passwordVerifiedAt")).thenReturn(LocalDateTime.now());

        Object body = controller.verifyOld(req, servletRequest).getBody();
        assertThat(body).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) body).get("message")).isEqualTo("验证方式不合法");
        verify(usersRepository, never()).findByEmailAndIsDeletedFalse(any());
    }

    @Test
    void sendCode_shouldNormalizeNullEmailToEmpty() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer = mock(AccountEmailChangeNotificationMailer.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        AccountEmailChangeController controller = new AccountEmailChangeController(
                usersRepository,
                emailVerificationService,
                emailVerificationMailer,
                accountTotpService,
                accountSecurityService,
                accountEmailChangeNotificationMailer,
                notificationsService,
                auditLogWriter
        );

        setAuthenticatedEmail("zz_test_ec_unit_null_new_email@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_unit_null_new_email@example.invalid"))
                .thenReturn(Optional.of(user(2L, "zz_test_ec_unit_null_new_email@example.invalid")));
        when(usersRepository.findByEmailAndIsDeletedFalse("")).thenReturn(Optional.empty());
        when(emailVerificationMailer.isEnabled()).thenReturn(false);

        EmailChangeSendCodeRequest req = new EmailChangeSendCodeRequest();
        req.setNewEmail(null);

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(servletRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("emailChange.passwordVerifiedAt")).thenReturn(LocalDateTime.now());
        when(session.getAttribute("emailChange.oldVerifiedAt")).thenReturn(LocalDateTime.now());

        Object body = controller.sendCode(req, servletRequest).getBody();
        assertThat(body).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) body).get("message")).isEqualTo("邮箱服务未启用");
    }

    @Test
    void change_shouldReturn400_whenNewEmailCodeNull() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer = mock(AccountEmailChangeNotificationMailer.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        AccountEmailChangeController controller = new AccountEmailChangeController(
                usersRepository,
                emailVerificationService,
                emailVerificationMailer,
                accountTotpService,
                accountSecurityService,
                accountEmailChangeNotificationMailer,
                notificationsService,
                auditLogWriter
        );

        setAuthenticatedEmail("zz_test_ec_unit_code_null@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_unit_code_null@example.invalid"))
                .thenReturn(Optional.of(user(3L, "zz_test_ec_unit_code_null@example.invalid")));
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_unit_code_null_new@example.invalid")).thenReturn(Optional.empty());
        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        ChangeEmailConfirmRequest req = new ChangeEmailConfirmRequest();
        req.setNewEmail("zz_test_ec_unit_code_null_new@example.invalid");
        req.setNewEmailCode(null);

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(servletRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("emailChange.passwordVerifiedAt")).thenReturn(LocalDateTime.now());
        when(session.getAttribute("emailChange.oldVerifiedAt")).thenReturn(LocalDateTime.now());

        Object body = controller.change(req, servletRequest).getBody();
        assertThat(body).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) body).get("message")).isEqualTo("请输入新邮箱验证码");
        verify(emailVerificationService, never()).verifyAndConsume(anyLong(), any(), any(), any());
    }

    @Test
    void sendOldEmailCode_shouldReturn400_whenPasswordVerifiedExpired() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer = mock(AccountEmailChangeNotificationMailer.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        AccountEmailChangeController controller = new AccountEmailChangeController(
                usersRepository,
                emailVerificationService,
                emailVerificationMailer,
                accountTotpService,
                accountSecurityService,
                accountEmailChangeNotificationMailer,
                notificationsService,
                auditLogWriter
        );

        setAuthenticatedEmail("zz_test_ec_unit_expired_pw@example.invalid");
        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(servletRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("emailChange.passwordVerifiedAt")).thenReturn(LocalDateTime.now().minusMinutes(11));

        Object body = controller.sendOldEmailCode(servletRequest).getBody();
        assertThat(body).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) body).get("message")).isEqualTo("请先验证密码");
    }

    @Test
    void sendCode_shouldReturn400_whenOldVerifiedExpired() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer = mock(AccountEmailChangeNotificationMailer.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        AccountEmailChangeController controller = new AccountEmailChangeController(
                usersRepository,
                emailVerificationService,
                emailVerificationMailer,
                accountTotpService,
                accountSecurityService,
                accountEmailChangeNotificationMailer,
                notificationsService,
                auditLogWriter
        );

        setAuthenticatedEmail("zz_test_ec_unit_expired_old@example.invalid");

        EmailChangeSendCodeRequest req = new EmailChangeSendCodeRequest();
        req.setNewEmail("zz_test_ec_unit_expired_old_new@example.invalid");

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(servletRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("emailChange.passwordVerifiedAt")).thenReturn(LocalDateTime.now());
        when(session.getAttribute("emailChange.oldVerifiedAt")).thenReturn(LocalDateTime.now().minusMinutes(11));

        Object body = controller.sendCode(req, servletRequest).getBody();
        assertThat(body).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) body).get("message")).isEqualTo("请先验证旧邮箱或动态验证码");
    }

    @Test
    void change_shouldNotInvalidate_whenSessionNullAtEnd() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer = mock(AccountEmailChangeNotificationMailer.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        AccountEmailChangeController controller = new AccountEmailChangeController(
                usersRepository,
                emailVerificationService,
                emailVerificationMailer,
                accountTotpService,
                accountSecurityService,
                accountEmailChangeNotificationMailer,
                notificationsService,
                auditLogWriter
        );

        setAuthenticatedEmail("zz_test_ec_unit_session_null_end@example.invalid");

        UsersEntity u = user(5L, "zz_test_ec_unit_session_null_end@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_unit_session_null_end@example.invalid")).thenReturn(Optional.of(u));
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_unit_session_null_end_new@example.invalid")).thenReturn(Optional.empty());
        when(emailVerificationMailer.isEnabled()).thenReturn(true);
        when(usersRepository.save(any())).thenReturn(u);

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(servletRequest.getSession(false)).thenReturn(session, session, null);
        when(session.getAttribute("emailChange.passwordVerifiedAt")).thenReturn(LocalDateTime.now());
        when(session.getAttribute("emailChange.oldVerifiedAt")).thenReturn(LocalDateTime.now());

        ChangeEmailConfirmRequest req = new ChangeEmailConfirmRequest();
        req.setNewEmail("zz_test_ec_unit_session_null_end_new@example.invalid");
        req.setNewEmailCode("ok");

        Object body = controller.change(req, servletRequest).getBody();
        assertThat(body).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) body).get("message")).isEqualTo("邮箱更换成功，请重新登录");
        verify(session, never()).invalidate();
    }

    @Test
    void unauthorized_shouldReturn401_whenAuthNull() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer = mock(AccountEmailChangeNotificationMailer.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        AccountEmailChangeController controller = new AccountEmailChangeController(
                usersRepository,
                emailVerificationService,
                emailVerificationMailer,
                accountTotpService,
                accountSecurityService,
                accountEmailChangeNotificationMailer,
                notificationsService,
                auditLogWriter
        );

        SecurityContextHolder.clearContext();

        VerifyPasswordRequest req = new VerifyPasswordRequest();
        req.setPassword("x");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        var resp = controller.verifyPassword(req, servletRequest);
        assertThat(resp.getStatusCodeValue()).isEqualTo(401);
        assertThat(resp.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) resp.getBody()).get("message")).isEqualTo("未登录或会话已过期");
    }

    @Test
    void unauthorized_shouldReturn401_whenAnonymousUserPrincipal() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer = mock(AccountEmailChangeNotificationMailer.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        AccountEmailChangeController controller = new AccountEmailChangeController(
                usersRepository,
                emailVerificationService,
                emailVerificationMailer,
                accountTotpService,
                accountSecurityService,
                accountEmailChangeNotificationMailer,
                notificationsService,
                auditLogWriter
        );

        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("anonymousUser");
        when(auth.getName()).thenReturn("x@example.invalid");
        SecurityContextHolder.getContext().setAuthentication(auth);

        VerifyPasswordRequest req = new VerifyPasswordRequest();
        req.setPassword("x");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        var resp = controller.verifyPassword(req, servletRequest);
        assertThat(resp.getStatusCodeValue()).isEqualTo(401);
        assertThat(resp.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) resp.getBody()).get("message")).isEqualTo("未登录或会话已过期");
    }

    @Test
    void sendOldEmailCode_shouldReturn400_whenMailerDisabled() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer = mock(AccountEmailChangeNotificationMailer.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        AccountEmailChangeController controller = new AccountEmailChangeController(
                usersRepository,
                emailVerificationService,
                emailVerificationMailer,
                accountTotpService,
                accountSecurityService,
                accountEmailChangeNotificationMailer,
                notificationsService,
                auditLogWriter
        );

        setAuthenticatedEmail("zz_test_ec_unit_old_send_disabled@example.invalid");
        when(emailVerificationMailer.isEnabled()).thenReturn(false);

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(servletRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("emailChange.passwordVerifiedAt")).thenReturn(LocalDateTime.now());

        var resp = controller.sendOldEmailCode(servletRequest);
        assertThat(resp.getStatusCodeValue()).isEqualTo(400);
        assertThat(resp.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) resp.getBody()).get("message")).isEqualTo("邮箱服务未启用");
    }

    @Test
    void sendOldEmailCode_shouldReturn200_whenCodeIssued() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer = mock(AccountEmailChangeNotificationMailer.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        AccountEmailChangeController controller = new AccountEmailChangeController(
                usersRepository,
                emailVerificationService,
                emailVerificationMailer,
                accountTotpService,
                accountSecurityService,
                accountEmailChangeNotificationMailer,
                notificationsService,
                auditLogWriter
        );

        setAuthenticatedEmail("zz_test_ec_unit_old_send_ok@example.invalid");
        when(emailVerificationMailer.isEnabled()).thenReturn(true);
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_unit_old_send_ok@example.invalid"))
                .thenReturn(Optional.of(user(100L, "zz_test_ec_unit_old_send_ok@example.invalid")));
        when(emailVerificationService.issueCode(100L, EmailVerificationPurpose.CHANGE_EMAIL_OLD, "zz_test_ec_unit_old_send_ok@example.invalid"))
                .thenReturn("111111");
        when(emailVerificationService.getDefaultResendWaitSeconds()).thenReturn(60);
        when(emailVerificationService.getDefaultTtlSeconds()).thenReturn(600);

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(servletRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("emailChange.passwordVerifiedAt")).thenReturn(LocalDateTime.now());

        var resp = controller.sendOldEmailCode(servletRequest);
        assertThat(resp.getStatusCodeValue()).isEqualTo(200);
        assertThat(resp.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) resp.getBody()).get("message")).isEqualTo("验证码已发送");
        verify(emailVerificationMailer).sendVerificationCode(
                "zz_test_ec_unit_old_send_ok@example.invalid",
                "111111",
                EmailVerificationPurpose.CHANGE_EMAIL_OLD
        );
    }

    @Test
    void verifyOld_shouldReturn400_whenEmailCodeEmpty() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer = mock(AccountEmailChangeNotificationMailer.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        AccountEmailChangeController controller = new AccountEmailChangeController(
                usersRepository,
                emailVerificationService,
                emailVerificationMailer,
                accountTotpService,
                accountSecurityService,
                accountEmailChangeNotificationMailer,
                notificationsService,
                auditLogWriter
        );

        setAuthenticatedEmail("zz_test_ec_unit_old_verify_email_empty@example.invalid");
        when(usersRepository.findByEmailAndIsDeletedFalse("zz_test_ec_unit_old_verify_email_empty@example.invalid"))
                .thenReturn(Optional.of(user(101L, "zz_test_ec_unit_old_verify_email_empty@example.invalid")));
        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        VerifyOldEmailRequest req = new VerifyOldEmailRequest();
        req.setMethod("email");
        req.setEmailCode("   ");

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(servletRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("emailChange.passwordVerifiedAt")).thenReturn(LocalDateTime.now());

        var resp = controller.verifyOld(req, servletRequest);
        assertThat(resp.getStatusCodeValue()).isEqualTo(400);
        assertThat(resp.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) resp.getBody()).get("message")).isEqualTo("请输入旧邮箱验证码");
    }

    @Test
    void safeMsg_and_maskEmail_shouldHandleNullInputs() throws Exception {
        Method safeMsg = AccountEmailChangeController.class.getDeclaredMethod("safeMsg", String.class);
        safeMsg.setAccessible(true);
        assertThat(safeMsg.invoke(null, new Object[]{null})).isNull();

        Method maskEmail = AccountEmailChangeController.class.getDeclaredMethod("maskEmail", String.class);
        maskEmail.setAccessible(true);
        assertThat(maskEmail.invoke(null, new Object[]{null})).isNull();
    }

    @Test
    void writeAuditSafely_shouldUseEmptyMap_whenDetailsNull() throws Exception {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationMailer emailVerificationMailer = mock(EmailVerificationMailer.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        AccountSecurityService accountSecurityService = mock(AccountSecurityService.class);
        AccountEmailChangeNotificationMailer accountEmailChangeNotificationMailer = mock(AccountEmailChangeNotificationMailer.class);
        NotificationsService notificationsService = mock(NotificationsService.class);
        AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);

        AccountEmailChangeController controller = new AccountEmailChangeController(
                usersRepository,
                emailVerificationService,
                emailVerificationMailer,
                accountTotpService,
                accountSecurityService,
                accountEmailChangeNotificationMailer,
                notificationsService,
                auditLogWriter
        );

        Method writeAuditSafely = AccountEmailChangeController.class.getDeclaredMethod(
                "writeAuditSafely",
                Long.class,
                String.class,
                String.class,
                com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult.class,
                String.class,
                Map.class
        );
        writeAuditSafely.setAccessible(true);

        ArgumentCaptor<Map<String, Object>> detailsCaptor = ArgumentCaptor.forClass(Map.class);

        writeAuditSafely.invoke(controller, 9L, "actor", "ACT", AuditResult.SUCCESS, "msg", null);

        verify(auditLogWriter).write(
                eq(9L),
                eq("actor"),
                eq("ACT"),
                eq("USER"),
                eq(9L),
                eq(AuditResult.SUCCESS),
                eq("msg"),
                eq(null),
                detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue()).isEmpty();
    }
}
