package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.config.AdminSetupManager;
import com.example.EnterpriseRagCommunity.config.MethodSecurityConfig;
import com.example.EnterpriseRagCommunity.config.SecurityConfig;
import com.example.EnterpriseRagCommunity.controller.access.AccessContextController;
import com.example.EnterpriseRagCommunity.controller.monitor.admin.AdminLlmQueueController;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolesRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.TenantsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardModeratorsRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.service.init.InitialAdminIndexBootstrapService;
import com.example.EnterpriseRagCommunity.service.init.TotpMasterKeyBootstrapService;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.example.EnterpriseRagCommunity.service.config.SystemConfigurationService;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.monitor.LlmQueueMonitorService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        AccessContextController.class,
        AdminLlmQueueController.class
})
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class
})
class AuthControllerSecuritySliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdministratorService administratorService;

    @MockitoBean
    AuthenticationManager authenticationManager;

    @MockitoBean
    PasswordEncoder passwordEncoder;

    @MockitoBean
    AdminSetupManager initialAdminSetupState;

    @MockitoBean
    TenantsRepository tenantsRepository;

    @MockitoBean
    UserRoleLinksRepository userRoleLinksRepository;

    @MockitoBean
    PermissionsRepository permissionsRepository;

    @MockitoBean
    RolePermissionsRepository rolePermissionsRepository;

    @MockitoBean
    AppSettingsService appSettingsService;

    @MockitoBean
    RolesRepository rolesRepository;

    @MockitoBean
    BoardsRepository boardsRepository;

    @MockitoBean
    BoardModeratorsRepository boardModeratorsRepository;

    @MockitoBean
    InitialAdminIndexBootstrapService initialAdminIndexBootstrapService;

    @MockitoBean
    TotpMasterKeyBootstrapService totpMasterKeyBootstrapService;

    @MockitoBean
    EmailVerificationService emailVerificationService;

    @MockitoBean
    EmailVerificationMailer emailVerificationMailer;

    @MockitoBean
    Security2faPolicyService security2faPolicyService;

    @MockitoBean
    AccountTotpService accountTotpService;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    AuditLogWriter auditLogWriter;

    @MockitoBean
    AccessControlService accessControlService;

    @MockitoBean
    AccessChangedFilter accessChangedFilter;

    @MockitoBean
    ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService;

    @MockitoBean
    LlmQueueMonitorService llmQueueMonitorService;

    @MockitoBean
    LlmQueueProperties llmQueueProperties;

    @MockitoBean
    SystemConfigurationService systemConfigurationService;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(accessChangedFilter).doFilter(any(), any(), any());

        ContentSafetyCircuitBreakerConfigDTO cfg = new ContentSafetyCircuitBreakerConfigDTO();
        cfg.setEnabled(false);
        when(contentSafetyCircuitBreakerService.getConfig()).thenReturn(cfg);
    }

    @Test
    void csrfToken_shouldReturn200_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void initialSetupStatus_shouldReturn200_whenAnonymous() throws Exception {
        when(initialAdminSetupState.isSetupRequired()).thenReturn(true);

        mockMvc.perform(get("/api/auth/initial-setup-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true));
    }

    @Test
    void accessContext_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/auth/access-context"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessContext_shouldReturn200_whenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/access-context")
                        .with(user("u@example.com")
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                                        new SimpleGrantedAuthority("PERM_post:write")
                                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("u@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.permissions[0]").value("post:write"));
    }

    @Test
    void currentAdmin_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/auth/current-admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentAdmin_shouldReturn404_whenAuthenticated_butUserNotFound() throws Exception {
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/current-admin").with(user("u@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void currentAdmin_shouldReturn200_whenAuthenticated_andUserFound() throws Exception {
        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setEmail("u@example.com");
        u.setUsername("u");
        u.setStatus(AccountStatus.ACTIVE);
        u.setIsDeleted(false);

        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(u));

        mockMvc.perform(get("/api/auth/current-admin").with(user("u@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("u@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void logout_shouldAllow_withoutCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());
    }

    @Test
    void logout_shouldAllow_withCsrf_evenWhenAnonymous() throws Exception {
        when(administratorService.findByUsername(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void login_shouldReturn400_whenCsrfPresent_butInvalidBody() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationStatus_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/auth/registration-status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationStatus_shouldReturn200_whenAuthenticated() throws Exception {
        when(appSettingsService.getLongOrDefault(AppSettingsService.KEY_REGISTRATION_ENABLED, 1L)).thenReturn(1L);

        mockMvc.perform(get("/api/auth/registration-status").with(user("u@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationEnabled").value(true));
    }

    @Test
    void adminLlmQueue_updateConfig_shouldDeny_withoutCsrf() throws Exception {
        mockMvc.perform(put("/api/admin/metrics/llm-queue/config")
                        .with(user("u").authorities(new SimpleGrantedAuthority("PERM_admin_metrics_llm_queue:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxConcurrent\":2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminLlmQueue_updateConfig_shouldDeny_withoutWritePermission() throws Exception {
        mockMvc.perform(put("/api/admin/metrics/llm-queue/config")
                        .with(user("u").authorities(new SimpleGrantedAuthority("PERM_admin_metrics_llm_queue:read")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxConcurrent\":2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminLlmQueue_updateConfig_shouldAllow_withCsrfAndWritePermission() throws Exception {
        mockMvc.perform(put("/api/admin/metrics/llm-queue/config")
                        .with(user("u").authorities(new SimpleGrantedAuthority("PERM_admin_metrics_llm_queue:write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxConcurrent\":2}"))
                .andExpect(status().isOk());
    }
}
