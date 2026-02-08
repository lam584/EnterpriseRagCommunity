package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import com.example.EnterpriseRagCommunity.entity.ai.LlmProviderSettingsEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmProviderRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmProviderSettingsRepository;
import com.example.EnterpriseRagCommunity.service.monitor.AppSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AiProvidersConfigServiceSettingsUpsertTest {

    @Test
    void upsertSettingsCreatesNewRowWithNullVersionSoJpaTreatsItAsNew() throws Exception {
        AppSettingsService appSettingsService = mock(AppSettingsService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AiProperties aiProperties = mock(AiProperties.class);
        LlmProviderRepository llmProviderRepository = mock(LlmProviderRepository.class);
        LlmProviderSettingsRepository llmProviderSettingsRepository = mock(LlmProviderSettingsRepository.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);
        LlmSecretsCryptoService llmSecretsCryptoService = mock(LlmSecretsCryptoService.class);

        AiProvidersConfigService svc = new AiProvidersConfigService(
                appSettingsService,
                objectMapper,
                aiProperties,
                llmProviderRepository,
                llmProviderSettingsRepository,
                llmModelRepository,
                llmSecretsCryptoService
        );

        when(llmProviderSettingsRepository.findById("default")).thenReturn(Optional.empty());
        when(llmProviderSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Method m = AiProvidersConfigService.class.getDeclaredMethod("upsertSettings", String.class, String.class, LocalDateTime.class, Long.class);
        m.setAccessible(true);

        LocalDateTime now = LocalDateTime.now();
        m.invoke(svc, "default", "p1", now, 123L);

        ArgumentCaptor<LlmProviderSettingsEntity> cap = ArgumentCaptor.forClass(LlmProviderSettingsEntity.class);
        verify(llmProviderSettingsRepository).save(cap.capture());

        LlmProviderSettingsEntity saved = cap.getValue();
        assertEquals("default", saved.getEnv());
        assertEquals("p1", saved.getActiveProviderId());
        assertEquals(now, saved.getUpdatedAt());
        assertEquals(123L, saved.getUpdatedBy());
        assertNull(saved.getVersion());
    }
}

