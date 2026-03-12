package com.example.EnterpriseRagCommunity.controller.monitor.admin;

import com.example.EnterpriseRagCommunity.dto.monitor.AdminTokenSourceDTO;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyEntity;
import com.example.EnterpriseRagCommunity.entity.ai.LlmRoutingPolicyId;
import com.example.EnterpriseRagCommunity.repository.ai.LlmRoutingPolicyRepository;
import com.example.EnterpriseRagCommunity.service.monitor.TokenCostMetricsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminTokenMetricsControllerSourcesTest {

    @Test
    void sourcesShouldIncludeUsedTypesWhenPolicyMissing() {
        TokenCostMetricsService tokenCostMetricsService = mock(TokenCostMetricsService.class);
        LlmRoutingPolicyRepository llmRoutingPolicyRepository = mock(LlmRoutingPolicyRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);

        LlmRoutingPolicyEntity e = new LlmRoutingPolicyEntity();
        e.setId(new LlmRoutingPolicyId("default", "MULTIMODAL_CHAT"));
        e.setLabel("多模态聊天");
        e.setCategory("TEXT_GEN");
        e.setSortIndex(10);

        when(llmRoutingPolicyRepository.findByIdEnvOrderBySortIndexAscIdTaskTypeAsc("default"))
                .thenReturn(List.of(e));

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of("embedding", "MODERATION_CHUNK", "multimodal_chat", "multimodal_moderation"));

        AdminTokenMetricsController c = new AdminTokenMetricsController(tokenCostMetricsService, llmRoutingPolicyRepository);
        ReflectionTestUtils.setField(c, "entityManager", entityManager);

        List<AdminTokenSourceDTO> out = c.sources();
        assertNotNull(out);
        Map<String, AdminTokenSourceDTO> byTaskType = out.stream()
                .filter(s -> s != null && s.getTaskType() != null)
                .collect(Collectors.toMap(s -> s.getTaskType().toUpperCase(), s -> s, (a, b) -> a));

        assertTrue(byTaskType.containsKey("MULTIMODAL_CHAT"));
        assertEquals("多模态聊天", byTaskType.get("MULTIMODAL_CHAT").getLabel());
        assertEquals("TEXT_GEN", byTaskType.get("MULTIMODAL_CHAT").getCategory());

        assertTrue(byTaskType.containsKey("MULTIMODAL_MODERATION"));
        assertEquals("多模态审核", byTaskType.get("MULTIMODAL_MODERATION").getLabel());
        assertEquals("TEXT_GEN", byTaskType.get("MULTIMODAL_MODERATION").getCategory());

        assertTrue(byTaskType.containsKey("EMBEDDING"));
        assertEquals("EMBEDDING", byTaskType.get("EMBEDDING").getCategory());

        assertTrue(byTaskType.containsKey("MODERATION_CHUNK"));
        assertEquals("分片审核", byTaskType.get("MODERATION_CHUNK").getLabel());
        assertEquals("TEXT_GEN", byTaskType.get("MODERATION_CHUNK").getCategory());
    }
}
