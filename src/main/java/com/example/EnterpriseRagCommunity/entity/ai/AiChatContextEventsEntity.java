package com.example.EnterpriseRagCommunity.entity.ai;

import com.example.EnterpriseRagCommunity.entity.converter.JsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "ai_chat_context_events")
public class AiChatContextEventsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "question_message_id")
    private Long questionMessageId;

    @Column(name = "kind", nullable = false, length = 32)
    private String kind;

    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    @Column(name = "target_prompt_tokens")
    private Integer targetPromptTokens;

    @Column(name = "reserve_answer_tokens")
    private Integer reserveAnswerTokens;

    @Column(name = "before_tokens", nullable = false)
    private Integer beforeTokens;

    @Column(name = "after_tokens", nullable = false)
    private Integer afterTokens;

    @Column(name = "before_chars", nullable = false)
    private Integer beforeChars;

    @Column(name = "after_chars", nullable = false)
    private Integer afterChars;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Convert(converter = JsonConverter.class)
    @Column(name = "detail_json", columnDefinition = "LONGTEXT")
    private Map<String, Object> detailJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
