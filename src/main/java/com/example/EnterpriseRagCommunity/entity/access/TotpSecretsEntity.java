package com.example.EnterpriseRagCommunity.entity.access;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "totp_secrets")
public class TotpSecretsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 使用外键ID字段而非对象引用
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Lob
    @Column(name = "secret_encrypted", nullable = false)
    private byte[] secretEncrypted;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
