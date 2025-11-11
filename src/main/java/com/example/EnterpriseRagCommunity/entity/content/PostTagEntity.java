package com.example.EnterpriseRagCommunity.entity.content;

import com.example.EnterpriseRagCommunity.entity.content.enums.PostTagSource;
import io.swagger.annotations.ApiModelProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for post_tags association table.
 * Composite PK: (post_id, tag_id, source)
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "post_tags")
@IdClass(PostTagEntity.PostTagKey.class)
public class PostTagEntity {

    @Id
    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Id
    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private PostTagSource source;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt;

    /** Composite key holder for IdClass */
    @Data
    @NoArgsConstructor
    public static class PostTagKey implements Serializable {
        private Long postId;
        private Long tagId;
        private PostTagSource source;
    }
}

