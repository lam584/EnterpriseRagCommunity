package com.example.EnterpriseRagCommunity.service.retrieval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagValueSupportTest {

    @Test
    void resolveIndexBuildChunking_shouldClampDefaultsAndUpperBounds() {
        RagValueSupport.ChunkingParams defaults = RagValueSupport.resolveIndexBuildChunking(null, null);
        assertEquals(800, defaults.maxChars());
        assertEquals(80, defaults.overlap());

        RagValueSupport.ChunkingParams bounded = RagValueSupport.resolveIndexBuildChunking(9000, 9999);
        assertEquals(5000, bounded.maxChars());
        assertEquals(4999, bounded.overlap());
    }

    @Test
    void resolveChunkingParams_shouldRespectCustomDefaults() {
        RagValueSupport.ChunkingParams params = RagValueSupport.resolveChunkingParams(100, -1, 1200, 8000, 120);
        assertEquals(1200, params.maxChars());
        assertEquals(120, params.overlap());
    }
}
