package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostTagSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTagSuggestResponse;
import com.example.EnterpriseRagCommunity.dto.ai.PostTagGenPublicConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiPostTagService;
import com.example.EnterpriseRagCommunity.service.ai.PostTagGenConfigService;
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

class AiPostTagControllerTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getTagGenConfig_returnsServiceValue() {
        AiPostTagService tagService = mock(AiPostTagService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostTagGenConfigService cfgService = mock(PostTagGenConfigService.class);
        AiPostTagController c = new AiPostTagController(tagService, administratorService, cfgService);

        PostTagGenPublicConfigDTO cfg = new PostTagGenPublicConfigDTO();
        when(cfgService.getPublicConfig()).thenReturn(cfg);

        Assertions.assertSame(cfg, c.getTagGenConfig());
    }

    @Test
    void tagSuggestions_authNull_throwsAuthenticationException() {
        AiPostTagService tagService = mock(AiPostTagService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostTagGenConfigService cfgService = mock(PostTagGenConfigService.class);
        AiPostTagController c = new AiPostTagController(tagService, administratorService, cfgService);

        SecurityContextHolder.getContext().setAuthentication(null);

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.tagSuggestions(new AiPostTagSuggestRequest())
        );
    }

    @Test
    void tagSuggestions_notAuthenticated_throwsAuthenticationException() {
        AiPostTagService tagService = mock(AiPostTagService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostTagGenConfigService cfgService = mock(PostTagGenConfigService.class);
        AiPostTagController c = new AiPostTagController(tagService, administratorService, cfgService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A")
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.tagSuggestions(new AiPostTagSuggestRequest())
        );
    }

    @Test
    void tagSuggestions_anonymousPrincipal_throwsAuthenticationException() {
        AiPostTagService tagService = mock(AiPostTagService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostTagGenConfigService cfgService = mock(PostTagGenConfigService.class);
        AiPostTagController c = new AiPostTagController(tagService, administratorService, cfgService);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "anonymousUser",
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        Assertions.assertThrows(
                org.springframework.security.core.AuthenticationException.class,
                () -> c.tagSuggestions(new AiPostTagSuggestRequest())
        );
    }

    @Test
    void tagSuggestions_userNotFound_throwsIllegalArgumentException() {
        AiPostTagService tagService = mock(AiPostTagService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostTagGenConfigService cfgService = mock(PostTagGenConfigService.class);
        AiPostTagController c = new AiPostTagController(tagService, administratorService, cfgService);

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
                () -> c.tagSuggestions(new AiPostTagSuggestRequest())
        );
    }

    @Test
    void tagSuggestions_ok_returnsServiceValue() {
        AiPostTagService tagService = mock(AiPostTagService.class);
        AdministratorService administratorService = mock(AdministratorService.class);
        PostTagGenConfigService cfgService = mock(PostTagGenConfigService.class);
        AiPostTagController c = new AiPostTagController(tagService, administratorService, cfgService);

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

        AiPostTagSuggestRequest req = new AiPostTagSuggestRequest();
        AiPostTagSuggestResponse resp = new AiPostTagSuggestResponse(List.of("tag"), "m", 1L);
        when(tagService.suggestTags(eq(req), eq(10L))).thenReturn(resp);

        Assertions.assertSame(resp, c.tagSuggestions(req));
    }
}

