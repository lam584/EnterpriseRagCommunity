package com.example.EnterpriseRagCommunity.controller.monitor.admin;

import java.util.Map;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestRunRequestDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestRunResponseDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestStatusDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AdminLlmLoadTestService;

import lombok.RequiredArgsConstructor;

import java.util.Locale;

@RestController
@RequestMapping("/api/admin/metrics/llm-loadtest")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
@Validated
public class AdminLlmLoadTestController {
    private static final java.util.regex.Pattern RUN_ID_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    private final AdminLlmLoadTestService service;

    @PostMapping("/run")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public AdminLlmLoadTestRunResponseDTO run(@RequestBody AdminLlmLoadTestRunRequestDTO req) {
        String runId = service.start(req);
        AdminLlmLoadTestRunResponseDTO out = new AdminLlmLoadTestRunResponseDTO();
        out.setRunId(runId);
        return out;
    }

    @GetMapping("/{runId}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public ResponseEntity<AdminLlmLoadTestStatusDTO> status(@PathVariable("runId") @Pattern(regexp = "^[A-Za-z0-9-]{1,64}$") String runId) {
        AdminLlmLoadTestStatusDTO st = service.status(runId);
        if (st == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(st);
    }

    @PostMapping("/{runId}/stop")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public ResponseEntity<?> stop(@PathVariable("runId") @Pattern(regexp = "^[A-Za-z0-9-]{1,64}$") String runId) {
        boolean ok = service.stop(runId);
        if (!ok) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("stopped", true));
    }

    @GetMapping("/{runId}/export")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public ResponseEntity<StreamingResponseBody> export(
            @PathVariable("runId") @Pattern(regexp = "^[A-Za-z0-9-]{1,64}$") String runId,
            @RequestParam(value = "format", required = false, defaultValue = "json") @Pattern(regexp = "^(json|csv)$") String format
    ) {
        String normalizedRunId = normalizeRunId(runId);
        if (normalizedRunId == null) {
            return ResponseEntity.badRequest().build();
        }
        return service.export(normalizedRunId, isCsvFormat(format));
    }

    private static boolean isCsvFormat(String format) {
        String f = format == null ? "json" : format.trim().toLowerCase(Locale.ROOT);
        return "csv".equals(f);
    }

    private static String normalizeRunId(String runId) {
        if (runId == null) {
            return null;
        }
        String trimmed = runId.trim();
        return RUN_ID_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }
}
