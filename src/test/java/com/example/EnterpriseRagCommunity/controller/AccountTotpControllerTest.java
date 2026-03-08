package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicyStatusDTO;
import com.example.EnterpriseRagCommunity.dto.access.TotpAdminSettingsDTO;
import com.example.EnterpriseRagCommunity.dto.access.request.TotpEnrollRequest;
import com.example.EnterpriseRagCommunity.dto.access.response.TotpEnrollResponse;
import com.example.EnterpriseRagCommunity.dto.access.response.TotpStatusResponse;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.service.access.TotpPolicyService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.notify.AccountSecurityNotificationMailer;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.MvcResult;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountTotpController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "u@example.invalid")
class AccountTotpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountTotpController accountTotpController;

    @MockitoBean
    private AccountTotpService accountTotpService;

    @MockitoBean
    private TotpPolicyService totpPolicyService;

    @MockitoBean
    private UsersRepository usersRepository;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private EmailVerificationMailer emailVerificationMailer;

    @MockitoBean
    private Security2faPolicyService security2faPolicyService;

    @MockitoBean
    private NotificationsService notificationsService;

    @MockitoBean
    private AccountSecurityNotificationMailer accountSecurityNotificationMailer;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AuditLogWriter auditLogWriter;

    private static UsersEntity userEntity(long id, String email, String passwordHash) {
        UsersEntity u = new UsersEntity();
        u.setId(id);
        u.setEmail(email);
        u.setPasswordHash(passwordHash);
        u.setIsDeleted(false);
        return u;
    }

    private static Security2faPolicyStatusDTO policy(boolean totpAllowed, boolean totpRequired, boolean emailOtpAllowed) {
        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setTotpAllowed(totpAllowed);
        p.setTotpRequired(totpRequired);
        p.setEmailOtpAllowed(emailOtpAllowed);
        return p;
    }

    private static MockHttpSession stepUpOkSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("admin.stepup.okUntilEpochMs", System.currentTimeMillis() + 600000L);
        return session;
    }

    @Test
    @WithAnonymousUser
    void policy_shouldReturn401_whenAnonymous() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/account/totp/policy"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    @WithAnonymousUser
    void status_shouldReturn401_whenAnonymous() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/account/totp/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    @WithAnonymousUser
    void verifyPassword_shouldReturn401_whenAnonymous() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(post("/api/account/totp/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"x\",\"action\":\"ENABLE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void enroll_shouldReturn403_whenTotpForbidden() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(false, false, true));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("admin.stepup.okUntilEpochMs", System.currentTimeMillis() + 600000L);

        mockMvc.perform(post("/api/account/totp/enroll")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("管理员已禁止启用 TOTP"));
    }

    @Test
    void enroll_shouldReturn403_whenEmailOtpForbidden() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, false));

        mockMvc.perform(post("/api/account/totp/enroll")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("管理员已禁止使用邮箱验证码，无法启用 TOTP"));
    }

    @Test
    void enroll_shouldReturn400_whenPasswordMissingAndNotVerified() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));

        mockMvc.perform(post("/api/account/totp/enroll")
                        .with(user("u@example.invalid")).session(stepUpOkSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先验证密码"));
    }

    @Test
    void enroll_shouldReturn400_whenPasswordIncorrect() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));
        when(passwordEncoder.matches(eq("wrong"), eq("hash"))).thenReturn(false);

        mockMvc.perform(post("/api/account/totp/enroll")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\",\"emailCode\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("密码不正确"));
    }

    @Test
    void enroll_shouldReturn400_whenEmailCodeMissing() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        mockMvc.perform(post("/api/account/totp/enroll")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入邮箱验证码"));
    }

    @Test
    void enroll_shouldReturn200_whenSuccess() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        TotpEnrollResponse resp = new TotpEnrollResponse();
        resp.setSecretBase32("S");
        resp.setOtpauthUri("otpauth://...");

        when(accountTotpService.enrollByEmail(eq("u@example.invalid"), any(TotpEnrollRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/account/totp/enroll")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\",\"emailCode\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secretBase32").value("S"));
    }

    @Test
    void verify_shouldReturn403_whenTotpForbidden() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(false, false, true));

        mockMvc.perform(post("/api/account/totp/verify")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\",\"password\":\"pass\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("管理员已禁止启用 TOTP"));
    }

    @Test
    void verify_shouldReturn400_whenNoPasswordAndNotVerified() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));

        mockMvc.perform(post("/api/account/totp/verify")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先验证密码"));
    }

    @Test
    void verify_shouldReturn200_whenNoPasswordButVerified() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));

        TotpStatusResponse resp = new TotpStatusResponse();
        resp.setEnabled(true);
        when(accountTotpService.verifyByEmailWithoutPassword("u@example.invalid", "123456")).thenReturn(resp);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("totp_enable_pwd_verified_at", System.currentTimeMillis());
        session.setAttribute("admin.stepup.okUntilEpochMs", System.currentTimeMillis() + 600000L);

        mockMvc.perform(post("/api/account/totp/verify")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void verify_shouldReturn200_whenWithPassword() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));

        TotpStatusResponse resp = new TotpStatusResponse();
        resp.setEnabled(false);
        when(accountTotpService.verifyByEmail("u@example.invalid", "pass", "123456")).thenReturn(resp);

        mockMvc.perform(post("/api/account/totp/verify")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\",\"password\":\"pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void disable_shouldReturn403_whenTotpRequired() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, true, true));

        mockMvc.perform(post("/api/account/totp/disable")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("管理员已强制启用 TOTP，无法关闭"));
    }

    @Test
    void disable_shouldReturn400_whenPasswordMissingAndNotVerified() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));

        mockMvc.perform(post("/api/account/totp/disable")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先验证密码"));
    }

    @Test
    void disable_shouldReturn400_whenPasswordIncorrect() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));
        when(passwordEncoder.matches(eq("wrong"), eq("hash"))).thenReturn(false);

        mockMvc.perform(post("/api/account/totp/disable")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("密码不正确"));
    }

    @Test
    void disable_shouldReturn403_whenEmailMethodButEmailOtpForbidden() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, false));

        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        mockMvc.perform(post("/api/account/totp/disable")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\",\"method\":\"email\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("管理员已禁止使用邮箱验证码"));
    }

    @Test
    void disable_shouldReturn400_whenEmailMethodButEmailCodeMissing() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        mockMvc.perform(post("/api/account/totp/disable")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\",\"method\":\"email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入邮箱验证码"));
    }

    @Test
    void disable_shouldReturn200_whenEmailMethodSuccess() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        TotpStatusResponse resp = new TotpStatusResponse();
        resp.setEnabled(false);
        when(accountTotpService.disableByEmailWithoutTotp("u@example.invalid")).thenReturn(resp);

        mockMvc.perform(post("/api/account/totp/disable")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\",\"method\":\"email\",\"emailCode\":\"999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void disable_shouldReturn400_whenTotpMethodButCodeMissing() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        mockMvc.perform(post("/api/account/totp/disable")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入验证码"));
    }

    @Test
    void disable_shouldReturn200_whenTotpMethodSuccess() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        TotpStatusResponse resp = new TotpStatusResponse();
        resp.setEnabled(false);
        when(accountTotpService.disableByEmail("u@example.invalid", "123456")).thenReturn(resp);

        mockMvc.perform(post("/api/account/totp/disable")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void verifyPassword_shouldReturn400_whenPasswordIncorrect() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("wrong"), eq("hash"))).thenReturn(false);

        mockMvc.perform(post("/api/account/totp/verify-password")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\",\"action\":\"ENABLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("密码不正确"));
    }

    @Test
    void verifyPassword_shouldReturn400_whenUnsupportedAction() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        mockMvc.perform(post("/api/account/totp/verify-password")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\",\"action\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("用途不支持"));
    }

    @Test
    void verifyPassword_shouldReturn200_whenEnable() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        mockMvc.perform(post("/api/account/totp/verify-password")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\",\"action\":\"ENABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("密码验证通过"));
    }

    @Test
    void verifyPassword_shouldReturn200_whenDisable() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        mockMvc.perform(post("/api/account/totp/verify-password")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\",\"action\":\"DISABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("密码验证通过"));
    }

    @Test
    void policy_shouldReturn200_whenAuthenticated() throws Exception {
        TotpAdminSettingsDTO dto = new TotpAdminSettingsDTO();
        dto.setIssuer("issuer-x");
        when(totpPolicyService.getSettingsOrDefault()).thenReturn(dto);

        mockMvc.perform(get("/api/account/totp/policy").with(user("u@example.invalid")).session(stepUpOkSession()))
                .andExpect(status().isOk())
            .andExpect(jsonPath("$.issuer").value("issuer-x"));
    }

    @Test
    void status_shouldReturn200_whenAuthenticated() throws Exception {
        TotpStatusResponse resp = new TotpStatusResponse();
        resp.setEnabled(false);
        when(accountTotpService.getStatusByEmail("u@example.invalid")).thenReturn(resp);

        mockMvc.perform(get("/api/account/totp/status").with(user("u@example.invalid")).session(stepUpOkSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void enroll_shouldVerifyAndConsumeEmailCode() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        TotpEnrollResponse resp = new TotpEnrollResponse();
        resp.setSecretBase32("S");
        when(accountTotpService.enrollByEmail(eq("u@example.invalid"), any(TotpEnrollRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/account/totp/enroll")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\",\"emailCode\":\"123456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void disable_shouldVerifyAndConsumeEmailCode_whenMethodEmail() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        TotpStatusResponse resp = new TotpStatusResponse();
        resp.setEnabled(false);
        when(accountTotpService.disableByEmailWithoutTotp("u@example.invalid")).thenReturn(resp);

        mockMvc.perform(post("/api/account/totp/disable")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\",\"method\":\"email\",\"emailCode\":\"999\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void policy_shouldReturn401_whenPrincipalIsAnonymousUser() throws Exception {
        TestingAuthenticationToken token = new TestingAuthenticationToken("anonymousUser", "n/a");
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);
        mockMvc.perform(get("/api/account/totp/policy"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void policy_shouldReturn401_whenNotAuthenticated() throws Exception {
        TestingAuthenticationToken token = new TestingAuthenticationToken("u@example.invalid", "n/a");
        token.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(token);
        mockMvc.perform(get("/api/account/totp/policy"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void enroll_shouldReturn400_whenPasswordIsNullAndNotVerified() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));

        mockMvc.perform(post("/api/account/totp/enroll")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":null,\"emailCode\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先验证密码"));
    }

    @Test
    void enroll_shouldReturn200_whenSessionAlreadyVerifiedAndPasswordOmitted() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));

        TotpEnrollResponse resp = new TotpEnrollResponse();
        resp.setSecretBase32("S");
        when(accountTotpService.enrollByEmail(eq("u@example.invalid"), any(TotpEnrollRequest.class))).thenReturn(resp);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("totp_enable_pwd_verified_at", System.currentTimeMillis());
        session.setAttribute("admin.stepup.okUntilEpochMs", System.currentTimeMillis() + 600000L);

        mockMvc.perform(post("/api/account/totp/enroll")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailCode\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secretBase32").value("S"));
    }

    @Test
    void verify_shouldReturn400_whenVerifiedSessionAttrIsNotLong() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("totp_enable_pwd_verified_at", "not-a-long");
        session.setAttribute("admin.stepup.okUntilEpochMs", System.currentTimeMillis() + 600000L);

        mockMvc.perform(post("/api/account/totp/verify")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先验证密码"));
    }

    @Test
    void verify_shouldReturn400_whenVerifiedSessionAttrExpired() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("totp_enable_pwd_verified_at", System.currentTimeMillis() - 5 * 60 * 1000L - 1);
        session.setAttribute("admin.stepup.okUntilEpochMs", System.currentTimeMillis() + 600000L);

        mockMvc.perform(post("/api/account/totp/verify")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先验证密码"));
    }

    @Test
    void verify_shouldReturn200_whenServiceReturnsNullAndSessionVerified() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));
        when(accountTotpService.verifyByEmailWithoutPassword("u@example.invalid", "123456")).thenReturn(null);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("totp_enable_pwd_verified_at", System.currentTimeMillis());
        session.setAttribute("admin.stepup.okUntilEpochMs", System.currentTimeMillis() + 600000L);

        mockMvc.perform(post("/api/account/totp/verify")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void verify_shouldReturn200_whenEnabledTrueButNotificationAndMailerFail() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));

        TotpStatusResponse resp = new TotpStatusResponse();
        resp.setEnabled(true);
        when(accountTotpService.verifyByEmailWithoutPassword("u@example.invalid", "123456")).thenReturn(resp);
        doThrow(new RuntimeException("x")).when(notificationsService).createNotification(eq(1L), any(), any(), any());
        doThrow(new RuntimeException("x")).when(accountSecurityNotificationMailer).sendTotpEnabled(eq("u@example.invalid"));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("totp_enable_pwd_verified_at", System.currentTimeMillis());
        session.setAttribute("admin.stepup.okUntilEpochMs", System.currentTimeMillis() + 600000L);

        mockMvc.perform(post("/api/account/totp/verify")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void disable_shouldReturn200_whenMethodIsUppercaseTotp() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        TotpStatusResponse resp = new TotpStatusResponse();
        resp.setEnabled(false);
        when(accountTotpService.disableByEmail("u@example.invalid", "123456")).thenReturn(resp);

        mockMvc.perform(post("/api/account/totp/disable")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\",\"method\":\"TOTP\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void disable_shouldReturn200_whenServiceReturnsNull() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);
        when(accountTotpService.disableByEmail("u@example.invalid", "123456")).thenReturn(null);

        mockMvc.perform(post("/api/account/totp/disable")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\",\"method\":\"totp\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void disable_shouldNotClearSession_whenRespEnabledIsTrue() throws Exception {
        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(security2faPolicyService.evaluateForUser(1L)).thenReturn(policy(true, false, true));

        TotpStatusResponse resp = new TotpStatusResponse();
        resp.setEnabled(true);
        when(accountTotpService.disableByEmail("u@example.invalid", "123456")).thenReturn(resp);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("totp_disable_pwd_verified_at", System.currentTimeMillis());
        session.setAttribute("admin.stepup.okUntilEpochMs", System.currentTimeMillis() + 600000L);

        MvcResult r = mockMvc.perform(post("/api/account/totp/disable")
                        .with(user("u@example.invalid")).session(stepUpOkSession())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"totp\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn();

        assertNotNull(r.getRequest().getSession(false));
        assertNotNull(r.getRequest().getSession(false).getAttribute("totp_disable_pwd_verified_at"));
    }

    @Test
    void verifyPassword_directCall_shouldReturn400_whenPasswordBlankAfterTrim() {
        TestingAuthenticationToken token = new TestingAuthenticationToken("u@example.invalid", "n/a");
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);

        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));

        var req = new com.example.EnterpriseRagCommunity.dto.access.request.TotpPasswordVerifyRequest();
        req.setPassword("   ");
        req.setAction("ENABLE");

        var servletRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        ResponseEntity<?> resp = accountTotpController.verifyPassword(req, servletRequest);

        assertEquals(400, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
    }

    @Test
    void verifyPassword_directCall_shouldReturn400_whenActionNull() {
        TestingAuthenticationToken token = new TestingAuthenticationToken("u@example.invalid", "n/a");
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);

        UsersEntity u = userEntity(1L, "u@example.invalid", "hash");
        when(usersRepository.findByEmailAndIsDeletedFalse("u@example.invalid")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("pass"), eq("hash"))).thenReturn(true);

        var req = new com.example.EnterpriseRagCommunity.dto.access.request.TotpPasswordVerifyRequest();
        req.setPassword("pass");
        req.setAction(null);

        var session = new MockHttpSession();
        var servletRequest = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(servletRequest.getSession(true)).thenReturn(session);

        ResponseEntity<?> resp = accountTotpController.verifyPassword(req, servletRequest);
        assertEquals(400, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
    }

    @Test
    void helpers_reflection_shouldCoverDetailsNullAndClearSessionNull() throws Exception {
        Method writeAuditSafely = AccountTotpController.class.getDeclaredMethod(
                "writeAuditSafely",
                Long.class,
                String.class,
                String.class,
                AuditResult.class,
                String.class,
                Map.class
        );
        writeAuditSafely.setAccessible(true);
        writeAuditSafely.invoke(
                accountTotpController,
                1L,
                "u@example.invalid",
                "ACCOUNT_TOTP_TEST",
                AuditResult.SUCCESS,
                "x",
                null
        );

        Method clearPasswordVerified = AccountTotpController.class.getDeclaredMethod("clearPasswordVerified", jakarta.servlet.http.HttpServletRequest.class, String.class);
        clearPasswordVerified.setAccessible(true);
        var servletRequest = org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletRequest.class);
        when(servletRequest.getSession(false)).thenReturn(null);
        clearPasswordVerified.invoke(null, servletRequest, "totp_enable_pwd_verified_at");

        assertNull(servletRequest.getSession(false));
    }
}
