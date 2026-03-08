package com.example.EnterpriseRagCommunity.service.monitor;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenMetricsModelItemDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenMetricsResponseDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenTimelineResponseDTO;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class TokenCostMetricsServiceUnknownModelTest {

    @Autowired
    TokenCostMetricsService tokenCostMetricsService;

    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    void queryShouldIncludeDoneRowsWithNullModel() {
        LocalDateTime now = LocalDateTime.of(2000, 1, 1, 12, 0);
        LocalDateTime finishedAt = now.plusMinutes(1);
        String taskId = "test-" + UUID.randomUUID();

        entityManager.createNativeQuery("""
                        INSERT INTO llm_queue_task_history
                        (task_id, seq, priority, type, status, provider_id, model,
                         created_at, started_at, finished_at, wait_ms, duration_ms,
                         tokens_in, tokens_out, total_tokens, tokens_per_sec,
                         error, input, output, input_chars, output_chars,
                         input_truncated, output_truncated, updated_at)
                        VALUES
                        (:taskId, 1, 0, 'MODERATION_CHUNK', 'DONE', NULL, NULL,
                         :createdAt, :startedAt, :finishedAt, 0, 1,
                         100, 20, 120, NULL,
                         NULL, NULL, NULL, NULL, NULL,
                         0, 0, :updatedAt)
                        """)
                .setParameter("taskId", taskId)
                .setParameter("createdAt", finishedAt)
                .setParameter("startedAt", finishedAt)
                .setParameter("finishedAt", finishedAt)
                .setParameter("updatedAt", finishedAt)
                .executeUpdate();

        AdminTokenMetricsResponseDTO resp = tokenCostMetricsService.query(
                now.minusMinutes(10),
                now.plusMinutes(10),
                "MODERATION_CHUNK",
                LlmPricing.Mode.DEFAULT
        );
        assertNotNull(resp);
        assertEquals(120L, resp.getTotalTokens());

        List<AdminTokenMetricsModelItemDTO> items = resp.getItems();
        assertNotNull(items);
        AdminTokenMetricsModelItemDTO unknown = items.stream()
                .filter(it -> it != null && "UNKNOWN".equals(it.getModel()))
                .findFirst()
                .orElse(null);
        assertNotNull(unknown);
        assertEquals(120L, unknown.getTotalTokens());

        AdminTokenTimelineResponseDTO tl = tokenCostMetricsService.queryTimeline(
                now.minusMinutes(10),
                now.plusMinutes(10),
                "MODERATION_CHUNK",
                TokenCostMetricsService.TimelineBucket.HOUR
        );
        assertNotNull(tl);
        assertEquals(120L, tl.getTotalTokens());
    }
}
