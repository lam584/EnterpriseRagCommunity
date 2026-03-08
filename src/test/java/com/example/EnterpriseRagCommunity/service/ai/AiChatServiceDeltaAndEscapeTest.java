package com.example.EnterpriseRagCommunity.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class AiChatServiceDeltaAndEscapeTest {
    @Test
    void extractDeltaContent_should_unescape_common_escapes() {
        String json = "{\"choices\":[{\"delta\":{\"content\":\"hi\\\\n\\\\t\\\\\\\"\\\\u4f60\\\\u597d\"}}]}";
        assertEquals("hi\n\t\"你好", AiChatService.extractDeltaContent(json));
    }

    @Test
    void extractDeltaContent_should_return_null_when_field_missing() {
        assertNull(AiChatService.extractDeltaContent("{\"choices\":[{\"delta\":{}}]}"));
    }

    @Test
    void extractDeltaContent_should_sanitize_reasoning_marker() {
        String json = "{\"choices\":[{\"delta\":{\"content\":\"reasoning_content\"}}]}";
        assertEquals("", AiChatService.extractDeltaContent(json));
    }

    @Test
    void extractDeltaReasoningContent_should_extract_reasoning_content() {
        String json = "{\"choices\":[{\"delta\":{\"reasoning_content\":\"step1\"}}]}";
        assertEquals("step1", AiChatService.extractDeltaReasoningContent(json));
    }

    @Test
    void extractDeltaStringField_should_ignore_invalid_unicode_and_keep_parsing() {
        String json = "{\"choices\":[{\"delta\":{\"content\":\"a\\\\uZZZZb\"}}]}";
        assertEquals("a" + "b", AiChatService.extractDeltaStringField(json, "content"));
    }

    @Test
    void jsonEscape_should_escape_controls_quotes_and_backslashes() {
        String raw = "\"a\\b\n\r\t";
        assertEquals("\\\"a\\\\b\\n\\r\\t", AiChatService.jsonEscape(raw));
    }

    @Test
    void extractDeltaStringField_should_keep_unknown_escape_char() {
        String json = "{\"choices\":[{\"delta\":{\"content\":\"a\\\\qb\"}}]}";
        assertEquals("aqb", AiChatService.extractDeltaStringField(json, "content"));
    }

    @Test
    void decodeEscapedContent_should_handle_unknown_escape_and_short_unicode() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("decodeEscapedContent", String.class);
        m.setAccessible(true);
        assertEquals("aqb", (String) m.invoke(null, "a\\qb"));
        assertEquals("x1y", (String) m.invoke(null, "x\\u1y"));
    }

    @Test
    void jsonEscape_should_cover_all_cases_in_one_input() {
        String in = "\"\\\b\f\n\r\t" + ((char) 0x01) + "A";
        String out = AiChatService.jsonEscape(in);
        assertTrue(out.contains("\\\""));
        assertTrue(out.contains("\\\\"));
        assertTrue(out.contains("\\b"));
        assertTrue(out.contains("\\f"));
        assertTrue(out.contains("\\n"));
        assertTrue(out.contains("\\r"));
        assertTrue(out.contains("\\t"));
        assertTrue(out.contains("\\u0001"));
        assertTrue(out.contains("A"));
    }

    @Test
    void applyThinkingDirective_should_not_duplicate_existing_directive() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("applyThinkingDirective", String.class, boolean.class, String.class);
        m.setAccessible(true);
        String out = (String) m.invoke(null, "hello\n/think", true, "qwen3-30b");
        assertEquals("hello\n/think", out);
    }

    @Test
    void applyThinkingDirective_should_append_on_newline_boundary() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("applyThinkingDirective", String.class, boolean.class, String.class);
        m.setAccessible(true);
        String out = (String) m.invoke(null, "hello\n", false, "qwen3-30b");
        assertEquals("hello\n/no_think", out);
    }
}
