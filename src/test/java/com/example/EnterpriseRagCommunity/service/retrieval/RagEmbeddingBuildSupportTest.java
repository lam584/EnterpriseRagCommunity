package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagEmbeddingBuildSupportTest {

    @Test
    void preselectTarget_should_cover_override_fixedProvider_and_providerOnly_cases() {
        RagEmbeddingBuildSupport.SelectedEmbeddingTarget override = RagEmbeddingBuildSupport.preselectTarget(
                " model-x ",
                " provider-x ",
                Map.of("embeddingProviderId", "fixed"),
                true
        );
        assertEquals("model-x", override.model());
        assertEquals("provider-x", override.providerId());

        RagEmbeddingBuildSupport.SelectedEmbeddingTarget fixed = RagEmbeddingBuildSupport.preselectTarget(
                null,
                null,
                Map.of("embeddingProviderId", " fixed-provider "),
                true
        );
        assertNull(fixed.model());
        assertEquals("fixed-provider", fixed.providerId());

        RagEmbeddingBuildSupport.SelectedEmbeddingTarget providerOnlyAllowed = RagEmbeddingBuildSupport.preselectTarget(
                null,
                " provider-only ",
                (String) null,
                false
        );
        assertNull(providerOnlyAllowed.model());
        assertEquals("provider-only", providerOnlyAllowed.providerId());

        RagEmbeddingBuildSupport.SelectedEmbeddingTarget providerOnlyWithStrictMode = RagEmbeddingBuildSupport.preselectTarget(
                null,
                " provider-only ",
                (String) null,
                true
        );
        assertNull(providerOnlyWithStrictMode.model());
        assertEquals("provider-only", providerOnlyWithStrictMode.providerId());
    }

    @Test
    void resolveEmbeddingTarget_should_cover_routing_legacy_and_error_cases() {
        LlmRoutingService routing = mock(LlmRoutingService.class);
        LlmRoutingService.RouteTarget target = new LlmRoutingService.RouteTarget(
                new LlmRoutingService.TargetId("provider-a", "model-a"),
                1,
                1,
                null
        );
        when(routing.pickNext(eq(com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType.POST_EMBEDDING), any()))
                .thenReturn(target)
                .thenReturn(null);
        when(routing.pickNextInProvider(eq(com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType.POST_EMBEDDING), eq("fixed-provider"), any()))
                .thenReturn(null);

        RagEmbeddingBuildSupport.ResolvedEmbeddingTarget routed = RagEmbeddingBuildSupport.resolveEmbeddingTarget(
                routing,
                null,
                null,
                Map.of(),
                true,
                null
        );
        assertEquals("model-a", routed.model());
        assertEquals("provider-a", routed.providerId());

        RagEmbeddingBuildSupport.ResolvedEmbeddingTarget legacy = RagEmbeddingBuildSupport.resolveEmbeddingTarget(
                routing,
                null,
                null,
                Map.of("embeddingProviderId", "fixed-provider"),
                true,
                "legacy-model"
        );
        assertEquals("legacy-model", legacy.model());
        assertNull(legacy.providerId());

        assertThrows(
                IllegalStateException.class,
                () -> RagEmbeddingBuildSupport.resolveEmbeddingTarget(
                        routing,
                        null,
                        null,
                        (String) null,
                        true,
                        null
                )
        );
    }
}
