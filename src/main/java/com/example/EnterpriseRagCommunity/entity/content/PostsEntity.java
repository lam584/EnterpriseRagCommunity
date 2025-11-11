package com.example.EnterpriseRagCommunity.entity.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.ContentFormat;
import com.example.EnterpriseRagCommunity.entity.content.enums.PostStatus;
import com.example.EnterpriseRagCommunity.entity.converter.MapJsonConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "posts")
public class PostsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "title", nullable = false, length = 191)
    private String title;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_format", nullable = false, length = 16)
    private ContentFormat contentFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PostStatus status;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Convert(converter = MapJsonConverter.class)
    @Column(name = "metadata", columnDefinition = "json")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
