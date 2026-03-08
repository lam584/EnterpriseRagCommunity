package com.example.EnterpriseRagCommunity.controller.monitor.admin;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenMetricsResponseDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenSourceDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenTimelineResponseDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmRoutingPolicyRepository;
import com.example.EnterpriseRagCommunity.security.Permissions;
import com.example.EnterpriseRagCommunity.service.monitor.TokenCostMetricsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/metrics/token")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminTokenMetricsController {

    private final TokenCostMetricsService tokenCostMetricsService;
    private final LlmRoutingPolicyRepository llmRoutingPolicyRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping("/sources")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_token','read'))")
    public List<AdminTokenSourceDTO> sources() {
        List<LlmRoutingPolicyEntity> entities = llmRoutingPolicyRepository.findByIdEnvOrderBySortIndexAscIdTaskTypeAsc("default");
        Map<String, AdminTokenSourceDTO> byTaskType = new HashMap<>();

        for (LlmRoutingPolicyEntity e : entities) {
            if (e == null || e.getId() == null || e.getId().getTaskType() == null) continue;
            AdminTokenSourceDTO d = new AdminTokenSourceDTO();
            String taskType = e.getId().getTaskType();
            String up = normalizeTaskType(taskType);
            if (up == null) continue;
            d.setTaskType(up);
            String label = e.getLabel();
            if (label == null || label.isBlank()) label = inferLabel(up, taskType);
            d.setLabel(label);
            d.setCategory(e.getCategory() == null || e.getCategory().isBlank() ? "TEXT_GEN" : e.getCategory());
            d.setSortIndex(e.getSortIndex() == null ? 0 : e.getSortIndex());
            byTaskType.putIfAbsent(up, d);
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        @SuppressWarnings("unchecked")
        List<Object> used = entityManager.createNativeQuery("""
                        SELECT DISTINCT h.type
                        FROM llm_queue_task_history h
                        WHERE h.status = 'DONE'
                          AND h.finished_at >= :cutoff
                          AND h.type IS NOT NULL
                        """)
                .setParameter("cutoff", cutoff)
                .getResultList();
        for (Object v : used) {
            String tt = normalizeTaskType(v == null ? null : String.valueOf(v));
            if (tt == null) continue;
            if (byTaskType.containsKey(tt)) continue;
            AdminTokenSourceDTO d = new AdminTokenSourceDTO();
            d.setTaskType(tt);
            d.setLabel(inferLabel(tt, tt));
            d.setCategory(inferCategory(tt));
            d.setSortIndex(9999);
            byTaskType.put(tt, d);
        }

        return new ArrayList<>(byTaskType.values());
    }

    private static String normalizeTaskType(String taskType) {
        if (taskType == null) return null;
        String s = taskType.trim();
        if (s.isBlank()) return null;
        return s.toUpperCase(Locale.ROOT);
    }

    private static String inferLabel(String taskType, String fallback) {
        String tt = normalizeTaskType(taskType);
        if (tt == null) return fallback;
        return switch (tt) {
            case "MODERATION_CHUNK" -> "分片审核";
            default -> fallback;
        };
    }

    private static String inferCategory(String taskType) {
        String tt = normalizeTaskType(taskType);
        if (tt == null) return "TEXT_GEN";
        if (tt.contains("RERANK")) return "RERANK";
        if (tt.contains("EMBED")) return "EMBEDDING";
        return "TEXT_GEN";
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_token','read'))")
    public AdminTokenMetricsResponseDTO query(
            @RequestParam(value = "start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(value = "end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "pricingMode", required = false) String pricingMode
    ) {
        com.example.EnterpriseRagCommunity.service.monitor.LlmPricing.Mode pm =
                com.example.EnterpriseRagCommunity.service.monitor.LlmPricing.Mode.fromNullableString(pricingMode);
        return tokenCostMetricsService.query(start, end, source, pm);
    }

    @GetMapping("/timeline")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_token','read'))")
    public AdminTokenTimelineResponseDTO timeline(
            @RequestParam(value = "start", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(value = "end", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "bucket", required = false) String bucket
    ) {
        TokenCostMetricsService.TimelineBucket buck = TokenCostMetricsService.TimelineBucket.fromNullableString(bucket);
        return tokenCostMetricsService.queryTimeline(start, end, source, buck);
    }
}
