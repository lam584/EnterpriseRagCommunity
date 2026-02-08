package com.example.EnterpriseRagCommunity.controller.monitor.admin;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenMetricsResponseDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenSourceDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenTimelineResponseDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingScenarioEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmRoutingScenarioRepository;
import com.example.EnterpriseRagCommunity.security.Permissions;
import com.example.EnterpriseRagCommunity.service.monitor.TokenCostMetricsService;
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
import java.util.List;

@RestController
@RequestMapping("/api/admin/metrics/token")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminTokenMetricsController {

    private final TokenCostMetricsService tokenCostMetricsService;
    private final LlmRoutingScenarioRepository llmRoutingScenarioRepository;

    @GetMapping("/sources")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_token','read'))")
    public List<AdminTokenSourceDTO> sources() {
        List<LlmRoutingScenarioEntity> entities = llmRoutingScenarioRepository.findAllByOrderBySortIndexAsc();
        List<AdminTokenSourceDTO> out = new ArrayList<>();
        for (LlmRoutingScenarioEntity e : entities) {
            if (e == null) continue;
            AdminTokenSourceDTO d = new AdminTokenSourceDTO();
            d.setTaskType(e.getTaskType());
            d.setLabel(e.getLabel());
            d.setCategory(e.getCategory());
            d.setSortIndex(e.getSortIndex());
            out.add(d);
        }
        return out;
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
