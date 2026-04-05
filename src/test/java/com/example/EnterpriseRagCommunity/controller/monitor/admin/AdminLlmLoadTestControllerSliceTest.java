package com.example.EnterpriseRagCommunity.controller.monitor.admin;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestStatusDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AdminLlmLoadTestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminLlmLoadTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminLlmLoadTestControllerSliceTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @MockitoBean
    private AdminLlmLoadTestService service;

    @Test
    void export_shouldPassFalse_whenFormatIsJson() throws Exception {
        when(service.export(eq("run-1"), eq(false)))
                .thenReturn(ResponseEntity.ok(outputStream -> outputStream.write(new byte[0])));

        mockMvc.perform(get("/api/admin/metrics/llm-loadtest/run-1/export")
                        .queryParam("format", "json"))
                .andExpect(status().isOk());

        verify(service).export("run-1", false);
    }

    @Test
    void export_shouldPassTrue_whenFormatIsCsv() throws Exception {
        when(service.export(eq("run-1"), eq(true)))
                .thenReturn(ResponseEntity.ok(outputStream -> outputStream.write(new byte[0])));

        mockMvc.perform(get("/api/admin/metrics/llm-loadtest/run-1/export")
                        .queryParam("format", "csv"))
                .andExpect(status().isOk());

        verify(service).export("run-1", true);
    }

    @Test
    void export_shouldDefaultToJson_whenFormatMissing() throws Exception {
        when(service.export(eq("run-1"), eq(false)))
                .thenReturn(ResponseEntity.ok(outputStream -> outputStream.write(new byte[0])));

        mockMvc.perform(get("/api/admin/metrics/llm-loadtest/run-1/export"))
                .andExpect(status().isOk());

        verify(service).export("run-1", false);
    }
}
