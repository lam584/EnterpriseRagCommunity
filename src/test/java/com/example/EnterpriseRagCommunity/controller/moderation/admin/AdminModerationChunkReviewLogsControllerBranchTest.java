package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkContentPreviewDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkLogDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkLogItemDTO;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationChunkReviewLogsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminModerationChunkReviewLogsControllerBranchTest {

    @Mock
    private AdminModerationChunkReviewLogsService service;

    @Test
    void list_shouldDelegate() {
        AdminModerationChunkReviewLogsController controller = new AdminModerationChunkReviewLogsController(service);
        AdminModerationChunkLogItemDTO item = new AdminModerationChunkLogItemDTO();
        when(service.listRecent(20, 1L, null, null, null, null, "k")).thenReturn(List.of(item));

        var out = controller.list(20, 1L, null, null, null, null, "k");

        assertEquals(1, out.size());
        verify(service).listRecent(20, 1L, null, null, null, null, "k");
    }

    @Test
    void get_shouldValidateId() {
        AdminModerationChunkReviewLogsController controller = new AdminModerationChunkReviewLogsController(service);
        assertThrows(IllegalArgumentException.class, () -> controller.get(null));
        assertThrows(IllegalArgumentException.class, () -> controller.get(0L));
        AdminModerationChunkLogDetailDTO detail = new AdminModerationChunkLogDetailDTO();
        when(service.getDetail(8L)).thenReturn(detail);

        var out = controller.get(8L);

        assertSame(detail, out);
    }

    @Test
    void getContent_shouldValidateId() {
        AdminModerationChunkReviewLogsController controller = new AdminModerationChunkReviewLogsController(service);
        assertThrows(IllegalArgumentException.class, () -> controller.getContent(null));
        assertThrows(IllegalArgumentException.class, () -> controller.getContent(-1L));
        AdminModerationChunkContentPreviewDTO dto = new AdminModerationChunkContentPreviewDTO();
        when(service.getContentPreview(7L)).thenReturn(dto);

        var out = controller.getContent(7L);

        assertSame(dto, out);
    }
}
