package com.example.EnterpriseRagCommunity.entity.content;

import com.example.EnterpriseRagCommunity.entity.content.id.CommentsClosureId;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@IdClass(CommentsClosureId.class)
@Table(name = "comments_closure")
public class CommentsClosureEntity {
    @Id
    @Column(name = "ancestor_id", nullable = false)
    private Long ancestorId;

    @Id
    @Column(name = "descendant_id", nullable = false)
    private Long descendantId;

    @Column(name = "depth", nullable = false)
    private Integer depth;
}
