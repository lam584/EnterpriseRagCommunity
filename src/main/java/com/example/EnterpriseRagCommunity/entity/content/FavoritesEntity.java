package com.example.EnterpriseRagCommunity.entity.content;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "favorites", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "post_id"}))
public class FavoritesEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 外键使用 Long xxxId，不使用对象引用
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
