package com.example.EnterpriseRagCommunity.entity.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.CommentStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "comments")
public class CommentsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 外键统一使用 xxxId(Long) 明确映射，不使用对象引用
    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CommentStatus status;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
