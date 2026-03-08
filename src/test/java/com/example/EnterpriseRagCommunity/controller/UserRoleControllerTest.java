package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.access.UserRolesCreateDTO;
import com.example.EnterpriseRagCommunity.service.UserRoleService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserRoleController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRoleService userRoleService;

    private static UserRolesCreateDTO validDto(Long id) {
        UserRolesCreateDTO dto = new UserRolesCreateDTO();
        dto.setId(id);
        dto.setTenantId(1L);
        dto.setRoles("USER");
        dto.setCanLogin(true);
        dto.setCanViewAnnouncement(true);
        dto.setCanViewHelpArticles(true);
        dto.setCanResetOwnPassword(true);
        dto.setCanComment(true);
        dto.setNotes("n");
        return dto;
    }

    private static final String VALID_BODY = """
            {
              "tenantId": 1,
              "roles": "USER",
              "canLogin": true,
              "canViewAnnouncement": true,
              "canViewHelpArticles": true,
              "canResetOwnPassword": true,
              "canComment": true,
              "notes": "n"
            }
            """;

    @Test
    void getAllUserRolesNoPage_shouldReturn200() throws Exception {
        when(userRoleService.listAll()).thenReturn(List.of(validDto(1L)));

        mockMvc.perform(get("/api/user-roles/all"))
                .andExpect(status().isOk());
    }

    @Test
    void getAllUserRoles_shouldReturn200() throws Exception {
        when(userRoleService.list(any())).thenReturn(new PageImpl<>(List.of(validDto(1L)), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/user-roles")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void getUserRoleById_shouldReturn200_whenFound() throws Exception {
        when(userRoleService.getById(1L)).thenReturn(validDto(1L));

        mockMvc.perform(get("/api/user-roles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getUserRoleById_shouldReturn404_whenNotFound() throws Exception {
        when(userRoleService.getById(1L)).thenThrow(new EntityNotFoundException("not found"));

        mockMvc.perform(get("/api/user-roles/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("用户角色不存在"));
    }

    @Test
    void createUserRole_shouldReturn201_whenSuccess() throws Exception {
        when(userRoleService.create(any())).thenReturn(validDto(1L));

        mockMvc.perform(post("/api/user-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createUserRole_shouldReturn400_whenServiceThrows() throws Exception {
        when(userRoleService.create(any())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/user-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("创建用户角色失败：boom"));
    }

    @Test
    void updateUserRole_shouldReturn200_whenSuccess() throws Exception {
        when(userRoleService.update(eq(1L), any())).thenReturn(validDto(1L));

        mockMvc.perform(put("/api/user-roles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateUserRole_shouldReturn404_whenNotFound() throws Exception {
        when(userRoleService.update(eq(1L), any())).thenThrow(new EntityNotFoundException("not found"));

        mockMvc.perform(put("/api/user-roles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("用户角色不存在"));
    }

    @Test
    void updateUserRole_shouldReturn400_whenServiceThrows() throws Exception {
        when(userRoleService.update(eq(1L), any())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(put("/api/user-roles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("更新用户角色失败：boom"));
    }

    @Test
    void deleteUserRole_shouldReturn200_whenSuccess() throws Exception {
        mockMvc.perform(delete("/api/user-roles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("删除成功"));
    }

    @Test
    void deleteUserRole_shouldReturn404_whenNotFound() throws Exception {
        doThrow(new EntityNotFoundException("not found")).when(userRoleService).delete(1L);

        mockMvc.perform(delete("/api/user-roles/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("用户角色不存在"));
    }

    @Test
    void deleteUserRole_shouldReturn400_whenServiceThrows() throws Exception {
        doThrow(new RuntimeException("boom")).when(userRoleService).delete(1L);

        mockMvc.perform(delete("/api/user-roles/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("删除用户角色失败：可能有其他记录在使用"));
    }
}
