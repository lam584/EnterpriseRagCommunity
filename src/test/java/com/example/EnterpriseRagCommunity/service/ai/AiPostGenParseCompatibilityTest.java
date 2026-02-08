package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AiPostGenParseCompatibilityTest {

    @Test
    void titleParse_supportsJsonArrayRoot() {
        AiPostTitleService svc = new AiPostTitleService(new AiProperties(), null, null);
        List<String> out = svc.parseTitlesFromAssistantText("[\"t1\",\"t2\"]", 3);
        assertEquals(List.of("t1", "t2"), out);
    }

    @Test
    void titleParse_supportsJsonObjectTitles() {
        AiPostTitleService svc = new AiPostTitleService(new AiProperties(), null, null);
        List<String> out = svc.parseTitlesFromAssistantText("{\"titles\":[\"t1\",\"t2\"]}", 3);
        assertEquals(List.of("t1", "t2"), out);
    }

    @Test
    void tagParse_supportsJsonArrayRoot() {
        AiPostTagService svc = new AiPostTagService(new AiProperties(), null, null);
        List<String> out = svc.parseTagsFromAssistantText("[\"a\",\"b\",\"c\"]", 5);
        assertEquals(List.of("a", "b", "c"), out);
    }

    @Test
    void summaryParse_supportsPlainTextFallback() {
        AiPostSummaryService svc = new AiPostSummaryService(new AiProperties(), null, null, null, null);
        String raw = "仅凭单个 `.gguf` 文件，无法在 Java 中严格复现 Qwen3 官方分词。";
        AiPostSummaryService.ParsedSummary parsed = svc.parseSummaryFromAssistantText(raw);
        assertNull(parsed.title());
        assertEquals(raw, parsed.summary());
    }

    @Test
    void summaryParse_supportsJsonObject() {
        AiPostSummaryService svc = new AiPostSummaryService(new AiProperties(), null, null, null, null);
        AiPostSummaryService.ParsedSummary parsed = svc.parseSummaryFromAssistantText("{\"title\":\"t\",\"summary\":\"s\"}");
        assertEquals("t", parsed.title());
        assertEquals("s", parsed.summary());
    }
}
