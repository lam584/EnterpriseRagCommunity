package com.example.EnterpriseRagCommunity.entity.semantic;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "document_chunks")
public class DocumentChunksEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Lob
    @Column(name = "content_text", nullable = false)
    private String contentText;

    @Column(name = "content_tokens")
    private Integer contentTokens;

    @Column(name = "embedding_provider", length = 64)
    private String embeddingProvider;

    @Column(name = "embedding_dim")
    private Integer embeddingDim;

    // BLOB 向量
    @Lob
    @Column(name = "embedding_vector")
    private byte[] embeddingVector;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
