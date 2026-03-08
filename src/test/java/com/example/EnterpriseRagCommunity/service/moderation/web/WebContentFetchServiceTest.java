package com.example.EnterpriseRagCommunity.service.moderation.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WebContentFetchServiceTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.web-extraction.max-urls", () -> "10");
        r.add("app.web-extraction.timeout-millis", () -> "1000");
        r.add("app.web-extraction.max-redirects", () -> "1");
    }

    @Autowired
    WebContentFetchService webContentFetchService;

    @Test
    void shouldExtractHttpUrlsFromText() {
        List<String> urls = webContentFetchService.extractUrls("see https://example.com/a?b=c and http://example.com/x.");
        assertEquals(2, urls.size());
        assertTrue(urls.get(0).startsWith("https://") || urls.get(0).startsWith("http://"));
    }

    @Test
    void shouldBlockLocalhostSsrf() {
        Map<String, Object> meta = webContentFetchService.fetchUrlsToMeta(List.of("http://127.0.0.1:80/test"));
        Object items = meta.get("items");
        assertTrue(items instanceof List<?>);
        Map<?, ?> first = (Map<?, ?>) ((List<?>) items).get(0);
        assertEquals("SSRF_BLOCKED", String.valueOf(first.get("status")));
    }
}

