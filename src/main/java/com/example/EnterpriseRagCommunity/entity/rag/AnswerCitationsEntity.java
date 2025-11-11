package com.example.EnterpriseRagCommunity.entity.rag;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "answer_citations")
public class AnswerCitationsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "chunk_id")
    private Long chunkId;

    @Lob
    @Column(name = "quote_text")
    private String quoteText;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "start_offset")
    private Integer startOffset;

    @Column(name = "end_offset")
    private Integer endOffset;

    @Column(name = "score")
    private Double score;
}
