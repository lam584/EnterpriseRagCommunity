package com.example.EnterpriseRagCommunity.service.ai.client;

import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LmStudioLegacyChatClientBranchTest {

    private final LmStudioLegacyChatClient client = new LmStudioLegacyChatClient();

    @BeforeEach
    void setUp() {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
    }

    @Test
    void chatOnce_modelNull_throwsIllegalArgumentException() {
        LmStudioLegacyChatClient.ChatRequest req = new LmStudioLegacyChatClient.ChatRequest(
                "k",
                "mockhttp://lmstudio",
                null,
                null,
                "hi",
                null,
                null,
                null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> client.chatOnce(req));
        assertEquals("model 不能为空", ex.getMessage());
    }

    @Test
    void chatOnce_modelBlank_throwsIllegalArgumentException() {
        LmStudioLegacyChatClient.ChatRequest req = new LmStudioLegacyChatClient.ChatRequest(
                "k",
                "mockhttp://lmstudio",
                "   ",
                null,
                "hi",
                null,
                null,
                null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> client.chatOnce(req));
        assertEquals("model 不能为空", ex.getMessage());
    }

    @Test
    void chatOnce_success_addsFallbackAuthorizationAndSystemPrompt() throws Exception {
        MockHttpUrl.enqueue(200, "{\"ok\":true}");

        LmStudioLegacyChatClient.ChatRequest req = new LmStudioLegacyChatClient.ChatRequest(
                " key-1 ",
                "mockhttp://lmstudio/",
                "qwen3",
                "你是助手",
                "hello",
                null,
                null,
                null
        );

        String resp = client.chatOnce(req);
        assertEquals("{\"ok\":true}", resp);

        MockHttpUrl.RequestCapture capture = MockHttpUrl.pollRequest();
        assertNotNull(capture);
        assertEquals("POST", capture.method());
        assertEquals("mockhttp://lmstudio/api/v1/chat", capture.url().toString());
        assertEquals("Bearer key-1", capture.headers().get("Authorization"));
        assertEquals("application/json; charset=UTF-8", capture.headers().get("Content-Type"));
        assertEquals("application/json", capture.headers().get("Accept"));
        String requestBody = new String(capture.body(), StandardCharsets.UTF_8);
        assertTrue(requestBody.contains("\"model\":\"qwen3\""));
        assertTrue(requestBody.contains("\"system_prompt\":\"你是助手\""));
        assertTrue(requestBody.contains("\"input\":\"hello\""));
    }

    @Test
    void chatOnce_success_usesExtraAuthorization_andNullInputBecomesEmpty() throws Exception {
        MockHttpUrl.enqueue(200, "{\"ok\":2}");
        Map<String, String> extraHeaders = new LinkedHashMap<>();
        extraHeaders.put(null, "v1");
        extraHeaders.put(" ", "v2");
        extraHeaders.put("X-Null", null);
        extraHeaders.put(" Authorization ", "Token t1");
        extraHeaders.put("X-Trace", "abc");

        LmStudioLegacyChatClient.ChatRequest req = new LmStudioLegacyChatClient.ChatRequest(
                "fallback-key",
                "mockhttp://lmstudio/api/v1/chat",
                "qwen3",
                " ",
                null,
                extraHeaders,
                1234,
                5678
        );

        String resp = client.chatOnce(req);
        assertEquals("{\"ok\":2}", resp);

        MockHttpUrl.RequestCapture capture = MockHttpUrl.pollRequest();
        assertNotNull(capture);
        assertEquals("mockhttp://lmstudio/api/v1/chat", capture.url().toString());
        assertEquals("Token t1", capture.headers().get(" Authorization "));
        assertNull(capture.headers().get("Authorization"));
        assertEquals("abc", capture.headers().get("X-Trace"));
        String requestBody = new String(capture.body(), StandardCharsets.UTF_8);
        assertFalse(requestBody.contains("system_prompt"));
        assertTrue(requestBody.contains("\"input\":\"\""));
    }

    @Test
    void chatOnce_non2xxWithBody_throwsIOException() {
        MockHttpUrl.enqueue(500, "{\"error\":\"bad\"}");
        LmStudioLegacyChatClient.ChatRequest req = new LmStudioLegacyChatClient.ChatRequest(
                null,
                "mockhttp://lmstudio/api/v1",
                "qwen3",
                null,
                "x",
                null,
                null,
                null
        );

        IOException ex = assertThrows(IOException.class, () -> client.chatOnce(req));
        assertTrue(ex.getMessage().contains("Upstream returned HTTP 500"));
        assertTrue(ex.getMessage().contains("{\"error\":\"bad\"}"));
    }

    @Test
    void chatOnce_non2xxWithoutBody_throwsIOExceptionWithoutBody() {
        MockHttpUrl.enqueue(503, null);
        LmStudioLegacyChatClient.ChatRequest req = new LmStudioLegacyChatClient.ChatRequest(
                null,
                "mockhttp://lmstudio/api/v1",
                "qwen3",
                null,
                "x",
                null,
                null,
                null
        );

        IOException ex = assertThrows(IOException.class, () -> client.chatOnce(req));
        assertTrue(ex.getMessage().contains("without body"));
    }

    @Test
    void chatOnce_statusBelow200_throwsIOException() {
        MockHttpUrl.enqueue(100, "info");
        LmStudioLegacyChatClient.ChatRequest req = new LmStudioLegacyChatClient.ChatRequest(
                null,
                "mockhttp://lmstudio",
                "qwen3",
                null,
                "x",
                null,
                null,
                null
        );

        IOException ex = assertThrows(IOException.class, () -> client.chatOnce(req));
        assertTrue(ex.getMessage().contains("Upstream returned HTTP 100"));
    }

    @Test
    void selectEndpoint_coversAllBranches() throws Exception {
        assertEquals("/api/v1/chat", invokeStaticString("selectEndpoint", null));
        assertEquals("mockhttp://a/api/v1/chat", invokeStaticString("selectEndpoint", "mockhttp://a"));
        assertEquals("mockhttp://a/api/v1/chat", invokeStaticString("selectEndpoint", "mockhttp://a/api/v1"));
        assertEquals("mockhttp://a/api/v1/chat", invokeStaticString("selectEndpoint", "mockhttp://a/api/v1/"));
        assertEquals("mockhttp://a/api/v1/chat", invokeStaticString("selectEndpoint", " mockhttp://a/api/v1/chat "));
    }

    @Test
    void normalizeString_andNormalizeBaseUrl_coverBranches() throws Exception {
        assertEquals("fb", invokeStaticNormalizeString(null, "fb"));
        assertEquals("fb", invokeStaticNormalizeString(" ", "fb"));
        assertEquals("abc", invokeStaticNormalizeString(" abc ", "fb"));

        assertEquals("", invokeStaticNormalizeBaseUrl(null, null));
        assertEquals("fb", invokeStaticNormalizeBaseUrl(" ", "fb"));
        assertEquals("http://x", invokeStaticNormalizeBaseUrl(" http://x/ ", null));
    }

    @Test
    void applyHeaders_coversFallbackAndSkipBranches() throws Exception {
        HttpURLConnection conn1 = (HttpURLConnection) new URL("mockhttp://unit/1").openConnection();
        invokeApplyHeaders(conn1, "k1", null);
        assertEquals("Bearer k1", conn1.getRequestProperty("Authorization"));

        HttpURLConnection conn2 = (HttpURLConnection) new URL("mockhttp://unit/2").openConnection();
        invokeApplyHeaders(conn2, "   ", Map.of());
        assertNull(conn2.getRequestProperty("Authorization"));

        HttpURLConnection conn3 = (HttpURLConnection) new URL("mockhttp://unit/3").openConnection();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Token existing");
        headers.put("X-Test", "v");
        invokeApplyHeaders(conn3, "k2", headers);
        assertEquals("Token existing", conn3.getRequestProperty("Authorization"));
        assertEquals("v", conn3.getRequestProperty("X-Test"));
    }

    @Test
    void openJsonPost_setsTimeoutsAndHeaders() throws Exception {
        HttpURLConnection connDefault = invokeOpenJsonPost("mockhttp://unit/default", "k1", null, null, null);
        assertEquals(10_000, connDefault.getConnectTimeout());
        assertEquals(300_000, connDefault.getReadTimeout());
        assertEquals("application/json; charset=UTF-8", connDefault.getRequestProperty("Content-Type"));
        assertEquals("application/json", connDefault.getRequestProperty("Accept"));
        assertEquals("Bearer k1", connDefault.getRequestProperty("Authorization"));

        HttpURLConnection connNonPositive = invokeOpenJsonPost("mockhttp://unit/nonpositive", null, Map.of(), 0, -1);
        assertEquals(10_000, connNonPositive.getConnectTimeout());
        assertEquals(300_000, connNonPositive.getReadTimeout());
        assertNull(connNonPositive.getRequestProperty("Authorization"));

        HttpURLConnection connCustom = invokeOpenJsonPost("mockhttp://unit/custom", null, Map.of(), 321, 654);
        assertEquals(321, connCustom.getConnectTimeout());
        assertEquals(654, connCustom.getReadTimeout());
    }

    private static String invokeStaticString(String methodName, String arg) throws Exception {
        Method m = LmStudioLegacyChatClient.class.getDeclaredMethod(methodName, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, arg);
    }

    private static String invokeStaticNormalizeString(String s, String fallback) throws Exception {
        Method m = LmStudioLegacyChatClient.class.getDeclaredMethod("normalizeString", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, s, fallback);
    }

    private static String invokeStaticNormalizeBaseUrl(String baseUrl, String fallback) throws Exception {
        Method m = LmStudioLegacyChatClient.class.getDeclaredMethod("normalizeBaseUrl", String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, baseUrl, fallback);
    }

    private static void invokeApplyHeaders(HttpURLConnection conn, String apiKey, Map<String, String> extraHeaders) throws Exception {
        Method m = LmStudioLegacyChatClient.class.getDeclaredMethod("applyHeaders", HttpURLConnection.class, String.class, Map.class);
        m.setAccessible(true);
        m.invoke(null, conn, apiKey, extraHeaders);
    }

    private HttpURLConnection invokeOpenJsonPost(
            String endpoint,
            String apiKey,
            Map<String, String> extraHeaders,
            Integer connectTimeoutMs,
            Integer readTimeoutMs
    ) throws Exception {
        Method m = LmStudioLegacyChatClient.class.getDeclaredMethod(
                "openJsonPost",
                String.class,
                String.class,
                Map.class,
                Integer.class,
                Integer.class
        );
        m.setAccessible(true);
        try {
            return (HttpURLConnection) m.invoke(client, endpoint, apiKey, extraHeaders, connectTimeoutMs, readTimeoutMs);
        } catch (InvocationTargetException ex) {
            Throwable c = ex.getCause();
            if (c instanceof Exception e) throw e;
            if (c instanceof Error e) throw e;
            throw ex;
        }
    }
}
