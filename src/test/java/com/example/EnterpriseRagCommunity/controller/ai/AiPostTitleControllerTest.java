package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestResponse;
import com.example.EnterpriseRagCommunity.dto.ai.PostTitleGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiPostTitleService;
import com.example.EnterpriseRagCommunity.service.ai.PostTitleGenConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiPostTitleControllerTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getTitleGenConfig_returnsServiceValue() {
        AiPostTitleService titleService = mock(AiPostTitleService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostTitleGenConfigService cfgService = mock(PostTitleGenConfigService.class);
        AiPostTitleController c = new AiPostTitleController(titleService, administratorService, cfgService);

        PostTitleGenPublicConfigDTO cfg = new PostTitleGenPublicConfigDTO();
        when(cfgService.getPublicConfig()).thenReturn(cfg);

        Assertions.assertSame(cfg, c.getTitleGenConfig());
    }

    @Test
    void titleSuggestions_authNull_throwsAuthenticationException() {
        AiPostTitleService titleService = mock(AiPostTitleService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostTitleGenConfigService cfgService = mock(PostTitleGenConfigService.class);
        AiPostTitleController c = new AiPostTitleController(titleService, administratorService, cfgService);

        SecurityContextHolder.getContext().setAuthentication(null);

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.titleSuggestions(new AiPostTitleSuggestRequest())
        );
    }

    @Test
    void titleSuggestions_notAuthenticated_throwsAuthenticationException() {
        AiPostTitleService titleService = mock(AiPostTitleService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostTitleGenConfigService cfgService = mock(PostTitleGenConfigService.class);
        AiPostTitleController c = new AiPostTitleController(titleService, administratorService, cfgService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A")
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.titleSuggestions(new AiPostTitleSuggestRequest())
        );
    }

    @Test
    void titleSuggestions_anonymousPrincipal_throwsAuthenticationException() {
        AiPostTitleService titleService = mock(AiPostTitleService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostTitleGenConfigService cfgService = mock(PostTitleGenConfigService.class);
        AiPostTitleController c = new AiPostTitleController(titleService, administratorService, cfgService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "anonymousUser",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.titleSuggestions(new AiPostTitleSuggestRequest())
        );
    }

    @Test
    void titleSuggestions_userNotFound_throwsIllegalArgumentException() {
        AiPostTitleService titleService = mock(AiPostTitleService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostTitleGenConfigService cfgService = mock(PostTitleGenConfigService.class);
        AiPostTitleController c = new AiPostTitleController(titleService, administratorService, cfgService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.empty());

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> c.titleSuggestions(new AiPostTitleSuggestRequest())
        );
    }

    @Test
    void titleSuggestions_ok_returnsServiceValue() {
        AiPostTitleService titleService = mock(AiPostTitleService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostTitleGenConfigService cfgService = mock(PostTitleGenConfigService.class);
        AiPostTitleController c = new AiPostTitleController(titleService, administratorService, cfgService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "alice@example.com",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
        UsersEntity u = new UsersEntity();
        u.setId(10L);
        u.setEmail("alice@example.com");
        when(administratorService.findByUsername("alice@example.com")).thenReturn(Optional.of(u));

        AiPostTitleSuggestRequest req = new AiPostTitleSuggestRequest();
        AiPostTitleSuggestResponse resp = new AiPostTitleSuggestResponse(List.of("title"), "m", 1L);
        when(titleService.suggestTitles(eq(req), eq(10L))).thenReturn(resp);

        Assertions.assertSame(resp, c.titleSuggestions(req));
    }
}

