package com.example.EnterpriseRagCommunity.service.moderation.jobs;

import com.example.EnterpriseRagCommunity.dto.moderation.LlmModerationTestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModerationLlmAutoRunnerQueueOutputSanitizeTest {

    @Test
    void sanitizeModerationResponseForQueueOutput_shouldRemovePromptMessagesAndLabelMap() throws Exception {
        ObjectMapper om = new ObjectMapper();

        LlmModerationTestResponse resp = new LlmModerationTestResponse();
        resp.setDecisionSuggestion("ALLOW");
        resp.setDecision("APPROVE");

        LlmModerationTestResponse.Message m1 = new LlmModerationTestResponse.Message();
        m1.setRole("system");
        m1.setContent("TRACE queueId=1 postId=1");
        resp.setPromptMessages(List.of(m1));

        LlmModerationTestResponse.LabelTaxonomy tax = new LlmModerationTestResponse.LabelTaxonomy();
        tax.setTaxonomyId("risk_tags");
        tax.setAllowedLabels(List.of("政治敏感"));
        LlmModerationTestResponse.LabelItem it = new LlmModerationTestResponse.LabelItem();
        it.setSlug("political");
        it.setName("政治敏感");
        tax.setLabelMap(List.of(it));
        resp.setLabelTaxonomy(tax);

        Object sanitized = ModerationLlmAutoRunner.sanitizeModerationResponseForQueueOutput(om, resp);
        JsonNode n = sanitized instanceof JsonNode j ? j : om.valueToTree(sanitized);
        assertNotNull(n);

        assertFalse(n.has("promptMessages"));

        JsonNode lt = n.get("labelTaxonomy");
        if (lt != null && lt.isObject()) {
            assertFalse(lt.has("labelMap"));
        }
    }
}

