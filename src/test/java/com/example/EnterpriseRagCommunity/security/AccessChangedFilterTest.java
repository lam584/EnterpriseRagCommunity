package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.AccountStatus;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AccessChangedFilterTest {

    @Test
    void should_refresh_authorities_when_users_updatedAt_changes() throws Exception {
        AccessControlService accessControlService = mock(AccessControlService.class);
        AccessChangedFilter filter = new AccessChangedFilter(accessControlService);

        // enable filter
        var enabledField = AccessChangedFilter.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.set(filter, true);

        UsersEntity user = new UsersEntity();
        user.setId(1L);
        user.setEmail("u@example.com");
        user.setUsername("u");
        user.setPasswordHash("x");
        user.setStatus(AccountStatus.ACTIVE);
        user.setIsDeleted(false);
        user.setCreatedAt(LocalDateTime.now().minusDays(1));
        user.setUpdatedAt(LocalDateTime.now());

        when(accessControlService.loadActiveUserByEmail("u@example.com")).thenReturn(user);
        when(accessControlService.buildAuthorities(1L)).thenReturn(List.of(new SimpleGrantedAuthority("PERM_admin_ui:access")));

        // Existing auth without the perm
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/auth/access-context");
        MockHttpSession session = new MockHttpSession();
        // session has old access timestamp
        session.setAttribute(AccessChangedFilter.SESSION_ACCESS_TS_KEY, 1L);
        req.setSession(session);

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .contains("PERM_admin_ui:access");

        verify(accessControlService, times(1)).buildAuthorities(1L);
    }
}

