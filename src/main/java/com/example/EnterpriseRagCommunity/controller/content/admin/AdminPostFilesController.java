package com.example.EnterpriseRagCommunity.controller.content.admin;

import com.example.EnterpriseRagCommunity.dto.content.admin.PostFileExtractionAdminDetailDTO;
import com.example.EnterpriseRagCommunity.dto.content.admin.PostFileExtractionAdminListItemDTO;
import com.example.EnterpriseRagCommunity.service.content.admin.AdminPostFilesService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/post-files")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminPostFilesController {
    private final AdminPostFilesService adminPostFilesService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_posts','read'))")
    public Page<PostFileExtractionAdminListItemDTO> list(@RequestParam(value = "page", defaultValue = "1") int page,
                                                        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                                        @RequestParam(value = "postId", required = false) Long postId,
                                                        @RequestParam(value = "fileAssetId", required = false) Long fileAssetId,
                                                        @RequestParam(value = "keyword", required = false) String keyword,
                                                        @RequestParam(value = "extractStatus", required = false) String extractStatus) {
        return adminPostFilesService.list(page, pageSize, postId, fileAssetId, keyword, extractStatus);
    }

    @GetMapping("/{attachmentId}")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_posts','read'))")
    public PostFileExtractionAdminDetailDTO detail(@PathVariable("attachmentId") Long attachmentId) {
        return adminPostFilesService.detail(attachmentId);
    }

    @PostMapping("/{attachmentId}/reextract")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_posts','update'))")
    public PostFileExtractionAdminDetailDTO reextract(@PathVariable("attachmentId") Long attachmentId) {
        return adminPostFilesService.reextract(attachmentId);
    }
}
