package com.example.EnterpriseRagCommunity.service.moderation.admin;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminModerationLlmServiceRiskTagsWhitelistTest {

    @Test
    void enforceRiskTagsWhitelist_shouldMapSlugToNameAndDropUnknown() {
        LlmModerationTestResponse.LabelItem it = new LlmModerationTestResponse.LabelItem();
        it.setSlug("violence");
        it.setName("violence");

        LlmModerationTestResponse.LabelTaxonomy tax = new LlmModerationTestResponse.LabelTaxonomy();
        tax.setTaxonomyId("risk_tags");
        tax.setAllowedLabels(List.of("violence", "scam"));
        tax.setLabelMap(List.of(it));

        StageCallResult in = new StageCallResult(
                "REJECT",
                0.9,
                List.of("violence", "unknown"),
                "REJECT",
                0.9,
                List.of("raw"),
                List.of("violence", "unknown"),
                null,
                null,
                List.of(),
                "{}",
                "m",
                1L,
                null,
                null,
                null,
                "text"
        );

        StageCallResult out = AdminModerationLlmService.enforceRiskTagsWhitelist(in, tax);
        assertEquals(List.of("violence"), out.riskTags());
        assertEquals(List.of("violence"), out.labels());
        assertTrue(out.reasons().stream().anyMatch(s -> s != null && s.contains("Filtered out")));
    }

    @Test
    void enforceRiskTagsWhitelist_shouldDowngradeRejectWhenAllTagsInvalid() {
        LlmModerationTestResponse.LabelTaxonomy tax = new LlmModerationTestResponse.LabelTaxonomy();
        tax.setTaxonomyId("risk_tags");
        tax.setAllowedLabels(List.of("violence"));
        tax.setLabelMap(List.of());

        StageCallResult in = new StageCallResult(
                "REJECT",
                0.9,
                List.of("illegal-medical"),
                "REJECT",
                0.9,
                List.of(),
                List.of("illegal-medical"),
                null,
                null,
                List.of(),
                "{}",
                "m",
                1L,
                null,
                null,
                null,
                "text"
        );

        StageCallResult out = AdminModerationLlmService.enforceRiskTagsWhitelist(in, tax);
        assertEquals(List.of(), out.riskTags());
        assertEquals("ESCALATE", out.decisionSuggestion());
        assertEquals("HUMAN", out.decision());
    }
}
