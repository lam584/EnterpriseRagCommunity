package com.example.EnterpriseRagCommunity.entity.access;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "auth_sessions")
public class AuthSessionsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "refresh_token_hash", nullable = false, length = 191)
    private String refreshTokenHash;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip", length = 64)
    private String ip;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
