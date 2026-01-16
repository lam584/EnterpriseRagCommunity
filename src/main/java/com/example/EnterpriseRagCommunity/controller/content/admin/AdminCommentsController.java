package com.example.EnterpriseRagCommunity.controller.content.admin;

import com.example.EnterpriseRagCommunity.dto.content.admin.CommentAdminDTO;
import com.example.EnterpriseRagCommunity.dto.content.admin.CommentSetDeletedRequest;
import com.example.EnterpriseRagCommunity.dto.content.admin.CommentUpdateStatusRequest;
import com.example.EnterpriseRagCommunity.service.content.admin.AdminCommentsService;
import com.example.EnterpriseRagCommunity.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/comments")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class AdminCommentsController {

    @Autowired
    private AdminCommentsService adminCommentsService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_comments','read'))")
    public Page<CommentAdminDTO> list(@RequestParam(value = "page", defaultValue = "1") int page,
                                     @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                     @RequestParam(value = "postId", required = false) Long postId,
                                     @RequestParam(value = "authorId", required = false) Long authorId,
                                     @RequestParam(value = "authorName", required = false) String authorName,
                                     @RequestParam(value = "createdFrom", required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
                                     @RequestParam(value = "createdTo", required = false)
                                     @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
                                     @RequestParam(value = "status", required = false) String status,
                                     @RequestParam(value = "isDeleted", required = false) Boolean isDeleted,
                                     @RequestParam(value = "keyword", required = false) String keyword) {
        return adminCommentsService.list(page, pageSize, postId, authorId, authorName, createdFrom, createdTo, status, isDeleted, keyword);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_comments','update'))")
    public CommentAdminDTO updateStatus(@PathVariable("id") Long id,
                                        @Valid @RequestBody CommentUpdateStatusRequest req) {
        return adminCommentsService.updateStatus(id, req);
    }

    @PatchMapping("/{id}/deleted")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_comments','update'))")
    public CommentAdminDTO setDeleted(@PathVariable("id") Long id,
                                      @Valid @RequestBody CommentSetDeletedRequest req) {
        return adminCommentsService.setDeleted(id, req);
    }
}
