package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueActionRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBackfillRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBackfillResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueItemDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationQueueQueryDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/moderation/queue")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AdminModerationQueueController {

    @Autowired
    private AdminModerationQueueService adminModerationQueueService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','read'))")
    public Page<AdminModerationQueueItemDTO> list(@RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                                 @RequestParam(value = "id", required = false) Long id,
                                                 @RequestParam(value = "contentType", required = false) ContentType contentType,
                                                 @RequestParam(value = "contentId", required = false) Long contentId,
                                                 @RequestParam(value = "status", required = false) QueueStatus status,
                                                 @RequestParam(value = "currentStage", required = false) QueueStage currentStage,
                                                 @RequestParam(value = "assignedToId", required = false) Long assignedToId,
                                                 @RequestParam(value = "minPriority", required = false) Integer minPriority,
                                                 @RequestParam(value = "maxPriority", required = false) Integer maxPriority,
                                                 @RequestParam(value = "createdFrom", required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
                                                 @RequestParam(value = "createdTo", required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo) {
        ModerationQueueQueryDTO q = new ModerationQueueQueryDTO();
        q.setPageNum(page);
        q.setPageSize(pageSize);
        q.setId(id);
        q.setContentType(contentType);
        q.setContentId(contentId);
        // When status is omitted, treat it as "all statuses" (no status filter).
        q.setStatus(status);
        q.setCurrentStage(currentStage);
        q.setAssignedToId(assignedToId);
        q.setMinPriority(minPriority);
        q.setMaxPriority(maxPriority);
        q.setCreatedFrom(createdFrom);
        q.setCreatedTo(createdTo);
        return adminModerationQueueService.list(q);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','read'))")
    public AdminModerationQueueDetailDTO getDetail(@PathVariable("id") Long id) {
        return adminModerationQueueService.getDetail(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action'))")
    public AdminModerationQueueDetailDTO approve(@PathVariable("id") Long id,
                                                 @Valid @RequestBody(required = false) AdminModerationQueueActionRequest req) {
        String reason = req == null ? null : req.getReason();
        return adminModerationQueueService.approve(id, reason);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action'))")
    public AdminModerationQueueDetailDTO reject(@PathVariable("id") Long id,
                                                @Valid @RequestBody(required = false) AdminModerationQueueActionRequest req) {
        String reason = req == null ? null : req.getReason();
        return adminModerationQueueService.reject(id, reason);
    }

    @PostMapping("/backfill")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action'))")
    public AdminModerationQueueBackfillResponse backfill(@RequestBody(required = false) AdminModerationQueueBackfillRequest req) {
        return adminModerationQueueService.backfill(req);
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action'))")
    public AdminModerationQueueDetailDTO claim(@PathVariable("id") Long id) {
        return adminModerationQueueService.claim(id);
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action'))")
    public AdminModerationQueueDetailDTO release(@PathVariable("id") Long id) {
        return adminModerationQueueService.release(id);
    }

    @PostMapping("/{id}/requeue")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action'))")
    public AdminModerationQueueDetailDTO requeue(@PathVariable("id") Long id,
                                                @Valid @RequestBody(required = false) AdminModerationQueueActionRequest req) {
        String reason = req == null ? null : req.getReason();
        return adminModerationQueueService.requeueToAuto(id, reason);
    }

    @PostMapping("/{id}/to-human")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action'))")
    public AdminModerationQueueDetailDTO toHuman(@PathVariable("id") Long id,
                                                 @Valid @RequestBody(required = false) AdminModerationQueueActionRequest req) {
        String reason = req == null ? null : req.getReason();
        return adminModerationQueueService.toHuman(id, reason);
    }
}
