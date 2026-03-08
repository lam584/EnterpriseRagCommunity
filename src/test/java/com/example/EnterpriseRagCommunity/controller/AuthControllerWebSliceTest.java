package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.config.AdminSetupManager;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolesRepository;
import com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository;
import com.example.EnterpriseRagCommunity.repository.access.TenantsRepository;
import com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardModeratorsRepository;
import com.example.EnterpriseRagCommunity.repository.content.BoardsRepository;
import com.example.EnterpriseRagCommunity.service.AccountTotpService;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AuditLogWriter;
import com.example.EnterpriseRagCommunity.service.access.EmailVerificationService;
import com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService;
import com.example.EnterpriseRagCommunity.service.init.InitialAdminIndexBootstrapService;
import com.example.EnterpriseRagCommunity.service.init.TotpMasterKeyBootstrapService;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WithMockUser(username = "u@example.invalid")
class AuthControllerWebSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdministratorService administratorService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AdminSetupManager initialAdminSetupState;

    @MockitoBean
    private TenantsRepository tenantsRepository;

    @MockitoBean
    private UserRoleLinksRepository userRoleLinksRepository;

    @MockitoBean
    private PermissionsRepository permissionsRepository;

    @MockitoBean
    private RolePermissionsRepository rolePermissionsRepository;

    @MockitoBean
    private AppSettingsService appSettingsService;

    @MockitoBean
    private RolesRepository rolesRepository;

    @MockitoBean
    private BoardsRepository boardsRepository;

    @MockitoBean
    private BoardModeratorsRepository boardModeratorsRepository;

    @MockitoBean
    private InitialAdminIndexBootstrapService initialAdminIndexBootstrapService;

    @MockitoBean
    private TotpMasterKeyBootstrapService totpMasterKeyBootstrapService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private EmailVerificationMailer emailVerificationMailer;

    @MockitoBean
    private Security2faPolicyService security2faPolicyService;

    @MockitoBean
    private AccountTotpService accountTotpService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private AuditLogWriter auditLogWriter;

    @Test
    void currentAdmin_shouldReturn404_whenUserNotFound() throws Exception {
        when(administratorService.findByUsername("u@example.invalid")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/current-admin").with(user("u@example.invalid")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("无法获取管理员信息"));
    }

    @Test
    void currentAdmin_shouldReturn200_whenUserFound() throws Exception {
        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setEmail("u@example.invalid");
        u.setUsername("u");
        u.setStatus(AccountStatus.ACTIVE);
        u.setIsDeleted(false);

        when(administratorService.findByUsername("u@example.invalid")).thenReturn(Optional.of(u));

        mockMvc.perform(get("/api/auth/current-admin").with(user("u@example.invalid")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("u@example.invalid"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void registrationStatus_shouldReturnTrue_whenEnabled() throws Exception {
        when(appSettingsService.getLongOrDefault(eq(AppSettingsService.KEY_REGISTRATION_ENABLED), eq(1L))).thenReturn(1L);

        mockMvc.perform(get("/api/auth/registration-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationEnabled").value(true));
    }

    @Test
    void registrationStatus_shouldReturnFalse_whenDisabled() throws Exception {
        when(appSettingsService.getLongOrDefault(eq(AppSettingsService.KEY_REGISTRATION_ENABLED), eq(1L))).thenReturn(0L);

        mockMvc.perform(get("/api/auth/registration-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationEnabled").value(false));
    }

    @Test
    void register_shouldReturn403_whenRegistrationDisabled() throws Exception {
        when(appSettingsService.getLongOrDefault(eq(AppSettingsService.KEY_REGISTRATION_ENABLED), eq(1L))).thenReturn(0L);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "u@example.invalid",
                                  "password": "pass1234",
                                  "username": "user"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("已关闭用户注册"));
    }

    @Test
    void initialSetupStatus_shouldReturn200() throws Exception {
        when(initialAdminSetupState.isSetupRequired()).thenReturn(true);

        mockMvc.perform(get("/api/auth/initial-setup-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true));
    }

    @Test
    void csrfToken_shouldReturn500_whenNoAttribute() throws Exception {
        mockMvc.perform(get("/api/auth/csrf-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("无法获取CSRF令牌"));
    }

    @Test
    void csrfToken_shouldReturn200_whenAttributePresent() throws Exception {
        CsrfToken token = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "t");

        mockMvc.perform(get("/api/auth/csrf-token")
                        .requestAttr(CsrfToken.class.getName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("t"));
    }

    @Test
    void logout_shouldReturn200_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("退出登录成功"));
    }

    @Test
    void logout_shouldReturn200_whenAuthenticatedAndSessionExists() throws Exception {
        UsersEntity u = new UsersEntity();
        u.setId(1L);
        u.setEmail("u@example.invalid");
        u.setIsDeleted(false);
        when(administratorService.findByUsername("u@example.invalid")).thenReturn(Optional.of(u));

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/logout")
                        .with(user("u@example.invalid"))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("退出登录成功"));
    }

    @Test
    void login_shouldReturn400_whenInvalidBody() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
