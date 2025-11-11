package com.example.EnterpriseRagCommunity.repository.access;

import com.example.EnterpriseRagCommunity.entity.access.EmailVerificationsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmailVerificationsRepository extends JpaRepository<EmailVerificationsEntity, Long>, JpaSpecificationExecutor<EmailVerificationsEntity> {
    Optional<EmailVerificationsEntity> findByCode(String code);
    List<EmailVerificationsEntity> findByUserIdAndPurposeAndConsumedAtIsNull(Long userId, EmailVerificationPurpose purpose);
    List<EmailVerificationsEntity> findByExpiresAtBefore(LocalDateTime time);
}
