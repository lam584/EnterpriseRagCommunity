package com.example.EnterpriseRagCommunity.service.content.impl;

import com.example.EnterpriseRagCommunity.entity.content.enums.TagType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class TagsServiceImplSlugValidationTest {

    @Test
    void validateSlug_shouldAllowUnicodeForRiskTags() {
        assertThatNoException().isThrownBy(() -> TagsServiceImpl.validateSlug(TagType.RISK, "色情"));
        assertThatNoException().isThrownBy(() -> TagsServiceImpl.validateSlug(TagType.RISK, "软-色情"));
    }

    @Test
    void validateSlug_shouldRejectSpacesForRiskTags() {
        assertThatThrownBy(() -> TagsServiceImpl.validateSlug(TagType.RISK, "软 色情"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateSlug_shouldRejectUnicodeForNonRiskTags() {
        assertThatThrownBy(() -> TagsServiceImpl.validateSlug(TagType.TOPIC, "色情"))
                .isInstanceOf(IllegalArgumentException.class);
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

