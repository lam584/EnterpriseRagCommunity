package com.example.EnterpriseRagCommunity.controller;

import com.example.EnterpriseRagCommunity.dto.monitor.UploadFormatsConfigDTO;
import com.example.EnterpriseRagCommunity.service.monitor.UploadFormatsConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PublicUploadFormatsConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicUploadFormatsConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadFormatsConfigService uploadFormatsConfigService;

    @Test
    void getFormatsConfig_shouldReturn200() throws Exception {
        UploadFormatsConfigDTO dto = new UploadFormatsConfigDTO();
        dto.setEnabled(true);
        when(uploadFormatsConfigService.getConfig()).thenReturn(dto);

        mockMvc.perform(get("/api/public/uploads/formats-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }
}

