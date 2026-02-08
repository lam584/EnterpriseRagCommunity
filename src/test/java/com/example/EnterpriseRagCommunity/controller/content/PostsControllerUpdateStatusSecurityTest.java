package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.content.PortalPostsService;
import com.example.EnterpriseRagCommunity.service.content.PostsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
class PostsControllerUpdateStatusSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PostsService postsService;

    @MockBean
    PortalPostsService portalPostsService;

    @MockBean
    AdministratorService administratorService;

    @Test
    void updateStatus_shouldDeny_withoutPermission() throws Exception {
        mockMvc.perform(put("/api/posts/1/status")
                        .with(user("u").authorities(new SimpleGrantedAuthority("PERM_admin_moderation_queue:read")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_shouldAllow_withActionPermission() throws Exception {
        PostsEntity p = new PostsEntity();
        p.setId(1L);
        p.setStatus(PostStatus.PUBLISHED);
        Mockito.when(postsService.updateStatus(Mockito.eq(1L), Mockito.eq(PostStatus.PUBLISHED))).thenReturn(p);

        mockMvc.perform(put("/api/posts/1/status")
                        .with(user("u").authorities(new SimpleGrantedAuthority("PERM_admin_moderation_queue:action")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk());
    }
}

