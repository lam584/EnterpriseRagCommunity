package com.example.EnterpriseRagCommunity.service.ai.client;

import com.example.EnterpriseRagCommunity.service.ai.dto.ChatMessage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatClientBranchCoverageTest {

    @Test
    void buildEndpoint_escapeJson_applyHeaders_should_cover_branches() throws Exception {
        OpenAiCompatClient client = new OpenAiCompatClient();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> invokePrivate(client, "buildEndpoint", new Class<?>[]{String.class, String.class}, null, "/chat/completions")
        );
        assertTrue(ex.getMessage().contains("AI Base URL is not configured"));
        IllegalArgumentException exBlank = assertThrows(
                IllegalArgumentException.class,
                () -> invokePrivate(client, "buildEndpoint", new Class<?>[]{String.class, String.class}, "   ", "/chat/completions")
        );
        assertTrue(exBlank.getMessage().contains("AI Base URL is not configured"));

        String endpoint = (String) invokePrivate(
                client,
                "buildEndpoint",
                new Class<?>[]{String.class, String.class},
                "http://127.0.0.1:8080/",
                "chat/completions"
        );
        assertEquals("http://127.0.0.1:8080/chat/completions", endpoint);

        String escapedNull = (String) invokePrivateStatic(
                OpenAiCompatClient.class,
                "escapeJson",
                new Class<?>[]{String.class},
                (Object) null
        );
        assertEquals("", escapedNull);

        String escaped = (String) invokePrivateStatic(
                OpenAiCompatClient.class,
                "escapeJson",
                new Class<?>[]{String.class},
                "\"\\\b\f\n\r\t" + ((char) 0x1F)
        );
        assertTrue(escaped.contains("\\\""));
        assertTrue(escaped.contains("\\\\"));
        assertTrue(escaped.contains("\\b"));
        assertTrue(escaped.contains("\\f"));
        assertTrue(escaped.contains("\\n"));
        assertTrue(escaped.contains("\\r"));
        assertTrue(escaped.contains("\\t"));
        assertTrue(escaped.contains("\\u001f"));

        DummyHttpURLConnection conn1 = new DummyHttpURLConnection();
        Map<String, String> headers1 = new LinkedHashMap<>();
        headers1.put(null, "x");
        headers1.put(" ", "x");
        headers1.put("X-A", null);
        headers1.put("authorization", "Bearer custom");
        headers1.put("X-B", "ok");
        invokePrivateStatic(
                OpenAiCompatClient.class,
                "applyHeaders",
                new Class<?>[]{HttpURLConnection.class, String.class, Map.class},
                conn1,
                "fallback-key",
                headers1
        );
        assertEquals("Bearer custom", conn1.getRequestProperty("authorization"));
        assertEquals("ok", conn1.getRequestProperty("X-B"));
        assertFalse(conn1.hasKey("Authorization"));

        DummyHttpURLConnection conn2 = new DummyHttpURLConnection();
        invokePrivateStatic(
                OpenAiCompatClient.class,
                "applyHeaders",
                new Class<?>[]{HttpURLConnection.class, String.class, Map.class},
                conn2,
                "fallback-key",
                Map.of()
        );
        assertEquals("Bearer fallback-key", conn2.getRequestProperty("Authorization"));

        DummyHttpURLConnection conn3 = new DummyHttpURLConnection();
        invokePrivateStatic(
                OpenAiCompatClient.class,
                "applyHeaders",
                new Class<?>[]{HttpURLConnection.class, String.class, Map.class},
                conn3,
                " ",
                null
        );
        assertFalse(conn3.hasKey("Authorization"));

        DummyHttpURLConnection conn4 = new DummyHttpURLConnection();
        invokePrivateStatic(
                OpenAiCompatClient.class,
                "applyHeaders",
                new Class<?>[]{HttpURLConnection.class, String.class, Map.class},
                conn4,
                null,
                null
        );
        assertFalse(conn4.hasKey("Authorization"));
    }

    @Test
    void buildBodyJson_should_cover_stream_stop_extra_body_and_message_fallbacks() throws Exception {
        Map<String, Object> badObject = new LinkedHashMap<>();
        badObject.put("self", badObject);

        Map<String, Object> extraBody = new LinkedHashMap<>();
        extraBody.put(null, "x");
        extraBody.put(" ", "x");
        extraBody.put("model", "x");
        extraBody.put("stream", "x");
        extraBody.put("stream_options", "x");
        extraBody.put("temperature", "x");
        extraBody.put("top_p", "x");
        extraBody.put("max_tokens", "x");
        extraBody.put("stop", "x");
        extraBody.put("enable_thinking", "x");
        extraBody.put("thinking_budget", "x");
        extraBody.put("messages", "x");
        extraBody.put("customNull", null);
        extraBody.put("customString", "text");
        extraBody.put("customNumber", 7);
        extraBody.put("customBoolean", true);
        extraBody.put("customObject", Map.of("k", "v"));
        extraBody.put("customBad", badObject);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(null);
        messages.add(new ChatMessage(" ", null));
        messages.add(new ChatMessage(null, "n"));
        messages.add(new ChatMessage("assistant", badObject));

        String body = (String) invokePrivateStatic(
                OpenAiCompatClient.class,
                "buildBodyJson",
                new Class<?>[]{
                        String.class,
                        List.class,
                        Double.class,
                        Double.class,
                        Integer.class,
                        List.class,
                        Boolean.class,
                        Integer.class,
                        Map.class,
                        boolean.class
                },
                "gpt-4o",
                messages,
                0.3d,
                0.7d,
                128,
                List.of("A", "B"),
                false,
                50,
                extraBody,
                true
        );

        assertTrue(body.contains("\"stream\":true"));
        assertTrue(body.contains("\"stream_options\":{\"include_usage\":true}"));
        assertTrue(body.contains("\"stop\":[\"A\",\"B\"]"));
        assertTrue(body.contains("\"enable_thinking\":false"));
        assertTrue(body.contains("\"thinking_budget\":50"));
        assertTrue(body.contains("\"customNull\":null"));
        assertTrue(body.contains("\"customString\":\"text\""));
        assertTrue(body.contains("\"customNumber\":7"));
        assertTrue(body.contains("\"customBoolean\":true"));
        assertTrue(body.contains("\"customObject\":{\"k\":\"v\"}"));
        assertTrue(body.contains("\"customBad\":\""));
        assertTrue(body.contains("\"messages\":["));
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"role\":\"assistant\""));
        assertTrue(body.contains("\"content\":\"\""));
    }

    @Test
    void buildBodyJson_should_cover_non_positive_limits_and_non_stream() throws Exception {
        String body = (String) invokePrivateStatic(
                OpenAiCompatClient.class,
                "buildBodyJson",
                new Class<?>[]{
                        String.class,
                        List.class,
                        Double.class,
                        Double.class,
                        Integer.class,
                        List.class,
                        Boolean.class,
                        Integer.class,
                        Map.class,
                        boolean.class
                },
                "gpt-4o",
                List.of(ChatMessage.user("hello")),
                null,
                null,
                0,
                List.of(),
                true,
                0,
                Map.of(),
                false
        );

        assertTrue(body.contains("\"stream\":false"));
        assertFalse(body.contains("\"stream_options\""));
        assertFalse(body.contains("\"max_tokens\""));
        assertFalse(body.contains("\"thinking_budget\""));
        assertTrue(body.contains("\"enable_thinking\":true"));
    }

    @Test
    void openJsonPost_with_explicit_overload_should_cover_stream_true() throws Exception {
        OpenAiCompatClient client = new OpenAiCompatClient();
        HttpURLConnection conn = (HttpURLConnection) invokePrivate(
                client,
                "openJsonPost",
                new Class<?>[]{String.class, String.class, Map.class, Integer.class, Integer.class, boolean.class},
                "http://127.0.0.1:1/responses",
                "k",
                Map.of("X-Test", "v"),
                5000,
                6000,
                true
        );
        assertEquals("text/event-stream", conn.getRequestProperty("Accept"));
    }

    @Test
    void chatCompletionsStream_should_cover_success_and_error_paths() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = "data: one\ndata: two\n".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/error/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/status199/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = "{\"error\":\"not-ready\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(199, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/no-body/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            OpenAiCompatClient client = new OpenAiCompatClient();

            OpenAiCompatClient.ChatRequest okReq = new OpenAiCompatClient.ChatRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/ok",
                    "m",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of(),
                    null,
                    null,
                    true
            );
            List<String> lines = new ArrayList<>();
            client.chatCompletionsStream(okReq, lines::add);
            assertEquals(2, lines.size());

            OpenAiCompatClient.ChatRequest errReq = new OpenAiCompatClient.ChatRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/error",
                    "m",
                    List.of(ChatMessage.user("x")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of(),
                    null,
                    null,
                    true
            );
            IOException errEx = assertThrows(IOException.class, () -> client.chatCompletionsStream(errReq, line -> {
            }));
            assertTrue(errEx.getMessage().contains("Upstream returned HTTP 500"));

            OpenAiCompatClient.ChatRequest noBodyReq = new OpenAiCompatClient.ChatRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/no-body",
                    "m",
                    List.of(ChatMessage.user("x")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of(),
                    null,
                    null,
                    true
            );
            IOException noBodyEx = assertThrows(IOException.class, () -> client.chatCompletionsStream(noBodyReq, line -> {
            }));
            assertTrue(noBodyEx.getMessage().contains("without body"));

            OpenAiCompatClient.ChatRequest status199Req = new OpenAiCompatClient.ChatRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/status199",
                    "m",
                    List.of(ChatMessage.user("x")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    100,
                    100,
                    true
            );
            IOException status199Ex = assertThrows(IOException.class, () -> client.chatCompletionsStream(status199Req, line -> {
            }));
            assertNotNull(status199Ex.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void chatCompletionsStream_when_model_blank_or_null_should_throw() {
        OpenAiCompatClient client = new OpenAiCompatClient();
        OpenAiCompatClient.ChatRequest blankReq = new OpenAiCompatClient.ChatRequest(
                "k",
                "http://127.0.0.1:1",
                " ",
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> client.chatCompletionsStream(blankReq, line -> {
        }));
        assertTrue(ex.getMessage().contains("AI Model is not configured"));

        OpenAiCompatClient.ChatRequest nullReq = new OpenAiCompatClient.ChatRequest(
                "k",
                "http://127.0.0.1:1",
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );
        IllegalArgumentException exNull = assertThrows(IllegalArgumentException.class, () -> client.chatCompletionsStream(nullReq, line -> {
        }));
        assertTrue(exNull.getMessage().contains("AI Model is not configured"));
    }

    @Test
    void chatCompletionsOnce_should_cover_success_error_no_body_and_model_null() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = "{\"ok\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/error/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = "{\"error\":\"x\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/status199/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = "{\"error\":\"not-ready\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(199, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/no-body/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            OpenAiCompatClient client = new OpenAiCompatClient();

            OpenAiCompatClient.ChatRequest okReq = new OpenAiCompatClient.ChatRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/ok",
                    "m",
                    null,
                    null,
                    null,
                    16,
                    null,
                    null,
                    null,
                    null,
                    null,
                    1,
                    1,
                    false
            );
            String ok = client.chatCompletionsOnce(okReq);
            assertTrue(ok.contains("\"ok\":1"));

            OpenAiCompatClient.ChatRequest errReq = new OpenAiCompatClient.ChatRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/error",
                    "m",
                    List.of(ChatMessage.user("x")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of("X-A", "1"),
                    -1,
                    -1,
                    false
            );
            IOException errEx = assertThrows(IOException.class, () -> client.chatCompletionsOnce(errReq));
            assertTrue(errEx.getMessage().contains("Upstream returned HTTP 500"));

            OpenAiCompatClient.ChatRequest noBodyReq = new OpenAiCompatClient.ChatRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/no-body",
                    "m",
                    List.of(ChatMessage.user("x")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false
            );
            IOException noBodyEx = assertThrows(IOException.class, () -> client.chatCompletionsOnce(noBodyReq));
            assertTrue(noBodyEx.getMessage().contains("without body"));

            OpenAiCompatClient.ChatRequest status199Req = new OpenAiCompatClient.ChatRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/status199",
                    "m",
                    List.of(ChatMessage.user("x")),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    100,
                    100,
                    false
            );
            IOException status199Ex = assertThrows(IOException.class, () -> client.chatCompletionsOnce(status199Req));
            assertNotNull(status199Ex.getMessage());

            OpenAiCompatClient.ChatRequest badModelReq = new OpenAiCompatClient.ChatRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false
            );
            IllegalArgumentException badModelEx = assertThrows(IllegalArgumentException.class, () -> client.chatCompletionsOnce(badModelReq));
            assertTrue(badModelEx.getMessage().contains("AI Model is not configured"));

            OpenAiCompatClient.ChatRequest badModelBlankReq = new OpenAiCompatClient.ChatRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    " ",
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false
            );
            IllegalArgumentException badModelBlankEx = assertThrows(IllegalArgumentException.class, () -> client.chatCompletionsOnce(badModelBlankReq));
            assertTrue(badModelBlankEx.getMessage().contains("AI Model is not configured"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void responsesOnce_should_cover_success_error_no_body_and_model_blank() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok/responses", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = "{\"id\":\"r1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/error/responses", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = "{\"error\":\"y\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/status199/responses", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] resp = "{\"error\":\"not-ready\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(199, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.createContext("/no-body/responses", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            OpenAiCompatClient client = new OpenAiCompatClient();

            OpenAiCompatClient.ResponsesRequest okReq = new OpenAiCompatClient.ResponsesRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/ok",
                    "m",
                    null,
                    0.6d,
                    32,
                    Map.of("X-A", "1"),
                    10,
                    10
            );
            String ok = client.responsesOnce(okReq);
            assertTrue(ok.contains("\"id\":\"r1\""));

            OpenAiCompatClient.ResponsesRequest errReq = new OpenAiCompatClient.ResponsesRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/error",
                    "m",
                    "in",
                    null,
                    0,
                    null,
                    -1,
                    -1
            );
            IOException errEx = assertThrows(IOException.class, () -> client.responsesOnce(errReq));
            assertTrue(errEx.getMessage().contains("Upstream returned HTTP 500"));

            OpenAiCompatClient.ResponsesRequest noBodyReq = new OpenAiCompatClient.ResponsesRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/no-body",
                    "m",
                    "in",
                    null,
                    null,
                    null,
                    null,
                    null
            );
            IOException noBodyEx = assertThrows(IOException.class, () -> client.responsesOnce(noBodyReq));
            assertTrue(noBodyEx.getMessage().contains("without body"));

            OpenAiCompatClient.ResponsesRequest status199Req = new OpenAiCompatClient.ResponsesRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/status199",
                    "m",
                    "in",
                    null,
                    null,
                    null,
                    100,
                    100
            );
            IOException status199Ex = assertThrows(IOException.class, () -> client.responsesOnce(status199Req));
            assertNotNull(status199Ex.getMessage());

            OpenAiCompatClient.ResponsesRequest badModelReq = new OpenAiCompatClient.ResponsesRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    " ",
                    "in",
                    null,
                    null,
                    null,
                    null,
                    null
            );
            IllegalArgumentException badModelEx = assertThrows(IllegalArgumentException.class, () -> client.responsesOnce(badModelReq));
            assertTrue(badModelEx.getMessage().contains("AI Model is not configured"));

            OpenAiCompatClient.ResponsesRequest badModelNullReq = new OpenAiCompatClient.ResponsesRequest(
                    "k",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    null,
                    "in",
                    null,
                    null,
                    null,
                    null,
                    null
            );
            IllegalArgumentException badModelNullEx = assertThrows(IllegalArgumentException.class, () -> client.responsesOnce(badModelNullReq));
            assertTrue(badModelNullEx.getMessage().contains("AI Model is not configured"));
        } finally {
            server.stop(0);
        }
    }

    private static Object invokePrivate(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) throw exception;
            throw e;
        }
    }

    private static Object invokePrivateStatic(Class<?> clazz, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = clazz.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) throw exception;
            throw e;
        }
    }

    private static final class DummyHttpURLConnection extends HttpURLConnection {
        private final Map<String, String> headers = new LinkedHashMap<>();

        private DummyHttpURLConnection() throws Exception {
            super(new URL("http://127.0.0.1:1"));
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        @Override
        public void setRequestProperty(String key, String value) {
            headers.put(key, value);
        }

        @Override
        public String getRequestProperty(String key) {
            return headers.get(key);
        }

        private boolean hasKey(String key) {
            return headers.containsKey(key);
        }
    }
}
