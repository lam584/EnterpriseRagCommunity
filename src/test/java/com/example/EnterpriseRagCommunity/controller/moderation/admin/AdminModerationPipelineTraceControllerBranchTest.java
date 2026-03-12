package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationPipelineRunDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationPipelineRunHistoryPageDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.service.moderation.trace.AdminModerationPipelineTraceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModerationPipelineTraceControllerBranchTest {

    @Mock
    private AdminModerationPipelineTraceService service;

    @Test
    void latestByQueueId_delegatesToService() {
        AdminModerationPipelineTraceController controller = new AdminModerationPipelineTraceController(service);
        AdminModerationPipelineRunDetailDTO dto = new AdminModerationPipelineRunDetailDTO(null, java.util.List.of());
        when(service.getLatestByQueueId(1L)).thenReturn(dto);

        AdminModerationPipelineRunDetailDTO actual = controller.latestByQueueId(1L);

        assertSame(dto, actual);
        verify(service).getLatestByQueueId(1L);
    }

    @Test
    void history_delegatesToService() {
        AdminModerationPipelineTraceController controller = new AdminModerationPipelineTraceController(service);
        AdminModerationPipelineRunHistoryPageDTO page = new AdminModerationPipelineRunHistoryPageDTO(java.util.List.of(), 0, 0L, 0, 0);
        when(service.history(1L, ContentType.POST, 2L, 3, 4)).thenReturn(page);

        AdminModerationPipelineRunHistoryPageDTO actual = controller.history(1L, ContentType.POST, 2L, 3, 4);

        assertSame(page, actual);
        verify(service).history(1L, ContentType.POST, 2L, 3, 4);
    }

    @Test
    void byRunId_delegatesToService() {
        AdminModerationPipelineTraceController controller = new AdminModerationPipelineTraceController(service);
        AdminModerationPipelineRunDetailDTO dto = new AdminModerationPipelineRunDetailDTO(null, java.util.List.of());
        when(service.getByRunId(10L)).thenReturn(dto);

        AdminModerationPipelineRunDetailDTO actual = controller.byRunId(10L);

        assertSame(dto, actual);
        verify(service).getByRunId(10L);
    }
}
