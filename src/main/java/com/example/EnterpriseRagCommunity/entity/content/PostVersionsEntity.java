package com.example.EnterpriseRagCommunity.entity.content;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "post_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pv_post_version", columnNames = {"post_id", "version"})
})
public class PostVersionsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 替换 ManyToOne 外键为 Long 类型字段，符合 xxxId 规范
    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "editor_id")
    private Long editorId;

    @Column(name = "title", nullable = false, length = 191)
    private String title;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
