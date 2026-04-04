package com.example.EnterpriseRagCommunity.config;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.security.AccessLogsFilter;
import com.example.EnterpriseRagCommunity.security.ContentSafetyCircuitBreakerFilter;
import com.example.EnterpriseRagCommunity.security.IpPathRateLimitFilter;
import com.example.EnterpriseRagCommunity.security.ThreatPathBlockFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    private SecurityConfig newConfig() {
        return newConfig(
                mock(AdministratorService.class),
                mock(AccessControlService.class),
                null,
                null,
                null,
                null,
                mock(ContentSafetyCircuitBreakerService.class),
                mock(ObjectMapper.class)
        );
    }

    private SecurityConfig newConfig(
            AdministratorService administratorService,
            AccessControlService accessControlService,
            AccessChangedFilter accessChangedFilter,
            AccessLogsFilter accessLogsFilter,
            ThreatPathBlockFilter threatPathBlockFilter,
            IpPathRateLimitFilter ipPathRateLimitFilter,
            ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService,
            ObjectMapper objectMapper
    ) {
        return new SecurityConfig(
                administratorService,
                accessControlService,
                provider(accessChangedFilter),
                provider(accessLogsFilter),
                provider(threatPathBlockFilter),
                provider(ipPathRateLimitFilter),
                contentSafetyCircuitBreakerService,
                objectMapper
        );
    }

    private <T> ObjectProvider<T> provider(T value) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    @Test
    void parseCorsList_should_return_empty_for_blank() {
        @SuppressWarnings("unchecked")
        List<String> out = (List<String>) ReflectionTestUtils.invokeMethod(SecurityConfig.class, "parseCorsList", "   ");
        assertNotNull(out);
        assertTrue(out.isEmpty());
    }

    @Test
    void parseCorsList_should_split_trim_and_filter() {
        @SuppressWarnings("unchecked")
        List<String> out = (List<String>) ReflectionTestUtils.invokeMethod(SecurityConfig.class, "parseCorsList", " a,  b   c ,,  ");
        assertEquals(List.of("a", "b", "c"), out);
    }

    @Test
    void corsConfigurationSource_should_choose_patterns_over_origins() {
        SecurityConfig cfg = newConfig();
        ReflectionTestUtils.setField(cfg, "corsAllowedOriginPatterns", "https://*.example.com");
        ReflectionTestUtils.setField(cfg, "corsAllowedOrigins", "http://should-not-win.com");

        CorsConfigurationSource src = cfg.corsConfigurationSource();
        CorsConfiguration c = src.getCorsConfiguration(new MockHttpServletRequest());
        assertNotNull(c);
        assertEquals(List.of("https://*.example.com"), c.getAllowedOriginPatterns());
    }

    @Test
    void corsConfigurationSource_should_choose_origins_when_patterns_empty() {
        SecurityConfig cfg = newConfig();
        ReflectionTestUtils.setField(cfg, "corsAllowedOriginPatterns", "   ");
        ReflectionTestUtils.setField(cfg, "corsAllowedOrigins", "http://a.com http://b.com");

        CorsConfigurationSource src = cfg.corsConfigurationSource();
        CorsConfiguration c = src.getCorsConfiguration(new MockHttpServletRequest());
        assertNotNull(c);
        assertEquals(List.of("http://a.com", "http://b.com"), c.getAllowedOrigins());
    }

    @Test
    void corsConfigurationSource_should_fallback_to_default_localhost_origins() {
        SecurityConfig cfg = newConfig();
        ReflectionTestUtils.setField(cfg, "corsAllowedOriginPatterns", "");
        ReflectionTestUtils.setField(cfg, "corsAllowedOrigins", "");

        CorsConfigurationSource src = cfg.corsConfigurationSource();
        CorsConfiguration c = src.getCorsConfiguration(new MockHttpServletRequest());
        assertNotNull(c);
        assertEquals(List.of("http://localhost:5173", "http://127.0.0.1:5173"), c.getAllowedOrigins());
    }

    @Test
    void userDetailsService_should_throw_when_user_missing() {
        AdministratorService administratorService = mock(AdministratorService.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        SecurityConfig cfg = newConfig(
                administratorService,
                accessControlService,
                null,
                null,
                null,
                null,
                mock(ContentSafetyCircuitBreakerService.class),
                mock(ObjectMapper.class)
        );

        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.empty());

        UserDetailsService uds = cfg.userDetailsService();
        assertThrows(UsernameNotFoundException.class, () -> uds.loadUserByUsername("u@example.com"));
    }

    @Test
    void userDetailsService_should_throw_when_user_not_active() {
        AdministratorService administratorService = mock(AdministratorService.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        SecurityConfig cfg = newConfig(
                administratorService,
                accessControlService,
                null,
                null,
                null,
                null,
                mock(ContentSafetyCircuitBreakerService.class),
                mock(ObjectMapper.class)
        );

        UsersEntity u = new UsersEntity();
        u.setId(7L);
        u.setEmail("u@example.com");
        u.setPasswordHash("h");
        u.setStatus(AccountStatus.DISABLED);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(u));

        UserDetailsService uds = cfg.userDetailsService();
        assertThrows(UsernameNotFoundException.class, () -> uds.loadUserByUsername("u@example.com"));
    }

    @Test
    void userDetailsService_should_build_authorities_for_active_user() {
        AdministratorService administratorService = mock(AdministratorService.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        SecurityConfig cfg = newConfig(
                administratorService,
                accessControlService,
                null,
                null,
                null,
                null,
                mock(ContentSafetyCircuitBreakerService.class),
                mock(ObjectMapper.class)
        );

        UsersEntity u = new UsersEntity();
        u.setId(7L);
        u.setEmail("u@example.com");
        u.setPasswordHash("h");
        u.setStatus(AccountStatus.ACTIVE);
        when(administratorService.findByUsername(eq("u@example.com"))).thenReturn(Optional.of(u));
        when(accessControlService.buildAuthorities(eq(7L))).thenReturn(List.of(new SimpleGrantedAuthority("PERM_x:y")));

        UserDetailsService uds = cfg.userDetailsService();
        UserDetails out = uds.loadUserByUsername("u@example.com");
        assertEquals("u@example.com", out.getUsername());
        assertEquals("h", out.getPassword());
        assertEquals(List.of("PERM_x:y"), out.getAuthorities().stream().map(a -> a.getAuthority()).toList());
    }

    @Test
    void securityFilterChain_should_add_circuit_breaker_after_access_changed_when_no_access_logs_filter() throws Exception {
        AccessChangedFilter accessChangedFilter = mock(AccessChangedFilter.class);
        ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService = mock(ContentSafetyCircuitBreakerService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        SecurityConfig cfg = newConfig(
                mock(AdministratorService.class),
                mock(AccessControlService.class),
                accessChangedFilter,
                null,
                null,
                null,
                contentSafetyCircuitBreakerService,
                objectMapper
        );

        HttpSecurity http = mock(HttpSecurity.class);
        when(http.securityMatcher(anyString())).thenReturn(http);
        when(http.addFilterAfter(any(), any())).thenReturn(http);
        when(http.cors(any())).thenReturn(http);
        when(http.csrf(any())).thenReturn(http);
        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.exceptionHandling(any())).thenReturn(http);
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
        when(http.build()).thenReturn(chain);

        SecurityFilterChain out = cfg.securityFilterChain(http);
        assertNotNull(out);

        verify(http).addFilterAfter(eq(accessChangedFilter), eq(SecurityContextHolderFilter.class));
        verify(http).addFilterAfter(argThat(f -> f instanceof ContentSafetyCircuitBreakerFilter), eq(AccessChangedFilter.class));
        verifyNoMoreInteractions(chain);
    }

    @Test
    void securityFilterChain_should_add_circuit_breaker_after_access_logs_when_access_logs_filter_present() throws Exception {
        AccessChangedFilter accessChangedFilter = mock(AccessChangedFilter.class);
        AccessLogsFilter accessLogsFilter = mock(AccessLogsFilter.class);
        ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService = mock(ContentSafetyCircuitBreakerService.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        SecurityConfig cfg = newConfig(
                mock(AdministratorService.class),
                mock(AccessControlService.class),
                accessChangedFilter,
                accessLogsFilter,
                null,
                null,
                contentSafetyCircuitBreakerService,
                objectMapper
        );

        HttpSecurity http = mock(HttpSecurity.class);
        when(http.securityMatcher(anyString())).thenReturn(http);
        when(http.addFilterAfter(any(), any())).thenReturn(http);
        when(http.cors(any())).thenReturn(http);
        when(http.csrf(any())).thenReturn(http);
        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.exceptionHandling(any())).thenReturn(http);
        DefaultSecurityFilterChain chain = mock(DefaultSecurityFilterChain.class);
        when(http.build()).thenReturn(chain);

        SecurityFilterChain out = cfg.securityFilterChain(http);
        assertNotNull(out);

        verify(http).addFilterAfter(eq(accessChangedFilter), eq(SecurityContextHolderFilter.class));
        verify(http).addFilterAfter(eq(accessLogsFilter), eq(AccessChangedFilter.class));
        verify(http).addFilterAfter(argThat(f -> f instanceof ContentSafetyCircuitBreakerFilter), eq(AccessLogsFilter.class));
        verifyNoMoreInteractions(chain);
    }
}

