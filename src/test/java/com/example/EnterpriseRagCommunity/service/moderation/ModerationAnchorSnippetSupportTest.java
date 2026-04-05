package com.example.EnterpriseRagCommunity.service.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class ModerationAnchorSnippetSupportTest {

    @Test
    void pickBestRegexSnippet_prefersShortestCleanedMatch() {
        Matcher matcher = Pattern.compile("before(.{0,20}?)after", Pattern.DOTALL)
                .matcher("before too long after before ok after");

        String result = ModerationAnchorSnippetSupport.pickBestRegexSnippet(
                matcher,
                "before too long after before ok after",
                "before",
                String::trim,
                (text, idx) -> "fallback:" + idx
        );

        assertEquals("ok", result);
    }

    @Test
    void pickBestRegexSnippet_returnsNullWhenMatchedButAllCleanedBlank() {
        Matcher matcher = Pattern.compile("before(.{0,20}?)after", Pattern.DOTALL)
                .matcher("before    after");

        String result = ModerationAnchorSnippetSupport.pickBestRegexSnippet(
                matcher,
                "before    after",
                "before",
                value -> "   ",
                (text, idx) -> "fallback:" + idx
        );

        assertNull(result);
    }

    @Test
    void pickBestRegexSnippet_usesFallbackWhenNoMatchExists() {
        Matcher matcher = Pattern.compile("before(.{0,20}?)after", Pattern.DOTALL)
                .matcher("before only");

        String result = ModerationAnchorSnippetSupport.pickBestRegexSnippet(
                matcher,
                "before only",
                "before",
                String::trim,
                (text, idx) -> "fallback:" + idx
        );

        assertEquals("fallback:6", result);
    }

    @Test
    void extractBetweenAnchorsByRegex_handlesBoundaryAndNormalization() {
        String result = ModerationAnchorSnippetSupport.extractBetweenAnchorsByRegex(
                " “before” target text。after",
                "\"before\"",
                null,
                40,
                String::trim,
                (text, idx) -> "fallback:" + idx
        );

        assertEquals("target text", result);
        assertEquals("\\Qbefore\\E\\s+\\Qcontext\\E", ModerationAnchorSnippetSupport.anchorToRegex(" before   context "));
        assertEquals("\"abc\"and'def'", ModerationAnchorSnippetSupport.normalizeForAnchorRegex(" “abc” and ‘def’ "));
    }
}
