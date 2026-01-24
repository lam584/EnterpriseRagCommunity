package com.example.EnterpriseRagCommunity.entity.access;

import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "email_verifications", indexes = {
        @Index(name = "idx_ev_user", columnList = "user_id")
})
public class EmailVerificationsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 外键以 Long userId 形式存在，遵守规范，不使用对象引用
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "target_email", length = 191)
    private String targetEmail;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private EmailVerificationPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
