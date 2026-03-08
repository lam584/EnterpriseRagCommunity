package com.example.EnterpriseRagCommunity.controller.retrieval.admin;

import com.example.EnterpriseRagCommunity.config.MethodSecurityConfig;
import com.example.EnterpriseRagCommunity.config.SecurityConfig;
import com.example.EnterpriseRagCommunity.dto.retrieval.HybridRetrievalConfigDTO;
import com.example.EnterpriseRagCommunity.dto.safety.ContentSafetyCircuitBreakerConfigDTO;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.ai.AiRerankService;
import com.example.EnterpriseRagCommunity.service.ai.LlmGateway;
import com.example.EnterpriseRagCommunity.service.retrieval.HybridRagRetrievalService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalConfigService;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.HybridRetrievalLogsService;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminRetrievalHybridController.class)
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class
})
class AdminRetrievalHybridControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    HybridRetrievalConfigService hybridRetrievalConfigService;

    @MockitoBean
    HybridRagRetrievalService hybridRagRetrievalService;

    @MockitoBean
    HybridRetrievalLogsService hybridRetrievalLogsService;

    @MockitoBean
    AiRerankService aiRerankService;

    @MockitoBean
    LlmGateway llmGateway;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    AdministratorService administratorService;

    @MockitoBean
    AccessControlService accessControlService;

    @MockitoBean
    AccessChangedFilter accessChangedFilter;

    @MockitoBean
    ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(accessChangedFilter).doFilter(any(), any(), any());

        ContentSafetyCircuitBreakerConfigDTO cfg = new ContentSafetyCircuitBreakerConfigDTO();
        cfg.setEnabled(false);
        when(contentSafetyCircuitBreakerService.getConfig()).thenReturn(cfg);
    }

    @Test
    void testRerank_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/admin/retrieval/hybrid/test-rerank")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"q\",\"documents\":[{\"docId\":\"1\",\"text\":\"x\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testRerank_shouldReturn403_withoutWritePermission() throws Exception {
        mockMvc.perform(post("/api/admin/retrieval/hybrid/test-rerank")
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_hybrid:access")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"q\",\"documents\":[{\"docId\":\"1\",\"text\":\"x\"}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRerank_shouldReturn403_withoutCsrf() throws Exception {
        mockMvc.perform(post("/api/admin/retrieval/hybrid/test-rerank")
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_hybrid:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"q\",\"documents\":[{\"docId\":\"1\",\"text\":\"x\"}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRerank_shouldReturnOkFalse_whenQueryBlank() throws Exception {
        mockMvc.perform(post("/api/admin/retrieval/hybrid/test-rerank")
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_hybrid:write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\" \",\"documents\":[{\"docId\":\"1\",\"text\":\"x\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.errorMessage").value("queryText is required"))
                .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void testRerank_shouldReturnOkFalse_whenDocumentsEmpty() throws Exception {
        mockMvc.perform(post("/api/admin/retrieval/hybrid/test-rerank")
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_hybrid:write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"q\",\"documents\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.errorMessage").value("documents is required"))
                .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void testRerank_shouldReturnOkTrue_withResultsAndDebug() throws Exception {
        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setPerDocMaxTokens(1000);
        cfg.setMaxInputTokens(10000);
        cfg.setRerankModel("m1");
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(cfg);

        when(llmGateway.rerankOnceRouted(
                any(),
                Mockito.isNull(),
                anyString(),
                anyString(),
                anyList(),
                Mockito.<Integer>any(),
                anyString(),
                Mockito.eq(false),
                Mockito.isNull()
        )).thenReturn(new AiRerankService.RerankResult(
                List.of(new AiRerankService.RerankHit(0, 0.9)),
                10,
                "p1",
                "m1"
        ));

        mockMvc.perform(post("/api/admin/retrieval/hybrid/test-rerank")
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_hybrid:write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"q\",\"debug\":true,\"useSavedConfig\":true,\"documents\":[{\"docId\":\"d1\",\"title\":\"t\",\"text\":\"x\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.usedProviderId").value("p1"))
                .andExpect(jsonPath("$.usedModel").value("m1"))
                .andExpect(jsonPath("$.totalTokens").value(10))
                .andExpect(jsonPath("$.topN").value(1))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].docId").value("d1"))
                .andExpect(jsonPath("$.debugInfo.docCountInput").value(1))
                .andExpect(jsonPath("$.debugInfo.docCountUsed").value(1));
    }

    @Test
    void testRerank_shouldReturnOkFalse_whenServiceThrows() throws Exception {
        HybridRetrievalConfigDTO cfg = new HybridRetrievalConfigDTO();
        cfg.setPerDocMaxTokens(1000);
        cfg.setMaxInputTokens(10000);
        cfg.setRerankModel("m1");
        when(hybridRetrievalConfigService.getConfigOrDefault()).thenReturn(cfg);

        when(llmGateway.rerankOnceRouted(
                any(),
                Mockito.isNull(),
                anyString(),
                anyString(),
                anyList(),
                Mockito.<Integer>any(),
                anyString(),
                Mockito.eq(false),
                Mockito.isNull()
        )).thenThrow(new IOException("boom"));

        mockMvc.perform(post("/api/admin/retrieval/hybrid/test-rerank")
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_hybrid:write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryText\":\"q\",\"documents\":[{\"docId\":\"d1\",\"text\":\"x\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.errorMessage").value("boom"))
                .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void updateConfig_shouldReturn400_whenServiceThrowsIllegalArgument() throws Exception {
        when(hybridRetrievalConfigService.updateConfig(any()))
                .thenThrow(new IllegalArgumentException("bad payload"));

        mockMvc.perform(put("/api/admin/retrieval/hybrid/config")
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_hybrid:write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad payload"));
    }

    @Test
    void updateConfig_shouldReturn200_whenOk() throws Exception {
        HybridRetrievalConfigDTO resp = new HybridRetrievalConfigDTO();
        resp.setEnabled(true);
        when(hybridRetrievalConfigService.updateConfig(any())).thenReturn(resp);

        mockMvc.perform(put("/api/admin/retrieval/hybrid/config")
                        .with(user("u@example.com")
                                .authorities(new SimpleGrantedAuthority("PERM_admin_retrieval_hybrid:write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }
}

