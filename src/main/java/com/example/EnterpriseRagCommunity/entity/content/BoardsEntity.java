package com.example.EnterpriseRagCommunity.entity.content;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "boards")
public class BoardsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Replace entity reference with Long foreign key id
    @Column(name = "tenant_id")
    private Long tenantId;

    // Replace self-reference entity with Long foreign key id
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "visible", nullable = false)
    private Boolean visible;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
