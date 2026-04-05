package com.example.EnterpriseRagCommunity.service.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;

public final class RagValueSupport {

    private RagValueSupport() {
    }

    public static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isBlank()) return null;
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer integer) return integer;
        if (value instanceof Long longValue) {
            if (longValue < Integer.MIN_VALUE) return Integer.MIN_VALUE;
            if (longValue > Integer.MAX_VALUE) return Integer.MAX_VALUE;
            return longValue.intValue();
        }
        if (value instanceof Number number) return number.intValue();
        try {
            String trimmed = String.valueOf(value).trim();
            if (trimmed.isBlank()) return null;
            return Integer.parseInt(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String sourceType(Map<String, Object> metadata) {
        return metadata == null || metadata.get("sourceType") == null ? null : String.valueOf(metadata.get("sourceType"));
    }

    public static boolean matchesSourceType(VectorIndicesEntity index, String expectedSourceType) {
        if (index == null || index.getId() == null) return false;
        String sourceType = sourceType(index.getMetadata());
        return sourceType != null && expectedSourceType != null && expectedSourceType.equalsIgnoreCase(sourceType.trim());
    }

    public static ChunkingParams resolveIndexBuildChunking(Integer chunkMaxChars, Integer chunkOverlapChars) {
        return resolveChunkingParams(chunkMaxChars, chunkOverlapChars, 800, 5000, 80);
    }

    public static ChunkingParams resolveChunkingParams(
            Integer chunkMaxChars,
            Integer chunkOverlapChars,
            int defaultMaxChars,
            int maxAllowedChars,
            int defaultOverlapChars
    ) {
        int maxChars = chunkMaxChars == null || chunkMaxChars < 200 ? defaultMaxChars : Math.min(maxAllowedChars, chunkMaxChars);
        int overlap = chunkOverlapChars == null || chunkOverlapChars < 0 ? defaultOverlapChars : Math.min(maxChars - 1, chunkOverlapChars);
        return new ChunkingParams(maxChars, overlap);
    }

    public static List<String> splitWithOverlap(String text, int maxChars, int overlap) {
        String s = text == null ? "" : text.trim();
        if (s.isBlank()) return List.of();
        if (s.length() <= maxChars) return List.of(s);
        int step = Math.max(1, maxChars - Math.max(0, overlap));
        List<String> out = new ArrayList<>();
        for (int start = 0; start < s.length(); start += step) {
            int end = Math.min(s.length(), start + maxChars);
            String part = s.substring(start, end).trim();
            if (!part.isBlank()) out.add(part);
            if (end >= s.length()) break;
        }
        return out;
    }

    public record ChunkingParams(int maxChars, int overlap) {
    }
}
