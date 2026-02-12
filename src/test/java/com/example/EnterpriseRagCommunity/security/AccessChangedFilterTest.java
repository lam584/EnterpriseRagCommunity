package com.example.EnterpriseRagCommunity.security;

import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AccessChangedFilterTest {

    @Test
    void should_refresh_authorities_when_access_version_changes() throws Exception {
        AccessControlService accessControlService = mock(AccessControlService.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        AccessChangedFilter filter = new AccessChangedFilter(accessControlService, usersRepository);

        // enable filter
        var enabledField = AccessChangedFilter.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.set(filter, true);

        var intervalField = AccessChangedFilter.class.getDeclaredField("checkIntervalMs");
        intervalField.setAccessible(true);
        intervalField.set(filter, 0L);

        UsersRepository.UserAccessMetaView meta = new UsersRepository.UserAccessMetaView() {
            @Override
            public Long getId() {
                return 1L;
            }

            @Override
            public Long getAccessVersion() {
                return 2L;
            }

            @Override
            public java.time.LocalDateTime getUpdatedAt() {
                return null;
            }

            @Override
            public java.time.LocalDateTime getSessionInvalidatedAt() {
                return null;
            }
        };
        when(usersRepository.findAccessMetaByEmail("u@example.com")).thenReturn(Optional.of(meta));
        when(accessControlService.buildAuthorities(1L)).thenReturn(List.of(new SimpleGrantedAuthority("PERM_admin_ui:access")));

        // Existing auth without the perm
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/auth/access-context");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AccessChangedFilter.SESSION_ACCESS_VER_KEY, 1L);
        req.setSession(session);

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .contains("PERM_admin_ui:access");

        verify(accessControlService, times(1)).buildAuthorities(1L);
    }
}

