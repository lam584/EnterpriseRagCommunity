package com.example.EnterpriseRagCommunity.controller.content;

import com.example.EnterpriseRagCommunity.dto.content.PostDraftsCreateDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostDraftsDTO;
import com.example.EnterpriseRagCommunity.dto.content.PostDraftsUpdateDTO;
import com.example.EnterpriseRagCommunity.service.content.PostDraftsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/post-drafts")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "true")
public class PostDraftsController {

    @Autowired
    private PostDraftsService postDraftsService;

    @GetMapping
    public Page<PostDraftsDTO> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return postDraftsService.listMine(pageable);
    }

    @GetMapping("/{id}")
    public PostDraftsDTO getMine(@PathVariable Long id) {
        return postDraftsService.getMine(id);
    }

    @PostMapping
    public PostDraftsDTO create(@Valid @RequestBody PostDraftsCreateDTO dto) {
        return postDraftsService.create(dto);
    }

    @PutMapping("/{id}")
    public PostDraftsDTO updateMine(@PathVariable Long id, @Valid @RequestBody PostDraftsUpdateDTO dto) {
        return postDraftsService.updateMine(id, dto);
    }

    @DeleteMapping("/{id}")
    public void deleteMine(@PathVariable Long id) {
        postDraftsService.deleteMine(id);
    }
}

