package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestRequest;
import com.example.EnterpriseRagCommunity.dto.ai.AiPostTitleSuggestResponse;
import com.example.EnterpriseRagCommunity.service.ai.AiPostTitleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/posts")
@RequiredArgsConstructor
public class AiPostTitleController {

    private final AiPostTitleService aiPostTitleService;

    @PostMapping("/title-suggestions")
    public AiPostTitleSuggestResponse titleSuggestions(@Valid @RequestBody AiPostTitleSuggestRequest req) {
        return aiPostTitleService.suggestTitles(req);
    }
}

