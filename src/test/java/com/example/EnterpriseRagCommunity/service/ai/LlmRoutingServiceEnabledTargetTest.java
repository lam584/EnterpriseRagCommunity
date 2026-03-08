package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.repository.ai.LlmModelRepository;
import com.example.EnterpriseRagCommunity.repository.ai.LlmRoutingPolicyRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LlmRoutingServiceEnabledTargetTest {

    @Test
    void isEnabledTarget_shouldCheckPurposeAndEnabled() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmRoutingPolicyRepository policyRepo = mock(LlmRoutingPolicyRepository.class);
        LlmCallQueueService queueSvc = mock(LlmCallQueueService.class);

        LlmRoutingService svc = new LlmRoutingService(modelRepo, policyRepo, queueSvc);

        when(modelRepo.existsByEnvAndPurposeAndProviderIdAndModelNameAndEnabledTrue(
                "default",
                "POST_EMBEDDING",
                "p1",
                "m1"
        )).thenReturn(true);

        assertTrue(svc.isEnabledTarget(LlmQueueTaskType.POST_EMBEDDING, "p1", "m1"));
        assertFalse(svc.isEnabledTarget(LlmQueueTaskType.POST_EMBEDDING, "p1", "missing"));
        assertFalse(svc.isEnabledTarget(LlmQueueTaskType.POST_EMBEDDING, " ", "m1"));
        assertFalse(svc.isEnabledTarget(LlmQueueTaskType.POST_EMBEDDING, "p1", " "));
    }

    @Test
    void isEnabledTarget_imageModeration_shouldAlsoRequireImageChatEnabled() {
        LlmModelRepository modelRepo = mock(LlmModelRepository.class);
        LlmRoutingPolicyRepository policyRepo = mock(LlmRoutingPolicyRepository.class);
        LlmCallQueueService queueSvc = mock(LlmCallQueueService.class);

        LlmRoutingService svc = new LlmRoutingService(modelRepo, policyRepo, queueSvc);

        when(modelRepo.existsByEnvAndPurposeAndProviderIdAndModelNameAndEnabledTrue(
                "default",
                "IMAGE_MODERATION",
                "p1",
                "m1"
        )).thenReturn(true);

        when(modelRepo.existsByEnvAndPurposeAndProviderIdAndModelNameAndEnabledTrue(
                "default",
                "IMAGE_CHAT",
                "p1",
                "m1"
        )).thenReturn(false);

        assertFalse(svc.isEnabledTarget(LlmQueueTaskType.IMAGE_MODERATION, "p1", "m1"));
    }
}

