package com.example.EnterpriseRagCommunity.controller.semantic.admin;

import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryGenHistoryDTO;
import com.example.EnterpriseRagCommunity.dto.ai.PostSummaryRegenerateRequestDTO;
import com.example.EnterpriseRagCommunity.entity.access.UsersEntity;
import com.example.EnterpriseRagCommunity.entity.ai.PostSummaryGenConfigEntity;
import com.example.EnterpriseRagCommunity.exception.ResourceNotFoundException;
import com.example.EnterpriseRagCommunity.service.AdministratorService;
import com.example.EnterpriseRagCommunity.service.ai.AiPostSummaryService;
import com.example.EnterpriseRagCommunity.service.ai.PostSummaryGenConfigService;
import com.example.EnterpriseRagCommunity.repository.content.PostsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/semantic/summary")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
@RequiredArgsConstructor
public class AdminPostSummaryController {

    private final PostSummaryGenConfigService postSummaryGenConfigService;
    private final AdministratorService administratorService;
    private final AiPostSummaryService aiPostSummaryService;
    private final PostsRepository postsRepository;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_summary','access'))")
    public PostSummaryGenConfigDTO getConfig() {
        return postSummaryGenConfigService.getAdminConfig();
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_summary','action'))")
    public PostSummaryGenConfigDTO upsertConfig(@RequestBody PostSummaryGenConfigDTO payload, Principal principal) {
        String username = AdminSemanticControllerSupport.resolveUsername(principal);
        Long userId = AdminSemanticControllerSupport.resolveActorUserId(administratorService, principal);
        return postSummaryGenConfigService.upsertAdminConfig(payload, userId, username);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_summary','access'))")
    public ResponseEntity<Page<PostSummaryGenHistoryDTO>> listHistory(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "postId", required = false) Long postId
    ) {
        return ResponseEntity.ok(postSummaryGenConfigService.listHistory(postId, page, size));
    }

    @PostMapping("/regenerate")
    @PreAuthorize("hasAuthority(T(com.example.EnterpriseRagCommunity.security.Permissions).perm('admin_semantic_summary','action'))")
    public ResponseEntity<Map<String, String>> regenerate(@RequestBody PostSummaryRegenerateRequestDTO payload, Principal principal) {
        Long postId = payload == null ? null : payload.getPostId();
        if (postId == null || postId <= 0) throw new IllegalArgumentException("postId 不能为空");

        PostSummaryGenConfigEntity cfg = postSummaryGenConfigService.getConfigEntityOrDefault();
        if (!Boolean.TRUE.equals(cfg.getEnabled())) throw new IllegalStateException("帖子摘要功能未启用，无法重新生成");

        if (postsRepository.findByIdAndIsDeletedFalse(postId).isEmpty()) {
            throw new ResourceNotFoundException("帖子不存在或已删除");
        }

        String username = principal == null ? null : principal.getName();
        Long actorUserId = null;
        if (username != null) {
            actorUserId = administratorService.findByUsername(username).map(UsersEntity::getId).orElse(null);
        }

        aiPostSummaryService.generateForPostIdAsync(postId, actorUserId);
        return new ResponseEntity<>(Map.of("message", "已提交重新生成任务"), HttpStatus.ACCEPTED);
    }
}
