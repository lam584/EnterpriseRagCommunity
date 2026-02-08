package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.PostDetailDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostsPublishDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostsUpdateDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.content.PortalPostsService;
import com.example.EnterpriseRagCommunity.service.content.PostsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/posts")
public class PostsController {

    @Autowired
    private PostsService postsService;

    @Autowired
    private PortalPostsService portalPostsService;

    @Autowired
    private AdministratorService administratorService;

    @PostMapping
    public PostsEntity publish(@Valid @RequestBody PostsPublishDTO dto) {
        return postsService.publish(dto);
    }

    @GetMapping
    public Page<PostDetailDTO> list(@RequestParam(value = "keyword", required = false) String keyword,
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
        // 门户默认只展示已发布；管理端需要看全部时可显式传 status=ALL
        PostStatus effectiveStatus;
        if (status == null || status.isBlank()) {
            effectiveStatus = PostStatus.PUBLISHED;
        } else if ("ALL".equalsIgnoreCase(status)) {
            effectiveStatus = null; // 不按状态过滤
        } else {
            effectiveStatus = PostStatus.valueOf(status);
        }
        return portalPostsService.query(keyword, postId, searchMode, boardId, effectiveStatus, authorId, createdFrom, createdTo, page, pageSize, sortBy, sortOrderDirection);
    }

    @GetMapping("/{id}")
    public PostDetailDTO getById(@PathVariable("id") Long id) {
        return portalPostsService.getById(id);
    }

    public static class UpdateStatusRequest {
        @NotNull(message = "status 不能为空")
        private PostStatus status;

        public PostStatus getStatus() {
            return status;
        }

        public void setStatus(PostStatus status) {
            this.status = status;
        }
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_moderation_queue','action'))")
    public PostsEntity updateStatus(@PathVariable("id") Long id, @Valid @RequestBody UpdateStatusRequest req) {
        return postsService.updateStatus(id, req.getStatus());
    }

    @PutMapping("/{id}")
    public PostsEntity update(@PathVariable("id") Long id, @Valid @RequestBody PostsUpdateDTO dto) {
        return postsService.update(id, dto);
    }

    @GetMapping("/bookmarks")
    public Page<PostDetailDTO> listMyBookmarks(@RequestParam(value = "page", defaultValue = "1") int page,
                                               @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return portalPostsService.queryMyBookmarkedPosts(page, pageSize);
    }

    /**
     * “我的帖子”列表：仅返回当前登录用户自己创建的帖子。
     *
     * 设计说明：
     * - authorId 不允许由前端传入，避免越权与歧义；由后端从会话中解析。
     * - status 为空表示 ALL；否则按指定状态过滤。
     * - 不强制 PUBLISHED（作者需要能看到 PENDING/DRAFT/REJECTED/ARCHIVED）。
     */
    @GetMapping("/mine")
    public Page<PostDetailDTO> listMine(@RequestParam(value = "keyword", required = false) String keyword,
                                        @RequestParam(value = "postId", required = false) Long postId,
                                        @RequestParam(value = "searchMode", required = false) String searchMode,
                                        @RequestParam(value = "boardId", required = false) Long boardId,
                                        @RequestParam(value = "status", required = false) String status,
                                        @RequestParam(value = "createdFrom", required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                        @RequestParam(value = "createdTo", required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                                        @RequestParam(value = "page", defaultValue = "1") int page,
                                        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                        @RequestParam(value = "sortBy", required = false) String sortBy,
                                        @RequestParam(value = "sortOrderDirection", required = false) String sortOrderDirection) {
        Long me;
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录或会话已过期");
        }
        String email = auth.getName();
        me = administratorService.findByUsername(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前用户不存在"))
                .getId();

        // status==null 表示 ALL：不加 status 过滤。
        PostStatus effectiveStatus;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            effectiveStatus = null;
        } else {
            effectiveStatus = PostStatus.valueOf(status);
        }

        return portalPostsService.query(keyword, postId, searchMode, boardId, effectiveStatus, me, createdFrom, createdTo,
                page, pageSize, sortBy, sortOrderDirection);
    }
}
