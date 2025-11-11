package com.example.EnterpriseRagCommunity.entity.access;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "login_attempts")
public class LoginAttemptsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId; // nullable per SQL (user_id BIGINT NULL)

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "reason", length = 64)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
