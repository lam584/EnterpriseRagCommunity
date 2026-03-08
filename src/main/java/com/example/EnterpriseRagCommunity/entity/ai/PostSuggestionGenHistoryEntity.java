package com.example.EnterpriseRagCommunity.entity.ai;

import com.example.EnterpriseRagCommunity.entity.converter.JsonConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "post_suggestion_gen_history")
public class PostSuggestionGenHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private SuggestionKind kind;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "board_name", length = 128)
    private String boardName;

    @Column(name = "title_excerpt", length = 256)
    private String titleExcerpt;

    @Convert(converter = JsonConverter.class)
    @Column(name = "input_tags_json", columnDefinition = "json")
    private Object inputTagsJson;

    @Column(name = "requested_count", nullable = false)
    private Integer requestedCount;

    @Column(name = "applied_max_content_chars", nullable = false)
    private Integer appliedMaxContentChars;

    @Column(name = "content_len", nullable = false)
    private Integer contentLen;

    @Column(name = "content_excerpt", length = 512)
    private String contentExcerpt;

    @Convert(converter = JsonConverter.class)
    @Column(name = "output_json", columnDefinition = "json", nullable = false)
    private Object outputJson;

    @Column(name = "job_id")
    private Long jobId;
}
