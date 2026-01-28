package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.SupportedLanguageDTO;
import com.example.EnterpriseRagCommunity.service.ai.SupportedLanguageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/supported-languages")
@RequiredArgsConstructor
public class AiSupportedLanguagesController {

    private final SupportedLanguageService supportedLanguageService;

    @GetMapping
    public List<SupportedLanguageDTO> listSupportedLanguages() {
        return supportedLanguageService.listActive();
    }
}

