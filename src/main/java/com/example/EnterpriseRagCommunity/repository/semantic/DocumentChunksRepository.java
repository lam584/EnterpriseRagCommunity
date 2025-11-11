package com.example.EnterpriseRagCommunity.repository.semantic;

import com.example.EnterpriseRagCommunity.entity.semantic.DocumentChunksEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentChunksRepository extends JpaRepository<DocumentChunksEntity, Long>, JpaSpecificationExecutor<DocumentChunksEntity> {
    // Foreign key: by document
    List<DocumentChunksEntity> findByDocumentId(Long documentId);

    // Foreign key + index
    Optional<DocumentChunksEntity> findByDocumentIdAndChunkIndex(Long documentId, Integer chunkIndex);

    // Time range
    List<DocumentChunksEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // Type/provider-related
    List<DocumentChunksEntity> findByEmbeddingProvider(String embeddingProvider);
    List<DocumentChunksEntity> findByEmbeddingProviderAndEmbeddingDim(String embeddingProvider, Integer embeddingDim);

    // Content-related
    List<DocumentChunksEntity> findByContentHash(String contentHash);
    List<DocumentChunksEntity> findByContentTokensGreaterThanEqual(Integer minTokens);
    List<DocumentChunksEntity> findByContentTokensBetween(Integer minTokens, Integer maxTokens);
}
