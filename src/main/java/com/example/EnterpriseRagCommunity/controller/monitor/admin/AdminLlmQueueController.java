package com.example.EnterpriseRagCommunity.controller.monitor.admin;

import com.example.EnterpriseRagCommunity.config.LlmQueueProperties;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueConfigDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueStatusDTO;
import com.example.EnterpriseRagCommunity.dto.monitor.AdminLlmQueueTaskDetailDTO;
import com.example.EnterpriseRagCommunity.service.monitor.LlmQueueMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/metrics/llm-queue")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminLlmQueueController {

    private final LlmQueueMonitorService llmQueueMonitorService;
    private final LlmQueueProperties llmQueueProperties;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public AdminLlmQueueStatusDTO status(
            @RequestParam(value = "windowSec", required = false) Integer windowSec,
            @RequestParam(value = "limitRunning", required = false) Integer limitRunning,
            @RequestParam(value = "limitPending", required = false) Integer limitPending,
            @RequestParam(value = "limitCompleted", required = false) Integer limitCompleted
    ) {
        return llmQueueMonitorService.query(windowSec, limitRunning, limitPending, limitCompleted);
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public AdminLlmQueueTaskDetailDTO taskDetail(@PathVariable("taskId") String taskId) {
        AdminLlmQueueTaskDetailDTO d = llmQueueMonitorService.getTaskDetail(taskId);
        if (d == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在或详情已被清理");
        return d;
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','read'))")
    public AdminLlmQueueConfigDTO getConfig() {
        AdminLlmQueueConfigDTO out = new AdminLlmQueueConfigDTO();
        out.setMaxConcurrent(llmQueueProperties.getMaxConcurrent());
        out.setMaxQueueSize(llmQueueProperties.getMaxQueueSize());
        out.setKeepCompleted(llmQueueProperties.getKeepCompleted());
        return out;
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_metrics_llm_queue','write'))")
    public AdminLlmQueueConfigDTO updateConfig(@RequestBody AdminLlmQueueConfigDTO payload) {
        if (payload != null) {
            if (payload.getMaxConcurrent() != null) {
                int v = Math.max(1, Math.min(1024, payload.getMaxConcurrent()));
                llmQueueProperties.setMaxConcurrent(v);
            }
            if (payload.getMaxQueueSize() != null) {
                int v = Math.max(100, Math.min(200000, payload.getMaxQueueSize()));
                llmQueueProperties.setMaxQueueSize(v);
            }
            if (payload.getKeepCompleted() != null) {
                int v = Math.max(0, Math.min(20000, payload.getKeepCompleted()));
                llmQueueProperties.setKeepCompleted(v);
            }
        }
        return getConfig();
    }
}
