package com.example.EnterpriseRagCommunity.service.moderation.web;

import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
        ReflectionTestUtils.setField(webContentFetchService, "maxUrls", 10);
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

    @Test
    void shouldCoverBuildWebBlockBranches() {
        assertNull(webContentFetchService.buildWebBlock(null));
        assertNull(webContentFetchService.buildWebBlock(Map.of("items", "bad")));
        assertNull(webContentFetchService.buildWebBlock(Map.of("items", List.of())));

        String text = webContentFetchService.buildWebBlock(Map.of(
                "items", List.of(
                        "x",
                        Map.of("status", "OK"),
                        Map.of("url", "https://a.example", "status", "OK", "text", " hello "),
                        Map.of("url", "https://b.example", "text", "   ")
                )));
        assertNotNull(text);
        assertTrue(text.startsWith("[WEB]"));
        assertTrue(text.contains("url=https://a.example status=OK"));
        assertTrue(text.contains("hello"));
        assertTrue(text.contains("url=https://b.example"));
    }

    @Test
    void shouldCoverExtractUrlsAndFetchUrlsToMetaBranches() {
        ReflectionTestUtils.setField(webContentFetchService, "maxUrls", 1);
        assertEquals(0, webContentFetchService.extractUrls(null).size());
        assertEquals(0, webContentFetchService.extractUrls(" \t ").size());
        assertEquals(0, webContentFetchService.extractUrls("plain words only").size());
        assertEquals(0, webContentFetchService.extractUrls("bad http:///x").size());

        List<String> urls = webContentFetchService.extractUrls("x https://a.example/p), y https://b.example/z");
        assertEquals(1, urls.size());
        assertEquals("https://a.example/p", urls.get(0));

        Map<String, Object> meta1 = webContentFetchService.fetchUrlsToMeta(null);
        assertEquals(0, meta1.get("requestedCount"));
        assertEquals(0, meta1.get("dedupedCount"));

        Map<String, Object> meta2 = webContentFetchService.fetchUrlsToMeta(Arrays.asList("https://a.example", null, " ", "https://a.example"));
        assertEquals(4, meta2.get("requestedCount"));
        assertEquals(1, meta2.get("dedupedCount"));
    }

    @Test
    void shouldCoverNormalizeAndSupportHelpers() {
        assertEquals(List.of(), ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeUrls", null, 2));
        assertEquals(List.of(), ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeUrls", List.of(), 2));

        List<String> normalized = ReflectionTestUtils.invokeMethod(
                WebContentFetchService.class,
                "normalizeUrls",
                List.of("  ", "https://a.example/a.", "https://a.example/a.", "http://b.example/b"),
                2
        );
        assertEquals(2, normalized.size());
        assertEquals("https://a.example/a", normalized.get(0));

        assertNull(ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeOneUrl", (Object) null));
        assertNull(ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeOneUrl", " "));
        assertNull(ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeOneUrl", "ftp://x"));
        assertNull(ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeOneUrl", "http:///abc"));
        assertNull(ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeOneUrl", "http://[::1"));
        assertEquals("https://ok.example/x", ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeOneUrl", "https://ok.example/x."));
        assertEquals("https://ok.example/x", ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeOneUrl", "https://ok.example/x,"));
        assertEquals("https://ok.example/x", ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeOneUrl", "https://ok.example/x;"));
        assertEquals("https://ok.example/x", ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeOneUrl", "https://ok.example/x)"));
        assertEquals("https://ok.example/x", ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeOneUrl", "https://ok.example/x]"));
        assertEquals("https://ok.example/x", ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeOneUrl", "https://ok.example/x}"));
        assertEquals("https://ok.example/x", ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeOneUrl", "https://ok.example/x>"));

        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "isSupportedContentType", (Object) null));
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "isSupportedContentType", " "));
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "isSupportedContentType", "text/plain; charset=utf-8"));
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "isSupportedContentType", "application/xhtml+xml"));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "isSupportedContentType", "application/json"));

        byte[] none = ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "readLimited", new ByteArrayInputStream("abc".getBytes()), 0);
        assertEquals(0, none.length);
        byte[] two = ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "readLimited", new ByteArrayInputStream("abcdef".getBytes()), 2);
        assertEquals(2, two.length);
        byte[] exact = ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "readLimited", new ByteArrayInputStream("xy".getBytes()), 10);
        assertEquals(2, exact.length);

        assertEquals("", ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeText", (Object) null));
        assertEquals("a b", ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "normalizeText", "a\u0000 \n b"));

        Exception e1 = new RuntimeException();
        Exception e2 = new RuntimeException("  ");
        Exception e3 = new RuntimeException("x".repeat(500));
        assertEquals("RuntimeException", ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "safeMsg", e1));
        assertEquals("RuntimeException", ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "safeMsg", e2));
        String msg = ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "safeMsg", e3);
        assertEquals(300, msg.length());

        assertNull(ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "stringOrNull", (Object) null));
        assertNull(ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "stringOrNull", "   "));
        assertEquals("ok", ReflectionTestUtils.invokeMethod(WebContentFetchService.class, "stringOrNull", " ok "));
    }

    @Test
    void shouldCoverPortAndUrlValidationBranches() {
        ReflectionTestUtils.setField(webContentFetchService, "allowedPorts", "80,,abc,443");
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(webContentFetchService, "isAllowedPort", -1));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(webContentFetchService, "isAllowedPort", 70000));
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(webContentFetchService, "isAllowedPort", 80));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(webContentFetchService, "isAllowedPort", 8080));

        ReflectionTestUtils.setField(webContentFetchService, "allowedPorts", "  ");
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(webContentFetchService, "isAllowedPort", 443));
        assertFalse((Boolean) ReflectionTestUtils.invokeMethod(webContentFetchService, "isAllowedPort", 8443));
        ReflectionTestUtils.setField(webContentFetchService, "allowedPorts", null);
        assertTrue((Boolean) ReflectionTestUtils.invokeMethod(webContentFetchService, "isAllowedPort", 80));

        assertEquals("NULL", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", (Object) null));
        assertEquals("SCHEME", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("ftp://example.com/a")));
        assertEquals("SCHEME", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("//example.com/a")));
        assertEquals("USERINFO", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("http://u@example.com/a")));
        assertNull(ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("http://@example.com/a")));
        assertEquals("HOST", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("http:///a")));
        assertEquals("LOCALHOST", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("http://localhost/a")));
        assertEquals("LOCALHOST", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("http://abc.localhost/a")));
        assertEquals("LOCALHOST", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("http://abc.local/a")));
        assertEquals("PORT", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("http://example.com:9999/a")));
        assertEquals("DNS", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("http://nonexistent-xyz-1234567890.invalid/a")));
        assertEquals("PRIVATE_IP", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("http://0.0.0.0/a")));
        assertEquals("PRIVATE_IP", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("http://127.0.0.1/a")));
        assertEquals("PRIVATE_IP", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("http://169.254.10.1/a")));
        assertEquals("PRIVATE_IP", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("http://10.0.0.1/a")));
        assertEquals("PRIVATE_IP", ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("http://224.0.0.1/a")));
        assertNull(ReflectionTestUtils.invokeMethod(webContentFetchService, "validateUrlForFetch", URI.create("https://8.8.8.8/a")));
    }

    @Test
    void shouldCoverFetchOneBranchesWithMockClient() throws Exception {
        HttpClient client = Mockito.mock(HttpClient.class);
        ReflectionTestUtils.setField(webContentFetchService, "client", client);
        ReflectionTestUtils.setField(webContentFetchService, "allowedPorts", "80,443");
        ReflectionTestUtils.setField(webContentFetchService, "maxRedirects", 1);
        ReflectionTestUtils.setField(webContentFetchService, "timeoutMillis", 1000);
        ReflectionTestUtils.setField(webContentFetchService, "maxPageBytes", 1024);
        ReflectionTestUtils.setField(webContentFetchService, "maxPageChars", 5);

        Map<String, Object> invalid = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http:// bad");
        assertEquals("INVALID_URL", invalid.get("status"));

        Map<String, Object> blocked = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://localhost/a");
        assertEquals("SSRF_BLOCKED", blocked.get("status"));

        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new RuntimeException("send failed"));
        Map<String, Object> fetchError = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://example.com/a");
        assertEquals("FETCH_ERROR", fetchError.get("status"));

        HttpResponse<InputStream> noLocationRes = mockResponse(302, Map.of(), InputStream.nullInputStream());
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(noLocationRes);
        Map<String, Object> noLocation = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://example.com/a");
        assertEquals("REDIRECT_NO_LOCATION", noLocation.get("status"));

        HttpResponse<InputStream> blankLocationRes = mockResponse(302, Map.of("location", List.of("   ")), InputStream.nullInputStream());
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(blankLocationRes);
        Map<String, Object> blankLocation = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://example.com/a");
        assertEquals("REDIRECT_NO_LOCATION", blankLocation.get("status"));

        HttpResponse<InputStream> invalidRedirectRes = mockResponse(302, Map.of("location", List.of("://bad")), InputStream.nullInputStream());
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(invalidRedirectRes);
        Map<String, Object> invalidRedirect = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://example.com/a");
        assertEquals("REDIRECT_INVALID", invalidRedirect.get("status"));

        HttpResponse<InputStream> redirectBlockedRes = mockResponse(302, Map.of("location", List.of("http://localhost/z")), InputStream.nullInputStream());
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(redirectBlockedRes);
        Map<String, Object> redirectBlocked = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://example.com/a");
        assertEquals("SSRF_BLOCKED", redirectBlocked.get("status"));
        assertEquals("http://localhost/z", redirectBlocked.get("redirectBlockedAt"));

        HttpResponse<InputStream> non2xxRes = mockResponse(500, Map.of(), InputStream.nullInputStream());
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(non2xxRes);
        Map<String, Object> non2xx = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://example.com/a");
        assertEquals("NON_2XX", non2xx.get("status"));

        HttpResponse<InputStream> informationalRes = mockResponse(100, Map.of(), InputStream.nullInputStream());
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(informationalRes);
        Map<String, Object> informational = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://example.com/a");
        assertEquals("NON_2XX", informational.get("status"));

        HttpResponse<InputStream> unsupportedRes = mockResponse(200, Map.of("content-type", List.of("application/json")), InputStream.nullInputStream());
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(unsupportedRes);
        Map<String, Object> unsupported = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://example.com/a");
        assertEquals("UNSUPPORTED_CONTENT_TYPE", unsupported.get("status"));

        InputStream broken = new InputStream() {
            @Override
            public int read() {
                throw new RuntimeException("io");
            }
        };
        HttpResponse<InputStream> readErrorRes = mockResponse(200, Map.of("content-type", List.of("text/plain")), broken);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(readErrorRes);
        Map<String, Object> readError = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://example.com/a");
        assertEquals("READ_ERROR", readError.get("status"));

        Tika badTika = Mockito.mock(Tika.class);
        when(badTika.parseToString(any(InputStream.class))).thenThrow(new RuntimeException("tika-fail"));
        ReflectionTestUtils.setField(webContentFetchService, "tika", badTika);
        HttpResponse<InputStream> truncatedRes = mockResponse(200, Map.of("content-type", List.of("text/plain")), new ByteArrayInputStream("abcdef".getBytes()));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(truncatedRes);
        Map<String, Object> truncated = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://example.com/a");
        assertEquals("OK", truncated.get("status"));
        assertEquals(true, truncated.get("truncated"));
        assertEquals("TEXT_CHAR_LIMIT", truncated.get("truncatedReason"));

        Tika goodTika = Mockito.mock(Tika.class);
        when(goodTika.parseToString(any(InputStream.class))).thenReturn("ok");
        ReflectionTestUtils.setField(webContentFetchService, "tika", goodTika);
        HttpResponse<InputStream> okRes = mockResponse(200, Map.of("content-type", List.of("text/plain")), new ByteArrayInputStream("abc".getBytes()));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(okRes);
        Map<String, Object> ok = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://example.com/a");
        assertEquals("OK", ok.get("status"));
        assertEquals(false, ok.get("truncated"));

        ReflectionTestUtils.setField(webContentFetchService, "maxRedirects", 0);
        HttpResponse<InputStream> tooManyRes = mockResponse(302, Map.of("location", List.of("http://example.com/b")), InputStream.nullInputStream());
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(tooManyRes);
        Map<String, Object> tooMany = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://example.com/a");
        assertEquals("REDIRECT_TOO_MANY", tooMany.get("status"));

        ReflectionTestUtils.setField(webContentFetchService, "maxRedirects", 1);
        HttpResponse<InputStream> r1 = mockResponse(302, Map.of("location", List.of("http://example.com/b")), InputStream.nullInputStream());
        HttpResponse<InputStream> r2 = mockResponse(200, Map.of(), new ByteArrayInputStream("ok".getBytes()));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(r1, r2);
        Map<String, Object> redirectThenOk = ReflectionTestUtils.invokeMethod(webContentFetchService, "fetchOne", "http://example.com/a");
        assertEquals("OK", redirectThenOk.get("status"));
        assertEquals("http://example.com/b", redirectThenOk.get("redirectedTo"));
    }

    private HttpResponse<InputStream> mockResponse(int code, Map<String, List<String>> headers, InputStream body) {
        HttpResponse<InputStream> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(code);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (k, v) -> true));
        when(response.body()).thenReturn(body);
        return response;
    }
}
