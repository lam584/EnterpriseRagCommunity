package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AiChatOptionsDTO;
import com.example.EnterpriseRagCommunity.service.ai.AiChatOptionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/chat/options")
@RequiredArgsConstructor
public class AiChatOptionsController {
    private final AiChatOptionsService aiChatOptionsService;

    @GetMapping
    public AiChatOptionsDTO getOptions() {
        return aiChatOptionsService.getOptions();
    }
}

