package com.example.EnterpriseRagCommunity.service.ai;

import com.example.EnterpriseRagCommunity.config.AiTokenizerProperties;
import com.example.EnterpriseRagCommunity.config.OpenSearchPlatformProperties;
import com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeRequest;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSearchTokenizeServiceTest {

    @Test
    void tokenize_messages_last_role_not_user_should_throw() {
        OpenSearchPlatformProperties props = new OpenSearchPlatformProperties();
        AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
        OpenSearchTokenizeService svc = new OpenSearchTokenizeService(props, tokenizerProps);

        OpenSearchTokenizeRequest.Message m1 = new OpenSearchTokenizeRequest.Message();
        m1.setRole("user");
        m1.setContent("hi");
        OpenSearchTokenizeRequest.Message m2 = new OpenSearchTokenizeRequest.Message();
        m2.setRole("assistant");
        m2.setContent("ok");

        OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
        req.setMessages(List.of(m1, m2));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.tokenize(req));
        assertEquals("Messages must be end with role[user].", ex.getMessage());
    }

    @Test
    void tokenize_messages_all_filtered_out_should_throw_messages_empty() {
        OpenSearchPlatformProperties props = new OpenSearchPlatformProperties();
        AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
        OpenSearchTokenizeService svc = new OpenSearchTokenizeService(props, tokenizerProps);

        OpenSearchTokenizeRequest.Message last = new OpenSearchTokenizeRequest.Message();
        last.setRole("   ");
        last.setContent("x");

        OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
        req.setMessages(List.of(new OpenSearchTokenizeRequest.Message(), last));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.tokenize(req));
        assertEquals("messages 不能为空", ex.getMessage());
    }

    @Test
    void tokenize_text_blank_should_throw_text_empty() {
        OpenSearchPlatformProperties props = new OpenSearchPlatformProperties();
        AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
        OpenSearchTokenizeService svc = new OpenSearchTokenizeService(props, tokenizerProps);

        OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
        req.setText("   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.tokenize(req));
        assertEquals("text 不能为空", ex.getMessage());
    }

    @Test
    void tokenize_when_api_key_missing_should_fallback_and_estimate_tokens() {
        OpenSearchPlatformProperties props = new OpenSearchPlatformProperties();
        props.setHost("http://127.0.0.1:0");
        AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
        tokenizerProps.setApiKey(" ");
        OpenSearchTokenizeService svc = new OpenSearchTokenizeService(props, tokenizerProps);

        OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
        req.setText("abcde");

        var resp = svc.tokenize(req);
        assertNotNull(resp);
        assertNotNull(resp.getUsage());
        assertEquals(2, resp.getUsage().getInputTokens());
    }

    @Test
    void tokenize_when_api_key_null_should_fallback_and_estimate_tokens() {
        OpenSearchPlatformProperties props = new OpenSearchPlatformProperties();
        props.setHost("http://127.0.0.1:0");
        AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
        tokenizerProps.setApiKey(null);
        OpenSearchTokenizeService svc = new OpenSearchTokenizeService(props, tokenizerProps);

        OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
        req.setText("abcd");

        var resp = svc.tokenize(req);
        assertNotNull(resp);
        assertNotNull(resp.getUsage());
        assertEquals(1, resp.getUsage().getInputTokens());
    }

    @Test
    void tokenize_when_messages_blank_content_should_fallback_with_zero_tokens() {
        OpenSearchPlatformProperties props = new OpenSearchPlatformProperties();
        AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
        OpenSearchTokenizeService svc = new OpenSearchTokenizeService(props, tokenizerProps);

        OpenSearchTokenizeRequest.Message m = new OpenSearchTokenizeRequest.Message();
        m.setRole("user");
        m.setContent("   ");

        OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
        req.setMessages(List.of(m));

        var resp = svc.tokenize(req);
        assertNotNull(resp);
        assertNotNull(resp.getUsage());
        assertEquals(0, resp.getUsage().getInputTokens());
    }

    @Test
    void tokenize_when_messages_cjk_should_fallback_and_count_per_char() {
        OpenSearchPlatformProperties props = new OpenSearchPlatformProperties();
        AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
        OpenSearchTokenizeService svc = new OpenSearchTokenizeService(props, tokenizerProps);

        OpenSearchTokenizeRequest.Message m = new OpenSearchTokenizeRequest.Message();
        m.setRole("user");
        m.setContent("中文");

        OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
        req.setMessages(List.of(m));

        var resp = svc.tokenize(req);
        assertNotNull(resp);
        assertNotNull(resp.getUsage());
        assertEquals(2, resp.getUsage().getInputTokens());
    }

    @Test
    void tokenize_when_can_call_upstream_and_response_ok_should_return_upstream_result() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> reqBodyRef = new AtomicReference<>();
        server.createContext("/v3/openapi/workspaces/ws/text-generation/sid/tokenizer", exchange -> {
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            reqBodyRef.set(new String(bodyBytes, StandardCharsets.UTF_8));
            byte[] respBytes = "{\"usage\":{\"input_tokens\":7},\"result\":{\"tokens\":[\"h\"]}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, respBytes.length);
            exchange.getResponseBody().write(respBytes);
            exchange.close();
        });
        server.start();
        try {
            OpenSearchPlatformProperties props = new OpenSearchPlatformProperties();
            props.setHost("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            props.setWorkspaceName("ws");
            props.setServiceId("sid");
            AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
            tokenizerProps.setApiKey("k");
            OpenSearchTokenizeService svc = new OpenSearchTokenizeService(props, tokenizerProps);

            OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
            req.setText("hello");
            var resp = svc.tokenize(req);

            assertNotNull(resp);
            assertNotNull(resp.getUsage());
            assertEquals(7, resp.getUsage().getInputTokens());
            assertTrue(reqBodyRef.get().contains("\"role\":\"user\""));
            assertTrue(reqBodyRef.get().contains("\"content\":\"hello\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tokenize_when_upstream_http_error_should_fallback_by_catch() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/openapi/workspaces/ws/text-generation/sid/tokenizer", exchange -> {
            byte[] respBytes = "{\"code\":\"BadRequest\",\"message\":\"fail\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(500, respBytes.length);
            exchange.getResponseBody().write(respBytes);
            exchange.close();
        });
        server.start();
        try {
            OpenSearchPlatformProperties props = new OpenSearchPlatformProperties();
            props.setHost("http://127.0.0.1:" + server.getAddress().getPort());
            props.setWorkspaceName("ws");
            props.setServiceId("sid");
            AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
            tokenizerProps.setApiKey("k");
            OpenSearchTokenizeService svc = new OpenSearchTokenizeService(props, tokenizerProps);

            OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
            req.setText("abcd");

            var resp = svc.tokenize(req);
            assertNotNull(resp);
            assertNotNull(resp.getUsage());
            assertEquals(1, resp.getUsage().getInputTokens());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tokenize_when_host_workspace_or_service_missing_should_fallback_without_upstream_call() {
        AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
        tokenizerProps.setApiKey("k");

        OpenSearchPlatformProperties missingHost = new OpenSearchPlatformProperties();
        OpenSearchTokenizeService svc1 = new OpenSearchTokenizeService(missingHost, tokenizerProps);
        OpenSearchTokenizeRequest req1 = new OpenSearchTokenizeRequest();
        req1.setText("abcd");
        assertEquals(1, svc1.tokenize(req1).getUsage().getInputTokens());

        OpenSearchPlatformProperties missingWorkspace = new OpenSearchPlatformProperties();
        missingWorkspace.setHost("http://127.0.0.1:0");
        OpenSearchTokenizeService svc2 = new OpenSearchTokenizeService(missingWorkspace, tokenizerProps);
        OpenSearchTokenizeRequest req2 = new OpenSearchTokenizeRequest();
        req2.setText("abcd");
        assertEquals(1, svc2.tokenize(req2).getUsage().getInputTokens());

        OpenSearchPlatformProperties missingService = new OpenSearchPlatformProperties();
        missingService.setHost("http://127.0.0.1:0");
        missingService.setWorkspaceName("ws");
        OpenSearchTokenizeService svc3 = new OpenSearchTokenizeService(missingService, tokenizerProps);
        OpenSearchTokenizeRequest req3 = new OpenSearchTokenizeRequest();
        req3.setText("abcd");
        assertEquals(1, svc3.tokenize(req3).getUsage().getInputTokens());
    }

    @Test
    void tokenize_when_request_workspace_and_service_present_should_not_require_props_defaults() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/openapi/workspaces/req-ws/text-generation/req-sid/tokenizer", exchange -> {
            byte[] respBytes = "{\"usage\":{\"input_tokens\":9}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, respBytes.length);
            exchange.getResponseBody().write(respBytes);
            exchange.close();
        });
        server.start();
        try {
            OpenSearchPlatformProperties props = new OpenSearchPlatformProperties();
            props.setHost("http://127.0.0.1:" + server.getAddress().getPort());
            AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
            tokenizerProps.setApiKey("k");
            OpenSearchTokenizeService svc = new OpenSearchTokenizeService(props, tokenizerProps);

            OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
            req.setWorkspaceName("req-ws");
            req.setServiceId("req-sid");
            req.setText("hello");

            var resp = svc.tokenize(req);
            assertNotNull(resp);
            assertNotNull(resp.getUsage());
            assertEquals(9, resp.getUsage().getInputTokens());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tokenize_when_messages_empty_list_should_fallback_to_text_path() {
        OpenSearchPlatformProperties props = new OpenSearchPlatformProperties();
        AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
        OpenSearchTokenizeService svc = new OpenSearchTokenizeService(props, tokenizerProps);

        OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
        req.setMessages(List.of());
        req.setText("abcd");

        var resp = svc.tokenize(req);
        assertNotNull(resp);
        assertNotNull(resp.getUsage());
        assertEquals(1, resp.getUsage().getInputTokens());
    }

    @Test
    void tokenize_when_messages_contains_null_blank_and_null_content_should_normalize_and_fallback() {
        OpenSearchPlatformProperties props = new OpenSearchPlatformProperties();
        AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
        OpenSearchTokenizeService svc = new OpenSearchTokenizeService(props, tokenizerProps);

        OpenSearchTokenizeRequest.Message ignoredNullRole = new OpenSearchTokenizeRequest.Message();
        ignoredNullRole.setRole(" ");
        ignoredNullRole.setContent("will-be-ignored");

        OpenSearchTokenizeRequest.Message userWithNullContent = new OpenSearchTokenizeRequest.Message();
        userWithNullContent.setRole("USER");
        userWithNullContent.setContent(null);

        OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
        req.setMessages(Arrays.asList(null, ignoredNullRole, userWithNullContent));

        var resp = svc.tokenize(req);
        assertNotNull(resp);
        assertNotNull(resp.getUsage());
        assertEquals(0, resp.getUsage().getInputTokens());
    }

    @Test
    void tokenize_when_fallback_collects_multiline_and_mixed_unicode_should_cover_all_cjk_ranges() {
        OpenSearchPlatformProperties props = new OpenSearchPlatformProperties();
        AiTokenizerProperties tokenizerProps = new AiTokenizerProperties();
        OpenSearchTokenizeService svc = new OpenSearchTokenizeService(props, tokenizerProps);

        String allRanges = new StringBuilder()
                .append(Character.toChars(0x4E00))
                .append(Character.toChars(0x3400))
                .append(Character.toChars(0x20000))
                .append(Character.toChars(0x2A700))
                .append(Character.toChars(0x2B740))
                .append(Character.toChars(0x2B820))
                .append(Character.toChars(0xF900))
                .append(Character.toChars(0x2F800))
                .append(Character.toChars(0x3042))
                .append(Character.toChars(0xAC00))
                .append('A')
                .toString();

        OpenSearchTokenizeRequest.Message m1 = new OpenSearchTokenizeRequest.Message();
        m1.setRole("user");
        m1.setContent(allRanges);
        OpenSearchTokenizeRequest.Message m2 = new OpenSearchTokenizeRequest.Message();
        m2.setRole("user");
        m2.setContent("   ");
        OpenSearchTokenizeRequest.Message m3 = new OpenSearchTokenizeRequest.Message();
        m3.setRole("user");
        m3.setContent("ok");

        OpenSearchTokenizeRequest req = new OpenSearchTokenizeRequest();
        req.setMessages(Arrays.asList(null, m1, m2, m3));

        var resp = svc.tokenize(req);
        assertNotNull(resp);
        assertNotNull(resp.getUsage());
        assertEquals(11, resp.getUsage().getInputTokens());
    }

    @Test
    void private_static_helpers_should_cover_fallback_null_and_estimate_null() throws Exception {
        Object estimated = invokePrivateStatic(
                OpenSearchTokenizeService.class,
                "estimateTokens",
                new Class<?>[]{String.class},
                (Object) null
        );
        assertEquals(0, estimated);

        Object fallback = invokePrivateStatic(
                OpenSearchTokenizeService.class,
                "fallbackResponse",
                new Class<?>[]{List.class},
                (Object) null
        );
        assertNotNull(fallback);
        assertNotNull(((com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse) fallback).getUsage());
        assertEquals(0, ((com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse) fallback).getUsage().getInputTokens());
    }

    @Test
    void private_static_helpers_should_cover_null_message_and_null_content_and_upper_bound_false() throws Exception {
        Map<String, String> nullContent = new HashMap<>();
        nullContent.put("role", "user");
        nullContent.put("content", null);
        Map<String, String> blankContent = new HashMap<>();
        blankContent.put("role", "user");
        blankContent.put("content", " ");
        Map<String, String> normalContent = new HashMap<>();
        normalContent.put("role", "user");
        normalContent.put("content", "x");

        Object fallback = invokePrivateStatic(
                OpenSearchTokenizeService.class,
                "fallbackResponse",
                new Class<?>[]{List.class},
                Arrays.asList(null, nullContent, blankContent, normalContent)
        );
        assertEquals(1,
                ((com.example.EnterpriseRagCommunity.dto.ai.OpenSearchTokenizeResponse) fallback).getUsage().getInputTokens());

        Object cjkExtGUpperOutside = invokePrivateStatic(
                OpenSearchTokenizeService.class,
                "isCjkLike",
                new Class<?>[]{int.class},
                0x2FA20
        );
        Object hangulUpperOutside = invokePrivateStatic(
                OpenSearchTokenizeService.class,
                "isCjkLike",
                new Class<?>[]{int.class},
                0xD7B0
        );
        assertEquals(false, cjkExtGUpperOutside);
        assertEquals(false, hangulUpperOutside);
    }

    private static Object invokePrivateStatic(Class<?> clazz, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = clazz.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
