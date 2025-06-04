package com.example.FinalAssignments.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "fines_rules")
public class FineRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 规则描述 */
    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "day_min", nullable = false)
    private Integer dayMin;

    @Column(name = "day_max", nullable = false)
    private Integer dayMax;

    /** 罚款/天 */
    @Column(name = "fine_per_day", nullable = false, precision = 10, scale = 2)
    private BigDecimal finePerDay;

    /** 规则是否有效 */
    @Column(nullable = false)
    private Boolean status;

    /** 管理员 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Administrator admin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}