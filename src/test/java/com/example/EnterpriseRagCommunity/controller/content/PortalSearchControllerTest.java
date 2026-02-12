package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.service.content.PortalSearchService;
import com.example.EnterpriseRagCommunity.service.access.AccessControlService;
import com.example.EnterpriseRagCommunity.repository.access.UsersRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PortalSearchController.class)
@AutoConfigureMockMvc(addFilters = false)
class PortalSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortalSearchService portalSearchService;

    @MockBean
    private AccessControlService accessControlService;

    @MockBean
    private UsersRepository usersRepository;

    @Test
    void search_should_return_200() throws Exception {
        when(portalSearchService.search(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/portal/search").param("q", "test"))
                .andExpect(status().isOk());
    }
}
