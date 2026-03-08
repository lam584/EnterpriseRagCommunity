package com.example.EnterpriseRagCommunity.controller.content.admin;

import com.example.EnterpriseRagCommunity.dto.content.admin.PostFileExtractionAdminDetailDTO;
import com.example.EnterpriseRagCommunity.dto.content.admin.PostFileExtractionAdminListItemDTO;
import com.example.EnterpriseRagCommunity.service.content.admin.AdminPostFilesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.access-refresh.enabled=false"
})
@AutoConfigureMockMvc
class AdminPostFilesControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminPostFilesService adminPostFilesService;

    @Test
    void list_shouldDeny_withoutReadPermission() throws Exception {
        mockMvc.perform(get("/api/admin/post-files")
                        .with(user("u")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_shouldAllow_withReadPermission() throws Exception {
        when(adminPostFilesService.list(anyInt(), anyInt(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.<PostFileExtractionAdminListItemDTO>of(), PageRequest.of(0, 20), 0));
        mockMvc.perform(get("/api/admin/post-files")
                        .with(user("u").authorities(new SimpleGrantedAuthority("PERM_admin_posts:read"))))
                .andExpect(status().isOk());
    }

    @Test
    void reextract_shouldDeny_withoutCsrf() throws Exception {
        when(adminPostFilesService.reextract(eq(1L))).thenReturn(new PostFileExtractionAdminDetailDTO());
        mockMvc.perform(post("/api/admin/post-files/1/reextract")
                        .with(user("u").authorities(new SimpleGrantedAuthority("PERM_admin_posts:update")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reextract_shouldAllow_withCsrfAndUpdatePermission() throws Exception {
        when(adminPostFilesService.reextract(eq(1L))).thenReturn(new PostFileExtractionAdminDetailDTO());
        mockMvc.perform(post("/api/admin/post-files/1/reextract")
                        .with(user("u").authorities(new SimpleGrantedAuthority("PERM_admin_posts:update")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}

