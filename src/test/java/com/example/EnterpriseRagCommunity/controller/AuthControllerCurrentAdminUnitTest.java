package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.service.AdministratorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
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
    void getCurrentAdmin_shouldReturn401_whenAuthNull() throws Exception {
        AdministratorService administratorService = mock(AdministratorService.class);
        AuthController c = new AuthController();
        setField(c, "administratorService", administratorService);

        SecurityContextHolder.clearContext();

        ResponseEntity<?> r = c.getCurrentAdmin(new MockHttpServletRequest());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) r.getBody()).get("message")).isEqualTo("未登录或会话已过期 (auth为null)");
    }

    @Test
    void getCurrentAdmin_shouldReturn401_whenNotAuthenticated() throws Exception {
        AdministratorService administratorService = mock(AdministratorService.class);
        AuthController c = new AuthController();
        setField(c, "administratorService", administratorService);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u@example.invalid", "p"));

        ResponseEntity<?> r = c.getCurrentAdmin(new MockHttpServletRequest());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(((Map<?, ?>) r.getBody()).get("message")).isEqualTo("未登录或会话已过期 (auth未认证)");
    }

    @Test
    void getCurrentAdmin_shouldReturn401_whenAnonymousUser() throws Exception {
        AdministratorService administratorService = mock(AdministratorService.class);
        AuthController c = new AuthController();
        setField(c, "administratorService", administratorService);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("anonymousUser", "p", java.util.List.of()));

        ResponseEntity<?> r = c.getCurrentAdmin(new MockHttpServletRequest());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(((Map<?, ?>) r.getBody()).get("message")).isEqualTo("未登录或会话已过期 (匿名用户)");
    }

    @Test
    void getCurrentAdmin_shouldReturn404_whenUserNotFound() throws Exception {
        AdministratorService administratorService = mock(AdministratorService.class);
        when(administratorService.findByUsername("u@example.invalid")).thenReturn(Optional.empty());

        AuthController c = new AuthController();
        setField(c, "administratorService", administratorService);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u@example.invalid", "p", java.util.List.of()));

        ResponseEntity<?> r = c.getCurrentAdmin(new MockHttpServletRequest());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((Map<?, ?>) r.getBody()).get("message")).isEqualTo("无法获取管理员信息");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = AuthController.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}

