package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueTaskDetailDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.LlmQueueTaskHistoryEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmQueueTaskHistoryRepository;
import com.example.EnterpriseRagCommunity.service.ai.LlmCallQueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmQueueMonitorServiceGetTaskDetailBranchTest {

    @Mock
    LlmCallQueueService llmCallQueueService;

    @Mock
    LlmQueueTaskHistoryRepository llmQueueTaskHistoryRepository;

    private LlmQueueMonitorService newService() {
        return new LlmQueueMonitorService(llmCallQueueService, llmQueueTaskHistoryRepository, new LlmQueueProperties());
    }

    @Test
    void getTaskDetail_shouldReturnDetail_whenInMemoryHit() {
        LlmQueueMonitorService svc = newService();

        LlmCallQueueService.TaskDetailSnapshot snap = new LlmCallQueueService.TaskDetailSnapshot(
                "tid",
                10L,
                1,
                null,
                "lbl",
                null,
                "p1",
                "m1",
                1_000L,
                2_000L,
                3_000L,
                11L,
                22L,
                1,
                2,
                3,
                4.5,
                "err",
                "in",
                "out"
        );

        when(llmCallQueueService.findTaskDetail("tid")).thenReturn(snap);

        AdminLlmQueueTaskDetailDTO out = svc.getTaskDetail("tid");

        assertNotNull(out);
        assertEquals("tid", out.getId());
        assertEquals(10L, out.getSeq());
        assertEquals("lbl", out.getLabel());
        assertEquals("p1", out.getProviderId());
        assertEquals("m1", out.getModel());
        assertEquals("in", out.getInput());
        assertEquals("out", out.getOutput());
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(1_000L), ZoneId.systemDefault()), out.getCreatedAt());
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(2_000L), ZoneId.systemDefault()), out.getStartedAt());
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(3_000L), ZoneId.systemDefault()), out.getFinishedAt());
        verify(llmQueueTaskHistoryRepository, never()).findById(anyString());
    }

    @Test
    void getTaskDetail_shouldReturnNull_whenDbMiss() {
        LlmQueueMonitorService svc = newService();
        when(llmCallQueueService.findTaskDetail("tid")).thenReturn(null);
        when(llmQueueTaskHistoryRepository.findById("tid")).thenReturn(Optional.empty());

        AdminLlmQueueTaskDetailDTO out = svc.getTaskDetail("tid");

        assertNull(out);
        verify(llmQueueTaskHistoryRepository).findById("tid");
    }

    @Test
    void getTaskDetail_shouldReturnMappedEntity_whenDbHit() {
        LlmQueueMonitorService svc = newService();
        when(llmCallQueueService.findTaskDetail("tid")).thenReturn(null);

        LlmQueueTaskHistoryEntity e = new LlmQueueTaskHistoryEntity();
        e.setTaskId("tid");
        e.setSeq(99L);
        e.setProviderId("p2");
        e.setModel("m2");
        e.setCreatedAt(LocalDateTime.parse("2025-01-01T00:00:00"));
        e.setInput("ein");
        e.setOutput("eout");
        when(llmQueueTaskHistoryRepository.findById("tid")).thenReturn(Optional.of(e));

        AdminLlmQueueTaskDetailDTO out = svc.getTaskDetail("tid");

        assertNotNull(out);
        assertEquals("tid", out.getId());
        assertEquals(99L, out.getSeq());
        assertNull(out.getLabel());
        assertEquals("p2", out.getProviderId());
        assertEquals("m2", out.getModel());
        assertEquals("ein", out.getInput());
        assertEquals("eout", out.getOutput());
    }

    @Test
    void mapTask_shouldReturnNull_whenEntityNull() {
        LlmQueueMonitorService svc = newService();

        Object out = ReflectionTestUtils.invokeMethod(svc, "mapTask", (Object) null);

        assertNull(out);
    }
}
