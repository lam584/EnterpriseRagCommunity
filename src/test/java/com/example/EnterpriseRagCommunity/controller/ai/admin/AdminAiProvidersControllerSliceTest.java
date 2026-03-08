package com.example.EnterpriseRagCommunity.controller.ai.admin;

import com.example.EnterpriseRagCommunity.config.MethodSecurityConfig;
import com.example.EnterpriseRagCommunity.config.SecurityConfig;
import com.example.EnterpriseRagCommunity.dto.ai.AiProvidersConfigDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.security.AccessChangedFilter;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.service.ai.AiProvidersConfigService;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAiProvidersController.class)
@Import({
        SecurityConfig.class,
        MethodSecurityConfig.class
})
class AdminAiProvidersControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AiProvidersConfigService aiProvidersConfigService;

    @MockitoBean
    AdministratorService administratorService;

    @MockitoBean
    ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService;

    @MockitoBean
    UserDetailsService userDetailsService;

    @MockitoBean
    AccessControlService accessControlService;

    @MockitoBean
    AccessChangedFilter accessChangedFilter;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(accessChangedFilter).doFilter(any(), any(), any());
    }

    @Test
    void getConfig_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/ai/providers/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getConfig_shouldReturn403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/admin/ai/providers/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getConfig_shouldReturn200_whenOk() throws Exception {
        AiProvidersConfigDTO dto = new AiProvidersConfigDTO();
        dto.setActiveProviderId("p1");
        when(aiProvidersConfigService.getAdminConfig()).thenReturn(dto);

        mockMvc.perform(get("/api/admin/ai/providers/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProviderId").value("p1"));
    }

    @Test
    void updateConfig_shouldReturn401_whenAnonymous() throws Exception {
        mockMvc.perform(put("/api/admin/ai/providers/config")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activeProviderId\":\"p1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateConfig_shouldReturn403_withoutPermission() throws Exception {
        mockMvc.perform(put("/api/admin/ai/providers/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "read")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activeProviderId\":\"p1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateConfig_shouldReturn403_withoutCsrf() throws Exception {
        mockMvc.perform(put("/api/admin/ai/providers/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activeProviderId\":\"p1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateConfig_shouldReturn200_whenOk_withoutUserResolution() throws Exception {
        AiProvidersConfigDTO dto = new AiProvidersConfigDTO();
        dto.setActiveProviderId("p2");
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.empty());
        when(aiProvidersConfigService.updateAdminConfig(any(AiProvidersConfigDTO.class), eq(null))).thenReturn(dto);

        mockMvc.perform(put("/api/admin/ai/providers/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activeProviderId\":\"p2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProviderId").value("p2"));
    }

    @Test
    void updateConfig_shouldRetryOnce_thenReturn200() throws Exception {
        UsersEntity u = new UsersEntity();
        u.setId(10L);
        when(administratorService.findByUsername("u@example.com")).thenReturn(Optional.of(u));

        AiProvidersConfigDTO dto = new AiProvidersConfigDTO();
        dto.setActiveProviderId("p3");

        when(aiProvidersConfigService.updateAdminConfig(any(AiProvidersConfigDTO.class), eq(10L)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Object.class, 1L))
                .thenReturn(dto);

        mockMvc.perform(put("/api/admin/ai/providers/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activeProviderId\":\"p3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProviderId").value("p3"));

        verify(aiProvidersConfigService, times(2)).updateAdminConfig(any(AiProvidersConfigDTO.class), eq(10L));
    }

    @Test
    void updateConfig_shouldReturn409_whenOptimisticLockExhausted() throws Exception {
        when(aiProvidersConfigService.updateAdminConfig(any(AiProvidersConfigDTO.class), eq(null)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Object.class, 1L));

        mockMvc.perform(put("/api/admin/ai/providers/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activeProviderId\":\"p4\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("保存失败：配置已被其他操作更新，请刷新后重试"));
    }

    @Test
    void updateConfig_shouldReturn500_whenDataIntegrityViolationExhausted() throws Exception {
        when(aiProvidersConfigService.updateAdminConfig(any(AiProvidersConfigDTO.class), eq(null)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        mockMvc.perform(put("/api/admin/ai/providers/config")
                        .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activeProviderId\":\"p5\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("操作失败：数据已存在"));
    }

    @Test
    void updateConfig_shouldPreserveInterruptedFlag_whenSleepInterrupted() throws Exception {
        when(aiProvidersConfigService.updateAdminConfig(any(AiProvidersConfigDTO.class), eq(null)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Object.class, 1L));

        MockHttpServletRequestBuilder req = put("/api/admin/ai/providers/config")
                .with(user("u@example.com").authorities(AiAdminControllerTestSupport.perm("admin_users", "write")))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activeProviderId\":\"p6\"}");

        Thread.interrupted();
        Thread.currentThread().interrupt();
        try {
            MvcResult r = mockMvc.perform(req).andReturn();
            org.junit.jupiter.api.Assertions.assertEquals(409, r.getResponse().getStatus());
            org.junit.jupiter.api.Assertions.assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}

