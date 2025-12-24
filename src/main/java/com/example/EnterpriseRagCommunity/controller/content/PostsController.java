package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.PostDetailDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostsPublishDTO;
import com.example.EnterpriseRagCommunity.entity.content.PostsEntity;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.service.content.PortalPostsService;
import com.example.EnterpriseRagCommunity.service.content.PostsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/posts")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class PostsController {

    @Autowired
    private PostsService postsService;

    @Autowired
    private PortalPostsService portalPostsService;

    @PostMapping
    public PostsEntity publish(@Valid @RequestBody PostsPublishDTO dto) {
        return postsService.publish(dto);
    }

    @GetMapping
    public Page<PostDetailDTO> list(@RequestParam(value = "keyword", required = false) String keyword,
                                   @RequestParam(value = "postId", required = false) Long postId,
                                   @RequestParam(value = "searchMode", required = false) String searchMode,
                                   @RequestParam(value = "boardId", required = false) Long boardId,
                                   @RequestParam(value = "status", required = false) PostStatus status,
                                   @RequestParam(value = "authorId", required = false) Long authorId,
                                   @RequestParam(value = "createdFrom", required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                   @RequestParam(value = "createdTo", required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                                   @RequestParam(value = "page", defaultValue = "1") int page,
                                   @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                   @RequestParam(value = "sortBy", required = false) String sortBy,
                                   @RequestParam(value = "sortOrderDirection", required = false) String sortOrderDirection) {
        return portalPostsService.query(keyword, postId, searchMode, boardId, status, authorId, createdFrom, createdTo, page, pageSize, sortBy, sortOrderDirection);
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
    public PostsEntity updateStatus(@PathVariable("id") Long id, @Valid @RequestBody UpdateStatusRequest req) {
        return postsService.updateStatus(id, req.getStatus());
    }
}
