package com.example.EnterpriseRagCommunity.entity.ai;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "post_summary_gen_history")
public class PostSummaryGenHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "applied_max_content_chars", nullable = false)
    private Integer appliedMaxContentChars;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "job_id")
    private Long jobId;
}
