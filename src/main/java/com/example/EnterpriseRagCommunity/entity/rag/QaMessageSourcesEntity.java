package com.example.EnterpriseRagCommunity.entity.rag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "qa_message_sources")
public class QaMessageSourcesEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "source_index", nullable = false)
    private Integer sourceIndex;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "score")
    private Double score;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "url", length = 512)
    private String url;
}
