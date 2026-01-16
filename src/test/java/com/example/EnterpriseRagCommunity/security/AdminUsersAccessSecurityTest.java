package com.example.EnterpriseRagCommunity.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.annotation.Resource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for RBAC model:
 *
 * The project issues authorities like:
 *  - ROLE_ID_{id}
 *  - PERM_{resource}:{action}
 *
 * So admin endpoints must check PERM_* (not hasRole('ADMIN')).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUsersAccessSecurityTest {

    @Resource
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "u", authorities = {"PERM_admin_users:access"})
    void rolePermissionsEndpoints_shouldAllow_withAdminUsersAccessPerm() throws Exception {
        mockMvc.perform(get("/api/admin/role-permissions/roles"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "u", authorities = {})
    void rolePermissionsEndpoints_shouldDeny_withoutPerm() throws Exception {
        mockMvc.perform(get("/api/admin/role-permissions/roles"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "u", authorities = {"PERM_admin_users:access"})
    void permissionsEndpoints_shouldAllow_withAdminUsersAccessPerm() throws Exception {
        mockMvc.perform(get("/api/admin/permissions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "u", authorities = {})
    void permissionsEndpoints_shouldDeny_withoutPerm() throws Exception {
        mockMvc.perform(get("/api/admin/permissions"))
                .andExpect(status().isForbidden());
    }
}

