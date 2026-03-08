package com.example.EnterpriseRagCommunity.controller.monitor.admin;

import com.example.EnterpriseRagCommunity.dto.safety.AdminCircuitBreakerMetricsDTO;
import com.example.EnterpriseRagCommunity.service.safety.ContentSafetyCircuitBreakerService;
import com.example.EnterpriseRagCommunity.service.safety.DependencyCircuitBreakerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/metrics/circuit-breaker")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminCircuitBreakerMetricsController {

    private final ContentSafetyCircuitBreakerService contentSafetyCircuitBreakerService;
    private final DependencyCircuitBreakerService dependencyCircuitBreakerService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_safety_circuit_breaker','read'))")
    public AdminCircuitBreakerMetricsDTO metrics() {
        AdminCircuitBreakerMetricsDTO out = new AdminCircuitBreakerMetricsDTO();
        out.setContentSafety(contentSafetyCircuitBreakerService.getStatus(50));

        Map<String, DependencyCircuitBreakerService.Snapshot> deps = new LinkedHashMap<>();
        deps.put("ES", dependencyCircuitBreakerService.snapshot("ES"));
        out.setDependencies(deps);
        return out;
    }
}

