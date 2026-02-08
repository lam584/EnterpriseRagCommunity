package com.example.EnterpriseRagCommunity.controller.monitor.admin;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadBalanceResponseDTO;
import com.example.EnterpriseRagCommunity.service.monitor.LlmLoadBalanceMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminLlmLoadBalanceController {
    private final LlmLoadBalanceMonitorService llmLoadBalanceMonitorService;

    @GetMapping({"/api/llm/load-balance", "/api/admin/metrics/llm-load-balance"})
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public AdminLlmLoadBalanceResponseDTO query(
            @RequestParam(value = "range", required = false) String range,
            @RequestParam(value = "hours", required = false) Integer hours
    ) {
        return llmLoadBalanceMonitorService.query(range, hours);
    }
}
