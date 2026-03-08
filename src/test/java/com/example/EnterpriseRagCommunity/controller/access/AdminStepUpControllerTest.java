package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicyStatusDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.security.stepup.AdminStepUpInterceptor;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.testsupport.SecurityContextTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminStepUpControllerTest {

    @AfterEach
    void tearDown() {
        SecurityContextTestSupport.clear();
    }

    @Test
    void status_should_include_methods_and_ok_flag_based_on_session_and_policy() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        Security2faPolicyService security2faPolicyService = mock(Security2faPolicyService.class);

        UsersEntity user = new UsersEntity();
        user.setId(10L);
        user.setEmail("u@example.com");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO policy = new Security2faPolicyStatusDTO();
        policy.setEmailOtpAllowed(true);
        when(security2faPolicyService.evaluateForUser(10L)).thenReturn(policy);
        when(accountTotpService.isEnabledByEmail("u@example.com")).thenReturn(true);

        AdminStepUpController controller = new AdminStepUpController(
                usersRepository,
                emailVerificationService,
                accountTotpService,
                security2faPolicyService
        );

        SecurityContextTestSupport.setAuthenticatedEmail("u@example.com");
        MockHttpSession session = new MockHttpSession();
        long okUntil = Instant.now().plusSeconds(1).toEpochMilli();
        session.setAttribute(AdminStepUpInterceptor.SESSION_KEY_OK_UNTIL_EPOCH_MS, okUntil);

        Map<?, ?> body = (Map<?, ?>) controller.status(session).getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("ok")).isEqualTo(true);
        assertThat(body.get("okUntilEpochMs")).isEqualTo(okUntil);
        @SuppressWarnings("unchecked")
        List<String> methods = (List<String>) body.get("methods");
        assertThat(methods).containsExactly("email", "totp");
        assertThat(body.get("emailOtpAllowed")).isEqualTo(true);
    }

    @Test
    void status_should_handle_okUntil_as_string_and_invalid_string() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        Security2faPolicyService security2faPolicyService = mock(Security2faPolicyService.class);

        UsersEntity user = new UsersEntity();
        user.setId(10L);
        user.setEmail("u@example.com");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO policy = new Security2faPolicyStatusDTO();
        policy.setEmailOtpAllowed(false);
        when(security2faPolicyService.evaluateForUser(10L)).thenReturn(policy);
        when(accountTotpService.isEnabledByEmail("u@example.com")).thenReturn(false);

        AdminStepUpController controller = new AdminStepUpController(
                usersRepository,
                emailVerificationService,
                accountTotpService,
                security2faPolicyService
        );

        SecurityContextTestSupport.setAuthenticatedEmail("u@example.com");

        MockHttpSession s1 = new MockHttpSession();
        long okUntil = Instant.now().plusSeconds(1).toEpochMilli();
        s1.setAttribute(AdminStepUpInterceptor.SESSION_KEY_OK_UNTIL_EPOCH_MS, String.valueOf(okUntil));
        Map<?, ?> b1 = (Map<?, ?>) controller.status(s1).getBody();
        assertThat(b1.get("ok")).isEqualTo(true);
        assertThat(b1.get("okUntilEpochMs")).isEqualTo(okUntil);
        assertThat((List<?>) b1.get("methods")).isEmpty();

        MockHttpSession s2 = new MockHttpSession();
        s2.setAttribute(AdminStepUpInterceptor.SESSION_KEY_OK_UNTIL_EPOCH_MS, "bad");
        Map<?, ?> b2 = (Map<?, ?>) controller.status(s2).getBody();
        assertThat(b2.get("ok")).isEqualTo(false);
        assertThat(b2.get("okUntilEpochMs")).isEqualTo(0L);
    }

    @Test
    void verify_should_return_403_when_email_method_disallowed() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        Security2faPolicyService security2faPolicyService = mock(Security2faPolicyService.class);

        UsersEntity user = new UsersEntity();
        user.setId(10L);
        user.setEmail("u@example.com");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO policy = new Security2faPolicyStatusDTO();
        policy.setEmailOtpAllowed(false);
        when(security2faPolicyService.evaluateForUser(10L)).thenReturn(policy);

        AdminStepUpController controller = new AdminStepUpController(
                usersRepository,
                emailVerificationService,
                accountTotpService,
                security2faPolicyService
        );

        SecurityContextTestSupport.setAuthenticatedEmail("u@example.com");

        AdminStepUpController.VerifyRequest req = new AdminStepUpController.VerifyRequest();
        req.setMethod("email");
        req.setCode("123456");

        MockHttpSession session = new MockHttpSession();
        var res = controller.verify(req, session);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
        verify(emailVerificationService, never()).verifyAndConsume(any(), any(), any());
        verify(accountTotpService, never()).requireValidEnabledCodeByEmail(any(), any());
    }

    @Test
    void verify_should_support_email_and_totp_and_write_okUntil() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        Security2faPolicyService security2faPolicyService = mock(Security2faPolicyService.class);

        UsersEntity user = new UsersEntity();
        user.setId(10L);
        user.setEmail("u@example.com");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO policy = new Security2faPolicyStatusDTO();
        policy.setEmailOtpAllowed(true);
        when(security2faPolicyService.evaluateForUser(10L)).thenReturn(policy);

        AdminStepUpController controller = new AdminStepUpController(
                usersRepository,
                emailVerificationService,
                accountTotpService,
                security2faPolicyService
        );

        SecurityContextTestSupport.setAuthenticatedEmail("u@example.com");

        MockHttpSession s1 = new MockHttpSession();
        AdminStepUpController.VerifyRequest r1 = new AdminStepUpController.VerifyRequest();
        r1.setMethod(" EMAIL ");
        r1.setCode("123456");
        var res1 = controller.verify(r1, s1);
        assertThat(res1.getStatusCode().value()).isEqualTo(200);
        Object okUntil1 = s1.getAttribute(AdminStepUpInterceptor.SESSION_KEY_OK_UNTIL_EPOCH_MS);
        assertThat(okUntil1).isInstanceOf(Long.class);
        verify(emailVerificationService).verifyAndConsume(eq(10L), eq(EmailVerificationPurpose.ADMIN_STEP_UP), eq("123456"));

        MockHttpSession s2 = new MockHttpSession();
        AdminStepUpController.VerifyRequest r2 = new AdminStepUpController.VerifyRequest();
        r2.setMethod("totp");
        r2.setCode("654321");
        var res2 = controller.verify(r2, s2);
        assertThat(res2.getStatusCode().value()).isEqualTo(200);
        verify(accountTotpService).requireValidEnabledCodeByEmail(eq("u@example.com"), eq("654321"));
    }

    @Test
    void verify_should_return_400_for_unknown_method() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        Security2faPolicyService security2faPolicyService = mock(Security2faPolicyService.class);

        UsersEntity user = new UsersEntity();
        user.setId(10L);
        user.setEmail("u@example.com");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.com")).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO policy = new Security2faPolicyStatusDTO();
        policy.setEmailOtpAllowed(true);
        when(security2faPolicyService.evaluateForUser(10L)).thenReturn(policy);

        AdminStepUpController controller = new AdminStepUpController(
                usersRepository,
                emailVerificationService,
                accountTotpService,
                security2faPolicyService
        );

        SecurityContextTestSupport.setAuthenticatedEmail("u@example.com");

        AdminStepUpController.VerifyRequest req = new AdminStepUpController.VerifyRequest();
        req.setMethod("sms");
        req.setCode("123");
        MockHttpSession session = new MockHttpSession();
        var res = controller.verify(req, session);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void currentUserOrThrow_should_fail_when_not_logged_in() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        Security2faPolicyService security2faPolicyService = mock(Security2faPolicyService.class);

        AdminStepUpController controller = new AdminStepUpController(
                usersRepository,
                emailVerificationService,
                accountTotpService,
                security2faPolicyService
        );

        assertThatThrownBy(() -> controller.status(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("未登录或会话已过期");
    }

    @Test
    void clear_should_remove_session_attribute() {
        UsersRepository usersRepository = mock(UsersRepository.class);
        EmailVerificationService emailVerificationService = mock(EmailVerificationService.class);
        AccountTotpService accountTotpService = mock(AccountTotpService.class);
        Security2faPolicyService security2faPolicyService = mock(Security2faPolicyService.class);

        AdminStepUpController controller = new AdminStepUpController(
                usersRepository,
                emailVerificationService,
                accountTotpService,
                security2faPolicyService
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminStepUpInterceptor.SESSION_KEY_OK_UNTIL_EPOCH_MS, 123L);
        Map<?, ?> body = (Map<?, ?>) controller.clear(session).getBody();
        assertThat(body.get("ok")).isEqualTo(true);
        assertThat(session.getAttribute(AdminStepUpInterceptor.SESSION_KEY_OK_UNTIL_EPOCH_MS)).isNull();
    }
}

