package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class TagsServiceImplSlugValidationTest {

    @Test
    void validateSlug_shouldRejectBlankSlug() {
        assertThatThrownBy(() -> TagsServiceImpl.validateSlug(TagType.TOPIC, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slug 不能为空。");
        assertThatThrownBy(() -> TagsServiceImpl.validateSlug(TagType.TOPIC, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slug 不能为空。");
        assertThatThrownBy(() -> TagsServiceImpl.validateSlug(TagType.TOPIC, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slug 不能为空。");
    }

    @Test
    void validateSlug_shouldAllowUnicodeForRiskTags() {
        assertThatNoException().isThrownBy(() -> TagsServiceImpl.validateSlug(TagType.RISK, "色情"));
        assertThatNoException().isThrownBy(() -> TagsServiceImpl.validateSlug(TagType.RISK, "软-色情"));
    }

    @Test
    void validateSlug_shouldRejectSpacesForRiskTags() {
        assertThatThrownBy(() -> TagsServiceImpl.validateSlug(TagType.RISK, "软 色情"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slug 必须为 kebab-case（中文/字母数字/短横线）。");
    }

    @Test
    void validateSlug_shouldRejectUnicodeForNonRiskTags() {
        assertThatThrownBy(() -> TagsServiceImpl.validateSlug(TagType.TOPIC, "色情"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateSlug_shouldRejectInvalidFormatForNonRiskTagsWithMessage() {
        assertThatThrownBy(() -> TagsServiceImpl.validateSlug(TagType.TOPIC, "ABC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slug 必须为 kebab-case（小写字母/数字/短横线）。");
        assertThatThrownBy(() -> TagsServiceImpl.validateSlug(TagType.TOPIC, "a_b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Slug 必须为 kebab-case（小写字母/数字/短横线）。");
    }

    @Test
    void validateSlug_shouldRejectPunctuationForRiskTags() {
        assertThatThrownBy(() -> TagsServiceImpl.validateSlug(TagType.RISK, "色情!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateSlug_shouldAllowKebabCaseForNonRiskTags() {
        assertThatNoException().isThrownBy(() -> TagsServiceImpl.validateSlug(TagType.TOPIC, "abc-123"));
    }
}
