package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueActionRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBackfillRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBackfillResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkProgressDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBatchRequeueRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueBatchRequeueResponse;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueItemDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationQueueRiskTagsRequest;
import com.example.EnterpriseRagCommunity.dto.moderation.ModerationQueueQueryDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ContentType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStage;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.QueueStatus;
import com.example.EnterpriseRagCommunity.service.moderation.AdminModerationQueueService;
import com.example.EnterpriseRagCommunity.service.moderation.ModerationChunkReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/moderation/queue")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminModerationQueueController {
    private final AdminModerationQueueService adminModerationQueueService;
    private final ModerationChunkReviewService moderationChunkReviewService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','read')) or (#boardId != null and @boardAcl.canModerateBoard(#boardId))")
    public Page<AdminModerationQueueItemDTO> list(@RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                                 @RequestParam(value = "orderBy", required = false) String orderBy,
                                                 @RequestParam(value = "sort", required = false) String sort,
                                                 @RequestParam(value = "id", required = false) Long id,
                                                 @RequestParam(value = "boardId", required = false) Long boardId,
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
        q.setOrderBy(orderBy);
        q.setSort(sort);
        q.setId(id);
        q.setBoardId(boardId);
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
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','read')) or @boardAcl.canModerateQueueItem(#p0)")
    public AdminModerationQueueDetailDTO getDetail(@PathVariable("id") Long id) {
        return adminModerationQueueService.getDetail(id);
    }

    @GetMapping("/{id}/chunk-progress")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','read')) or @boardAcl.canModerateQueueItem(#p0)")
    public AdminModerationChunkProgressDTO getChunkProgress(@PathVariable("id") Long id,
                                                            @RequestParam(value = "includeChunks", defaultValue = "0") int includeChunks,
                                                            @RequestParam(value = "limit", defaultValue = "80") int limit) {
        boolean inc = includeChunks == 1;
        int lim = Math.clamp(limit, 0, 300);
        return moderationChunkReviewService.getProgress(id, inc, lim);
    }

    @GetMapping("/{id}/risk-tags")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','read')) or @boardAcl.canModerateQueueItem(#p0)")
    public List<String> getRiskTags(@PathVariable("id") Long id) {
        return adminModerationQueueService.getRiskTags(id);
    }

    @PostMapping("/{id}/risk-tags")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action')) or @boardAcl.canModerateQueueItem(#p0)")
    public AdminModerationQueueDetailDTO setRiskTags(@PathVariable("id") Long id,
                                                     @Valid @RequestBody(required = false) AdminModerationQueueRiskTagsRequest req) {
        List<String> tags = req == null ? null : req.getRiskTags();
        return adminModerationQueueService.setRiskTags(id, tags);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action')) or @boardAcl.canModerateQueueItem(#p0)")
    public AdminModerationQueueDetailDTO approve(@PathVariable("id") Long id,
                                                 @Valid @RequestBody AdminModerationQueueActionRequest req) {
        return adminModerationQueueService.approve(id, req.getReason());
    }

    @PostMapping("/{id}/override-approve")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action')) or @boardAcl.canModerateQueueItem(#p0)")
    public AdminModerationQueueDetailDTO overrideApprove(@PathVariable("id") Long id,
                                                         @Valid @RequestBody AdminModerationQueueActionRequest req) {
        return adminModerationQueueService.overrideApprove(id, req.getReason());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action')) or @boardAcl.canModerateQueueItem(#p0)")
    public AdminModerationQueueDetailDTO reject(@PathVariable("id") Long id,
                                                @Valid @RequestBody AdminModerationQueueActionRequest req) {
        return adminModerationQueueService.reject(id, req.getReason());
    }

    @PostMapping("/{id}/override-reject")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action')) or @boardAcl.canModerateQueueItem(#p0)")
    public AdminModerationQueueDetailDTO overrideReject(@PathVariable("id") Long id,
                                                        @Valid @RequestBody AdminModerationQueueActionRequest req) {
        return adminModerationQueueService.overrideReject(id, req.getReason());
    }

    @PostMapping("/backfill")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action'))")
    public AdminModerationQueueBackfillResponse backfill(@RequestBody(required = false) AdminModerationQueueBackfillRequest req) {
        return adminModerationQueueService.backfill(req);
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action')) or @boardAcl.canModerateQueueItem(#p0)")
    public AdminModerationQueueDetailDTO claim(@PathVariable("id") Long id) {
        return adminModerationQueueService.claim(id);
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action')) or @boardAcl.canModerateQueueItem(#p0)")
    public AdminModerationQueueDetailDTO release(@PathVariable("id") Long id) {
        return adminModerationQueueService.release(id);
    }

    @PostMapping("/{id}/requeue")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action')) or @boardAcl.canModerateQueueItem(#p0)")
    public AdminModerationQueueDetailDTO requeue(@PathVariable("id") Long id,
                                                @Valid @RequestBody AdminModerationQueueActionRequest req) {
        return adminModerationQueueService.requeueToAuto(id, req.getReason(), req.getReviewStage());
    }

    @PostMapping("/{id}/ban-user")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action')) or @boardAcl.canModerateQueueItem(#p0)")
    public AdminModerationQueueDetailDTO banUser(@PathVariable("id") Long id,
                                                 @Valid @RequestBody AdminModerationQueueActionRequest req) {
        return adminModerationQueueService.banUser(id, req.getReason());
    }

    @PostMapping("/batch/requeue")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action'))")
    public AdminModerationQueueBatchRequeueResponse batchRequeue(@RequestBody(required = false) AdminModerationQueueBatchRequeueRequest req) {
        List<Long> ids = req == null ? null : req.getIds();
        String reason = req == null ? null : req.getReason();
        String reviewStage = req == null ? null : req.getReviewStage();
        return adminModerationQueueService.batchRequeueToAuto(ids, reason, reviewStage);
    }

    @PostMapping("/{id}/to-human")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action')) or @boardAcl.canModerateQueueItem(#p0)")
    public AdminModerationQueueDetailDTO toHuman(@PathVariable("id") Long id,
                                                 @Valid @RequestBody AdminModerationQueueActionRequest req) {
        return adminModerationQueueService.toHuman(id, req.getReason());
    }
}
