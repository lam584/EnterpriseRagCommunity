package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationPipelineRunDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationPipelineRunHistoryPageDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.service.moderation.trace.AdminModerationPipelineTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/moderation/pipeline")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminModerationPipelineTraceController {

    private final AdminModerationPipelineTraceService service;

    @GetMapping("/latest")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public AdminModerationPipelineRunDetailDTO latestByQueueId(@RequestParam("queueId") Long queueId) {
        return service.getLatestByQueueId(queueId);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public AdminModerationPipelineRunHistoryPageDTO history(
            @RequestParam(value = "queueId", required = false) Long queueId,
            @RequestParam(value = "contentType", required = false) ContentType contentType,
            @RequestParam(value = "contentId", required = false) Long contentId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        return service.history(queueId, contentType, contentId, page, pageSize);
    }

    @GetMapping("/{runId}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public AdminModerationPipelineRunDetailDTO byRunId(@PathVariable Long runId) {
        return service.getByRunId(runId);
    }
}
