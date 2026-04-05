package com.example.EnterpriseRagCommunity.service.retrieval.es;

import java.util.Map;

public final class EmbeddingMappingSupport {

    private EmbeddingMappingSupport() {
    }

    @SuppressWarnings("unchecked")
    public static Integer extractEmbeddingDims(Map<String, Object> mapping) {
        if (mapping == null) {
            return null;
        }
        Object props0 = mapping.get("properties");
        Integer dims = extractEmbeddingDimsFromMappingProperties(props0);
        if (dims != null) {
            return dims;
        }

        Object mappings0 = mapping.get("mappings");
        if (mappings0 instanceof Map<?, ?> mm) {
            Object props1 = ((Map<String, Object>) mm).get("properties");
            return extractEmbeddingDimsFromMappingProperties(props1);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Integer extractEmbeddingDimsFromMappingProperties(Object props0) {
        if (!(props0 instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> props = (Map<String, Object>) props0;
        Object embedding0 = props.get("embedding");
        if (!(embedding0 instanceof Map<?, ?> embedding)) {
            return null;
        }
        Object dims0 = embedding.get("dims");
        if (dims0 instanceof Number number) {
            return number.intValue();
        }
        if (dims0 instanceof String text) {
            try {
                String trimmed = text.trim();
                if (trimmed.isEmpty()) {
                    return null;
                }
                return Integer.parseInt(trimmed);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
