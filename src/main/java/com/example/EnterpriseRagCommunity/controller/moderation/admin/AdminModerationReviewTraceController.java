package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationReviewTraceTaskDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationReviewTraceTaskPageDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.service.moderation.trace.AdminModerationReviewTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/moderation/review-trace")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminModerationReviewTraceController {

    private final AdminModerationReviewTraceService service;

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public AdminModerationReviewTraceTaskPageDTO listTasks(
            @RequestParam(value = "queueId", required = false) Long queueId,
            @RequestParam(value = "contentType", required = false) ContentType contentType,
            @RequestParam(value = "contentId", required = false) Long contentId,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "status", required = false) QueueStatus status,
            @RequestParam(value = "updatedFrom", required = false) String updatedFrom,
            @RequestParam(value = "updatedTo", required = false) String updatedTo,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        LocalDateTime from = AdminModerationReviewTraceService.parseLocalDateTimeOrNull(updatedFrom);
        LocalDateTime to = AdminModerationReviewTraceService.parseLocalDateTimeOrNull(updatedTo);
        return service.listTasks(queueId, contentType, contentId, traceId, status, from, to, page, pageSize);
    }

    @GetMapping("/tasks/{queueId}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_logs','read'))")
    public AdminModerationReviewTraceTaskDetailDTO taskDetail(@PathVariable Long queueId) {
        return service.getTaskDetail(queueId);
    }
}

