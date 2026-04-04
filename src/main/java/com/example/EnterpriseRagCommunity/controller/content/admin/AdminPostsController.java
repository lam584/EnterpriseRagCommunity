package com.example.EnterpriseRagCommunity.controller.content.admin;

import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.service.content.PostsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/posts")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminPostsController {
    private final PostsService postsService;

    /**
     * 管理端帖子查询：默认不过滤 status（即 ALL），用于查看 PENDING 等状态。
     *
     * 约定：
     * - 不传 status 或 status=ALL => 不按状态过滤
     * - 传 DRAFT/PENDING/PUBLISHED/REJECTED/ARCHIVED => 精确过滤
     */
    @GetMapping
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_posts','read')) or (#boardId != null and @boardAcl.canModerateBoard(#boardId))")
    public Page<PostsEntity> list(@RequestParam(value = "keyword", required = false) String keyword,
                                 @RequestParam(value = "postId", required = false) Long postId,
                                 @RequestParam(value = "searchMode", required = false) String searchMode,
                                 @RequestParam(value = "boardId", required = false) Long boardId,
                                 @RequestParam(value = "status", required = false) String status,
                                 @RequestParam(value = "authorId", required = false) Long authorId,
                                 @RequestParam(value = "createdFrom", required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                 @RequestParam(value = "createdTo", required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                                 @RequestParam(value = "page", defaultValue = "1") int page,
                                 @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                 @RequestParam(value = "sortBy", required = false) String sortBy,
                                 @RequestParam(value = "sortOrderDirection", required = false) String sortOrderDirection) {

        PostStatus effectiveStatus;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            effectiveStatus = null; // 管理端默认 ALL
        } else {
            effectiveStatus = PostStatus.valueOf(status);
        }

        return postsService.query(keyword, postId, searchMode, boardId, effectiveStatus, authorId,
                createdFrom, createdTo, page, pageSize, sortBy, sortOrderDirection);
    }
}
