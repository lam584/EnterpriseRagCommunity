package com.example.EnterpriseRagCommunity.service.access;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.EnterpriseRagCommunity.entity.access.EmailVerificationsEntity;
import com.example.EnterpriseRagCommunity.entity.access.enums.EmailVerificationPurpose;
import com.example.EnterpriseRagCommunity.repository.access.EmailVerificationsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final SecureRandom random = new SecureRandom();

    private final EmailVerificationsRepository emailVerificationsRepository;

    @Transactional
    public String issueCode(Long userId, EmailVerificationPurpose purpose) {
        return issueCode(userId, purpose, Duration.ofMinutes(10), Duration.ofSeconds(30));
    }

    @Transactional
    public String issueCode(Long userId, EmailVerificationPurpose purpose, Duration ttl, Duration minInterval) {
        return issueCode(userId, purpose, null, ttl, minInterval);
    }

    @Transactional
    public String issueCode(Long userId, EmailVerificationPurpose purpose, String targetEmail) {
        return issueCode(userId, purpose, targetEmail, Duration.ofMinutes(10), Duration.ofSeconds(30));
    }

    @Transactional
    public String issueCode(Long userId, EmailVerificationPurpose purpose, String targetEmail, Duration ttl, Duration minInterval) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId is required");
        if (purpose == null) throw new IllegalArgumentException("purpose is required");
        if (ttl == null || ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("ttl is invalid");
        if (minInterval == null || minInterval.isNegative()) throw new IllegalArgumentException("minInterval is invalid");

        LocalDateTime now = LocalDateTime.now();
        if (!minInterval.isZero()) {
            emailVerificationsRepository.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose).ifPresent(last -> {
                LocalDateTime createdAt = last.getCreatedAt();
                if (createdAt != null && createdAt.isAfter(now.minus(minInterval))) {
                    throw new IllegalArgumentException("发送过于频繁，请稍后重试");
                }
            });
        }

        String code = String.format("%06d", random.nextInt(1_000_000));

        EmailVerificationsEntity e = new EmailVerificationsEntity();
        e.setUserId(userId);
        e.setPurpose(purpose);
        e.setCode(code);
        e.setTargetEmail(targetEmail == null || targetEmail.isBlank() ? null : targetEmail.trim());
        e.setCreatedAt(now);
        e.setExpiresAt(now.plus(ttl));
        e.setConsumedAt(null);
        emailVerificationsRepository.save(e);

        return code;
    }

    @Transactional
    public void verifyAndConsume(Long userId, EmailVerificationPurpose purpose, String code) {
        verifyAndConsume(userId, purpose, null, code);
    }

    @Transactional
    public void verifyAndConsume(Long userId, EmailVerificationPurpose purpose, String targetEmail, String code) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId is required");
        if (purpose == null) throw new IllegalArgumentException("purpose is required");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");

        LocalDateTime now = LocalDateTime.now();
        Optional<EmailVerificationsEntity> found;
        if (targetEmail == null || targetEmail.isBlank()) {
            found = emailVerificationsRepository
                    .findFirstByUserIdAndPurposeAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, purpose, now);
        } else {
            found = emailVerificationsRepository
                    .findFirstByUserIdAndPurposeAndTargetEmailAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, purpose, targetEmail.trim(), now);
        }
        EmailVerificationsEntity e = found.orElseThrow(() -> new IllegalArgumentException("验证码不存在或已过期"));

        if (!code.trim().equals(e.getCode())) {
            throw new IllegalArgumentException("验证码错误");
        }

        e.setConsumedAt(now);
        emailVerificationsRepository.save(e);
    }
}
