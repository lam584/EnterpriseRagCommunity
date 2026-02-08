package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.PostComposeAiSnapshotApplyRequest;
import com.example.EnterpriseRagCommunity.dto.content.PostComposeAiSnapshotCreateRequest;
import com.example.EnterpriseRagCommunity.dto.content.PostComposeAiSnapshotDTO;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostComposeAiSnapshotTargetType;
import com.example.EnterpriseRagCommunity.service.content.PostComposeAiSnapshotsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/post-compose/ai-snapshots")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class PostComposeAiSnapshotsController {

    @Autowired
    private PostComposeAiSnapshotsService service;

    @PostMapping
    public PostComposeAiSnapshotDTO create(@Valid @RequestBody PostComposeAiSnapshotCreateRequest req) {
        return service.create(req);
    }

    @GetMapping("/pending")
    public ResponseEntity<PostComposeAiSnapshotDTO> getPending(
            @RequestParam("targetType") PostComposeAiSnapshotTargetType targetType,
            @RequestParam(name = "draftId", required = false) Long draftId,
            @RequestParam(name = "postId", required = false) Long postId
    ) {
        PostComposeAiSnapshotDTO dto = service.getPending(targetType, draftId, postId);
        if (dto == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/apply")
    public PostComposeAiSnapshotDTO apply(@PathVariable("id") Long id, @Valid @RequestBody PostComposeAiSnapshotApplyRequest req) {
        return service.apply(id, req);
    }

    @PostMapping("/{id}/revert")
    public PostComposeAiSnapshotDTO revert(@PathVariable("id") Long id) {
        return service.revert(id);
    }
}

