package com.example.EnterpriseRagCommunity.service.ai;

final class AiPromptSamplingSupport {

    private AiPromptSamplingSupport() {
    }

    static PromptSampling resolve(String requestedModel, String defaultModel,
                                  Double requestedTemperature, Double defaultTemperature, double fallbackTemperature,
                                  Double requestedTopP, Double defaultTopP, double fallbackTopP) {
        String modelOverride = resolveModelOverride(requestedModel, defaultModel);
        Double temperature = requestedTemperature != null ? requestedTemperature : defaultTemperature;
        if (temperature == null) temperature = fallbackTemperature;
        if (temperature < 0 || temperature > 2) throw new IllegalArgumentException("temperature 需在 [0,2] 范围内");

        Double topP = requestedTopP != null ? requestedTopP : defaultTopP;
        if (topP == null) topP = fallbackTopP;
        if (topP < 0 || topP > 1) throw new IllegalArgumentException("topP 需在 [0,1] 范围内");
        return new PromptSampling(modelOverride, temperature, topP);
    }

    static String resolveModelOverride(String requestedModel, String defaultModel) {
        if (requestedModel == null || requestedModel.isBlank()) return defaultModel;
        return requestedModel.trim();
    }

    record PromptSampling(String modelOverride, Double temperature, Double topP) {
    }
}
