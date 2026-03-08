package com.example.EnterpriseRagCommunity.controller.admin;

import com.example.EnterpriseRagCommunity.config.MethodSecurityConfig;
import com.example.EnterpriseRagCommunity.config.SecurityConfig;
import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.content.HotScoresService;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HotScoresAdminController.class)
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class
})
class HotScoresAdminControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    HotScoresService hotScoresService;

    @MockitoBean
    AccessChangedFilter accessChangedFilter;

    @MockitoBean
    ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService;

    @MockitoBean
    AdministratorService administratorService;

    @MockitoBean
    AccessControlService accessControlService;

    @BeforeEach
    void setUp() throws Exception {
        reset(hotScoresService);

        doAnswer(invocation -> {
            FilterChain chain = (FilterChain) invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(accessChangedFilter).doFilter(any(), any(), any());

        ContentSafetyCircuitBreakerConfigDTO cfg = new ContentSafetyCircuitBreakerConfigDTO();
        cfg.setEnabled(false);
        when(contentSafetyCircuitBreakerService.getConfig()).thenReturn(cfg);
    }

    @Test
    void recompute24h_should401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/admin/hot-scores/recompute-24h"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(hotScoresService);
    }

    @Test
    void recompute7d_should401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/admin/hot-scores/recompute-7d"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(hotScoresService);
    }

    @Test
    void recomputeAll_should401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/admin/hot-scores/recompute-all"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(hotScoresService);
    }

    @Test
    void recompute24h_should200_withoutCsrf_whenAuthenticated() throws Exception {
        mockMvc.perform(post("/api/admin/hot-scores/recompute-24h")
                        .with(user("u")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.window").value("H24"))
                .andExpect(jsonPath("$.at").isNotEmpty());

        verify(hotScoresService, times(1)).recompute24hHourly();
        verifyNoMoreInteractions(hotScoresService);
    }

    @Test
    void recompute7d_should200_withoutCsrf_whenAuthenticated() throws Exception {
        mockMvc.perform(post("/api/admin/hot-scores/recompute-7d")
                        .with(user("u")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.window").value("D7"))
                .andExpect(jsonPath("$.at").isNotEmpty());

        verify(hotScoresService, times(1)).recomputeAllWindowsDaily();
        verifyNoMoreInteractions(hotScoresService);
    }

    @Test
    void recomputeAll_should200_withoutCsrf_whenAuthenticated() throws Exception {
        mockMvc.perform(post("/api/admin/hot-scores/recompute-all")
                        .with(user("u")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.window").value("ALL_WINDOWS"))
                .andExpect(jsonPath("$.at").isNotEmpty());

        verify(hotScoresService, times(1)).recomputeAllWindowsDaily();
        verifyNoMoreInteractions(hotScoresService);
    }

    @Test
    void recompute24h_should200_withCsrf_whenAuthenticated() throws Exception {
        mockMvc.perform(post("/api/admin/hot-scores/recompute-24h")
                        .with(user("u"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.window").value("H24"))
                .andExpect(jsonPath("$.at").isNotEmpty());

        verify(hotScoresService, times(1)).recompute24hHourly();
        verifyNoMoreInteractions(hotScoresService);
    }

    @Test
    void recompute7d_should200_withCsrf_whenAuthenticated() throws Exception {
        mockMvc.perform(post("/api/admin/hot-scores/recompute-7d")
                        .with(user("u"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.window").value("D7"))
                .andExpect(jsonPath("$.at").isNotEmpty());

        verify(hotScoresService, times(1)).recomputeAllWindowsDaily();
        verifyNoMoreInteractions(hotScoresService);
    }

    @Test
    void recomputeAll_should200_withCsrf_whenAuthenticated() throws Exception {
        mockMvc.perform(post("/api/admin/hot-scores/recompute-all")
                        .with(user("u"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.window").value("ALL_WINDOWS"))
                .andExpect(jsonPath("$.at").isNotEmpty());

        verify(hotScoresService, times(1)).recomputeAllWindowsDaily();
        verifyNoMoreInteractions(hotScoresService);
    }
}

