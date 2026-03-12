package com.example.EnterpriseRagCommunity.service.ai.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.example.EnterpriseRagCommunity.testutil.MockHttpUrl;

class BailianOpenAiSseClientTest {

    @BeforeEach
    void installMockProtocol() {
        MockHttpUrl.installOnce();
        MockHttpUrl.reset();
    }

    @Test
    void escapeJson_shouldHandleNullAndAllEscapeBranches() throws Exception {
        String nullResult = (String) invokePrivateStatic("escapeJson", new Class[]{String.class}, new Object[]{null});
        assertEquals("", nullResult);

        String input = "\"\\\b\f\n\r\t" + ((char) 0x1f) + "A";
        String escaped = (String) invokePrivateStatic("escapeJson", new Class[]{String.class}, new Object[]{input});
        assertEquals("\\\"\\\\\\b\\f\\n\\r\\t\\u001fA", escaped);
    }

    @Test
    void buildBodyJson_shouldCoverTemperatureRoleContentAndFallbackBranches() throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(null);
        messages.add(new ChatMessage("", "hello"));
        messages.add(new ChatMessage(null, Map.of("k", "v")));
        messages.add(new ChatMessage("assistant", null));
        messages.add(new ChatMessage("user", new BrokenPojo()));

        String body = (String) invokePrivateStatic(
                "buildBodyJson",
                new Class[]{String.class, List.class, Double.class},
                new Object[]{"qwen-plus", messages, 0.7d}
        );

