package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.PasswordResetTokensEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokensRepository extends JpaRepository<PasswordResetTokensEntity, Long>, JpaSpecificationExecutor<PasswordResetTokensEntity> {
    Optional<PasswordResetTokensEntity> findByTokenHash(String tokenHash);
    List<PasswordResetTokensEntity> findByUserIdAndConsumedAtIsNull(Long userId);
    List<PasswordResetTokensEntity> findByExpiresAtBefore(LocalDateTime time);
}
