package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.AuditLogsViewDTO;
import com.example.EnterpriseRagCommunity.entity.access.enums.AuditResult;
import com.example.EnterpriseRagCommunity.service.access.AuditLogsService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogsControllerTest {

    @Test
    void exportCsv_should_include_header_and_escape_quotes_and_handle_nulls() {
        AuditLogsService service = mock(AuditLogsService.class);
        AuditLogsController controller = new AuditLogsController(service);

        AuditLogsViewDTO row = new AuditLogsViewDTO(
                1L,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                null,
                2L,
                "a\"ctor",
                "ACT",
                "TYPE",
                9L,
                AuditResult.SUCCESS,
                "m\"sg",
                null,
                "t1",
                "POST",
                "/p",
                true,
                null
        );

        when(service.query(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 5000), 1));

        byte[] body = controller.exportCsv(null, null, null, null, null, null, null, null, null, null, null, null).getBody();
        assertThat(body).isNotNull();
        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFid,createdAt,actorId,actorName,action,entityType,entityId,result,traceId,method,path,autoCrud,message\n");
        assertThat(csv).contains("\"a\"\"ctor\"");
        assertThat(csv).contains("\"m\"\"sg\"");
        assertThat(csv).contains("\"\"");
    }

    @Test
    void list_and_getById_should_delegate_to_service() {
        AuditLogsService service = mock(AuditLogsService.class);
        AuditLogsController controller = new AuditLogsController(service);

        when(service.query(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        controller.list(1, 20, null, null, null, null, null, null, null, null, null, null, null, null);
        verify(service).query(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        AuditLogsViewDTO dto = new AuditLogsViewDTO(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(service.getById(1L)).thenReturn(dto);
        assertThat(controller.getById(1L).getBody()).isSameAs(dto);
    }

    @Test
    void getById_endpoint_should_bind_path_variable_without_parameter_metadata() throws Exception {
        AuditLogsService service = mock(AuditLogsService.class);
        AuditLogsController controller = new AuditLogsController(service);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        AuditLogsViewDTO dto = new AuditLogsViewDTO(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(service.getById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/admin/audit-logs/1"))
                .andExpect(status().isOk());

        verify(service).getById(1L);
    }
}