        assertTrue(body.contains("\"stream\":true"));
        assertTrue(body.contains("\"temperature\":0.7"));
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"role\":\"assistant\""));
        assertTrue(body.contains("\"content\":\"hello\""));
        assertTrue(body.contains("\"content\":{\"k\":\"v\"}"));
        assertTrue(body.contains("\"content\":\"\""));
        assertTrue(body.contains("\"content\":\"broken\""));
    }

    @Test
    void buildBodyJsonOnce_shouldCoverTemperatureAbsentAndObjectSerializationBranches() throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(" ", Map.of("k", "v")));
        messages.add(new ChatMessage("assistant", "ok"));
        messages.add(new ChatMessage(null, null));
        messages.add(null);
        messages.add(new ChatMessage("user", new BrokenPojo()));

        String body = (String) invokePrivateStatic(
                "buildBodyJsonOnce",
                new Class[]{String.class, List.class, Double.class},
                new Object[]{"qwen-plus", messages, null}
        );

        assertTrue(body.contains("\"stream\":false"));
        assertFalse(body.contains("\"temperature\""));
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"role\":\"assistant\""));
        assertTrue(body.contains("\"content\":{\"k\":\"v\"}"));
        assertTrue(body.contains("\"content\":\"ok\""));
        assertTrue(body.contains("\"content\":\"broken\""));
    }

    @Test
    void chatCompletionsStream_shouldThrowWhenBaseUrlInvalid() {
        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> client.chatCompletionsStream("k", " ", "m", List.of(ChatMessage.user("x")), null, line -> {
                })
        );
        assertEquals("baseUrl 不能为空", ex.getMessage());
    }

    @Test
    void chatCompletionsStream_shouldThrowWhenBaseUrlNull() {
        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> client.chatCompletionsStream("k", null, "m", List.of(ChatMessage.user("x")), null, line -> {
                })
        );
        assertEquals("baseUrl 不能为空", ex.getMessage());
    }

    @Test
    void chatCompletionsStream_shouldThrowWhenModelInvalid() {
        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> client.chatCompletionsStream("k", "mockhttp://stream-host", "", List.of(ChatMessage.user("x")), null, line -> {
                })
        );
        assertEquals("model 不能为空", ex.getMessage());
    }

    @Test
    void chatCompletionsStream_shouldThrowWhenModelNull() {
        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> client.chatCompletionsStream("k", "mockhttp://stream-host", null, List.of(ChatMessage.user("x")), null, line -> {
                })
        );
        assertEquals("model 不能为空", ex.getMessage());
    }

    @Test
    void chatCompletionsStream_shouldReadSuccessInputAndSetAuthorizationHeader() throws Exception {
        MockHttpUrl.enqueue(200, "data: one\ndata: two\n");

        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        List<String> lines = new ArrayList<>();
        client.chatCompletionsStream("secret-key", "mockhttp://stream-host/", "qwen-plus", List.of(ChatMessage.user("hi")), 0.3d, lines::add);
        MockHttpUrl.RequestCapture request = MockHttpUrl.pollRequest();

        assertEquals(List.of("data: one", "data: two"), lines);
        assertNotNull(request);
        assertEquals("Bearer secret-key", request.headers().get("Authorization"));
        assertEquals("text/event-stream", request.headers().get("Accept"));
        assertTrue(new String(request.body(), StandardCharsets.UTF_8).contains("\"stream\":true"));
    }

    @Test
    void chatCompletionsStream_shouldUseErrorStreamWhenNon2xx() throws Exception {
        MockHttpUrl.enqueue(500, "err-line\n");

        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        List<String> lines = new ArrayList<>();
        client.chatCompletionsStream("k", "mockhttp://stream-err", "qwen-plus", List.of(ChatMessage.user("x")), null, lines::add);

        assertEquals(List.of("err-line"), lines);
    }

    @Test
    void chatCompletionsStream_shouldThrowWhenBodyMissing() throws Exception {
        MockHttpUrl.enqueue(500, null);

        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        IOException ex = assertThrows(
                IOException.class,
                () -> client.chatCompletionsStream(null, "mockhttp://stream-null", "qwen-plus", List.of(ChatMessage.user("x")), null, line -> {
                })
        );
        MockHttpUrl.RequestCapture request = MockHttpUrl.pollRequest();
        assertEquals("Upstream returned HTTP 500 without body", ex.getMessage());
        assertNotNull(request);
        assertFalse(request.headers().containsKey("Authorization"));
    }

    @Test
    void chatCompletionsStream_shouldUseErrorStreamWhenStatusBelow200() throws Exception {
        MockHttpUrl.enqueue(199, "pre-ok\n");

        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        List<String> lines = new ArrayList<>();
        client.chatCompletionsStream(" ", "mockhttp://stream-199", "qwen-plus", List.of(ChatMessage.user("x")), null, lines::add);
        MockHttpUrl.RequestCapture request = MockHttpUrl.pollRequest();
        assertEquals(List.of("pre-ok"), lines);
        assertNotNull(request);
        assertFalse(request.headers().containsKey("Authorization"));
    }

    @Test
    void chatCompletionsOnce_shouldReturnResponseOn2xx() throws Exception {
        MockHttpUrl.enqueue(201, "{\"ok\":true}\n");

        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        String resp = client.chatCompletionsOnce("token", "mockhttp://once-ok", "qwen-plus", List.of(ChatMessage.user("hi")), 0.1d);
        MockHttpUrl.RequestCapture request = MockHttpUrl.pollRequest();

        assertEquals("{\"ok\":true}", resp);
        assertNotNull(request);
        assertEquals("Bearer token", request.headers().get("Authorization"));
        assertEquals("application/json", request.headers().get("Accept"));
        assertTrue(new String(request.body(), StandardCharsets.UTF_8).contains("\"stream\":false"));
    }

    @Test
    void chatCompletionsOnce_shouldThrowWithErrorBodyOnNon2xx() throws Exception {
        MockHttpUrl.enqueue(429, "{\"error\":\"rate_limit\"}\n");

        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        IOException ex = assertThrows(
                IOException.class,
                () -> client.chatCompletionsOnce("k", "mockhttp://once-fail/", "qwen-plus", List.of(ChatMessage.user("x")), null)
        );
        assertEquals("Upstream returned HTTP 429: {\"error\":\"rate_limit\"}", ex.getMessage());
    }

    @Test
    void chatCompletionsOnce_shouldThrowWhenErrorBodyMissing() throws Exception {
        MockHttpUrl.enqueue(503, null);

        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        IOException ex = assertThrows(
                IOException.class,
                () -> client.chatCompletionsOnce(null, "mockhttp://once-null", "qwen-plus", List.of(ChatMessage.user("x")), null)
        );
        MockHttpUrl.RequestCapture request = MockHttpUrl.pollRequest();
        assertEquals("Upstream returned HTTP 503 without body", ex.getMessage());
        assertNotNull(request);
        assertFalse(request.headers().containsKey("Authorization"));
    }

    @Test
    void chatCompletionsOnce_shouldThrowWhenBaseUrlNull() {
        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> client.chatCompletionsOnce("k", null, "qwen-plus", List.of(ChatMessage.user("x")), null)
        );
        assertEquals("baseUrl 不能为空", ex.getMessage());
    }

    @Test
    void chatCompletionsOnce_shouldThrowWhenBaseUrlBlank() {
        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> client.chatCompletionsOnce("k", " ", "qwen-plus", List.of(ChatMessage.user("x")), null)
        );
        assertEquals("baseUrl 不能为空", ex.getMessage());
    }

    @Test
    void chatCompletionsOnce_shouldThrowWhenModelBlank() {
        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> client.chatCompletionsOnce("k", "mockhttp://once-ok", " ", List.of(ChatMessage.user("x")), null)
        );
        assertEquals("model 不能为空", ex.getMessage());
    }

    @Test
    void chatCompletionsOnce_shouldThrowWhenModelNull() {
        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> client.chatCompletionsOnce("k", "mockhttp://once-ok", null, List.of(ChatMessage.user("x")), null)
        );
        assertEquals("model 不能为空", ex.getMessage());
    }

    @Test
    void chatCompletionsOnce_shouldThrowWhenStatusBelow200() throws Exception {
        MockHttpUrl.enqueue(199, "{\"error\":\"precondition\"}\n");

        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        IOException ex = assertThrows(
                IOException.class,
                () -> client.chatCompletionsOnce(" ", "mockhttp://once-199", "qwen-plus", List.of(ChatMessage.user("x")), null)
        );
        MockHttpUrl.RequestCapture request = MockHttpUrl.pollRequest();
        assertEquals("Upstream returned HTTP 199: {\"error\":\"precondition\"}", ex.getMessage());
        assertNotNull(request);
        assertFalse(request.headers().containsKey("Authorization"));
    }

    @Test
    void constructor_shouldBeCovered() {
        BailianOpenAiSseClient client = new BailianOpenAiSseClient();
        assertNotNull(client);
    }

    private static Object invokePrivateStatic(String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method method = BailianOpenAiSseClient.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

}

final class BrokenPojo {
    public Object getBad() {
        throw new RuntimeException("boom");
    }

    @Override
    public String toString() {
        return "broken";
    }
}
