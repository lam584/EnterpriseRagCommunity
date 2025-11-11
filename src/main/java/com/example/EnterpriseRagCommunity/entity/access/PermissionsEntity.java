package com.example.EnterpriseRagCommunity.entity.access;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "permissions", uniqueConstraints = @UniqueConstraint(name = "uk_perm", columnNames = {"resource", "action"}))
public class PermissionsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "resource", nullable = false, length = 64)
    private String resource;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "description", length = 255)
    private String description;
}
