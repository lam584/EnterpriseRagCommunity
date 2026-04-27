package com.example.EnterpriseRagCommunity.controller.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkContentPreviewDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkLogDetailDTO;
import com.example.EnterpriseRagCommunity.dto.moderation.AdminModerationChunkLogItemDTO;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkSourceType;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.ChunkStatus;
import com.example.EnterpriseRagCommunity.entity.moderation.enums.Verdict;
import com.example.EnterpriseRagCommunity.service.moderation.admin.AdminModerationChunkReviewLogsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/moderation/chunk-review/logs")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminModerationChunkReviewLogsController {
    private final AdminModerationChunkReviewLogsService logsService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_chunk_review','access'))")
    public List<AdminModerationChunkLogItemDTO> list(@RequestParam(value = "limit", defaultValue = "50") int limit,
                                                     @RequestParam(value = "queueId", required = false) Long queueId,
                                                     @RequestParam(value = "status", required = false) ChunkStatus status,
                                                     @RequestParam(value = "verdict", required = false) Verdict verdict,
                                                     @RequestParam(value = "sourceType", required = false) ChunkSourceType sourceType,
                                                     @RequestParam(value = "fileAssetId", required = false) Long fileAssetId,
                                                     @RequestParam(value = "keyword", required = false) String keyword) {
        return logsService.listRecent(limit, queueId, status, verdict, sourceType, fileAssetId, keyword);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_chunk_review','access'))")
    public AdminModerationChunkLogDetailDTO get(@PathVariable Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("id 不合法");
        return logsService.getDetail(id);
    }

    @GetMapping("/{id}/content")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_chunk_review','access'))")
    public AdminModerationChunkContentPreviewDTO getContent(@PathVariable Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("id 不合法");
        return logsService.getContentPreview(id);
    }
}
