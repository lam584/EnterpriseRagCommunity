package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.entity.semantic.PromptsEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptLlmParamResolverTest {

    private final PromptLlmParamResolver resolver = new PromptLlmParamResolver();

    @Test
    void resolveText_should_use_defaults_when_prompt_null_and_ignore_fallbacks() {
        PromptLlmParams params = resolver.resolveText(
                null,
                "fb-provider",
                "fb-model",
                0.99,
                0.66,
                4096,
                true,
                0.35,
                0.8
        );

        assertNull(params.providerId());
        assertNull(params.model());
        assertEquals(0.35, params.temperature());
        assertEquals(0.8, params.topP());
        assertNull(params.maxTokens());
        assertFalse(params.enableThinking());
    }

    @Test
    void resolveText_should_trim_non_blank_provider_and_model() {
        PromptsEntity prompt = new PromptsEntity();
        prompt.setProviderId("  dashscope  ");
        prompt.setModelName("  qwen-max  ");

        PromptLlmParams params = resolver.resolveText(
                prompt,
                "fb-provider",
                "fb-model",
                0.99,
                0.66,
                4096,
                true,
                0.2,
                0.3
        );

        assertEquals("dashscope", params.providerId());
        assertEquals("qwen-max", params.model());
    }

    @Test
    void resolveText_should_return_null_for_blank_provider_and_model() {
        PromptsEntity prompt = new PromptsEntity();
        prompt.setProviderId("   ");
        prompt.setModelName("");

        PromptLlmParams params = resolver.resolveText(
                prompt,
                "fb-provider",
                "fb-model",
                0.99,
                0.66,
                4096,
                true,
                0.2,
                0.3
        );

        assertNull(params.providerId());
        assertNull(params.model());
    }

    @Test
    void resolveText_should_prefer_prompt_values_over_defaults() {
        PromptsEntity prompt = new PromptsEntity();
        prompt.setProviderId("provider-in-prompt");
        prompt.setModelName("model-in-prompt");
        prompt.setTemperature(0.15);
        prompt.setTopP(0.95);
        prompt.setMaxTokens(2048);
        prompt.setEnableDeepThinking(true);

        PromptLlmParams params = resolver.resolveText(
                prompt,
                "fb-provider",
                "fb-model",
                0.99,
                0.66,
                4096,
                false,
                0.2,
                0.3
        );

        assertEquals("provider-in-prompt", params.providerId());
        assertEquals("model-in-prompt", params.model());
        assertEquals(0.15, params.temperature());
        assertEquals(0.95, params.topP());
        assertEquals(2048, params.maxTokens());
        assertTrue(params.enableThinking());
    }

    @Test
    void resolveText_should_use_default_values_for_temperature_topP_and_false_when_flag_false() {
        PromptsEntity prompt = new PromptsEntity();
        prompt.setTemperature(null);
        prompt.setTopP(null);
        prompt.setMaxTokens(null);
        prompt.setEnableDeepThinking(false);

        PromptLlmParams params = resolver.resolveText(
                prompt,
                "fb-provider",
                "fb-model",
                0.99,
                0.66,
                4096,
                true,
                0.42,
                0.73
        );

        assertEquals(0.42, params.temperature());
        assertEquals(0.73, params.topP());
        assertNull(params.maxTokens());
        assertFalse(params.enableThinking());
    }

    @Test
    void resolveText_should_return_null_for_temperature_and_topP_when_both_prompt_and_default_missing() {
        PromptsEntity prompt = new PromptsEntity();
        prompt.setTemperature(null);
        prompt.setTopP(null);
        prompt.setEnableDeepThinking(null);

        PromptLlmParams params = resolver.resolveText(
                prompt,
                "fb-provider",
                "fb-model",
                0.99,
                0.66,
                4096,
                true,
                null,
                null
        );

        assertNull(params.temperature());
        assertNull(params.topP());
        assertFalse(params.enableThinking());
    }
}
