package com.example.EnterpriseRagCommunity.service.ai;

public record PromptLlmParams(
        String providerId,
        String model,
        Double temperature,
        Double topP,
        Integer maxTokens,
        Boolean enableThinking
) {
}
