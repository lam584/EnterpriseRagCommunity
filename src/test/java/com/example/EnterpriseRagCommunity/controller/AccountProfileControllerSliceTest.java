package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.access.Security2faPolicyStatusDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.entity.moderation.ModerationQueueEntity;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ModerationCaseType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationActionsRepository;
import com.example.EnterpriseRagCommunity.repository.moderation.ModerationQueueRepository;
import com.example.EnterpriseRagCommunity.service.AccountSecurityService;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.AuditDiffBuilder;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationAutoKickService;
import com.example.EnterpriseRagCommunity.service.monitor.NotificationsService;
import com.example.EnterpriseRagCommunity.service.notify.AccountSecurityNotificationMailer;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;
import com.example.EnterpriseRagCommunity.testsupport.SecurityContextTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountProfileControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsersRepository usersRepository;

    @MockitoBean
    private AccountSecurityService accountSecurityService;

    @MockitoBean
    private AccountTotpService accountTotpService;

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
    private AuditLogWriter auditLogWriter;

    @MockitoBean
    private AuditDiffBuilder auditDiffBuilder;

    @MockitoBean
    private ModerationQueueRepository moderationQueueRepository;

    @MockitoBean
    private ModerationActionsRepository moderationActionsRepository;

    @MockitoBean
    private ModerationAutoKickService moderationAutoKickService;

    @MockitoBean
    private com.example.EnterpriseRagCommunity.service.moderation.jobs.ModerationRuleAutoRunner moderationRuleAutoRunner;

    @MockitoBean
    private com.example.EnterpriseRagCommunity.security.ClientIpResolver clientIpResolver;

    @AfterEach
    void clearSecurity() {
        SecurityContextTestSupport.clear();
    }

    @Test
    void getMyProfileView_shouldUnauthorized_whenAuthMissing() throws Exception {
        mockMvc.perform(get("/api/account/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void getMyProfileView_shouldUnauthorized_whenAuthNotAuthenticated() throws Exception {
        var auth = SecurityContextTestSupport.setAuthenticatedEmail("u@example.invalid");
        if (auth instanceof org.springframework.security.authentication.TestingAuthenticationToken t) {
            t.setAuthenticated(false);
        }

        mockMvc.perform(get("/api/account/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyProfileView_shouldUnauthorized_whenAnonymousPrincipal() throws Exception {
        SecurityContextTestSupport.setAuthenticatedEmail("anonymousUser");

        mockMvc.perform(get("/api/account/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyProfileView_shouldOk_whenNoModerationQueue() throws Exception {
        String email = "u@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(1L, email, "u", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.PROFILE, 1L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/account/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.metadata.profileModeration").doesNotExist());
    }

    @Test
    void getMyProfileView_shouldIncludeModerationMetadata_whenQueueExists_withNullFields() throws Exception {
        String email = "u2@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(2L, email, "u2", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(9L);
        q.setStatus(null);
        q.setCurrentStage(null);
        q.setUpdatedAt(null);
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.PROFILE, 2L))
                .thenReturn(Optional.of(q));

        mockMvc.perform(get("/api/account/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.profileModeration.queueId").value(9))
                .andExpect(jsonPath("$.metadata.profileModeration.status").value(nullValue()))
                .andExpect(jsonPath("$.metadata.profileModeration.stage").value(nullValue()))
                .andExpect(jsonPath("$.metadata.profileModeration.updatedAt").value(nullValue()));
    }

    @Test
    void getMyProfileView_shouldIncludeModerationMetadata_whenQueueExists_withNonNullFields() throws Exception {
        String email = "u3@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(3L, email, "u3", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(11L);
        q.setStatus(QueueStatus.PENDING);
        q.setCurrentStage(QueueStage.RULE);
        q.setUpdatedAt(LocalDateTime.parse("2026-01-01T01:02:03"));
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.PROFILE, 3L))
                .thenReturn(Optional.of(q));

        mockMvc.perform(get("/api/account/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.profileModeration.queueId").value(11))
                .andExpect(jsonPath("$.metadata.profileModeration.status").value("PENDING"))
                .andExpect(jsonPath("$.metadata.profileModeration.stage").value("RULE"))
                .andExpect(jsonPath("$.metadata.profileModeration.updatedAt").value("2026-01-01T01:02:03"));
    }

    @Test
    void getMySecurity2faPolicy_shouldUnauthorized_whenAuthMissing() throws Exception {
        mockMvc.perform(get("/api/account/security-2fa-policy"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void getMySecurity2faPolicy_shouldOk_whenAuthorized() throws Exception {
        String email = "u4@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(4L, email, "u4", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setLogin2faAllowed(true);
        p.setLogin2faCanEnable(true);
        when(security2faPolicyService.evaluateForUser(4L)).thenReturn(p);

        mockMvc.perform(get("/api/account/security-2fa-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login2faAllowed").value(true))
                .andExpect(jsonPath("$.login2faCanEnable").value(true));
    }

    @Test
    void verifyLogin2faPreferencePassword_shouldUnauthorized_whenAuthMissing() throws Exception {
        mockMvc.perform(post("/api/account/login-2fa-preference/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void verifyLogin2faPreferencePassword_shouldOk_whenPasswordOk_andNotificationThrows() throws Exception {
        String email = "u5@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(5L, email, "u5", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("boom")).when(notificationsService).createNotification(anyLong(), any(), any(), any());

        mockMvc.perform(post("/api/account/login-2fa-preference/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("密码验证通过"));

        verify(auditLogWriter).write(eq(5L), eq(email), eq("ACCOUNT_LOGIN_2FA_PREFERENCE_VERIFY_PASSWORD"), eq("USER"), eq(5L),
                eq(AuditResult.SUCCESS), any(), any(), any());
    }

    @Test
    void verifyLogin2faPreferencePassword_shouldBadRequest_whenPasswordInvalid_andUserPresent() throws Exception {
        String email = "u6@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        doThrow(new IllegalArgumentException("bad")).when(accountSecurityService).verifyPasswordByEmail(eq(email), any());
        UsersEntity user = user(6L, email, "u6", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/account/login-2fa-preference/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad"));
    }

    @Test
    void verifyLogin2faPreferencePassword_shouldBadRequest_whenPasswordInvalid_andUserMissing() throws Exception {
        String email = "u7@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        doThrow(new IllegalArgumentException("bad")).when(accountSecurityService).verifyPasswordByEmail(eq(email), any());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/account/login-2fa-preference/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad"));
    }

    @Test
    void verifyLogin2faPreferencePassword_shouldBadRequest_whenUserMissingAfterPasswordOk() throws Exception {
        String email = "u7b@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/account/login-2fa-preference/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void updateMyLogin2faPreference_shouldUnauthorized_whenAuthMissing() throws Exception {
        mockMvc.perform(put("/api/account/login-2fa-preference")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"method\":\"totp\",\"totpCode\":\"123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void updateMyLogin2faPreference_shouldBadRequest_whenPasswordVerifiedExpired() throws Exception {
        String email = "u9b@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(91L, email, "u9b", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setLogin2faCanEnable(true);
        when(security2faPolicyService.evaluateForUser(91L)).thenReturn(p);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("login2faPref.passwordVerifiedAt", System.currentTimeMillis() - 11 * 60 * 1000L);

        mockMvc.perform(put("/api/account/login-2fa-preference")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"method\":\"totp\",\"totpCode\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先验证密码"));
    }

    @Test
    void updateMyLogin2faPreference_shouldForbidden_whenPolicyDisallow() throws Exception {
        String email = "u8@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(8L, email, "u8", Map.of("metadata", "not_map"));
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(usersRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setLogin2faCanEnable(false);
        when(security2faPolicyService.evaluateForUser(8L)).thenReturn(p);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("login2faPref.passwordVerifiedAt", System.currentTimeMillis());

        mockMvc.perform(put("/api/account/login-2fa-preference")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"method\":\"totp\",\"totpCode\":\"123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("当前策略不允许你配置登录二次验证"));

        verify(auditLogWriter).write(eq(8L), eq(email), eq("ACCOUNT_LOGIN_2FA_PREFERENCE_UPDATE"), eq("USER"), eq(8L),
                eq(AuditResult.FAIL), any(), any(), any());
    }

    @Test
    void updateMyLogin2faPreference_shouldBadRequest_whenPasswordNotVerified() throws Exception {
        String email = "u9@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(9L, email, "u9", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setLogin2faCanEnable(true);
        when(security2faPolicyService.evaluateForUser(9L)).thenReturn(p);

        mockMvc.perform(put("/api/account/login-2fa-preference")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"method\":\"totp\",\"totpCode\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请先验证密码"));
    }

    @Test
    void updateMyLogin2faPreference_shouldBadRequest_whenMethodInvalid() throws Exception {
        String email = "u10@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(10L, email, "u10", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setLogin2faCanEnable(true);
        when(security2faPolicyService.evaluateForUser(10L)).thenReturn(p);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("login2faPref.passwordVerifiedAt", System.currentTimeMillis());

        mockMvc.perform(put("/api/account/login-2fa-preference")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"method\":\"bad\",\"totpCode\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("验证方式不合法"));
    }

    @Test
    void updateMyLogin2faPreference_shouldForbidden_whenTotpDisallowed() throws Exception {
        String email = "u11@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(11L, email, "u11", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setLogin2faCanEnable(true);
        p.setTotpAllowed(false);
        when(security2faPolicyService.evaluateForUser(11L)).thenReturn(p);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("login2faPref.passwordVerifiedAt", System.currentTimeMillis());

        mockMvc.perform(put("/api/account/login-2fa-preference")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"method\":\"totp\",\"totpCode\":\"123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("管理员已禁止使用动态验证码"));
    }

    @Test
    void updateMyLogin2faPreference_shouldBadRequest_whenTotpNotEnabled() throws Exception {
        String email = "u12@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(12L, email, "u12", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setLogin2faCanEnable(true);
        p.setTotpAllowed(true);
        when(security2faPolicyService.evaluateForUser(12L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(false);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("login2faPref.passwordVerifiedAt", System.currentTimeMillis());

        mockMvc.perform(put("/api/account/login-2fa-preference")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"method\":\"totp\",\"totpCode\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前账号未启用 TOTP"));
    }

    @Test
    void updateMyLogin2faPreference_shouldBadRequest_whenTotpCodeMissing() throws Exception {
        String email = "u13@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(13L, email, "u13", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setLogin2faCanEnable(true);
        p.setTotpAllowed(true);
        when(security2faPolicyService.evaluateForUser(13L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(true);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("login2faPref.passwordVerifiedAt", System.currentTimeMillis());

        mockMvc.perform(put("/api/account/login-2fa-preference")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"method\":\"totp\",\"totpCode\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入动态验证码"));
    }

    @Test
    void updateMyLogin2faPreference_shouldOk_whenTotpValid_andMetadataTypesInvalid() throws Exception {
        String email = "u14@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        Map<String, Object> md = new LinkedHashMap<>();
        md.put("preferences", "not_map");
        UsersEntity user = user(14L, email, "u14", md);
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(usersRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of("changes", Map.of()));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setLogin2faCanEnable(true);
        p.setTotpAllowed(true);
        when(security2faPolicyService.evaluateForUser(14L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(true);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("login2faPref.passwordVerifiedAt", System.currentTimeMillis());

        mockMvc.perform(put("/api/account/login-2fa-preference")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"method\":\"totp\",\"totpCode\":\"123\"}"))
                .andExpect(status().isOk());

        verify(accountTotpService).requireValidEnabledCodeByEmail(email, "123");
        verify(usersRepository, times(1)).save(any());
    }

    @Test
    void updateMyLogin2faPreference_shouldForbidden_whenEmailOtpDisallowed() throws Exception {
        String email = "u15@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(15L, email, "u15", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setLogin2faCanEnable(true);
        p.setEmailOtpAllowed(false);
        when(security2faPolicyService.evaluateForUser(15L)).thenReturn(p);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("login2faPref.passwordVerifiedAt", System.currentTimeMillis());

        mockMvc.perform(put("/api/account/login-2fa-preference")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"method\":\"email\",\"emailCode\":\"abc\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("管理员已禁止使用邮箱验证码"));
    }

    @Test
    void updateMyLogin2faPreference_shouldBadRequest_whenEmailCodeMissing() throws Exception {
        String email = "u16@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(16L, email, "u16", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setLogin2faCanEnable(true);
        p.setEmailOtpAllowed(true);
        when(security2faPolicyService.evaluateForUser(16L)).thenReturn(p);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("login2faPref.passwordVerifiedAt", System.currentTimeMillis());

        mockMvc.perform(put("/api/account/login-2fa-preference")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"method\":\"email\",\"emailCode\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入邮箱验证码"));
    }

    @Test
    void updateMyLogin2faPreference_shouldOk_whenEmailValid_andClearsSession() throws Exception {
        String email = "u17@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        Map<String, Object> md = new LinkedHashMap<>();
        md.put("preferences", Map.of("security", Map.of("login2faEnabled", false)));
        UsersEntity user = user(17L, email, "u17", md);
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(usersRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setLogin2faCanEnable(true);
        p.setEmailOtpAllowed(true);
        when(security2faPolicyService.evaluateForUser(17L)).thenReturn(p);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("login2faPref.passwordVerifiedAt", System.currentTimeMillis());

        mockMvc.perform(put("/api/account/login-2fa-preference")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"method\":\"email\",\"emailCode\":\"abc\"}"))
                .andExpect(status().isOk());

        verify(emailVerificationService).verifyAndConsume(17L, com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose.LOGIN_2FA_PREFERENCE, "abc");
        verify(usersRepository).save(any());
    }

    @Test
    void updateMyProfile_shouldUnauthorized_whenAuthMissing() throws Exception {
        mockMvc.perform(put("/api/account/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    void updateMyProfile_shouldBadRequest_whenUsernameBlank() throws Exception {
        String email = "p1@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(31L, email, "p1", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        mockMvc.perform(put("/api/account/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("昵称不能为空"));
    }

    @Test
    void updateMyProfile_shouldOk_whenCreatesQueue_andSnapshotSaveThrows() throws Exception {
        String email = "p2@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put(null, "x");
        profile.put("avatarUrl", "https://example.invalid/a.png");

        Map<String, Object> md = new LinkedHashMap<>();
        md.put("profile", profile);

        UsersEntity user = user(32L, email, "p2", md);
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(usersRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.PROFILE, 32L))
                .thenReturn(Optional.empty());
        when(moderationQueueRepository.save(any())).thenAnswer(inv -> {
            ModerationQueueEntity q = inv.getArgument(0);
            q.setId(99L);
            return q;
        });
        doThrow(new RuntimeException("boom")).when(moderationActionsRepository).save(any());

        mockMvc.perform(put("/api/account/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"p2-new\",\"avatarUrl\":\"\",\"bio\":\"b\",\"location\":\"l\",\"website\":\"https://example.invalid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(32))
                .andExpect(jsonPath("$.metadata.profilePending.username").value("p2-new"))
                .andExpect(jsonPath("$.metadata.profilePending.avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.metadata.profilePending.bio").value("b"))
                .andExpect(jsonPath("$.metadata.profileModeration.queueId").value(99))
                .andExpect(jsonPath("$.metadata.profileModeration.status").value("PENDING"));

        verify(moderationAutoKickService).kickQueueId(99L);
    }

    @Test
    void updateMyProfile_shouldOk_whenQueueExists_andPendingHasUsername() throws Exception {
        String email = "p3@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("username", "keep");

        Map<String, Object> md = new LinkedHashMap<>();
        md.put("profile", Map.of("bio", "hello"));
        md.put("profilePending", pending);

        UsersEntity user = user(33L, email, "p3", md);
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));
        when(usersRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditDiffBuilder.build(any(), any())).thenReturn(Map.of());

        ModerationQueueEntity q = new ModerationQueueEntity();
        q.setId(7L);
        when(moderationQueueRepository.findByCaseTypeAndContentTypeAndContentId(ModerationCaseType.CONTENT, ContentType.PROFILE, 33L))
                .thenReturn(Optional.of(q));

        mockMvc.perform(put("/api/account/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.profilePending.username").value("keep"))
                .andExpect(jsonPath("$.metadata.profilePending.bio").value(nullValue()))
                .andExpect(jsonPath("$.metadata.profileModeration.queueId").value(7))
                .andExpect(jsonPath("$.metadata.profileModeration.status").value("PENDING"));

        verify(moderationQueueRepository, never()).save(any());
        verify(moderationQueueRepository).requeueToAuto(eq(7L), eq(QueueStatus.PENDING), eq(QueueStage.RULE), any());
    }

    @Test
    void changeMyPassword_shouldUnauthorized_whenAuthMissing() throws Exception {
        mockMvc.perform(post("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeMyPassword_shouldBadRequest_whenRequireTotpButNotEnabled() throws Exception {
        String email = "u18@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(18L, email, "u18", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setTotpRequired(true);
        p.setTotpAllowed(true);
        p.setEmailOtpAllowed(false);
        when(security2faPolicyService.evaluateForUser(18L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(false);

        mockMvc.perform(post("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("管理员已强制启用 TOTP，请先在账号安全页启用后再修改密码"));
    }

    @Test
    void changeMyPassword_shouldBadRequest_whenRequireEmailMissing() throws Exception {
        String email = "u19@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(19L, email, "u19", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setEmailOtpRequired(true);
        p.setEmailOtpAllowed(true);
        when(security2faPolicyService.evaluateForUser(19L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(false);

        mockMvc.perform(post("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入邮箱验证码"));
    }

    @Test
    void changeMyPassword_shouldBadRequest_whenRequireTotpMissing() throws Exception {
        String email = "u20@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(20L, email, "u20", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setTotpRequired(true);
        p.setTotpAllowed(true);
        when(security2faPolicyService.evaluateForUser(20L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(true);

        mockMvc.perform(post("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入动态验证码"));
    }

    @Test
    void changeMyPassword_shouldBadRequest_whenOptionalAndBothCodesMissing_andCanUseEmailAndTotp() throws Exception {
        String email = "u21@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(21L, email, "u21", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setEmailOtpAllowed(true);
        p.setTotpAllowed(true);
        when(security2faPolicyService.evaluateForUser(21L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(true);

        mockMvc.perform(post("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入验证码（邮箱或动态验证码任选其一）"));
    }

    @Test
    void changeMyPassword_shouldBadRequest_whenOptionalAndEmailOnlyMissing() throws Exception {
        String email = "u22@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(22L, email, "u22", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setEmailOtpAllowed(true);
        p.setTotpAllowed(false);
        when(security2faPolicyService.evaluateForUser(22L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(true);

        mockMvc.perform(post("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入邮箱验证码"));
    }

    @Test
    void changeMyPassword_shouldBadRequest_whenOptionalAndTotpOnlyMissing() throws Exception {
        String email = "u23@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(23L, email, "u23", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setEmailOtpAllowed(false);
        p.setTotpAllowed(true);
        when(security2faPolicyService.evaluateForUser(23L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(true);

        mockMvc.perform(post("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请输入动态验证码"));
    }

    @Test
    void changeMyPassword_shouldOk_whenOptionalAndEmailCodeProvided() throws Exception {
        String email = "u24@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(24L, email, "u24", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setEmailOtpAllowed(true);
        p.setTotpAllowed(true);
        when(security2faPolicyService.evaluateForUser(24L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(true);

        mockMvc.perform(post("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\",\"emailCode\":\"ec\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("密码修改成功"));

        verify(emailVerificationService).verifyAndConsume(24L, com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose.CHANGE_PASSWORD, "ec");
        verify(accountSecurityService).changePasswordByEmail(email, "old", "newpass");
    }

    @Test
    void changeMyPassword_shouldOk_whenOptionalAndTotpCodeProvided() throws Exception {
        String email = "u25@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(25L, email, "u25", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setEmailOtpAllowed(true);
        p.setTotpAllowed(true);
        when(security2faPolicyService.evaluateForUser(25L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(true);

        doThrow(new RuntimeException("boom")).when(notificationsService).createNotification(anyLong(), any(), any(), any());
        doThrow(new RuntimeException("boom")).when(accountSecurityNotificationMailer).sendPasswordChanged(any());

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/account/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\",\"totpCode\":\"tc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("密码修改成功"));

        verify(accountTotpService).requireValidEnabledCodeByEmail(email, "tc");
        verify(accountSecurityService).changePasswordByEmail(email, "old", "newpass");
    }

    @Test
    void changeMyPassword_shouldOk_whenOptionalAndBothCodesProvided() throws Exception {
        String email = "u25b@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(251L, email, "u25b", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setEmailOtpAllowed(true);
        p.setTotpAllowed(true);
        when(security2faPolicyService.evaluateForUser(251L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(true);

        mockMvc.perform(post("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\",\"emailCode\":\"ec\",\"totpCode\":\"tc\"}"))
                .andExpect(status().isOk());

        verify(emailVerificationService).verifyAndConsume(251L, com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose.CHANGE_PASSWORD, "ec");
        verify(accountTotpService).requireValidEnabledCodeByEmail(email, "tc");
    }

    @Test
    void changeMyPassword_shouldOk_whenNoOtpAvailableAndNotRequired() throws Exception {
        String email = "u25c@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(252L, email, "u25c", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setEmailOtpAllowed(false);
        p.setTotpAllowed(false);
        when(security2faPolicyService.evaluateForUser(252L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(false);

        mockMvc.perform(post("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("密码修改成功"));

        verify(emailVerificationService, never()).verifyAndConsume(anyLong(), any(), any());
        verify(accountTotpService, never()).requireValidEnabledCodeByEmail(any(), any());
        verify(accountSecurityService).changePasswordByEmail(email, "old", "newpass");
    }

    @Test
    void changeMyPassword_shouldOk_whenRequireEmailAndTotp_withCodes() throws Exception {
        String email = "u25d@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(253L, email, "u25d", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setEmailOtpRequired(true);
        p.setEmailOtpAllowed(true);
        p.setTotpRequired(true);
        p.setTotpAllowed(true);
        when(security2faPolicyService.evaluateForUser(253L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(true);

        mockMvc.perform(post("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\",\"emailCode\":\"ec\",\"totpCode\":\"tc\"}"))
                .andExpect(status().isOk());

        verify(emailVerificationService).verifyAndConsume(253L, com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose.CHANGE_PASSWORD, "ec");
        verify(accountTotpService).requireValidEnabledCodeByEmail(email, "tc");
    }

    @Test
    void changeMyPassword_shouldBadRequest_whenChangePasswordThrows() throws Exception {
        String email = "u26@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(26L, email, "u26", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setEmailOtpAllowed(true);
        p.setTotpAllowed(false);
        when(security2faPolicyService.evaluateForUser(26L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(false);

        doThrow(new IllegalArgumentException("bad")).when(accountSecurityService).changePasswordByEmail(eq(email), any(), any());

        mockMvc.perform(post("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\",\"emailCode\":\"ec\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad"));

        verify(auditLogWriter).write(eq(26L), eq(email), eq("ACCOUNT_PASSWORD_CHANGE"), eq("USER"), eq(26L),
                eq(AuditResult.FAIL), any(), any(), any());
    }

    @Test
    void changeMyPassword_shouldBadRequest_whenChangePasswordThrowsIllegalState() throws Exception {
        String email = "u26b@example.invalid";
        SecurityContextTestSupport.setAuthenticatedEmail(email);

        UsersEntity user = user(261L, email, "u26b", Map.of());
        when(usersRepository.findByEmailAndIsDeletedFalse(email)).thenReturn(Optional.of(user));

        Security2faPolicyStatusDTO p = new Security2faPolicyStatusDTO();
        p.setEmailOtpAllowed(false);
        p.setTotpAllowed(false);
        when(security2faPolicyService.evaluateForUser(261L)).thenReturn(p);
        when(accountTotpService.isEnabledByEmail(email)).thenReturn(false);

        doThrow(new IllegalStateException("bad-state")).when(accountSecurityService).changePasswordByEmail(eq(email), any(), any());

        mockMvc.perform(post("/api/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad-state"));
    }

    private static UsersEntity user(Long id, String email, String username, Map<String, Object> metadata) {
        UsersEntity u = new UsersEntity();
        u.setId(id);
        u.setEmail(email);
        u.setUsername(username);
        u.setIsDeleted(false);
        u.setMetadata(metadata);
        return u;
    }
}
