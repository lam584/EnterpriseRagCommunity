package com.example.EnterpriseRagCommunity.service.retrieval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPostIndexBuildServiceDimsTest {

    @Test
    void validateEmbeddingDims_shouldUseValueEquality_notReferenceEquality() {
        Integer configured = Integer.valueOf(1024);
        Integer inferred = Integer.valueOf(1024);

        assertDoesNotThrow(() -> RagPostIndexBuildService.validateEmbeddingDims(configured, inferred));
    }

    @Test
    void validateEmbeddingDims_shouldThrowWhenMismatch() {
        Integer configured = 1024;
        Integer inferred = 768;

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> RagPostIndexBuildService.validateEmbeddingDims(configured, inferred));
        assertTrue(ex.getMessage().contains("embedding dims mismatch"));
    }
}

