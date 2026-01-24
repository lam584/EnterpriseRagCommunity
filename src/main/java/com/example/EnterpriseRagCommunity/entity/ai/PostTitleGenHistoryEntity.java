package com.example.EnterpriseRagCommunity.entity.ai;

import com.example.EnterpriseRagCommunity.entity.converter.JsonConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "post_title_gen_history")
public class PostTitleGenHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "board_name", length = 128)
    private String boardName;

    @Convert(converter = JsonConverter.class)
    @Column(name = "tags_json", columnDefinition = "json")
    private Object tagsJson;

    @Column(name = "requested_count", nullable = false)
    private Integer requestedCount;

    @Column(name = "applied_max_content_chars", nullable = false)
    private Integer appliedMaxContentChars;

    @Column(name = "content_len", nullable = false)
    private Integer contentLen;

    @Column(name = "content_excerpt", length = 512)
    private String contentExcerpt;

    @Convert(converter = JsonConverter.class)
    @Column(name = "titles_json", columnDefinition = "json", nullable = false)
    private Object titlesJson;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "prompt_version")
    private Integer promptVersion;
}

