package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.CommentCreateRequest;
import com.example.EnterpriseRagCommunity.dto.content.CommentDTO;
import com.example.EnterpriseRagCommunity.service.content.CommentsService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class PostCommentsController {

    @Autowired
    private CommentsService commentsService;

    @GetMapping("/{postId}/comments")
    public Page<CommentDTO> list(@PathVariable("postId") Long postId,
                                 @RequestParam(value = "page", defaultValue = "1") int page,
                                 @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                 @RequestParam(value = "includeMinePending", defaultValue = "false") boolean includeMinePending) {
        return commentsService.listByPostId(postId, page, pageSize, includeMinePending);
    }

    @PostMapping("/{postId}/comments")
    public CommentDTO create(@PathVariable("postId") Long postId, @Valid @RequestBody CommentCreateRequest req) {
        return commentsService.createForPost(postId, req);
    }

    @Setter
    @Getter
    public static class CountResponse {
        private long count;

        public CountResponse(long count) {
            this.count = count;
        }

    }

    @GetMapping("/{postId}/comments/count")
    public CountResponse count(@PathVariable("postId") Long postId) {
        return new CountResponse(commentsService.countByPostId(postId));
    }
}

