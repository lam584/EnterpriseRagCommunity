package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.service.AdministratorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerCurrentAdminUnitTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentAdmin_shouldReturn401_whenAuthNull() {
        AdministratorService administratorService = mock(AdministratorService.class);
        AuthController c = newController(administratorService);

        SecurityContextHolder.clearContext();

        ResponseEntity<?> r = c.getCurrentAdmin(new MockHttpServletRequest());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) r.getBody()).get("message")).isEqualTo("未登录或会话已过期 (auth为null)");
    }

    @Test
    void getCurrentAdmin_shouldReturn401_whenNotAuthenticated() {
        AdministratorService administratorService = mock(AdministratorService.class);
        AuthController c = newController(administratorService);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u@example.invalid", "p"));

        ResponseEntity<?> r = c.getCurrentAdmin(new MockHttpServletRequest());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(((Map<?, ?>) r.getBody()).get("message")).isEqualTo("未登录或会话已过期 (auth未认证)");
    }

    @Test
    void getCurrentAdmin_shouldReturn401_whenAnonymousUser() {
        AdministratorService administratorService = mock(AdministratorService.class);
        AuthController c = newController(administratorService);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("anonymousUser", "p", java.util.List.of()));

        ResponseEntity<?> r = c.getCurrentAdmin(new MockHttpServletRequest());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(((Map<?, ?>) r.getBody()).get("message")).isEqualTo("未登录或会话已过期 (匿名用户)");
    }

    @Test
    void getCurrentAdmin_shouldReturn404_whenUserNotFound() {
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.invalid")).thenReturn(Optional.empty());

        AuthController c = newController(administratorService);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u@example.invalid", "p", java.util.List.of()));

        ResponseEntity<?> r = c.getCurrentAdmin(new MockHttpServletRequest());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((Map<?, ?>) r.getBody()).get("message")).isEqualTo("无法获取管理员信息");
    }

    private static AuthController newController(AdministratorService administratorService) {
        return new AuthController(
                administratorService,
                mock(org.springframework.security.authentication.AuthenticationManager.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                mock(com.example.EnterpriseRagCommunity.config.AdminSetupManager.class),
                mock(com.example.EnterpriseRagCommunity.repository.access.TenantsRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.access.UserRoleLinksRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.access.PermissionsRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.access.RolePermissionsRepository.class),
                mock(com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService.class),
                mock(com.example.EnterpriseRagCommunity.repository.access.RolesRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.content.BoardsRepository.class),
                mock(com.example.EnterpriseRagCommunity.repository.content.BoardModeratorsRepository.class),
                mock(com.example.EnterpriseRagCommunity.service.init.InitialAdminIndexBootstrapService.class),
                mock(com.example.EnterpriseRagCommunity.service.init.TotpMasterKeyBootstrapService.class),
                mock(com.example.EnterpriseRagCommunity.service.access.EmailVerificationService.class),
                mock(com.example.EnterpriseRagCommunity.service.notify.EmailVerificationMailer.class),
                mock(com.example.EnterpriseRagCommunity.service.access.Security2faPolicyService.class),
                mock(com.example.EnterpriseRagCommunity.service.AccountTotpService.class),
                mock(org.springframework.security.core.userdetails.UserDetailsService.class),
                mock(com.example.EnterpriseRagCommunity.service.access.AuditLogWriter.class)
        );
    }
}
