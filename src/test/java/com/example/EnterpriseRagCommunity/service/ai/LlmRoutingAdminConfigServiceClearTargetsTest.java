package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmRoutingConfigDTO;
import com.example.EnterpriseRagCommunity.dto.ai.AdminLlmRoutingPolicyDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmModelEntity;
import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmRoutingPolicyRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LlmRoutingAdminConfigServiceClearTargetsTest {

    @Test
    void updateAdminConfigDeletesExistingTargetsWhenIncomingEmpty() {
        LlmRoutingService llmRoutingService = mock(LlmRoutingService.class);
        LlmRoutingPolicyRepository llmRoutingPolicyRepository = mock(LlmRoutingPolicyRepository.class);
        LlmModelRepository llmModelRepository = mock(LlmModelRepository.class);

        when(llmRoutingService.getPolicy(anyString())).thenReturn(new LlmRoutingService.Policy(
                LlmRoutingService.Strategy.WEIGHTED_RR,
                2,
                2,
                30_000
        ));
        when(llmRoutingPolicyRepository.findById(any())).thenReturn(Optional.empty());
        when(llmRoutingPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime before = LocalDateTime.now().minusDays(1);
        LlmModelEntity existing = new LlmModelEntity();
        existing.setEnv("default");
        existing.setProviderId("p1");
        existing.setPurpose("CHAT");
        existing.setModelName("m1");
        existing.setEnabled(Boolean.TRUE);
        existing.setIsDefault(Boolean.FALSE);
        existing.setWeight(0);
        existing.setPriority(0);
        existing.setSortIndex(0);
        existing.setCreatedAt(before);
        existing.setUpdatedAt(before);

        when(llmModelRepository.findByEnvOrderByPurposeAscSortIndexAscPriorityDescWeightDescIsDefaultDescIdAsc(eq("default")))
                .thenReturn(List.of(existing));

        LlmRoutingAdminConfigService svc = new LlmRoutingAdminConfigService(
                llmRoutingService,
                llmRoutingPolicyRepository,
                llmModelRepository
        );

        AdminLlmRoutingPolicyDTO chatPolicy = new AdminLlmRoutingPolicyDTO();
        chatPolicy.setTaskType("CHAT");
        AdminLlmRoutingConfigDTO payload = new AdminLlmRoutingConfigDTO();
        payload.setPolicies(List.of(chatPolicy));
        payload.setTargets(List.of());

        svc.updateAdminConfig(payload, 7L);

        verify(llmModelRepository, times(1)).delete(any(LlmModelEntity.class));
    }
}
