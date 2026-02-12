package com.example.EnterpriseRagCommunity.entity.access;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "roles")
public class RolesEntity {
    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "role_name", nullable = false, length = 128)
    private String roleName;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "risk_level", nullable = false, length = 16)
    private String riskLevel;

    @Column(name = "builtin", nullable = false)
    private Boolean builtin;

    @Column(name = "immutable", nullable = false)
    private Boolean immutable;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

