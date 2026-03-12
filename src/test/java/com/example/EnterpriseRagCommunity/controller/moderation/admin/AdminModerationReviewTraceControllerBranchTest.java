package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationReviewTraceTaskDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationReviewTraceTaskPageDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.service.moderation.trace.AdminModerationReviewTraceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModerationReviewTraceControllerBranchTest {

    @Mock
    private AdminModerationReviewTraceService service;

    @Test
    void listTasks_shouldParseAndDelegate() {
        AdminModerationReviewTraceController controller = new AdminModerationReviewTraceController(service);
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();
        AdminModerationReviewTraceTaskPageDTO page = mock(AdminModerationReviewTraceTaskPageDTO.class);
        try (MockedStatic<AdminModerationReviewTraceService> mocked = mockStatic(AdminModerationReviewTraceService.class)) {
            mocked.when(() -> AdminModerationReviewTraceService.parseLocalDateTimeOrNull("f")).thenReturn(from);
            mocked.when(() -> AdminModerationReviewTraceService.parseLocalDateTimeOrNull("t")).thenReturn(to);
            when(service.listTasks(1L, null, 2L, "trace", QueueStatus.HUMAN, from, to, 3, 4)).thenReturn(page);

            var out = controller.listTasks(1L, null, 2L, "trace", QueueStatus.HUMAN, "f", "t", 3, 4);

            assertSame(page, out);
            verify(service).listTasks(1L, null, 2L, "trace", QueueStatus.HUMAN, from, to, 3, 4);
        }
    }

    @Test
    void taskDetail_shouldDelegate() {
        AdminModerationReviewTraceController controller = new AdminModerationReviewTraceController(service);
        AdminModerationReviewTraceTaskDetailDTO detail = mock(AdminModerationReviewTraceTaskDetailDTO.class);
        when(service.getTaskDetail(10L)).thenReturn(detail);

        var out = controller.taskDetail(10L);

        assertSame(detail, out);
        verify(service).getTaskDetail(10L);
    }
}
