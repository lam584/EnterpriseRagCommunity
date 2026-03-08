package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostLangLabelSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostLangLabelSuggestResponse;
import com.example.EnterpriseRagCommunity.dto.ai.PostLangLabelGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiPostLangLabelService;
import com.example.EnterpriseRagCommunity.service.ai.PostLangLabelGenConfigService;
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

class AiPostLangLabelControllerTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getLangLabelGenConfig_returnsServiceValue() {
        AiPostLangLabelService langService = mock(AiPostLangLabelService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostLangLabelGenConfigService cfgService = mock(PostLangLabelGenConfigService.class);
        AiPostLangLabelController c = new AiPostLangLabelController(langService, administratorService, cfgService);

        PostLangLabelGenPublicConfigDTO cfg = new PostLangLabelGenPublicConfigDTO();
        when(cfgService.getPublicConfig()).thenReturn(cfg);

        Assertions.assertSame(cfg, c.getLangLabelGenConfig());
    }

    @Test
    void langLabelSuggestions_authNull_throwsAuthenticationException() {
        AiPostLangLabelService langService = mock(AiPostLangLabelService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostLangLabelGenConfigService cfgService = mock(PostLangLabelGenConfigService.class);
        AiPostLangLabelController c = new AiPostLangLabelController(langService, administratorService, cfgService);

        SecurityContextHolder.getContext().setAuthentication(null);

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.langLabelSuggestions(new AiPostLangLabelSuggestRequest())
        );
    }

    @Test
    void langLabelSuggestions_notAuthenticated_throwsAuthenticationException() {
        AiPostLangLabelService langService = mock(AiPostLangLabelService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostLangLabelGenConfigService cfgService = mock(PostLangLabelGenConfigService.class);
        AiPostLangLabelController c = new AiPostLangLabelController(langService, administratorService, cfgService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A")
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.langLabelSuggestions(new AiPostLangLabelSuggestRequest())
        );
    }

    @Test
    void langLabelSuggestions_anonymousPrincipal_throwsAuthenticationException() {
        AiPostLangLabelService langService = mock(AiPostLangLabelService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostLangLabelGenConfigService cfgService = mock(PostLangLabelGenConfigService.class);
        AiPostLangLabelController c = new AiPostLangLabelController(langService, administratorService, cfgService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "anonymousUser",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.langLabelSuggestions(new AiPostLangLabelSuggestRequest())
        );
    }

    @Test
    void langLabelSuggestions_userNotFound_throwsIllegalArgumentException() {
        AiPostLangLabelService langService = mock(AiPostLangLabelService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostLangLabelGenConfigService cfgService = mock(PostLangLabelGenConfigService.class);
        AiPostLangLabelController c = new AiPostLangLabelController(langService, administratorService, cfgService);

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
                () -> c.langLabelSuggestions(new AiPostLangLabelSuggestRequest())
        );
    }

    @Test
    void langLabelSuggestions_ok_returnsServiceValue() {
        AiPostLangLabelService langService = mock(AiPostLangLabelService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostLangLabelGenConfigService cfgService = mock(PostLangLabelGenConfigService.class);
        AiPostLangLabelController c = new AiPostLangLabelController(langService, administratorService, cfgService);

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

        AiPostLangLabelSuggestRequest req = new AiPostLangLabelSuggestRequest();
        AiPostLangLabelSuggestResponse resp = new AiPostLangLabelSuggestResponse(List.of("zh-CN"), "m", 1L);
        when(langService.suggestLanguages(eq(req))).thenReturn(resp);

        Assertions.assertSame(resp, c.langLabelSuggestions(req));
    }
}

