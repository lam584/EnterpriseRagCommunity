package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.SupportedLanguageDTO;
import com.example.EnterpriseRagCommunity.service.ai.SupportedLanguageService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiSupportedLanguagesControllerTest {

    @Test
    void listSupportedLanguages_returnsServiceValue() {
        SupportedLanguageService svc = mock(SupportedLanguageService.class);
        List<SupportedLanguageDTO> langs = List.of(new SupportedLanguageDTO());
        when(svc.listActive()).thenReturn(langs);

        AiSupportedLanguagesController c = new AiSupportedLanguagesController(svc);
        Assertions.assertSame(langs, c.listSupportedLanguages());
    }
}

