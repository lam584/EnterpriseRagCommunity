package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.PromptsRepository;

import java.util.function.Consumer;

public final class PromptConfigAdminSupport {

    private PromptConfigAdminSupport() {
    }

    public static void applyBaseFields(
            Long id,
            Integer version,
            Boolean enabled,
            String promptCode,
            Integer maxContentChars,
            Consumer<Long> idSetter,
            Consumer<Integer> versionSetter,
            Consumer<Boolean> enabledSetter,
            Consumer<String> promptCodeSetter,
            Consumer<Integer> maxContentCharsSetter
    ) {
        idSetter.accept(id);
        versionSetter.accept(version);
        enabledSetter.accept(enabled);
        promptCodeSetter.accept(promptCode);
        maxContentCharsSetter.accept(maxContentChars);
    }

    public static ResolvedPromptFields resolvePromptFields(String promptCode, PromptsRepository promptsRepository) {
        if (promptCode == null || promptCode.isBlank() || promptsRepository == null) {
            return ResolvedPromptFields.empty();
        }
        PromptsEntity prompt = promptsRepository.findByPromptCode(promptCode).orElse(null);
        if (prompt == null) {
            return ResolvedPromptFields.empty();
        }
        return new ResolvedPromptFields(
                prompt.getModelName(),
                prompt.getProviderId(),
                prompt.getTemperature(),
                prompt.getTopP(),
                prompt.getEnableDeepThinking()
        );
    }

    public record ResolvedPromptFields(
            String model,
            String providerId,
            Double temperature,
            Double topP,
            Boolean enableThinking
    ) {
        static ResolvedPromptFields empty() {
            return new ResolvedPromptFields(null, null, null, null, null);
        }

        public void applyTo(
                Consumer<String> modelSetter,
                Consumer<String> providerIdSetter,
                Consumer<Double> temperatureSetter,
                Consumer<Double> topPSetter,
                Consumer<Boolean> enableThinkingSetter
        ) {
            modelSetter.accept(model);
            providerIdSetter.accept(providerId);
            temperatureSetter.accept(temperature);
            topPSetter.accept(topP);
            enableThinkingSetter.accept(enableThinking);
        }
    }
}
