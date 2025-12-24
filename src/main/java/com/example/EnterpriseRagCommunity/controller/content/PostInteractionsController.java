package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.PostToggleResponseDTO;
import com.example.EnterpriseRagCommunity.service.content.PostInteractionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class PostInteractionsController {

    @Autowired
    private PostInteractionsService postInteractionsService;

    @PostMapping("/{postId}/like")
    public PostToggleResponseDTO toggleLike(@PathVariable("postId") Long postId) {
        return postInteractionsService.toggleLike(postId);
    }

    @PostMapping("/{postId}/favorite")
    public PostToggleResponseDTO toggleFavorite(@PathVariable("postId") Long postId) {
        return postInteractionsService.toggleFavorite(postId);
    }
}

