package com.example.EnterpriseRagCommunity.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AiPostGenParseCompatibilityTest {

    @Test
    void parseTitle_compatibleWithV1AndV2() {
        AiPostTitleService svc = new AiPostTitleService(null, null, null, null, null);
        
        // V1 style (just list)
        List<String> r1 = svc.parseTitlesFromAssistantText("[\"T1\", \"T2\"]", 2);
        assertEquals(2, r1.size());
        assertEquals("T1", r1.get(0));

        // V2 style (JSON object)
        List<String> r2 = svc.parseTitlesFromAssistantText("{\"titles\":[\"T3\", \"T4\"]}", 2);
        assertEquals(2, r2.size());
        assertEquals("T3", r2.get(0));
    }

    @Test
    void parseTag_compatibleWithV1AndV2() {
        AiPostTagService svc = new AiPostTagService(null, null, null, null, null);

        List<String> r1 = svc.parseTagsFromAssistantText("[\"A\", \"B\"]", 2);
        assertEquals(2, r1.size());
        assertEquals("A", r1.get(0));

        List<String> r2 = svc.parseTagsFromAssistantText("{\"tags\":[\"C\", \"D\"]}", 2);
        assertEquals(2, r2.size());
        assertEquals("C", r2.get(0));
    }

    @Test
    void parseSummary_compatibleWithV1AndV2() {
        AiPostSummaryService svc = new AiPostSummaryService(null, null, null, null, null, null, null);

        // V1: just string or simple json? actually V1 summary was often just text. 
        // But the parseOutput logic handles JSON.
        // Let's test the JSON object wrapper
        AiPostSummaryService.ParsedSummary r1 = svc.parseSummaryFromAssistantText("{\"title\":\"T\",\"summary\":\"S\"}");
        assertEquals("T", r1.title());
        assertEquals("S", r1.summary());
    }
}
