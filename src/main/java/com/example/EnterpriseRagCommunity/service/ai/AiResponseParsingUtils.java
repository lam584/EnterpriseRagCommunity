package com.example.EnterpriseRagCommunity.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

public final class AiResponseParsingUtils {

    private AiResponseParsingUtils() {
    }

    public static String extractAssistantContent(ObjectMapper objectMapper, String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode first = choices.get(0);
                JsonNode contentNode = first.path("message").path("content");
                if (!contentNode.isMissingNode() && contentNode.isTextual()) {
                    return contentNode.asText();
                }
                JsonNode textNode = first.path("text");
                if (!textNode.isMissingNode() && textNode.isTextual()) {
                    return textNode.asText();
                }
            }
        } catch (Exception ignore) {
        }
        return rawJson;
    }

    static Integer readIntLike(JsonNode v) {
        if (v == null || v.isNull()) return null;
        try {
            if (v.isInt() || v.isLong()) return v.intValue();
            if (v.isNumber()) return (int) Math.round(v.doubleValue());
            if (v.isTextual()) {
                String t = v.asText().trim();
                if (t.isEmpty()) return null;
                double n = Double.parseDouble(t);
                if (!Double.isFinite(n)) return null;
                return (int) Math.round(n);
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }

    static List<String> deduplicateAndLimit(List<String> items, int limit) {
        List<String> out = new ArrayList<>(new LinkedHashSet<>(items));
        if (out.size() > limit) {
            out = out.subList(0, limit);
        }
        return out;
    }

    static String extractObjectJson(String text) {
        String json = text == null ? "" : text.trim();
        int left = json.indexOf('{');
        int right = json.lastIndexOf('}');
        if (left >= 0 && right > left) {
            return json.substring(left, right + 1);
        }
        return json;
    }

    static List<String> parseStringArrayField(ObjectMapper objectMapper,
                                              String assistantText,
                                              String fieldName,
                                              Function<String, String> cleaner,
                                              String errorMessage,
                                              int limit) {
        List<String> out = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(extractObjectJson(assistantText));
            JsonNode arr = root.path(fieldName);
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    if (!node.isTextual()) continue;
                    String cleaned = cleaner.apply(node.asText());
                    if (cleaned != null && !cleaned.isBlank()) {
                        out.add(cleaned);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(errorMessage, e);
        }
        return deduplicateAndLimit(out, limit);
    }

    static String normalizeJsonPayload(String text) throws java.io.IOException {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            throw new java.io.IOException("Upstream returned empty response");
        }

        normalized = stripCodeFences(normalized);

        int leftArray = normalized.indexOf('[');
        int leftObject = normalized.indexOf('{');
        if (leftArray >= 0 && (leftObject < 0 || leftArray < leftObject)) {
            int rightArray = normalized.lastIndexOf(']');
            if (rightArray > leftArray) {
                return "{\"results\":" + normalized.substring(leftArray, rightArray + 1) + "}";
            }
        }

        int rightObject = normalized.lastIndexOf('}');
        if (leftObject >= 0 && rightObject > leftObject) {
            return normalized.substring(leftObject, rightObject + 1);
        }

        int rightArray = normalized.lastIndexOf(']');
        if (leftArray >= 0 && rightArray > leftArray) {
            return "{\"results\":" + normalized.substring(leftArray, rightArray + 1) + "}";
        }

        throw new java.io.IOException("Upstream response does not contain JSON: " + shrink(normalized, 240));
    }

    static String stripCodeFences(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.startsWith("```")) {
            int firstNewLine = normalized.indexOf('\n');
            if (firstNewLine >= 0) {
                normalized = normalized.substring(firstNewLine + 1);
                int lastFence = normalized.lastIndexOf("```");
                if (lastFence >= 0) {
                    normalized = normalized.substring(0, lastFence);
                }
            }
        }
        return normalized.trim();
    }

    static String shrink(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxChars - 3)) + "...";
    }
}
