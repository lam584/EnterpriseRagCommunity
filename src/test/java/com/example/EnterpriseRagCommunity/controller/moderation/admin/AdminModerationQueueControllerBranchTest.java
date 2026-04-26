package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkProgressDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBatchRequeueRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBatchRequeueResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueRiskTagsRequest;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminModerationQueueControllerBranchTest {

    @Mock
    private AdminModerationQueueService queueService;
    @Mock
    private ModerationChunkReviewService chunkReviewService;

    @Test
    void getChunkProgress_shouldCoverIncludeAndLimitBranches() {
        AdminModerationQueueController controller = newController();
        AdminModerationChunkProgressDTO dto = new AdminModerationChunkProgressDTO();
        when(chunkReviewService.getProgress(1L, true, 300)).thenReturn(dto);
        when(chunkReviewService.getProgress(2L, false, 0)).thenReturn(dto);

        var a = controller.getChunkProgress(1L, 1, 999);
        var b = controller.getChunkProgress(2L, 0, -5);

        assertSame(dto, a);
        assertSame(dto, b);
        verify(chunkReviewService).getProgress(1L, true, 300);
        verify(chunkReviewService).getProgress(2L, false, 0);
    }

    @Test
    void setRiskTags_and_batchRequeue_shouldCoverNullRequestBranches() {
        AdminModerationQueueController controller = newController();
        AdminModerationQueueDetailDTO detail = new AdminModerationQueueDetailDTO();
        AdminModerationQueueBatchRequeueResponse batch = new AdminModerationQueueBatchRequeueResponse();
        when(queueService.setRiskTags(10L, null)).thenReturn(detail);
        when(queueService.batchRequeueToAuto(null, null, null)).thenReturn(batch);

        var a = controller.setRiskTags(10L, null);
        var b = controller.batchRequeue(null);

        assertSame(detail, a);
        assertSame(batch, b);
        verify(queueService).setRiskTags(10L, null);
        verify(queueService).batchRequeueToAuto(null, null, null);
    }

    @Test
    void setRiskTags_and_batchRequeue_shouldCoverNonNullRequestBranches() {
        AdminModerationQueueController controller = newController();
        AdminModerationQueueDetailDTO detail = new AdminModerationQueueDetailDTO();
        AdminModerationQueueBatchRequeueResponse batch = new AdminModerationQueueBatchRequeueResponse();
        AdminModerationQueueRiskTagsRequest tagsReq = new AdminModerationQueueRiskTagsRequest();
        tagsReq.setRiskTags(List.of("r1", "r2"));
        AdminModerationQueueBatchRequeueRequest req = new AdminModerationQueueBatchRequeueRequest();
        req.setIds(List.of(1L, 2L));
        req.setReason("manual");
        req.setReviewStage("appeal");
        when(queueService.setRiskTags(11L, List.of("r1", "r2"))).thenReturn(detail);
        when(queueService.batchRequeueToAuto(List.of(1L, 2L), "manual", "appeal")).thenReturn(batch);

        var a = controller.setRiskTags(11L, tagsReq);
        var b = controller.batchRequeue(req);

        assertSame(detail, a);
        assertSame(batch, b);
        verify(queueService).setRiskTags(11L, List.of("r1", "r2"));
        verify(queueService).batchRequeueToAuto(List.of(1L, 2L), "manual", "appeal");
    }

    @Test
    void list_shouldMapQueryArgumentsToDto() {
        AdminModerationQueueController controller = newController();
        org.springframework.data.domain.PageImpl<com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueItemDTO> page =
                new org.springframework.data.domain.PageImpl<>(List.of());
        when(queueService.list(org.mockito.ArgumentMatchers.any())).thenReturn(page);

        var out = controller.list(2, 30, "id", "desc", 1L, 2L, ContentType.POST, 3L, null, null, 9L, 1, 5, null, null);

        assertSame(page, out);
        verify(queueService).list(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void endpoints_shouldBindPathVariableWithoutParameterMetadata() throws Exception {
        AdminModerationQueueService queueService = mock(AdminModerationQueueService.class);
        ModerationChunkReviewService chunkReviewService = mock(ModerationChunkReviewService.class);
        AdminModerationQueueController controller = new AdminModerationQueueController(queueService, chunkReviewService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        AdminModerationQueueDetailDTO detail = new AdminModerationQueueDetailDTO();
        detail.setId(1L);
        when(queueService.getDetail(1L)).thenReturn(detail);
        when(queueService.approve(1L, "ok")).thenReturn(detail);

        mockMvc.perform(get("/api/admin/moderation/queue/1"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/moderation/queue/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ok\"}"))
                .andExpect(status().isOk());

        verify(queueService).getDetail(1L);
        verify(queueService).approve(1L, "ok");
    }

    private AdminModerationQueueController newController() {
        return new AdminModerationQueueController(queueService, chunkReviewService);
    }
}
