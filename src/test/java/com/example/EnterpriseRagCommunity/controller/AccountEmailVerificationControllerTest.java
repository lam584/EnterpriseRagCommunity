package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicyStatusDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountEmailVerificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "u@example.invalid")
class AccountEmailVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsersRepository usersRepository;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private EmailVerificationMailer emailVerificationMailer;

    @MockitoBean
    private NotificationsService notificationsService;

    @MockitoBean
    private Security2faPolicyService security2faPolicyService;

    @Test
    void send_shouldReturn401_whenAnonymous() throws Exception {
        when(emailVerificationMailer.isEnabled()).thenReturn(true);
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/api/account/email-verification/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"CHANGE_PASSWORD\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void send_shouldReturn400_whenPurposeInvalidEnum() throws Exception {
        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        mockMvc.perform(post("/api/account/email-verification/send")
                        .with(user("u@example.invalid"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"NOT_A_PURPOSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用途不合法"));
    }

    @Test
    void send_shouldReturn400_whenPurposeNotSupported() throws Exception {
        when(emailVerificationMailer.isEnabled()).thenReturn(true);

        mockMvc.perform(post("/api/account/email-verification/send")
                        .with(user("u@example.invalid"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不支持的用途"));
    }

    @Test
    void send_shouldReturn400_whenMailerDisabled() throws Exception {
        when(emailVerificationMailer.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/account/email-verification/send")
                        .with(user("u@example.invalid"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"CHANGE_PASSWORD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("邮箱服务未启用"));
    }

    @Test
    void send_shouldReturn403_whenPolicyForbidsEmailOtp() throws Exception {
        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setEmail("u@example.invalid");

        when(emailVerificationMailer.isEnabled()).thenReturn(true);
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));

        Security2faPolicyStatusDTO policy = new Security2faPolicyStatusDTO();
        policy.setEmailOtpAllowed(false);
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy);

        mockMvc.perform(post("/api/account/email-verification/send")
                        .with(user("u@example.invalid"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"CHANGE_PASSWORD\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("管理员已禁止使用邮箱验证码"));
    }

    @ParameterizedTest
    @EnumSource(value = EmailVerificationPurpose.class, names = {
            "CHANGE_PASSWORD",
            "LOGIN_2FA_PREFERENCE",
            "ADMIN_STEP_UP",
            "TOTP_ENABLE",
            "TOTP_DISABLE"
    })
    void send_shouldReturn200_forSupportedPurposes(EmailVerificationPurpose purpose) throws Exception {
        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setEmail("u@example.invalid");

        when(emailVerificationMailer.isEnabled()).thenReturn(true);
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));

        Security2faPolicyStatusDTO policy = new Security2faPolicyStatusDTO();
        policy.setEmailOtpAllowed(true);
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy);

        when(emailVerificationService.issueCode(1L, purpose)).thenReturn("123456");
        when(emailVerificationService.getDefaultResendWaitSeconds()).thenReturn(60);
        when(emailVerificationService.getDefaultTtlSeconds()).thenReturn(300);

        mockMvc.perform(post("/api/account/email-verification/send")
                        .with(user("u@example.invalid"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"" + purpose.name() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("验证码已发送"))
                .andExpect(jsonPath("$.resendWaitSeconds").value(60))
                .andExpect(jsonPath("$.codeTtlSeconds").value(300));
    }

    @Test
    void parsePurpose_shouldThrow_whenEmpty() throws Exception {
        Method m = AccountEmailVerificationController.class.getDeclaredMethod("parsePurpose", String.class);
        m.setAccessible(true);

        assertThatThrownBy(() -> {
            try {
                m.invoke(null, "   ");
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用途不能为空");
    }

    @Test
    void parsePurpose_shouldReturnEnum_whenOk() throws Exception {
        Method m = AccountEmailVerificationController.class.getDeclaredMethod("parsePurpose", String.class);
        m.setAccessible(true);

        Object out = m.invoke(null, "change_password");

        assertThat(out).isEqualTo(EmailVerificationPurpose.CHANGE_PASSWORD);
    }
}
