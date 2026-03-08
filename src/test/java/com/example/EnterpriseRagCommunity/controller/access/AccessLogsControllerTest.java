package com.example.EnterpriseRagCommunity.controller.access;

import com.example.EnterpriseRagCommunity.dto.access.AccessLogsViewDTO;
import com.example.EnterpriseRagCommunity.service.access.AccessLogsService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessLogsControllerTest {

    @Test
    void exportCsv_should_include_header_and_escape_quotes_and_handle_nulls() {
        AccessLogsService service = mock(AccessLogsService.class);
        AccessLogsController controller = new AccessLogsController(service);

        AccessLogsViewDTO row = new AccessLogsViewDTO(
                1L,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                null,
                2L,
                "u\"name",
                "GET",
                "/p",
                null,
                200,
                10,
                "127.0.0.1",
                null,
                null,
                null,
                null,
                null,
                "rid",
                "tid",
                "ua\"x",
                null,
                null
        );

        when(service.query(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 5000), 1));

        byte[] body = controller.exportCsv(null, null, null, null, null, null, null, null, null, null, null, null).getBody();
        assertThat(body).isNotNull();
        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFid,createdAt,userId,username,method,path,statusCode,latencyMs,clientIp,clientPort,serverIp,serverPort,requestId,traceId,userAgent\n");
        assertThat(csv).contains("\"u\"\"name\"");
        assertThat(csv).contains("\"ua\"\"x\"");
        assertThat(csv).contains("\"\"");
    }

    @Test
    void list_and_getById_should_delegate_to_service() {
        AccessLogsService service = mock(AccessLogsService.class);
        AccessLogsController controller = new AccessLogsController(service);

        when(service.query(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        controller.list(1, 20, null, null, null, null, null, null, null, null, null, null, null, null);
        verify(service).query(anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        AccessLogsViewDTO dto = new AccessLogsViewDTO(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        when(service.getById(1L)).thenReturn(dto);
        assertThat(controller.getById(1L).getBody()).isSameAs(dto);
    }
}
