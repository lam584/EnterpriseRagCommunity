package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import org.springframework.stereotype.Component;

@Component
public class PromptLlmParamResolver {

    public PromptLlmParams resolveText(
            PromptsEntity prompt,
            String fallbackProviderId,
            String fallbackModel,
            Double fallbackTemperature,
            Double fallbackTopP,
            Integer fallbackMaxTokens,
            Boolean fallbackEnableThinking,
            Double defaultTemperature,
            Double defaultTopP
    ) {
            // Cutover rule: invocation params are authoritative in prompts.
            // Keep method signature for compatibility while intentionally ignoring
            // non-prompt fallbacks from feature-specific config tables.
        String providerId = firstNonBlank(
                prompt == null ? null : prompt.getProviderId()
        );
        String model = firstNonBlank(
                prompt == null ? null : prompt.getModelName()
        );

        Double temperature = firstNonNull(
                prompt == null ? null : prompt.getTemperature(),
                defaultTemperature
        );
        Double topP = firstNonNull(
                prompt == null ? null : prompt.getTopP(),
                defaultTopP
        );
        Integer maxTokens = firstNonNull(
                prompt == null ? null : prompt.getMaxTokens()
        );
        Boolean enableThinking = firstNonNull(
                prompt == null ? null : prompt.getEnableDeepThinking(),
                Boolean.FALSE
        );

        return new PromptLlmParams(providerId, model, temperature, topP, maxTokens, enableThinking);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
