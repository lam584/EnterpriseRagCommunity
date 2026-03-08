package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestHistoryRecordDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestHistoryUpsertRequestDTO;
import com.example.EnterpriseRagCommunity.entity.monitor.LlmLoadTestRunHistoryEntity;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunDetailRepository;
import com.example.EnterpriseRagCommunity.repository.monitor.LlmLoadTestRunHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminLlmLoadTestHistoryServiceBranchUnitTest {

    private final ObjectMapper realOm = new ObjectMapper();

    @Mock
    LlmLoadTestRunHistoryRepository repository;

    @Mock
    LlmLoadTestRunDetailRepository detailRepository;

    @Mock
    ObjectMapper objectMapper;

    @Captor
    ArgumentCaptor<LlmLoadTestRunHistoryEntity> entityCaptor;

    @Captor
    ArgumentCaptor<Pageable> pageableCaptor;

    private AdminLlmLoadTestHistoryService newService() {
        return new AdminLlmLoadTestHistoryService(repository, detailRepository, objectMapper);
    }

    private static AdminLlmLoadTestHistoryUpsertRequestDTO req(String runId, JsonNode summary) {
        AdminLlmLoadTestHistoryUpsertRequestDTO req = new AdminLlmLoadTestHistoryUpsertRequestDTO();
        req.setRunId(runId);
        req.setSummary(summary);
        return req;
    }

    @Test
    void upsert_nullReq_throws() {
        AdminLlmLoadTestHistoryService svc = newService();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.upsert(null));
        assertEquals("参数不能为空", ex.getMessage());
    }

    @Test
    void upsert_blankRunId_and_summaryNull_throwsRunIdEmpty() {
        AdminLlmLoadTestHistoryService svc = newService();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.upsert(req("  ", null)));
        assertEquals("runId 不能为空", ex.getMessage());
    }

    @Test
    void upsert_summaryJsonNull_throwsSummaryEmpty() {
        AdminLlmLoadTestHistoryService svc = newService();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.upsert(req("r1", NullNode.getInstance())));
        assertEquals("summary 不能为空", ex.getMessage());
    }

    @Test
    void upsert_runIdFallsBackFromSummary_mapsFields_and_parsesInstantCreatedAt() throws Exception {
        AdminLlmLoadTestHistoryService svc = newService();

        var cfg = realOm.createObjectNode();
        cfg.put("providerId", "  p1  ");
        cfg.put("model", "  m1  ");
        cfg.put("stream", true);
        cfg.put("enableThinking", false);
        cfg.put("timeoutMs", 123);
        cfg.put("retries", 2);
        cfg.put("retryDelayMs", 456);

        var summary = realOm.createObjectNode();
        summary.put("runId", "  rid-1  ");
        summary.put("createdAt", "2025-01-01T00:00:00Z");
        summary.set("config", cfg);

        when(repository.findById("rid-1")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any(JsonNode.class))).thenReturn("{\"ok\":true}");
        when(objectMapper.readTree(anyString())).thenReturn(realOm.createObjectNode().put("ok", true));
        when(repository.save(any(LlmLoadTestRunHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminLlmLoadTestHistoryRecordDTO dto = svc.upsert(req("  ", summary));

        verify(repository).save(entityCaptor.capture());
        LlmLoadTestRunHistoryEntity saved = entityCaptor.getValue();

        assertEquals("rid-1", saved.getRunId());
        assertEquals(LocalDateTime.ofInstant(Instant.parse("2025-01-01T00:00:00Z"), ZoneId.systemDefault()), saved.getCreatedAt());
        assertEquals("p1", saved.getProviderId());
        assertEquals("m1", saved.getModel());
        assertEquals(true, saved.getStream());
        assertEquals(false, saved.getEnableThinking());
        assertEquals(123, saved.getTimeoutMs());
        assertEquals(2, saved.getRetries());
        assertEquals(456, saved.getRetryDelayMs());
        assertEquals("{\"ok\":true}", saved.getSummaryJson());
        assertNotNull(saved.getUpdatedAt());

        assertEquals("rid-1", dto.getRunId());
        assertNotNull(dto.getSummary());
    }

    @Test
    void upsert_existingEntity_cfgMissing_parsesLocalDateTime_and_trimsToNull() throws Exception {
        AdminLlmLoadTestHistoryService svc = newService();

        LlmLoadTestRunHistoryEntity existing = new LlmLoadTestRunHistoryEntity();
        existing.setRunId("old");
        existing.setSummaryJson("{}");
        existing.setCreatedAt(LocalDateTime.now().minusDays(1));
        existing.setUpdatedAt(LocalDateTime.now().minusDays(1));

        var summary = realOm.createObjectNode();
        summary.put("createdAt", "2025-01-01T00:00:00");

        when(repository.findById("rid-2")).thenReturn(Optional.of(existing));
        when(objectMapper.writeValueAsString(any(JsonNode.class))).thenReturn("{\"x\":1}");
        when(objectMapper.readTree(anyString())).thenReturn(realOm.createObjectNode().put("x", 1));
        when(repository.save(any(LlmLoadTestRunHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminLlmLoadTestHistoryRecordDTO dto = svc.upsert(req("  rid-2  ", summary));

        verify(repository).save(entityCaptor.capture());
        LlmLoadTestRunHistoryEntity saved = entityCaptor.getValue();

        assertSame(existing, saved);
        assertEquals("rid-2", saved.getRunId());
        assertEquals(LocalDateTime.parse("2025-01-01T00:00:00"), saved.getCreatedAt());
        assertNull(saved.getProviderId());
        assertNull(saved.getModel());
        assertNull(saved.getStream());
        assertNull(saved.getEnableThinking());
        assertNull(saved.getTimeoutMs());
        assertNull(saved.getRetries());
        assertNull(saved.getRetryDelayMs());
        assertNotNull(saved.getUpdatedAt());
        assertNotNull(dto.getSummary());
    }

    @Test
    void upsert_badCreatedAt_and_writeJsonThrows_throwsSerializeFailed() throws Exception {
        AdminLlmLoadTestHistoryService svc = newService();

        var cfg = realOm.createObjectNode();
        cfg.put("providerId", "   ");
        cfg.put("model", "   ");

        var summary = realOm.createObjectNode();
        summary.put("createdAt", "bad-date");
        summary.set("config", cfg);

        when(repository.findById("rid-3")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any(JsonNode.class))).thenThrow(new RuntimeException("boom"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.upsert(req("rid-3", summary)));
        assertEquals("summary 序列化失败", ex.getMessage());
    }

    @Test
    void upsert_createdAtMissing_usesNow_and_cfgMissing_mapsNulls() throws Exception {
        AdminLlmLoadTestHistoryService svc = newService();

        var summary = realOm.createObjectNode();

        when(repository.findById("rid-4")).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any(JsonNode.class))).thenReturn("{}");
        when(objectMapper.readTree(anyString())).thenReturn(realOm.createObjectNode());
        when(repository.save(any(LlmLoadTestRunHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminLlmLoadTestHistoryRecordDTO dto = svc.upsert(req("rid-4", summary));

        verify(repository).save(entityCaptor.capture());
        LlmLoadTestRunHistoryEntity saved = entityCaptor.getValue();

        assertEquals("rid-4", saved.getRunId());
        assertNotNull(saved.getCreatedAt());
        assertNull(saved.getProviderId());
        assertNull(saved.getModel());
        assertNull(saved.getStream());
        assertNull(saved.getEnableThinking());
        assertNull(saved.getTimeoutMs());
        assertNull(saved.getRetries());
        assertNull(saved.getRetryDelayMs());
        assertNotNull(saved.getUpdatedAt());

        assertEquals("rid-4", dto.getRunId());
    }

    @Test
    void list_limitClampsTo1_and_handlesEmptyRows() {
        AdminLlmLoadTestHistoryService svc = newService();

        when(repository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(List.of());

        List<AdminLlmLoadTestHistoryRecordDTO> out = svc.list(0);

        verify(repository).findAllByOrderByCreatedAtDesc(pageableCaptor.capture());
        assertEquals(1, pageableCaptor.getValue().getPageSize());
        assertEquals(0, out.size());
    }

    @Test
    void list_limitClampsTo200_and_coversNullBlankBadJson() throws Exception {
        AdminLlmLoadTestHistoryService svc = newService();

        LlmLoadTestRunHistoryEntity eNull = new LlmLoadTestRunHistoryEntity();
        eNull.setRunId("a");
        eNull.setCreatedAt(LocalDateTime.now());
        eNull.setSummaryJson(null);
        eNull.setUpdatedAt(LocalDateTime.now());

        LlmLoadTestRunHistoryEntity eBlank = new LlmLoadTestRunHistoryEntity();
        eBlank.setRunId("b");
        eBlank.setCreatedAt(LocalDateTime.now());
        eBlank.setSummaryJson("  ");
        eBlank.setUpdatedAt(LocalDateTime.now());

        LlmLoadTestRunHistoryEntity eBad = new LlmLoadTestRunHistoryEntity();
        eBad.setRunId("c");
        eBad.setCreatedAt(LocalDateTime.now());
        eBad.setSummaryJson("bad");
        eBad.setUpdatedAt(LocalDateTime.now());

        List<LlmLoadTestRunHistoryEntity> rows = new ArrayList<>();
        rows.add(null);
        rows.add(eNull);
        rows.add(eBlank);
        rows.add(eBad);
        when(repository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(rows);
        when(objectMapper.readTree(eq("bad"))).thenThrow(new RuntimeException("bad json"));

        List<AdminLlmLoadTestHistoryRecordDTO> out = svc.list(999);

        verify(repository).findAllByOrderByCreatedAtDesc(pageableCaptor.capture());
        assertEquals(200, pageableCaptor.getValue().getPageSize());
        assertEquals(4, out.size());
        assertNull(out.get(0));
        assertNull(out.get(1).getSummary());
        assertNull(out.get(2).getSummary());
        assertNull(out.get(3).getSummary());
    }

    @Test
    void delete_blankRunId_throws() {
        AdminLlmLoadTestHistoryService svc = newService();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.delete("   "));
        assertEquals("runId 不能为空", ex.getMessage());
    }

    @Test
    void delete_nullRunId_throws() {
        AdminLlmLoadTestHistoryService svc = newService();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.delete(null));
        assertEquals("runId 不能为空", ex.getMessage());
    }

    @Test
    void delete_detailDeleteThrows_isSwallowed_and_stillDeletesHistory() {
        AdminLlmLoadTestHistoryService svc = newService();

        doThrow(new RuntimeException("boom")).when(detailRepository).deleteByRunId("rid-1");
        svc.delete("  rid-1  ");

        verify(detailRepository).deleteByRunId("rid-1");
        verify(repository).deleteById("rid-1");
    }
}
