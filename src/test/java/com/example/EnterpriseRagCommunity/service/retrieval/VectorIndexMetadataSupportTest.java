package com.example.EnterpriseRagCommunity.service.retrieval;

import com.example.EnterpriseRagCommunity.entity.semantic.VectorIndicesEntity;
import com.example.EnterpriseRagCommunity.repository.semantic.VectorIndicesRepository;
import com.example.EnterpriseRagCommunity.entity.semantic.enums.VectorIndexStatus;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorIndexMetadataSupportTest {

    @Test
    void touchMetadata_shouldHandleNullAndPersistMutatedCopy() {
        VectorIndicesRepository repository = mock(VectorIndicesRepository.class);
        VectorIndicesEntity entity = new VectorIndicesEntity();
        entity.setId(9L);
        entity.setMetadata(new HashMap<>(Map.of("old", 1)));
        when(repository.findById(9L)).thenReturn(Optional.of(entity));

        VectorIndexMetadataSupport.touchMetadata(9L, repository, meta -> meta.put("new", 2));

        assertEquals(1, entity.getMetadata().get("old"));
        assertEquals(2, entity.getMetadata().get("new"));
        verify(repository).save(entity);

        VectorIndexMetadataSupport.touchMetadata(null, repository, meta -> meta.put("x", 1));
        verify(repository, never()).findById(null);
    }

    @Test
    void touchMetadata_shouldIgnoreMissingEntityAndNullMutator() {
        VectorIndicesRepository repository = mock(VectorIndicesRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.empty());
        VectorIndexMetadataSupport.touchMetadata(1L, repository, null);
        verify(repository, never()).save(any());

        VectorIndicesEntity entity = new VectorIndicesEntity();
        entity.setId(2L);
        when(repository.findById(2L)).thenReturn(Optional.of(entity));
        VectorIndexMetadataSupport.touchMetadata(2L, repository, null);
        assertTrue(entity.getMetadata().isEmpty());
        verify(repository).save(entity);
    }

    @Test
    void prepareBuildMetadata_shouldApplyCompletionStateAndCommonMetadata() {
        VectorIndicesRepository repository = mock(VectorIndicesRepository.class);
        VectorIndicesEntity entity = new VectorIndicesEntity();
        entity.setId(3L);
        entity.setMetadata(new HashMap<>(Map.of("embeddingModel", "old", "keep", 1)));

        Map<String, Object> meta = VectorIndexMetadataSupport.prepareBuildMetadata(
                entity,
                repository,
                768,
                0,
                "idx_posts",
                "POST"
        );

        assertEquals(768, entity.getDim());
        assertEquals("cosine", entity.getMetric());
        assertEquals(VectorIndexStatus.READY, entity.getStatus());
        assertEquals(1, meta.get("keep"));
        assertEquals("idx_posts", meta.get("esIndex"));
        assertEquals("POST", meta.get("sourceType"));
        assertTrue(!meta.containsKey("embeddingModel"));
    }

    @Test
    void applyBuildCompletionState_shouldPersistErrorBeforeThrowingOnDimMismatch() {
        VectorIndicesRepository repository = mock(VectorIndicesRepository.class);
        VectorIndicesEntity entity = new VectorIndicesEntity();
        entity.setId(4L);
        entity.setDim(512);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> VectorIndexMetadataSupport.applyBuildCompletionState(entity, repository, 768, 0)
        );

        assertTrue(ex.getMessage().contains("vector index dim mismatch"));
        assertEquals(VectorIndexStatus.ERROR, entity.getStatus());
        verify(repository).save(entity);
    }
}
