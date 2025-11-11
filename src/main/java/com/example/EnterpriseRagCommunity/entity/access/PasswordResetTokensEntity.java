package com.example.EnterpriseRagCommunity.entity.access;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "password_reset_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_prt_user_token", columnNames = {"user_id", "token_hash"})
)
public class PasswordResetTokensEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 191)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
