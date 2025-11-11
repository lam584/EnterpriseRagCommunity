package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.AuthSessionsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuthSessionsRepository extends JpaRepository<AuthSessionsEntity, Long>, JpaSpecificationExecutor<AuthSessionsEntity> {
    List<AuthSessionsEntity> findByUserId(Long userId);
    Optional<AuthSessionsEntity> findByRefreshTokenHash(String refreshTokenHash);
    List<AuthSessionsEntity> findByExpiresAtBefore(LocalDateTime time);
    List<AuthSessionsEntity> findByRevokedAtIsNullAndExpiresAtAfter(LocalDateTime time);
}
