package com.example.EnterpriseRagCommunity.service.ai;

public final class PromptConfigValidationSupport {

    private PromptConfigValidationSupport() {
    }

    public static ValidatedPromptConfig validatePromptCodeAndMaxContentChars(String promptCode,
                                                                             Integer maxContentChars,
                                                                             int defaultMaxContentChars,
                                                                             int maxAllowedContentChars) {
        if (promptCode == null || promptCode.isBlank()) {
            throw new IllegalArgumentException("promptCode 不能为空");
        }
        if (promptCode.length() > 64) {
            throw new IllegalArgumentException("promptCode 长度不能超过 64");
        }
        int safeMaxContentChars = maxContentChars == null ? defaultMaxContentChars : maxContentChars;
        if (safeMaxContentChars < 200 || safeMaxContentChars > maxAllowedContentChars) {
            throw new IllegalArgumentException("maxContentChars 需在 [200," + maxAllowedContentChars + "] 范围内");
        }
        return new ValidatedPromptConfig(promptCode, safeMaxContentChars);
    }

    public record ValidatedPromptConfig(String promptCode, int maxContentChars) {
    }
}
