package com.example.EnterpriseRagCommunity.service.moderation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RiskLabelingServiceSlugNormalizationTest {

    @Test
    void normalizeSlug_shouldKeepChineseLetters() {
        assertThat(RiskLabelingService.normalizeSlug("色情")).isEqualTo("色情");
        assertThat(RiskLabelingService.normalizeSlug("成人内容")).isEqualTo("成人内容");
        assertThat(RiskLabelingService.normalizeSlug("软色情")).isEqualTo("软色情");
    }

    @Test
    void normalizeSlug_shouldNormalizeWhitespaceAndPunctuation() {
        assertThat(RiskLabelingService.normalizeSlug(" 色情 / 成人内容 ")).isEqualTo("色情-成人内容");
        assertThat(RiskLabelingService.normalizeSlug("软 色情")).isEqualTo("软-色情");
    }

    @Test
    void normalizeSlug_shouldNormalizeEnglishAndUnderscore() {
        assertThat(RiskLabelingService.normalizeSlug("ADULT_CONTENT")).isEqualTo("adult-content");
        assertThat(RiskLabelingService.normalizeSlug("soft-porn")).isEqualTo("soft-porn");
    }

    @Test
    void normalizeSlugs_shouldDeduplicateAndKeepOrder() {
        assertThat(RiskLabelingService.normalizeSlugs(List.of(" 色情 ", "色情", "成人内容", "成人内容")))
                .containsExactly("色情", "成人内容");
    }
}

