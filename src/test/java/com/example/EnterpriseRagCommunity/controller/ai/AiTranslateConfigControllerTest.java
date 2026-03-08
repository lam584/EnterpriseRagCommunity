package com.example.EnterpriseRagCommunity.controller.ai;

import com.example.EnterpriseRagCommunity.dto.ai.SemanticTranslatePublicConfigDTO;
import com.example.EnterpriseRagCommunity.service.ai.SemanticTranslateConfigService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiTranslateConfigControllerTest {

    @Test
    void getConfig_returnsServiceValue() {
        SemanticTranslateConfigService svc = mock(SemanticTranslateConfigService.class);
        SemanticTranslatePublicConfigDTO dto = new SemanticTranslatePublicConfigDTO();
        dto.setEnabled(true);

        when(svc.getPublicConfig()).thenReturn(dto);

        AiTranslateConfigController c = new AiTranslateConfigController(svc);
        Assertions.assertSame(dto, c.getConfig());
    }
}

