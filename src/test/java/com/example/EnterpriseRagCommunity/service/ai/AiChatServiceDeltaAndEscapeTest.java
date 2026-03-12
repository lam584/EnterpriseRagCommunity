package com.example.EnterpriseRagCommunity.service.ai;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void supportsThinkingDirectiveModel_should_cover_qwen_variants_and_thinking_block() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("supportsThinkingDirectiveModel", String.class);
        m.setAccessible(true);
        assertEquals(false, (Boolean) m.invoke(null, (String) null));
        assertEquals(false, (Boolean) m.invoke(null, "  "));
        assertEquals(true, (Boolean) m.invoke(null, "openrouter/qwen3-32b"));
        assertEquals(true, (Boolean) m.invoke(null, "provider:qwen-turbo-2025-04-28"));
        assertEquals(false, (Boolean) m.invoke(null, "qwen3-thinking"));
    }

    @Test
    void approxTokens_and_truncateByApproxTokens_should_cover_ascii_and_cjk_boundaries() throws Exception {
        Method approx = AiChatService.class.getDeclaredMethod("approxTokens", String.class);
        approx.setAccessible(true);
        Method truncate = AiChatService.class.getDeclaredMethod("truncateByApproxTokens", String.class, int.class);
        truncate.setAccessible(true);

        assertEquals(0, (Integer) approx.invoke(null, (String) null));
        assertEquals(0, (Integer) approx.invoke(null, ""));
        assertEquals(1, (Integer) approx.invoke(null, "abcd"));
        assertEquals(2, (Integer) approx.invoke(null, "ab你"));

        assertEquals("", (String) truncate.invoke(null, (String) null, 5));
        assertEquals("", (String) truncate.invoke(null, "abc", 0));
        assertEquals("abcd", (String) truncate.invoke(null, "abcdXYZ", 1));
        assertEquals("你ab", (String) truncate.invoke(null, "你ab", 2));
    }

    @Test
    void isCitationQuote_and_findCitationQuoteClose_should_cover_newline_and_escape_paths() throws Exception {
        Method isQuote = AiChatService.class.getDeclaredMethod("isCitationQuote", char.class);
        isQuote.setAccessible(true);
        Method findClose = AiChatService.class.getDeclaredMethod("findCitationQuoteClose", String.class, int.class);
        findClose.setAccessible(true);

        assertEquals(true, (Boolean) isQuote.invoke(null, '"'));
        assertEquals(true, (Boolean) isQuote.invoke(null, '“'));
        assertEquals(true, (Boolean) isQuote.invoke(null, '』'));
        assertEquals(false, (Boolean) isQuote.invoke(null, 'a'));

        assertEquals(-1, (Integer) findClose.invoke(null, "abc\n\"[1]", 0));
        assertEquals(3, (Integer) findClose.invoke(null, "ab\\\"c[1]", 0));
        assertEquals(2, (Integer) findClose.invoke(null, "ab」x[1]", 0));
    }

    @Test
    void normalizeCitationQuoteFormatting_should_convert_citation_quotes_and_preserve_code_blocks() throws Exception {
        Method normalize = AiChatService.class.getDeclaredMethod("normalizeCitationQuoteFormatting", String.class);
        normalize.setAccessible(true);

        assertNull((String) normalize.invoke(null, (String) null));
        assertEquals(" ", (String) normalize.invoke(null, " "));
        assertEquals("前缀“引用内容”[1]后缀", (String) normalize.invoke(null, "前缀\"引用内容\"[1]后缀"));
        assertEquals("x\"y\"", (String) normalize.invoke(null, "x\\\"y\\\""));
        assertEquals("`\"code\"[2]`", (String) normalize.invoke(null, "`\"code\"[2]`"));
        assertEquals("```\"fence\"[3]```", (String) normalize.invoke(null, "```\"fence\"[3]```"));
    }

    @Test
    void extractDeltaStringField_should_cover_null_field_empty_colon_and_quote_paths() {
        assertNull(AiChatService.extractDeltaStringField(null, "content"));
        assertNull(AiChatService.extractDeltaStringField("{\"content\":\"x\"}", " "));
        assertNull(AiChatService.extractDeltaStringField("{\"content\"}", "content"));
        assertNull(AiChatService.extractDeltaStringField("{\"content\":1}", "content"));
        assertNull(AiChatService.extractDeltaStringField("{\"content\":\"x\"}", null));
    }

    @Test
    void decodeEscapedContent_should_cover_remaining_escape_types() throws Exception {
        Method m = AiChatService.class.getDeclaredMethod("decodeEscapedContent", String.class);
        m.setAccessible(true);
        String out = (String) m.invoke(null, "\\/\\b\\f\\r\\t\\u4f60");
        assertEquals("/\b\f\r\t你", out);
        assertEquals("\\", (String) m.invoke(null, "\\\\"));
    }

    @Test
    void citation_quote_helpers_should_cover_all_quote_variants() throws Exception {
        Method open = AiChatService.class.getDeclaredMethod("isCitationOpenQuote", char.class);
        open.setAccessible(true);

        assertEquals(true, (Boolean) open.invoke(null, '“'));
        assertEquals(false, (Boolean) open.invoke(null, '‘'));
        assertEquals(true, (Boolean) open.invoke(null, '「'));
        assertEquals(true, (Boolean) open.invoke(null, '『'));
        assertEquals(true, (Boolean) open.invoke(null, '"'));
    }

    @Test
    void normalizeCitationQuoteFormatting_should_keep_non_citation_quotes() throws Exception {
        Method normalize = AiChatService.class.getDeclaredMethod("normalizeCitationQuoteFormatting", String.class);
        normalize.setAccessible(true);
        assertEquals("普通\"引用\"文本", (String) normalize.invoke(null, "普通\"引用\"文本"));
        assertEquals("“已是中文引号”[2]", (String) normalize.invoke(null, "“已是中文引号”[2]"));
    }
}
