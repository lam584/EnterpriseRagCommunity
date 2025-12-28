package com.example.EnterpriseRagCommunity.entity.content;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "post_views_daily")
@IdClass(PostViewsDailyEntity.Pk.class)
public class PostViewsDailyEntity {

    @Id
    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Id
    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    public static class Pk implements Serializable {
        private Long postId;
        private LocalDate day;
    }
}

