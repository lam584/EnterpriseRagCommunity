package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmProviderEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmProviderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AiProviderModelsAdminServiceResetRuntimeStateTest {

    @Test
    void addProviderModel_shouldResetRuntimeState() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        AiProvidersConfigService providersConfig = mock(AiProvidersConfigService.class);
        ObjectMapper om = new ObjectMapper();
        LlmRoutingService routingService = mock(LlmRoutingService.class);

        AiProviderModelsAdminService svc = new AiProviderModelsAdminService(
                modelRepo,
                providerRepo,
                providersConfig,
                om,
                routingService
        );

        when(providerRepo.findByEnvAndProviderId(eq("default"), eq("p1"))).thenReturn(Optional.of(new LlmProviderEntity()));
        when(modelRepo.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("POST_EMBEDDING"), eq("m1")))
                .thenReturn(Optional.empty());
        when(modelRepo.save(any(LlmModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(modelRepo.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc(eq("default"), eq("p1")))
                .thenReturn(List.of());

        svc.addProviderModel("p1", "POST_EMBEDDING", "m1", 1L);

        verify(routingService).resetRuntimeState();
    }

    @Test
    void deleteProviderModel_shouldResetRuntimeState() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmProviderRepository providerRepo = mock(LlmProviderRepository.class);
        AiProvidersConfigService providersConfig = mock(AiProvidersConfigService.class);
        ObjectMapper om = new ObjectMapper();
        LlmRoutingService routingService = mock(LlmRoutingService.class);

        AiProviderModelsAdminService svc = new AiProviderModelsAdminService(
                modelRepo,
                providerRepo,
                providersConfig,
                om,
                routingService
        );

        LlmModelEntity e = new LlmModelEntity();
        e.setEnv("default");
        e.setProviderId("p1");
        e.setPurpose("POST_EMBEDDING");
        e.setModelName("m1");

        when(modelRepo.findByEnvAndProviderIdAndPurposeAndModelName(eq("default"), eq("p1"), eq("POST_EMBEDDING"), eq("m1")))
                .thenReturn(Optional.of(e));
        when(modelRepo.findByEnvAndProviderIdOrderByPurposeAscIsDefaultDescWeightDescIdAsc(eq("default"), eq("p1")))
                .thenReturn(List.of());

        svc.deleteProviderModel("p1", "POST_EMBEDDING", "m1");

        verify(routingService).resetRuntimeState();
    }
}

