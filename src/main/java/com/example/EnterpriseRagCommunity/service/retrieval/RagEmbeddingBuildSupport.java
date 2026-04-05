package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.service.ai.LlmQueueTaskType;
import com.example.EnterpriseRagCommunity.service.ai.LlmRoutingService;

import java.util.HashSet;
import java.util.Map;

public final class RagEmbeddingBuildSupport {

    private RagEmbeddingBuildSupport() {
    }

    public static SelectedEmbeddingTarget preselectTarget(String embeddingModelOverride,
                                                          String embeddingProviderId,
                                                          Map<String, Object> meta0ForDefaults,
                                                          boolean requireModelForProviderOverride) {
        String fixedProviderId = meta0ForDefaults == null ? null : toNonBlankString(meta0ForDefaults.get("embeddingProviderId"));
        return preselectTarget(embeddingModelOverride, embeddingProviderId, fixedProviderId, requireModelForProviderOverride);
    }

    public static SelectedEmbeddingTarget preselectTarget(String embeddingModelOverride,
                                                          String embeddingProviderId,
                                                          String fixedProviderId,
                                                          boolean requireModelForProviderOverride) {
        String overrideModel = toNonBlankString(embeddingModelOverride);
        String overrideProviderId = toNonBlankString(embeddingProviderId);
        String fixedProvider = toNonBlankString(fixedProviderId);

        String modelToUse = null;
        String providerToUse = null;
        if (overrideModel != null) {
            modelToUse = overrideModel;
            providerToUse = overrideProviderId;
        } else if (fixedProvider != null) {
            providerToUse = fixedProvider;
        } else if (overrideProviderId != null) {
            providerToUse = overrideProviderId;
        }
        return new SelectedEmbeddingTarget(modelToUse, providerToUse);
    }

    public static ResolvedEmbeddingTarget resolveEmbeddingTarget(
            LlmRoutingService llmRoutingService,
            String embeddingModelOverride,
            String embeddingProviderId,
            Map<String, Object> meta0ForDefaults,
            boolean requireModelForProviderOverride,
            String legacyModelFallback
    ) {
        String fixedProviderId = meta0ForDefaults == null ? null : toNonBlankString(meta0ForDefaults.get("embeddingProviderId"));
        return resolveEmbeddingTarget(
                llmRoutingService,
                embeddingModelOverride,
                embeddingProviderId,
                fixedProviderId,
                requireModelForProviderOverride,
                legacyModelFallback
        );
    }

    public static ResolvedEmbeddingTarget resolveEmbeddingTarget(
            LlmRoutingService llmRoutingService,
            String embeddingModelOverride,
            String embeddingProviderId,
            String fixedProviderId,
            boolean requireModelForProviderOverride,
            String legacyModelFallback
    ) {
        SelectedEmbeddingTarget selectedTarget = preselectTarget(
                embeddingModelOverride,
                embeddingProviderId,
                fixedProviderId,
                requireModelForProviderOverride
        );
        String modelToUse = selectedTarget.model();
        String providerToUse = selectedTarget.providerId();
        if (modelToUse != null) {
            return new ResolvedEmbeddingTarget(modelToUse, providerToUse);
        }

        LlmRoutingService.RouteTarget target = (providerToUse == null)
                ? llmRoutingService.pickNext(LlmQueueTaskType.POST_EMBEDDING, new HashSet<>())
                : llmRoutingService.pickNextInProvider(LlmQueueTaskType.POST_EMBEDDING, providerToUse, new HashSet<>());
        if (target == null) {
            String legacy = toNonBlankString(legacyModelFallback);
            if (legacy == null) {
                throw new IllegalStateException(providerToUse == null
                        ? "no eligible embedding target (please check embedding routing config)"
                        : ("no eligible embedding target for providerId=" + providerToUse + " (please check embedding routing config)"));
            }
            return new ResolvedEmbeddingTarget(legacy, null);
        }
        return new ResolvedEmbeddingTarget(target.modelName(), target.providerId());
    }

    public static Integer resolveConfiguredDims(Integer expectedEmbeddingDims, Integer vectorIndexDims) {
        Integer configuredDims = expectedEmbeddingDims != null && expectedEmbeddingDims > 0 ? expectedEmbeddingDims : null;
        if (configuredDims == null) {
            configuredDims = vectorIndexDims != null && vectorIndexDims > 0 ? vectorIndexDims : null;
        }
        return configuredDims;
    }

    private static String toNonBlankString(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public record SelectedEmbeddingTarget(String model, String providerId) {
    }

    public record ResolvedEmbeddingTarget(String model, String providerId) {
    }
}
