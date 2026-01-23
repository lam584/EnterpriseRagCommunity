package com.example.EnterpriseRagCommunity.entity.rag;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "qa_turns")
public class QaTurnsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 外键字段统一使用 Long 映射，命名 sessionId，对应 session_id
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    // 问题消息ID，NOT NULL
    @Column(name = "question_message_id", nullable = false)
    private Long questionMessageId;

    // 答案消息ID，可空
    @Column(name = "answer_message_id")
    private Long answerMessageId;

    // 延迟（毫秒），可空
    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "first_token_latency_ms")
    private Integer firstTokenLatencyMs;

    // 上下文窗口ID，可空
    @Column(name = "context_window_id")
    private Long contextWindowId;

    // 创建时间，审计字段，NOT NULL
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
