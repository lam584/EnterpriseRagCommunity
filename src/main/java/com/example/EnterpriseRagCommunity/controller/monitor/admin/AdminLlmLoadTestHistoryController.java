package com.example.EnterpriseRagCommunity.controller.monitor.admin;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestHistoryRecordDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmLoadTestHistoryUpsertRequestDTO;
import com.example.EnterpriseRagCommunity.service.monitor.AdminLlmLoadTestHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/metrics/llm-loadtest/history")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminLlmLoadTestHistoryController {

    private final AdminLlmLoadTestHistoryService service;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public List<AdminLlmLoadTestHistoryRecordDTO> list(@RequestParam(value = "limit", required = false) Integer limit) {
        return service.list(limit == null ? 50 : limit);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public AdminLlmLoadTestHistoryRecordDTO upsert(@RequestBody AdminLlmLoadTestHistoryUpsertRequestDTO req) {
        try {
            return service.upsert(req);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{runId}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public void delete(@PathVariable String runId) {
        try {
            service.delete(runId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
