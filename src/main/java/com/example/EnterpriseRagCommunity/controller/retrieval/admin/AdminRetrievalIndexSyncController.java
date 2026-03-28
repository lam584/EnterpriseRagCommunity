package com.example.EnterpriseRagCommunity.controller.retrieval.admin;

import com.example.EnterpriseRagCommunity.dto.retrieval.AdminBatchIdsRequest;
import com.example.EnterpriseRagCommunity.dto.retrieval.CommentIndexSyncStatusDTO;
import com.example.EnterpriseRagCommunity.dto.retrieval.PostIndexSyncStatusDTO;
import com.example.EnterpriseRagCommunity.security.Permissions;
import com.example.EnterpriseRagCommunity.service.retrieval.admin.AdminRetrievalIndexSyncStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/retrieval/index-sync")
@RequiredArgsConstructor
public class AdminRetrievalIndexSyncController {

    private final AdminRetrievalIndexSyncStatusService indexSyncStatusService;

    @PostMapping("/posts")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_posts','read'))")
    public ResponseEntity<List<PostIndexSyncStatusDTO>> batchPostStatuses(@RequestBody(required = false) AdminBatchIdsRequest req) {
        List<Long> ids = req == null ? null : req.getIds();
        return ResponseEntity.ok(indexSyncStatusService.batchPostStatuses(ids));
    }

    @PostMapping("/comments")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_comments','read'))")
    public ResponseEntity<List<CommentIndexSyncStatusDTO>> batchCommentStatuses(@RequestBody(required = false) AdminBatchIdsRequest req) {
        List<Long> ids = req == null ? null : req.getIds();
        return ResponseEntity.ok(indexSyncStatusService.batchCommentStatuses(ids));
    }
}
