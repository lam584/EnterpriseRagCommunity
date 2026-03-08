package com.example.EnterpriseRagCommunity.service.retrieval.es;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RagPostsIndexServiceTest {

    @Test
    void extractEmbeddingDims_shouldHandleDifferentMappingShapes() {
        Map<String, Object> props = new HashMap<>();
        props.put("embedding", Map.of("dims", 3));
        Map<String, Object> m1 = new HashMap<>();
        m1.put("properties", props);
        assertEquals(3, RagPostsIndexService.extractEmbeddingDims(m1));

        Map<String, Object> props2 = new HashMap<>();
        props2.put("embedding", Map.of("dims", " 1536 "));
        Map<String, Object> m2 = new HashMap<>();
        m2.put("mappings", Map.of("properties", props2));
        assertEquals(1536, RagPostsIndexService.extractEmbeddingDims(m2));

        Map<String, Object> props3 = new HashMap<>();
        props3.put("embedding", Map.of("dims", " "));
        Map<String, Object> m3 = new HashMap<>();
        m3.put("properties", props3);
        assertNull(RagPostsIndexService.extractEmbeddingDims(m3));

        Map<String, Object> props4 = new HashMap<>();
        props4.put("embedding", Map.of("dims", "abc"));
        Map<String, Object> m4 = new HashMap<>();
        m4.put("properties", props4);
        assertNull(RagPostsIndexService.extractEmbeddingDims(m4));

        assertNull(RagPostsIndexService.extractEmbeddingDims(null));
    }
}

