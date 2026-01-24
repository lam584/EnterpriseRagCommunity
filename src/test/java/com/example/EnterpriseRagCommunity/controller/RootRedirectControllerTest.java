package com.example.EnterpriseRagCommunity.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.EnterpriseRagCommunity.service.access.AccessControlService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RootRedirectController.class)
@AutoConfigureMockMvc(addFilters = false)
class RootRedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccessControlService accessControlService;

    @Test
    void getRoot_should_redirect_to_portal_home() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/portal/discover/home"));
    }
}
