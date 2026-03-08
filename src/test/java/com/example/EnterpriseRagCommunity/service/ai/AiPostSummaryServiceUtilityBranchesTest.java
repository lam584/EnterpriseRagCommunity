package com.example.EnterpriseRagCommunity.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPostSummaryServiceUtilityBranchesTest {

    private final AiPostSummaryService service = new AiPostSummaryService(null, null, null, null, null, null, null);

    @Test
    void extractAssistantContent_should_cover_message_text_and_fallback_paths() throws Exception {
        String fromMessage = (String) invokePrivateInstance(
                service,
                "extractAssistantContent",
                new Class<?>[]{String.class},
                "{\"choices\":[{\"message\":{\"content\":\"inside\"}}]}"
        );
        assertEquals("inside", fromMessage);

        String fromText = (String) invokePrivateInstance(
                service,
                "extractAssistantContent",
                new Class<?>[]{String.class},
                "{\"choices\":[{\"message\":{\"content\":{}},\"text\":\"plain\"}]}"
        );
        assertEquals("plain", fromText);

        String onePassContent = (String) invokePrivateInstance(
                service,
                "extractAssistantContent",
                new Class<?>[]{String.class},
                "{\"content\":\"fallback-c\"}"
        );
        assertEquals("fallback-c", onePassContent);

        String onePassText = (String) invokePrivateInstance(
                service,
                "extractAssistantContent",
                new Class<?>[]{String.class},
                "{\"content\":\"   \",\"text\":\"fallback-t\"}"
        );
        assertEquals("fallback-t", onePassText);

        String rawFallback = (String) invokePrivateInstance(
                service,
                "extractAssistantContent",
                new Class<?>[]{String.class},
                "{\"content\":\"\",\"text\":123}"
        );
        assertEquals("{\"content\":\"\",\"text\":123}", rawFallback);

        String invalidJsonFallback = (String) invokePrivateInstance(
                service,
                "extractAssistantContent",
                new Class<?>[]{String.class},
                "bad-json"
        );
        assertEquals("bad-json", invalidJsonFallback);
    }

    @Test
    void extractJsonStringFieldOnePass_should_cover_all_guard_and_decode_branches() throws Exception {
        assertNull(invokePrivateInstance(
                service,
                "extractJsonStringFieldOnePass",
                new Class<?>[]{String.class, String.class},
                null,
                "x"
        ));

        assertNull(invokePrivateInstance(
                service,
                "extractJsonStringFieldOnePass",
                new Class<?>[]{String.class, String.class},
                "{}",
                "   "
        ));

        assertNull(invokePrivateInstance(
                service,
                "extractJsonStringFieldOnePass",
                new Class<?>[]{String.class, String.class},
                "{\"a\":\"b\"}",
                "x"
        ));

        assertNull(invokePrivateInstance(
                service,
                "extractJsonStringFieldOnePass",
                new Class<?>[]{String.class, String.class},
                "{\"x\" \"b\"}",
                "x"
        ));

        assertNull(invokePrivateInstance(
                service,
                "extractJsonStringFieldOnePass",
                new Class<?>[]{String.class, String.class},
                "{\"x\":123}",
                "x"
        ));

        assertNull(invokePrivateInstance(
                service,
                "extractJsonStringFieldOnePass",
                new Class<?>[]{String.class, String.class},
                "{\"x\":\"unterminated}",
                "x"
        ));

        assertEquals("A\"B", invokePrivateInstance(
                service,
                "extractJsonStringFieldOnePass",
                new Class<?>[]{String.class, String.class},
                "{\"x\":\"A\\\"B\"}",
                "x"
        ));

        assertEquals("\\uZZZZ", invokePrivateInstance(
                service,
                "extractJsonStringFieldOnePass",
                new Class<?>[]{String.class, String.class},
                "{\"x\":\"\\\\uZZZZ\"}",
                "x"
        ));
    }

    @Test
    void parseJsonObjectLenient_should_cover_direct_textual_decoded_and_non_object_paths() throws Exception {
        assertNull(invokePrivateInstance(
                service,
                "parseJsonObjectLenient",
                new Class<?>[]{String.class},
                "   "
        ));

        JsonNode direct = (JsonNode) invokePrivateInstance(
                service,
                "parseJsonObjectLenient",
                new Class<?>[]{String.class},
                "{\"title\":\"T\",\"summary\":\"S\"}"
        );
        assertNotNull(direct);
        assertEquals("T", direct.path("title").asText());

        JsonNode textual = (JsonNode) invokePrivateInstance(
                service,
                "parseJsonObjectLenient",
                new Class<?>[]{String.class},
                "\"{\\\"title\\\":\\\"TX\\\",\\\"summary\\\":\\\"SX\\\"}\""
        );
        assertNotNull(textual);
        assertEquals("SX", textual.path("summary").asText());

        JsonNode decodedCandidate = (JsonNode) invokePrivateInstance(
                service,
                "parseJsonObjectLenient",
                new Class<?>[]{String.class},
                "{\\\"title\\\":\\\"D\\\",\\\"summary\\\":\\\"E\\\"}"
        );
        assertNotNull(decodedCandidate);
        assertEquals("D", decodedCandidate.path("title").asText());

        assertNull(invokePrivateInstance(
                service,
                "parseJsonObjectLenient",
                new Class<?>[]{String.class},
                "[1,2,3]"
        ));
    }

    @Test
    void decodeEscapedContent_should_cover_escape_switch_and_unicode_edges() throws Exception {
        String decoded = (String) invokePrivateStatic(
                AiPostSummaryService.class,
                "decodeEscapedContent",
                new Class<?>[]{String.class},
                "\\\"\\\\\\/\\b\\f\\n\\r\\t\\u4F60\\x"
        );
        assertTrue(decoded.contains("\""));
        assertTrue(decoded.contains("\\"));
        assertTrue(decoded.contains("/"));
        assertTrue(decoded.contains("\b"));
        assertTrue(decoded.contains("\f"));
        assertTrue(decoded.contains("\n"));
        assertTrue(decoded.contains("\r"));
        assertTrue(decoded.contains("\t"));
        assertTrue(decoded.contains("你"));
        assertTrue(decoded.contains("x"));

        String shortUnicode = (String) invokePrivateStatic(
                AiPostSummaryService.class,
                "decodeEscapedContent",
                new Class<?>[]{String.class},
                "\\u12"
        );
        assertEquals("\\u12", shortUnicode);

        String badUnicode = (String) invokePrivateStatic(
                AiPostSummaryService.class,
                "decodeEscapedContent",
                new Class<?>[]{String.class},
                "\\uZZZZ"
        );
        assertEquals("", badUnicode);

        assertEquals("", invokePrivateStatic(
                AiPostSummaryService.class,
                "decodeEscapedContent",
                new Class<?>[]{String.class},
                ""
        ));
    }

    @Test
    void staticHelpers_should_cover_extract_render_tags_clean_and_stacktrace() throws Exception {
        assertNull(invokePrivateStatic(
                AiPostSummaryService.class,
                "extractBetween",
                new Class<?>[]{String.class, String.class, String.class},
                null,
                "a",
                "b"
        ));
        assertNull(invokePrivateStatic(
                AiPostSummaryService.class,
                "extractBetween",
                new Class<?>[]{String.class, String.class, String.class},
                "abc",
                "x",
                "c"
        ));
        assertNull(invokePrivateStatic(
                AiPostSummaryService.class,
                "extractBetween",
                new Class<?>[]{String.class, String.class, String.class},
                "abc",
                "a",
                "x"
        ));
        assertEquals("b", invokePrivateStatic(
                AiPostSummaryService.class,
                "extractBetween",
                new Class<?>[]{String.class, String.class, String.class},
                "abc",
                "a",
                "c"
        ));

        String rendered = (String) invokePrivateStatic(
                AiPostSummaryService.class,
                "renderPrompt",
                new Class<?>[]{String.class, String.class, String.class, String.class},
                null,
                null,
                "C",
                null
        );
        assertEquals("", rendered);

        String rendered2 = (String) invokePrivateStatic(
                AiPostSummaryService.class,
                "renderPrompt",
                new Class<?>[]{String.class, String.class, String.class, String.class},
                "{{title}}|{{content}}|{{tagsLine}}",
                "T",
                null,
                "标签：X"
        );
        assertEquals("T||标签：X", rendered2);

        assertEquals("", invokePrivateStatic(
                AiPostSummaryService.class,
                "extractTagsLine",
                new Class<?>[]{Map.class},
                (Object) null
        ));
        assertEquals("", invokePrivateStatic(
                AiPostSummaryService.class,
                "extractTagsLine",
                new Class<?>[]{Map.class},
                Map.of()
        ));
        assertEquals("", invokePrivateStatic(
                AiPostSummaryService.class,
                "extractTagsLine",
                new Class<?>[]{Map.class},
                Map.of("tags", "x")
        ));
        assertEquals("", invokePrivateStatic(
                AiPostSummaryService.class,
                "extractTagsLine",
                new Class<?>[]{Map.class},
                Map.of("tags", Arrays.asList("", "   ", null))
        ));
        assertEquals("标签：java、ai", invokePrivateStatic(
                AiPostSummaryService.class,
                "extractTagsLine",
                new Class<?>[]{Map.class},
                Map.of("tags", Arrays.asList(" java ", null, "ai"))
        ));

        assertNull(invokePrivateStatic(
                AiPostSummaryService.class,
                "cleanTitle",
                new Class<?>[]{String.class},
                (Object) null
        ));
        assertNull(invokePrivateStatic(
                AiPostSummaryService.class,
                "cleanTitle",
                new Class<?>[]{String.class},
                "   "
        ));
        String cleanedTitle = (String) invokePrivateStatic(
                AiPostSummaryService.class,
                "cleanTitle",
                new Class<?>[]{String.class},
                "\"“" + "T".repeat(210) + "”\""
        );
        assertNotNull(cleanedTitle);
        assertFalse(cleanedTitle.startsWith("\""));
        assertEquals(191, cleanedTitle.length());

        assertNull(invokePrivateStatic(
                AiPostSummaryService.class,
                "cleanSummary",
                new Class<?>[]{String.class},
                (Object) null
        ));
        assertNull(invokePrivateStatic(
                AiPostSummaryService.class,
                "cleanSummary",
                new Class<?>[]{String.class},
                "   "
        ));
        String cleanedSummary = (String) invokePrivateStatic(
                AiPostSummaryService.class,
                "cleanSummary",
                new Class<?>[]{String.class},
                " " + "S".repeat(8100) + " "
        );
        assertNotNull(cleanedSummary);
        assertEquals(8000, cleanedSummary.length());

        assertEquals("", invokePrivateStatic(
                AiPostSummaryService.class,
                "stackTraceToString",
                new Class<?>[]{Throwable.class},
                new Object[]{null}
        ));
        String st = (String) invokePrivateStatic(
                AiPostSummaryService.class,
                "stackTraceToString",
                new Class<?>[]{Throwable.class},
                new IllegalStateException("boom")
        );
        assertTrue(st.contains("boom"));
    }

    private static Object invokePrivateStatic(Class<?> clazz, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = clazz.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Object invokePrivateInstance(Object target, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }
}
