package com.example.EnterpriseRagCommunity.controller.monitor.admin;

import com.example.EnterpriseRagCommunity.service.monitor.LlmQueueMonitorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.access-refresh.enabled=false"
})
@AutoConfigureMockMvc
class AdminLlmQueueControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    LlmQueueMonitorService llmQueueMonitorService;

    @Test
    void updateConfig_shouldDeny_withoutCsrf() throws Exception {
        mockMvc.perform(put("/api/admin/metrics/llm-queue/config")
                        .with(user("u").authorities(new SimpleGrantedAuthority("PERM_admin_metrics_llm_queue:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxConcurrent\":2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateConfig_shouldDeny_withoutWritePermission() throws Exception {
        mockMvc.perform(put("/api/admin/metrics/llm-queue/config")
                        .with(user("u").authorities(new SimpleGrantedAuthority("PERM_admin_metrics_llm_queue:read")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxConcurrent\":2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateConfig_shouldAllow_withCsrfAndWritePermission() throws Exception {
        mockMvc.perform(put("/api/admin/metrics/llm-queue/config")
                        .with(user("u").authorities(new SimpleGrantedAuthority("PERM_admin_metrics_llm_queue:write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxConcurrent\":2}"))
                .andExpect(status().isOk());
    }
}
